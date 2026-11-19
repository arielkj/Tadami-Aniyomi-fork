package tools;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;

/**
 * Construye el grafo offline de Republica Dominicana (extracto de
 * Geofabrik de Hispaniola: haiti-and-domrep) para la version final
 * del radio. Perfil carro + CH (rutas rapidas). La app abre este
 * mismo grafo con GraphHopper 7.0 — NO cambiar la version sin
 * reconstruir el grafo.
 *
 * v1.0.7: el perfil "car" usa weighting "fastest" (el de serie de
 * GraphHopper, Java puro, SIN Janino, SIN compilacion en tiempo de
 * ejecucion). El CustomModel de 9.1/10.0 NO puede funcionar en este
 * radio: GraphHopper compila el modelo a medida con Janino y Janino
 * genera .class de JVM, que ART no puede cargar en Android
 * ("Cannot compile expression: can't load this type of class file",
 * registro del usuario 19 nov 17:42:18, v1.0.6 con largeHeap: el
 * grafo ya cargaba pero el perfil custom no se podia crear).
 * "fastest" NO existe en 8.0/9.1 (su DefaultWeightingFactory solo
 * acepta "custom") y SI existe en 7.0 (FastestWeighting, Java puro,
 * 0 referencias a Janino). La API es identica en 7.0 (GraphHopper,
 * CHProfile, Profile, GHRequest, ResponsePath, Instruction,
 * Parameters, load()/init()/route()). El grafo se reconstruye en CI
 * con la MISMA 7.0 (:tools) y el id cambia a gh70-dr-v1 para que el
 * radio lo vuelva a copiar en el primer arranque (el formato del
 * grafo depende de la version: el grafo gh91 NO lo abre GH 7.0).
 *
 * Detalle de 7.0: los valores codificados del vehiculo NO se ponen
 * en setEncodedValuesString (su fabrica no conoce "car_access"); se
 * crean con setVehiclesString("car") y en el string van solo los
 * genericos que "fastest" necesita ("road_access": el ponderado
 * rapido lo exige). Verificado con una prueba local en JVM antes de
 * decidir (grafo sintetico de Santo Domingo: 16 nodos, 8 vias, CH
 * preparado, ruta calculada por ALT_ROUTE y por CH sin errores).
 *
 * Uso: ./gradlew :tools:buildGraph -Ppbf=<.osm.pbf> -PgraphDir=<salida>
 */
public class BuildGraph {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Uso: BuildGraph <archivo.osm.pbf> <directorio-grafo>");
            System.exit(2);
        }
        String pbf = args[0];
        String graphDir = args[1];
        System.out.println("Construyendo grafo desde " + pbf + " en " + graphDir);

        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(pbf);
        hopper.setGraphHopperLocation(graphDir);
        // Valores codificados del vehiculo "car" (car_access,
        // car_average_speed, ...) + "road_access", que exige fastest.
        hopper.setVehiclesString("car");
        hopper.setEncodedValuesString("road_access");
        // Perfil "car" con el ponderado rapido de serie y CH encima.
        hopper.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        hopper.importOrLoad();
        System.out.println("GRAFO LISTO: " + graphDir);
        System.out.println("Nodos: " + hopper.getBaseGraph().getNodes()
                + " aristas: " + hopper.getBaseGraph().getEdges());
        hopper.close();
    }
}
