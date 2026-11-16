// Archivo de simulacion
package orquitas.herramientas;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import orquitas.servidor.navegacion.DetectorAtasco;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.MemoriaNavegacion;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.OrdenMovimiento;
import orquitas.servidor.navegacion.PreprocesadorTelemetria;
import orquitas.servidor.navegacion.RedMovimiento;
import orquitas.servidor.navegacion.RedPercepcion;
import orquitas.servidor.navegacion.ReflejoSeguridad;
import orquitas.simulacion.bahia.RobotSimulado;
import orquitas.simulacion.bahia.SensorSimulado;
import orquitas.simulacion.bahia.SimuladorBahia;

/**
 * Experimento E4 (no entrena): con redes ya entrenadas, mide qué pasa si el reflejo de seguridad solo
 * acepta la etiqueta LOBITO cuando la percepción le da una probabilidad de al menos un umbral. Por debajo
 * del umbral el sector toma la etiqueta que sigue en probabilidad. La red de movimiento recibe siempre
 * las mismas probabilidades (no cambia); solo cambia lo que ve el reflejo.
 *
 * Uso: EvaluacionUmbralLobito carpetaModelos [pruebas=300] [reporte.md]
 */
public final class EvaluacionUmbralLobito {
    /** 0 = como hoy (la etiqueta más probable); 2 = el reflejo nunca acepta LOBITO. */
    static final double[] UMBRALES = {0, 0.5, 0.6, 0.7, 0.8, 0.9, 2};

    private EvaluacionUmbralLobito() { }

    /** Etiquetas para el reflejo: LOBITO solo si su probabilidad llega al umbral. */
    static EtiquetaSector[] etiquetasConUmbral(double[] probabilidades, double umbral) {
        EtiquetaSector[] etiquetas = RedPercepcion.etiquetas(probabilidades);
        if (umbral <= 0) return etiquetas;
        int lobito = EtiquetaSector.LOBITO.ordinal();
        for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
            if (etiquetas[s] != EtiquetaSector.LOBITO) continue;
            int base = s * RedPercepcion.ETIQUETAS;
            if (probabilidades[base + lobito] >= umbral) continue;
            int mejor = -1;
            for (int e = 0; e < RedPercepcion.ETIQUETAS; e++)
                if (e != lobito && (mejor < 0 || probabilidades[base + e] > probabilidades[base + mejor])) mejor = e;
            etiquetas[s] = EtiquetaSector.values()[mejor];
        }
        return etiquetas;
    }

    private static boolean contiene(EtiquetaSector[] etiquetas, EtiquetaSector buscada) {
        for (EtiquetaSector e : etiquetas) if (e == buscada) return true;
        return false;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) throw new IllegalArgumentException("Uso: EvaluacionUmbralLobito carpetaModelos [pruebas=300] [reporte.md]");
        Path carpeta = Path.of(args[0]);
        int pruebas = args.length > 1 ? Integer.parseInt(args[1]) : 300;
        RedPercepcion percepcion = new RedPercepcion(carpeta.resolve("percepcion.nnet"));
        RedMovimiento movimiento = new RedMovimiento(carpeta.resolve("movimiento.nnet"));
        RobotSimulado.Calibracion cal = RobotSimulado.Calibracion.ESTIMADA;
        StringBuilder tabla = new StringBuilder("# E4: umbral de LOBITO para el reflejo de seguridad\n\n")
                .append("Modelos: `").append(carpeta).append("`; ").append(pruebas).append(" bahías (semillas ")
                .append(EntrenamientoSimulado.SEMILLA_EVALUACION).append("+). Datos sintéticos.\n\n")
                .append("| Umbral | Misión | Capturas | Choques | Puntaje | s | Barridos sin Lobito real donde el reflejo ve uno | Barridos con Lobito real donde ve alguno | Choques al avanzar con Lobito falso al frente |\n")
                .append("|---|---|---|---|---|---|---|---|---|\n");
        for (double umbral : UMBRALES) {
            int liberadas = 0, choquesFalso = 0;
            double capturas = 0, choques = 0, puntaje = 0, segundos = 0;
            long sinLobito = 0, falsos = 0, conLobito = 0, vistos = 0;
            for (int i = 0; i < pruebas; i++) {
                SimuladorBahia sim = SimuladorBahia.alAzar(EntrenamientoSimulado.SEMILLA_EVALUACION + i, EntrenamientoSimulado.CANICAS,
                        EntrenamientoSimulado.UMBRAL, cal, SensorSimulado.Ruido.NORMAL);
                DetectorAtasco atasco = new DetectorAtasco();
                MemoriaNavegacion memoria = new MemoriaNavegacion();
                while (!sim.terminada()) {
                    SensorSimulado.Barrido b = sim.medir();
                    double[] p = percepcion.probabilidades(b.telemetria());
                    EtiquetaSector[] etiquetas = etiquetasConUmbral(p, umbral);
                    boolean real = contiene(b.etiquetas(), EtiquetaSector.LOBITO), visto = contiene(etiquetas, EtiquetaSector.LOBITO);
                    if (sim.modo() == ModoOperativo.BUSQUEDA) {
                        if (real) { conLobito++; if (visto) vistos++; } else { sinLobito++; if (visto) falsos++; }
                    }
                    double[] entradas = RedMovimiento.entradasDelCicloDe(movimiento.variante(), b.telemetria(), p, sim.modo(), memoria);
                    OrdenMovimiento orden = atasco.revisar(b.telemetria(), ReflejoSeguridad.ajustar(movimiento.decidir(entradas), b.telemetria(), etiquetas));
                    boolean falsoAlFrente = false;
                    for (int s = 5; s <= 7; s++)
                        if (etiquetas[s] == EtiquetaSector.LOBITO && b.etiquetas()[s] != EtiquetaSector.LOBITO) falsoAlFrente = true;
                    if (sim.ejecutar(orden).choco() && orden.movimiento() == Movimiento.AVANZAR && falsoAlFrente) choquesFalso++;
                    memoria.registrarOrden(orden);
                }
                if (sim.modo() == ModoOperativo.LIBERADA) liberadas++;
                capturas += sim.capturas(); choques += sim.choques(); puntaje += sim.puntaje(); segundos += sim.segundos();
            }
            String nombre = umbral <= 0 ? "como hoy" : umbral > 1 ? "nunca LOBITO" : String.format(Locale.ROOT, "%.1f", umbral);
            String fila = String.format(Locale.ROOT, "| %s | %d/%d (%.0f %%) | %.2f | %.2f | %.1f | %.0f | %.1f %% | %.1f %% | %d |%n", nombre, liberadas, pruebas,
                    100.0 * liberadas / pruebas, capturas / pruebas, choques / pruebas, puntaje / pruebas, segundos / pruebas,
                    100.0 * falsos / Math.max(1, sinLobito), 100.0 * vistos / Math.max(1, conLobito), choquesFalso);
            System.out.print("[E4] " + fila);
            tabla.append(fila);
        }
        if (args.length > 2) {
            Path reporte = Path.of(args[2]);
            if (reporte.getParent() != null) Files.createDirectories(reporte.getParent());
            Files.writeString(reporte, tabla.toString());
            System.out.println("[E4] Reporte: " + reporte);
        }
    }
}
