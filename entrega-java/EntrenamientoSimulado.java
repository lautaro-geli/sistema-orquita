// Archivo de simulacion
package orquitas.herramientas;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.nnet.learning.MomentumBackpropagation;
import org.neuroph.util.TransferFunctionType;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.OrdenMovimiento;
import orquitas.servidor.navegacion.PreprocesadorTelemetria;
import orquitas.servidor.navegacion.RedMovimiento;
import orquitas.servidor.navegacion.RedPercepcion;
import orquitas.simulacion.bahia.Maestro;
import orquitas.simulacion.bahia.Piloto;
import orquitas.simulacion.bahia.RobotSimulado;
import orquitas.simulacion.bahia.SensorSimulado;
import orquitas.simulacion.bahia.SimuladorBahia;

/**
 * Entrena las dos redes con la Bahía de Caza simulada (no forma parte de los diagramas). Uso:
 * {@code EntrenamientoSimulado [episodios=250] [epocas=60] [carpeta=modelos] [rondasDagger=2]}.
 *
 * 1. El maestro (que conoce la geometría real) maneja el robot en bahías al azar; a veces se ejecuta
 *    una orden al azar para visitar situaciones variadas. Se guarda cada barrido con sus etiquetas
 *    verdaderas por sector y la orden del maestro.
 * 2. Red de percepción por ventana: un sector y 2 vecinos de cada lado -> su etiqueta (46 -> ocultas -> 5),
 *    aplicada a los 13 sectores.
 * 3. Red de movimiento: telemetría + percepción + modo -> dirección y ms (184 -> ocultas -> 6).
 * 4. DAgger: las redes manejan, el maestro dice qué habría hecho en cada barrido, se suman esos
 *    ejemplos y se reentrena (así la red aprende a salir de los errores que ella misma comete).
 * 5. Evaluación en bahías nuevas (semillas que no se usaron para entrenar): redes contra maestro.
 * Guarda percepcion.nnet, movimiento.nnet y un reporte en docs/evidencias/.
 */
public class EntrenamientoSimulado {
    static final long SEMILLA = 20261113L;
    static final long SEMILLA_EVALUACION = 900_000L;
    static final int OCULTAS_PERCEPCION = 24;
    static final int OCULTAS_MOVIMIENTO = 48;
    static final double PROBABILIDAD_ORDEN_AL_AZAR = 0.15;
    static final int CANICAS = 3, UMBRAL = 3;

    /** Un barrido con lo que se sabe de él. */
    record Ejemplo(int[] telemetria, EtiquetaSector[] etiquetas, ModoOperativo modo, OrdenMovimiento orden) { }

    /** Resultado de varias pruebas. */
    record Resumen(int pruebas, int liberadas, double capturas, double choques, double puntaje, double segundos) {
        String fila(String nombre) {
            return String.format(Locale.ROOT, "| %s | %d/%d (%.0f %%) | %.2f | %.2f | %.1f | %.0f |", nombre, liberadas, pruebas,
                    100.0 * liberadas / pruebas, capturas, choques, puntaje, segundos);
        }
    }

    public static void main(String[] args) throws Exception {
        int episodios = args.length > 0 ? Integer.parseInt(args[0]) : 250;
        int epocas = args.length > 1 ? Integer.parseInt(args[1]) : 60;
        Path carpeta = Path.of(args.length > 2 ? args[2] : "modelos");
        int rondas = args.length > 3 ? Integer.parseInt(args[3]) : 2;
        Files.createDirectories(carpeta);
        StringBuilder reporte = new StringBuilder();
        long inicio = System.currentTimeMillis();
        RobotSimulado.Calibracion cal = RobotSimulado.Calibracion.ESTIMADA;

        List<Ejemplo> datos = recolectarConMaestro(episodios, cal);
        log("Ejemplos del maestro: " + datos.size());

        MultiLayerPerceptron percepcion = nuevaRed(RedPercepcion.ENTRADAS, OCULTAS_PERCEPCION, RedPercepcion.SALIDAS_RED);
        // La percepción tiene ~8 filas por barrido (una por sector, balanceadas): necesita menos épocas.
        entrenar(percepcion, datosPercepcion(datos), Math.max(5, epocas / 3), "percepción");
        MultiLayerPerceptron movimiento = nuevaRed(RedMovimiento.ENTRADAS, OCULTAS_MOVIMIENTO, RedMovimiento.SALIDAS);
        entrenar(movimiento, datosMovimiento(datos, new RedPercepcion(percepcion)), epocas, "movimiento");

        for (int ronda = 1; ronda <= rondas; ronda++) {
            List<Ejemplo> nuevos = recolectarConRedes(episodios / 2, cal, new RedPercepcion(percepcion),
                    new RedMovimiento(movimiento), SEMILLA + 10_000L * ronda);
            datos.addAll(nuevos);
            log("DAgger ronda " + ronda + ": +" + nuevos.size() + " ejemplos (total " + datos.size() + ")");
            entrenar(percepcion, datosPercepcion(datos), Math.max(3, epocas / 6), "percepción");
            entrenar(movimiento, datosMovimiento(datos, new RedPercepcion(percepcion)), Math.max(10, epocas / 2), "movimiento");
        }

        Path rutaPercepcion = carpeta.resolve("percepcion.nnet"), rutaMovimiento = carpeta.resolve("movimiento.nnet");
        percepcion.save(rutaPercepcion.toString());
        movimiento.save(rutaMovimiento.toString());

        // Evaluación con bahías nuevas.
        RedPercepcion rp = new RedPercepcion(rutaPercepcion);
        RedMovimiento rm = new RedMovimiento(rutaMovimiento);
        int pruebas = 100;
        Resumen maestro = evaluar(Piloto::maestro, pruebas, cal);
        Resumen redes = evaluar(() -> Piloto.redes(rp, rm), pruebas, cal);
        int[][] confusion = confusionPercepcion(rp, 40, cal);
        double acuerdo = acuerdoDireccion(rp, rm, 40, cal);
        double minutos = (System.currentTimeMillis() - inicio) / 60000.0;

        reporte.append("# Entrenamiento con la Bahía de Caza simulada (").append(LocalDate.now()).append(")\n\n")
               .append("Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: ")
               .append("sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.\n\n")
               .append(String.format(Locale.ROOT, "- Episodios del maestro: %d; rondas DAgger: %d; épocas: %d; ejemplos: %d; tiempo: %.1f min.%n",
                       episodios, rondas, epocas, datos.size(), minutos))
               .append(String.format(Locale.ROOT, "- Calibración usada: %.0f mm/s, %.0f °/s, robot %.0f x %.0f mm [A CONFIRMAR con el robot real].%n",
                       cal.mmPorSegundo(), cal.gradosPorSegundo(), cal.largoMm(), cal.anchoMm()))
               .append(String.format(Locale.ROOT, "- Redes: percepción por ventana %d-%d-%d (x 13 sectores), movimiento %d-%d-%d (sigmoide, MomentumBackpropagation).%n%n",
                       RedPercepcion.ENTRADAS, OCULTAS_PERCEPCION, RedPercepcion.SALIDAS_RED,
                       RedMovimiento.ENTRADAS, OCULTAS_MOVIMIENTO, RedMovimiento.SALIDAS))
               .append("## Pruebas completas (").append(pruebas).append(" bahías nuevas, 7 min, 3 Lobitos)\n\n")
               .append("| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |\n|---|---|---|---|---|---|\n")
               .append(maestro.fila("Maestro (geometría real)")).append('\n')
               .append(redes.fila("Redes (solo telemetría)")).append("\n\n")
               .append(String.format(Locale.ROOT, "Acuerdo de dirección red/maestro en barridos nuevos: %.1f %%.%n%n", acuerdo * 100))
               .append("## Percepción por sector (barridos nuevos)\n\nFilas: etiqueta real. Columnas: etiqueta de la red.\n\n")
               .append(tablaConfusion(confusion));
        Path rutaReporte = Path.of("docs/evidencias/entrenamiento_simulado_"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".md");
        Files.createDirectories(rutaReporte.getParent());
        Files.writeString(rutaReporte, reporte.toString(), StandardCharsets.UTF_8);
        System.out.println(reporte);
        log("Modelos: " + rutaPercepcion + ", " + rutaMovimiento + ". Reporte: " + rutaReporte);
    }

    static void log(String s) {
        System.out.println("[Entrenamiento] " + s);
    }

    static MultiLayerPerceptron nuevaRed(int entradas, int ocultas, int salidas) {
        MultiLayerPerceptron red = new MultiLayerPerceptron(TransferFunctionType.SIGMOID, entradas, ocultas, salidas);
        red.randomizeWeights(new Random(SEMILLA));
        return red;
    }

    static void entrenar(MultiLayerPerceptron red, DataSet datos, int epocas, String nombre) {
        MomentumBackpropagation regla = new MomentumBackpropagation();
        regla.setLearningRate(0.1);
        regla.setMomentum(0.6);
        regla.setMaxIterations(epocas);
        regla.setMaxError(0.001);
        red.setLearningRule(regla);
        long inicio = System.currentTimeMillis();
        red.learn(datos);
        log(String.format(Locale.ROOT, "%s: %d épocas, error %.5f, %.1f min", nombre, regla.getCurrentIteration(),
                regla.getTotalNetworkError(), (System.currentTimeMillis() - inicio) / 60000.0));
    }

    /** DataSet con las filas mezcladas (semilla fija). */
    static DataSet mezclado(int entradas, int salidas, List<DataSetRow> filas) {
        Collections.shuffle(filas, new Random(SEMILLA));
        DataSet ds = new DataSet(entradas, salidas);
        for (DataSetRow f : filas) ds.add(f);
        return ds;
    }

    /**
     * Percepción: una fila por sector (ventana). Para balancear, los sectores LOBITO se repiten 4 veces
     * y de los PARED se toma 1 de cada 2 (son la gran mayoría).
     */
    static DataSet datosPercepcion(List<Ejemplo> datos) {
        List<DataSetRow> filas = new ArrayList<>();
        Random azar = new Random(SEMILLA);
        for (Ejemplo e : datos) {
            double[] entradas = PreprocesadorTelemetria.entradas(e.telemetria());
            for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
                EtiquetaSector et = e.etiquetas()[s];
                int veces = et == EtiquetaSector.LOBITO ? 4 : et == EtiquetaSector.PARED ? (azar.nextInt(2) == 0 ? 1 : 0) : 1;
                if (veces == 0) continue;
                DataSetRow fila = new DataSetRow(PreprocesadorTelemetria.entradasVentana(entradas, s), RedPercepcion.salidaDeseada(et));
                for (int i = 0; i < veces; i++) filas.add(fila);
            }
        }
        return mezclado(RedPercepcion.ENTRADAS, RedPercepcion.SALIDAS_RED, filas);
    }

    /** Movimiento: usa la salida de la red de percepción (no la verdad) para que entrene con lo que verá. */
    static DataSet datosMovimiento(List<Ejemplo> datos, RedPercepcion percepcion) {
        List<DataSetRow> filas = new ArrayList<>();
        for (Ejemplo e : datos) {
            double[] p = percepcion.probabilidades(e.telemetria());
            filas.add(new DataSetRow(RedMovimiento.entradas(e.telemetria(), p, e.modo()), RedMovimiento.salidaDeseada(e.orden())));
        }
        return mezclado(RedMovimiento.ENTRADAS, RedMovimiento.SALIDAS, filas);
    }

    /** El maestro maneja; con cierta probabilidad se ejecuta una orden al azar (pero se guarda la del maestro). */
    static List<Ejemplo> recolectarConMaestro(int episodios, RobotSimulado.Calibracion cal) {
        List<Ejemplo> datos = new ArrayList<>();
        Random azar = new Random(SEMILLA);
        for (int i = 0; i < episodios; i++) {
            int umbral = 1 + azar.nextInt(UMBRAL); // También pruebas con umbral 1 y 2 (más escapes).
            SimuladorBahia sim = SimuladorBahia.alAzar(SEMILLA + i, CANICAS, umbral, cal, SensorSimulado.Ruido.NORMAL);
            while (!sim.terminada()) {
                SensorSimulado.Barrido b = sim.medir();
                OrdenMovimiento orden = Maestro.decidir(sim, b);
                datos.add(new Ejemplo(b.telemetria(), b.etiquetas(), sim.modo(), orden));
                sim.ejecutar(azar.nextDouble() < PROBABILIDAD_ORDEN_AL_AZAR ? ordenAlAzar(azar) : orden);
            }
        }
        return datos;
    }

    /** Las redes manejan (la mitad de las veces el maestro, para no quedar trabadas); se guarda lo que diría el maestro. */
    static List<Ejemplo> recolectarConRedes(int episodios, RobotSimulado.Calibracion cal, RedPercepcion rp, RedMovimiento rm, long semilla) {
        List<Ejemplo> datos = new ArrayList<>();
        Random azar = new Random(semilla);
        for (int i = 0; i < episodios; i++) {
            Piloto redes = Piloto.redes(rp, rm);
            int umbral = 1 + azar.nextInt(UMBRAL);
            SimuladorBahia sim = SimuladorBahia.alAzar(semilla + i, CANICAS, umbral, cal, SensorSimulado.Ruido.NORMAL);
            while (!sim.terminada()) {
                SensorSimulado.Barrido b = sim.medir();
                OrdenMovimiento maestro = Maestro.decidir(sim, b);
                datos.add(new Ejemplo(b.telemetria(), b.etiquetas(), sim.modo(), maestro));
                sim.ejecutar(azar.nextDouble() < 0.5 ? redes.decidir(sim, b).orden() : maestro);
            }
        }
        return datos;
    }

    static OrdenMovimiento ordenAlAzar(Random azar) {
        Movimiento[] m = {Movimiento.AVANZAR, Movimiento.GIRAR_IZQ, Movimiento.GIRAR_DER, Movimiento.RETROCEDER};
        return new OrdenMovimiento(m[azar.nextInt(m.length)], 100 + azar.nextInt(900));
    }

    /** Corre las pruebas con un piloto nuevo por prueba (el detector de atasco tiene memoria). */
    static Resumen evaluar(java.util.function.Supplier<Piloto> fabrica, int pruebas, RobotSimulado.Calibracion cal) {
        int liberadas = 0;
        double capturas = 0, choques = 0, puntaje = 0, segundos = 0;
        for (int i = 0; i < pruebas; i++) {
            SimuladorBahia sim = SimuladorBahia.alAzar(SEMILLA_EVALUACION + i, CANICAS, UMBRAL, cal, SensorSimulado.Ruido.NORMAL);
            Piloto piloto = fabrica.get();
            while (!sim.terminada()) sim.ejecutar(piloto.decidir(sim, sim.medir()).orden());
            if (sim.modo() == ModoOperativo.LIBERADA) liberadas++;
            capturas += sim.capturas();
            choques += sim.choques();
            puntaje += sim.puntaje();
            segundos += sim.segundos();
        }
        return new Resumen(pruebas, liberadas, capturas / pruebas, choques / pruebas, puntaje / pruebas, segundos / pruebas);
    }

    /** Matriz de confusión de la percepción en barridos nuevos (el maestro maneja). */
    static int[][] confusionPercepcion(RedPercepcion rp, int pruebas, RobotSimulado.Calibracion cal) {
        int n = EtiquetaSector.values().length;
        int[][] m = new int[n][n];
        for (int i = 0; i < pruebas; i++) {
            SimuladorBahia sim = SimuladorBahia.alAzar(SEMILLA_EVALUACION + 500 + i, CANICAS, 1 + i % UMBRAL, cal, SensorSimulado.Ruido.NORMAL);
            while (!sim.terminada()) {
                SensorSimulado.Barrido b = sim.medir();
                EtiquetaSector[] red = RedPercepcion.etiquetas(rp.probabilidades(b.telemetria()));
                for (int s = 0; s < red.length; s++) m[b.etiquetas()[s].ordinal()][red[s].ordinal()]++;
                sim.ejecutar(Maestro.decidir(sim, b));
            }
        }
        return m;
    }

    /** Fracción de barridos nuevos en que la red elige la misma dirección que el maestro. */
    static double acuerdoDireccion(RedPercepcion rp, RedMovimiento rm, int pruebas, RobotSimulado.Calibracion cal) {
        int iguales = 0, total = 0;
        for (int i = 0; i < pruebas; i++) {
            SimuladorBahia sim = SimuladorBahia.alAzar(SEMILLA_EVALUACION + 800 + i, CANICAS, 1 + i % UMBRAL, cal, SensorSimulado.Ruido.NORMAL);
            while (!sim.terminada()) {
                SensorSimulado.Barrido b = sim.medir();
                OrdenMovimiento maestro = Maestro.decidir(sim, b);
                OrdenMovimiento red = rm.decidir(b.telemetria(), rp.probabilidades(b.telemetria()), sim.modo());
                if (red.movimiento() == maestro.movimiento()) iguales++;
                total++;
                sim.ejecutar(maestro);
            }
        }
        return total == 0 ? 0 : iguales / (double) total;
    }

    static String tablaConfusion(int[][] m) {
        EtiquetaSector[] e = EtiquetaSector.values();
        StringBuilder sb = new StringBuilder("| real \\ red |");
        for (EtiquetaSector x : e) sb.append(' ').append(x).append(" |");
        sb.append(" acierto |\n|---|");
        for (int i = 0; i <= e.length; i++) sb.append("---|");
        sb.append('\n');
        long aciertos = 0, total = 0;
        for (int i = 0; i < e.length; i++) {
            sb.append("| ").append(e[i]).append(" |");
            int fila = 0;
            for (int j = 0; j < e.length; j++) { sb.append(' ').append(m[i][j]).append(" |"); fila += m[i][j]; }
            sb.append(String.format(Locale.ROOT, " %.1f %% |%n", fila == 0 ? 0 : 100.0 * m[i][i] / fila));
            aciertos += m[i][i];
            total += fila;
        }
        sb.append(String.format(Locale.ROOT, "%nAcierto total por sector: %.1f %%.%n", total == 0 ? 0 : 100.0 * aciertos / total));
        return sb.toString();
    }
}
