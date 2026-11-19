package tools;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.json.Statement;
import com.graphhopper.util.CustomModel;

/**
 * Construye el grafo offline de Republica Dominicana (extracto de
 * Geofabrik de Hispaniola: haiti-and-domrep) para la version final
 * del radio. Perfil carro + CH (rutas rapidas). La app abre este
 * mismo grafo con GraphHopper 9.1 — NO cambiar la version sin
 * reconstruir el grafo.
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

        // Modelo "car" de GraphHopper (igual que car.json del jar):
        // distance_influence 90, sin acceso si !car_access, velocidad
        // limitada a car_average_speed.
        CustomModel carModel = new CustomModel();
        carModel.setDistanceInfluence(90.0);
        carModel.addToPriority(Statement.If("!car_access", Statement.Op.MULTIPLY, "0"));
        carModel.addToSpeed(Statement.If("true", Statement.Op.LIMIT, "car_average_speed"));

        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(pbf);
        hopper.setGraphHopperLocation(graphDir);
        // Valores codificados que necesita el modelo "car".
        hopper.setEncodedValuesString("car_access, car_average_speed");
        hopper.setProfiles(new Profile("car").setWeighting("custom").setCustomModel(carModel));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        hopper.importOrLoad();
        System.out.println("GRAFO LISTO: " + graphDir);
        System.out.println("Nodos: " + hopper.getBaseGraph().getNodes()
                + " aristas: " + hopper.getBaseGraph().getEdges());
        hopper.close();
    }
}
