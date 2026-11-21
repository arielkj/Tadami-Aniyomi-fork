// JNI bridge for on-device Real-ESRGAN cover upscaling (RealESRGAN_x4plus_anime_6B).
// Thin wrapper around the vendored xinntao/Real-ESRGAN-ncnn-vulkan sources
// (realesrgan.cpp/.h + .comp shaders) linked against the ncnn Android Vulkan
// prebuilt. Everything here is best-effort: any failure returns an error code
// and the Kotlin side falls back to the normal cover. Never throws across JNI.
//
// Input/output pixels are Android ARGB int arrays (one int per pixel).
// The model is x4: a 460x650 cover becomes 1840x2600.

#include <jni.h>
#include <android/log.h>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "realesrgan.h"
#include "gpu.h"

#define LOG_TAG "RealESRGAN-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Mobile-safe tuning: smaller tiles than the desktop CLI (400) so peak
// Vulkan memory stays modest on phones. prepadding 10 matches upstream.
static const int kScale = 4;
static const int kTileSize = 200;
static const int kPrePadding = 10;

static std::mutex g_mutex;
static RealESRGAN* g_realesrgan = nullptr;
static std::string g_loadedParam;
static std::string g_loadedBin;

static bool fileReadable(const std::string& path) {
    FILE* fp = fopen(path.c_str(), "rb");
    if (!fp) return false;
    fseek(fp, 0, SEEK_END);
    long size = ftell(fp);
    fclose(fp);
    return size > 0;
}

extern "C" {

// How many Vulkan-capable GPUs ncnn sees. 0 means no Vulkan: AI enhance
// is unavailable on this device and the app must not try it.
JNIEXPORT jint JNICALL
Java_eu_kanade_tachiyomi_data_coil_ai_RealEsrganBridge_nativeGpuCount(JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;
    int count = 0;
    try {
        count = ncnn::get_gpu_count();
    } catch (...) {
        count = 0;
    }
    return count;
}

// Loads the .param/.bin model. Returns 0 on success, -1 when the files
// cannot be read, -2 when the engine cannot be created. Idempotent: calling
// again with the same paths is a no-op.
JNIEXPORT jint JNICALL
Java_eu_kanade_tachiyomi_data_coil_ai_RealEsrganBridge_nativeLoadModel(
    JNIEnv* env, jclass clazz, jstring paramPath, jstring binPath) {
    (void)clazz;
    std::lock_guard<std::mutex> lock(g_mutex);

    const char* paramChars = env->GetStringUTFChars(paramPath, nullptr);
    const char* binChars = env->GetStringUTFChars(binPath, nullptr);
    if (!paramChars || !binChars) {
        if (paramChars) env->ReleaseStringUTFChars(paramPath, paramChars);
        if (binChars) env->ReleaseStringUTFChars(binPath, binChars);
        return -1;
    }
    std::string param(paramChars);
    std::string bin(binChars);
    env->ReleaseStringUTFChars(paramPath, paramChars);
    env->ReleaseStringUTFChars(binPath, binChars);

    if (g_realesrgan != nullptr && g_loadedParam == param && g_loadedBin == bin) {
        return 0;
    }
    if (!fileReadable(param) || !fileReadable(bin)) {
        LOGE("model files not readable: %s / %s", param.c_str(), bin.c_str());
        return -1;
    }
    try {
        if (ncnn::get_gpu_count() <= 0) {
            LOGE("no Vulkan GPU available");
            return -2;
        }
        // Recreate when the paths changed (old instance is process-lifetime;
        // leaking the previous one is intentional: destroying ncnn Vulkan
        // pipelines mid-flight is not safe).
        RealESRGAN* engine = new RealESRGAN(0, false);
        engine->scale = kScale;
        engine->tilesize = kTileSize;
        engine->prepadding = kPrePadding;
        engine->load(param, bin);
        g_realesrgan = engine;
        g_loadedParam = param;
        g_loadedBin = bin;
        LOGI("model loaded: %s", bin.c_str());
        return 0;
    } catch (...) {
        LOGE("exception while loading model");
        return -2;
    }
}

// Upscales one cover. argb is w*h Android ARGB ints; returns a new int
// array of (w*scale)*(h*scale) ARGB ints, or null on any failure. The
// caller must have loaded the model first (nativeLoadModel == 0).
JNIEXPORT jintArray JNICALL
Java_eu_kanade_tachiyomi_data_coil_ai_RealEsrganBridge_nativeEnhance(
    JNIEnv* env, jclass clazz, jintArray argb, jint w, jint h) {
    (void)clazz;
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_realesrgan == nullptr) {
        LOGE("enhance called before loadModel");
        return nullptr;
    }
    if (w <= 0 || h <= 0 || w > 4096 || h > 4096) {
        LOGE("bad input size %dx%d", (int)w, (int)h);
        return nullptr;
    }

    jsize inLen = env->GetArrayLength(argb);
    if (inLen < w * h) {
        return nullptr;
    }
    jint* inPixels = env->GetIntArrayElements(argb, nullptr);
    if (!inPixels) {
        return nullptr;
    }

    jintArray outArray = nullptr;
    try {
        // ARGB int -> RGBA bytes for ncnn::Mat::from_pixels(PIXEL_RGBA).
        std::vector<unsigned char> rgba((size_t)w * h * 4);
        for (int i = 0; i < w * h; i++) {
            uint32_t px = (uint32_t)inPixels[i];
            rgba[(size_t)i * 4 + 0] = (unsigned char)((px >> 16) & 0xff);  // R
            rgba[(size_t)i * 4 + 1] = (unsigned char)((px >> 8) & 0xff);   // G
            rgba[(size_t)i * 4 + 2] = (unsigned char)(px & 0xff);          // B
            rgba[(size_t)i * 4 + 3] = (unsigned char)((px >> 24) & 0xff);  // A
        }
        env->ReleaseIntArrayElements(argb, inPixels, JNI_ABORT);
        inPixels = nullptr;

        ncnn::Mat inimage = ncnn::Mat::from_pixels(
            rgba.data(), ncnn::Mat::PIXEL_RGBA, (int)w, (int)h);

        ncnn::Mat outimage;
        int ret = g_realesrgan->process(inimage, outimage);
        if (ret != 0) {
            LOGE("process failed: %d", ret);
            return nullptr;
        }
        const int outW = outimage.w;
        const int outH = outimage.h;
        if (outW <= 0 || outH <= 0 || outimage.elempack != 4) {
            LOGE("unexpected output %dx%d elempack=%d", outW, outH, outimage.elempack);
            return nullptr;
        }
        const unsigned char* outData = (const unsigned char*)outimage.data;
        if (!outData) {
            return nullptr;
        }

        outArray = env->NewIntArray((jsize)outW * outH);
        if (!outArray) {
            return nullptr;
        }
        std::vector<jint> outPixels((size_t)outW * outH);
        for (int i = 0; i < outW * outH; i++) {
            unsigned char r = outData[(size_t)i * 4 + 0];
            unsigned char g = outData[(size_t)i * 4 + 1];
            unsigned char b = outData[(size_t)i * 4 + 2];
            unsigned char a = outData[(size_t)i * 4 + 3];
            outPixels[i] = (jint)(((uint32_t)a << 24) | ((uint32_t)r << 16) |
                                  ((uint32_t)g << 8) | (uint32_t)b);
        }
        env->SetIntArrayRegion(outArray, 0, (jsize)outW * outH, outPixels.data());
        return outArray;
    } catch (...) {
        LOGE("exception during enhance");
        if (inPixels) {
            env->ReleaseIntArrayElements(argb, inPixels, JNI_ABORT);
        }
        return nullptr;
    }
}

}  // extern "C"
