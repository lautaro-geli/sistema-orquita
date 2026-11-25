// Archivo de simulacion
package orquitas.herramientas;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.Neuron;
import org.neuroph.core.learning.error.ErrorFunction;
import org.neuroph.core.learning.error.MeanSquaredError;
import org.neuroph.core.transfer.Linear;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.nnet.learning.MomentumBackpropagation;
import org.neuroph.util.TransferFunctionType;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.MemoriaNavegacion;
import orquitas.servidor.navegacion.MetaMovimiento;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.OrdenMovimiento;
import orquitas.servidor.navegacion.PreprocesadorTelemetria;
import orquitas.servidor.navegacion.RedMovimiento;
import orquitas.servidor.navegacion.RedPercepcion;
import orquitas.servidor.navegacion.RepresentacionIntermedia;
import orquitas.servidor.navegacion.MetaMovimiento;
import orquitas.simulacion.bahia.Maestro;
import orquitas.simulacion.bahia.MaestroV2;
import orquitas.simulacion.bahia.Piloto;
import orquitas.simulacion.bahia.RobotSimulado;
import orquitas.simulacion.bahia.SensorSimulado;
import orquitas.simulacion.bahia.SimuladorBahia;

/**
 * Entrena las dos redes con la Bahía de Caza simulada (no forma parte de los diagramas). Uso:
 * {@code EntrenamientoSimulado [episodios=250] [epocas=60] [carpeta=modelos] [rondasDagger=2] [--hilos=N]}.
 *
 * {@code --hilos=N} (opcional, en cualquier posición): en cuántos hilos se reparte la simulación. Por
 * defecto, los núcleos disponibles menos 1 (mínimo 1). Se reparten las pruebas simuladas, que son
 * independientes entre sí: los episodios del maestro, los de las rondas DAgger, la evaluación, la matriz
 * de confusión y el acuerdo de dirección. El entrenamiento de las redes (Neuroph) sigue en un solo hilo.
 * El resultado es exactamente el mismo con 1 hilo o con N: cada prueba depende solo de su semilla (la
 * de su índice) y los resultados se juntan en el orden del índice, no en el que terminan los hilos.
 *
 * 1. El maestro (que conoce la geometría real) maneja el robot en bahías al azar; a veces se ejecuta
 *    una orden al azar para visitar situaciones variadas. Los ejemplos se guardan agrupados por episodio
 *    y en orden: cada barrido con sus etiquetas verdaderas por sector, la orden del maestro (lo que se
 *    enseña) y la orden que realmente se ejecutó.
 * 2. Red de percepción por ventana: un sector y 2 vecinos de cada lado -> su etiqueta (46 -> ocultas -> 5),
 *    aplicada a los 13 sectores.
 * 3. Red de movimiento (24 -> ocultas -> 15): 14 entradas de la representación intermedia
 *    (RepresentacionIntermedia) y 10 de la memoria (MemoriaNavegacion) -> sector meta (o atrás) y mm; el
 *    Servidor lo traduce a giro o avance en ms (MetaMovimiento). Como la memoria depende de lo que pasó
 *    antes, cada episodio se reproduce en orden con la percepción ya entrenada y con las órdenes
 *    ejecutadas: así la memoria del entrenamiento se calcula igual que en el Servidor.
 * 4. DAgger: las redes manejan, el maestro dice qué habría hecho en cada barrido, se suman esos
 *    ejemplos y se reentrena (así la red aprende a salir de los errores que ella misma comete).
 * 5. Evaluación en bahías nuevas (semillas que no se usaron para entrenar): redes contra maestro.
 * Guarda percepcion.nnet, movimiento.nnet y un reporte en docs/evidencias/.
 */
public class EntrenamientoSimulado {
    static final String USO = "Uso: EntrenamientoSimulado [episodios=250] [epocas=60] [carpeta=modelos] [rondasDagger=2] [--hilos=N]"
            + " [--entradas=VARIANTE] [--semilla=N] [--percepcion-reforzada] [--repeticion=N] [--pruebas=N] [--error=cuadratico|entropia] [--mm-solo-avance] [--peso-mm=X] [--escapes=N] [--maestro=actual|v2|barrido|opciones] [--reales=ruta.csv] [--peso-reales=N]";
    /** Semilla de la recolección, de los pesos iniciales y de las mezclas (--semilla=N la cambia; la evaluación no). */
    static long SEMILLA = 20261113L;
    static final long SEMILLA_EVALUACION = 900_000L;
    static final int OCULTAS_PERCEPCION = 24;
    static final int OCULTAS_MOVIMIENTO = 20;
    /** Capa oculta de la red de movimiento cuando recibe la telemetría cruda (184 entradas o más). */
    static final int OCULTAS_MOVIMIENTO_CRUDA = 48;
    /** Variante de entradas de la red de movimiento (--entradas=...). */
    static RedMovimiento.Variante VARIANTE = RedMovimiento.Variante.REPRESENTACION_MEMORIA;
    /** --percepcion-reforzada: el doble de épocas para la percepción y más peso a OBSTACULO y SALIDA. */
    static boolean PERCEPCION_REFORZADA = false;
    static final double PROBABILIDAD_ORDEN_AL_AZAR = 0.15;
    static final int CANICAS = 3, UMBRAL = 3;
    /** Tope de repeticiones de una clase rara de meta al balancear la red de movimiento. */
    static int REPETICION_MAXIMA = 6;
    /**
     * --error=entropia: la red de movimiento se entrena con entropía cruzada (softmax en las 14 clases,
     * salida lineal); por defecto, error cuadrático con salida sigmoide.
     */
    static boolean ENTROPIA = false;
    /** --mm-solo-avance: el error de los mm solo cuenta en avances y retrocesos (requiere --error=entropia). */
    static boolean MM_SOLO_AVANCE = false;
    /** --peso-mm=X: peso del error de los mm (requiere --error=entropia). */
    static double PESO_MM = 1;
    /**
     * --escapes=N: además de los episodios normales, N episodios que arrancan en Modo Escape desde un lugar
     * al azar (y N/2 en cada ronda DAgger), para tener más ejemplos de escape.
     */
    static int ESCAPES = 0;
    /**
     * --maestro=v2 (o --maestro=giro+seguro+embudo, etc.): enseña el Maestro v2 en lugar del actual. Con
     * "giro", la red aprende "girar hacia el sector y avanzar X mm" en el mismo barrido (los mm de un giro
     * son el avance que sigue) y así se la evalúa. Null = Maestro actual.
     */
    static MaestroV2.Opciones MAESTRO_V2 = null;
    /**
     * --maestro=barrido: enseña el modo barrido del Servidor (PilotoBarrido) con las etiquetas verdaderas por
     * sector: un maestro sin trampa, que no conoce la geometría (dónde están las canicas o la salida).
     */
    static boolean MAESTRO_BARRIDO = false;
    /**
     * --reales=ruta.csv: barridos reales etiquetados por sector (39 valores + 13 etiquetas por línea) que se
     * suman a los datos de la percepción, repetidos --peso-reales=N veces (por defecto 20).
     */
    static Path REALES = null;
    static int PESO_REALES = 20;

    /** Bahías nuevas de la evaluación final (--pruebas=N). */
    static int PRUEBAS = 100;

    /**
     * Un barrido con lo que se sabe de él: la telemetría, las etiquetas verdaderas, el modo, la orden del
     * maestro (lo que se enseña) y la orden que realmente se ejecutó después (la del maestro, una al azar
     * o la de las redes en DAgger), que es la que entra en la memoria del ciclo siguiente.
     */
    record Ejemplo(int[] telemetria, EtiquetaSector[] etiquetas, ModoOperativo modo, OrdenMovimiento orden,
                   OrdenMovimiento ejecutada, MetaMovimiento meta, OrdenMovimiento avanceEjecutado) {
        /** Sin "girar y avanzar": la meta sale de la orden y no hubo avance después del giro. */
        Ejemplo(int[] telemetria, EtiquetaSector[] etiquetas, ModoOperativo modo, OrdenMovimiento orden, OrdenMovimiento ejecutada) {
            this(telemetria, etiquetas, modo, orden, ejecutada, MetaMovimiento.desde(orden), null);
        }
    }

    /** Las dos redes que usa un hilo de simulación: sus propias copias, que no comparte con otro hilo. */
    record Redes(RedPercepcion percepcion, RedMovimiento movimiento) { }

    /** Resultado de una prueba completa. */
    record Prueba(boolean liberada, int capturas, int choques, int puntaje, double segundos) { }

    /** Resultado de varias pruebas. */
    record Resumen(int pruebas, int liberadas, double capturas, double choques, double puntaje, double segundos) {
        String fila(String nombre) {
            return String.format(Locale.ROOT, "| %s | %d/%d (%.0f %%) | %.2f | %.2f | %.1f | %.0f |", nombre, liberadas, pruebas,
                    100.0 * liberadas / pruebas, capturas, choques, puntaje, segundos);
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> posicionales;
        int hilos;
        try {
            posicionales = posicionales(args);
            hilos = hilos(args);
            opciones(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(USO);
            System.exit(2);
            return;
        }
        int episodios = posicionales.size() > 0 ? Integer.parseInt(posicionales.get(0)) : 250;
        int epocas = posicionales.size() > 1 ? Integer.parseInt(posicionales.get(1)) : 60;
        Path carpeta = Path.of(posicionales.size() > 2 ? posicionales.get(2) : "modelos");
        int rondas = posicionales.size() > 3 ? Integer.parseInt(posicionales.get(3)) : 2;
        Files.createDirectories(carpeta);
        StringBuilder reporte = new StringBuilder();
        long inicio = System.currentTimeMillis();
        RobotSimulado.Calibracion cal = RobotSimulado.Calibracion.ESTIMADA;
        log("Simulación repartida en " + hilos + " hilo(s) (núcleos disponibles: " + Runtime.getRuntime().availableProcessors()
                + "); las redes se entrenan en uno solo");

        // Cada elemento es un episodio (una prueba) con sus ejemplos en orden.
        List<List<Ejemplo>> datos = recolectarConMaestro(episodios, cal, hilos);
        if (ESCAPES > 0) {
            List<List<Ejemplo>> escapes = repartir(ESCAPES, hilos, i -> episodioConMaestro(SEMILLA + 500_000L + i, cal, true));
            datos.addAll(escapes);
            log("Episodios que arrancan en escape (maestro): " + escapes.size() + ", " + contar(escapes) + " ejemplos");
        }
        log("Ejemplos del maestro: " + contar(datos) + " en " + datos.size() + " episodios");

        MultiLayerPerceptron percepcion = nuevaRed(RedPercepcion.ENTRADAS, OCULTAS_PERCEPCION, RedPercepcion.SALIDAS_RED);
        // La percepción tiene ~8 filas por barrido (una por sector, balanceadas): necesita menos épocas.
        int factorPercepcion = PERCEPCION_REFORZADA ? 2 : 1;
        entrenar(percepcion, datosPercepcion(datos), factorPercepcion * Math.max(5, epocas / 3), "percepción");
        int ocultas = ocultasMovimiento();
        log("Red de movimiento: variante " + VARIANTE + " (" + VARIANTE.entradas + " entradas, " + ocultas + " ocultas)"
                + "; error " + (ENTROPIA ? "entropía cruzada" : "cuadrático")
                + (MM_SOLO_AVANCE ? "; mm solo en avances" : "") + (PESO_MM != 1 ? "; peso mm " + PESO_MM : "")
                + (ESCAPES > 0 ? "; episodios de escape " + ESCAPES : "")
                + "; maestro " + (MAESTRO_BARRIDO ? "barrido (sin trampa)" : MAESTRO_V2 == null ? "actual" : "v2 " + MAESTRO_V2) + "; sim realista " + SensorSimulado.REALISTA + "; barrido " + SimuladorBahia.SEGUNDOS_BARRIDO + " s" + "; semilla " + SEMILLA + "; repetición máxima " + REPETICION_MAXIMA + "; evaluación en " + PRUEBAS + " bahías"
                + (PERCEPCION_REFORZADA ? "; percepción reforzada" : ""));
        MultiLayerPerceptron movimiento = nuevaRed(VARIANTE.entradas, ocultas, RedMovimiento.SALIDAS);
        if (ENTROPIA) salidaLineal(movimiento);
        entrenarMovimiento(movimiento, datosMovimiento(datos, new RedPercepcion(percepcion)), epocas);

        for (int ronda = 1; ronda <= rondas; ronda++) {
            List<List<Ejemplo>> nuevos = recolectarConRedes(episodios / 2, cal, copiasDe(percepcion, movimiento),
                    SEMILLA + 10_000L * ronda, hilos);
            datos.addAll(nuevos);
            if (ESCAPES > 0) {
                long base = SEMILLA + 500_000L + 10_000L * ronda;
                List<List<Ejemplo>> escapes = repartir(ESCAPES / 2, hilos, copiasDe(percepcion, movimiento),
                        (redes, i) -> episodioConRedes(base + i, cal, redes, true));
                datos.addAll(escapes);
                nuevos = new ArrayList<>(nuevos);
                nuevos.addAll(escapes);
            }
            log("DAgger ronda " + ronda + ": +" + contar(nuevos) + " ejemplos (total " + contar(datos) + ")");
            entrenar(percepcion, datosPercepcion(datos), factorPercepcion * Math.max(3, epocas / 6), "percepción");
            entrenarMovimiento(movimiento, datosMovimiento(datos, new RedPercepcion(percepcion)), Math.max(10, epocas / 2));
        }

        Path rutaPercepcion = carpeta.resolve("percepcion.nnet"), rutaMovimiento = carpeta.resolve("movimiento.nnet");
        percepcion.save(rutaPercepcion.toString());
        // El modelo guardado no depende de la función de error del entrenamiento.
        if (movimiento.getLearningRule() != null) movimiento.getLearningRule().setErrorFunction(new MeanSquaredError());
        movimiento.save(rutaMovimiento.toString());

        // Evaluación con bahías nuevas: cada hilo carga sus redes de los archivos recién guardados.
        Supplier<Redes> guardadas = () -> new Redes(new RedPercepcion(rutaPercepcion), new RedMovimiento(rutaMovimiento));
        int pruebas = PRUEBAS;
        Resumen maestro = evaluarMaestro(pruebas, cal, hilos);
        Resumen redes = evaluarRedes(guardadas, pruebas, cal, hilos);
        int[][] confusion = confusionPercepcion(guardadas, 40, cal, hilos);
        double acuerdo = acuerdoDireccion(guardadas, 40, cal, hilos);
        double minutos = (System.currentTimeMillis() - inicio) / 60000.0;

        reporte.append("# Entrenamiento con la Bahía de Caza simulada (").append(LocalDate.now()).append(")\n\n")
               .append("Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: ")
               .append("sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.\n\n")
               .append(String.format(Locale.ROOT, "- Episodios del maestro: %d; rondas DAgger: %d; épocas: %d; ejemplos: %d; tiempo: %.1f min.%n",
                       episodios, rondas, epocas, contar(datos), minutos))
               .append(String.format(Locale.ROOT, "- Hilos de simulación: %d (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).%n", hilos))
               .append(String.format(Locale.ROOT, "- Calibración usada: %.0f mm/s, %.0f °/s, robot %.0f x %.0f mm [A CONFIRMAR con el robot real].%n",
                       cal.mmPorSegundo(), cal.gradosPorSegundo(), cal.largoMm(), cal.anchoMm()))
               .append(String.format(Locale.ROOT, "- Redes: percepción por ventana %d-%d-%d (x 13 sectores), movimiento %d-%d-%d (%s, MomentumBackpropagation).%n",
                       RedPercepcion.ENTRADAS, OCULTAS_PERCEPCION, RedPercepcion.SALIDAS_RED,
                       VARIANTE.entradas, ocultas, RedMovimiento.SALIDAS,
                       ENTROPIA ? "salida lineal, entropía cruzada" : "sigmoide, error cuadrático"))
               .append(String.format(Locale.ROOT, "- Entradas de la red de movimiento: variante %s (%d).%n",
                       VARIANTE, VARIANTE.entradas))
               .append(String.format(Locale.ROOT, "- Semilla de recolección: %d; percepción reforzada: %s; repetición máxima de clases (movimiento): %d.%n",
                       SEMILLA, PERCEPCION_REFORZADA ? "sí" : "no", REPETICION_MAXIMA))
               .append(String.format(Locale.ROOT, "- Milímetros: %s, peso %.1f; episodios que arrancan en escape: %d (+%d por ronda DAgger).%n",
                       MM_SOLO_AVANCE ? "solo en avances y retrocesos" : "en todas las filas", PESO_MM, ESCAPES, ESCAPES / 2))
               .append(String.format(Locale.ROOT, "- Maestro: %s; barrido %.2f s; ruido por ROI (4x4, 8x8, 16x16) x%s.%n%n",
                       MAESTRO_V2 == null ? "actual" : "v2 " + MAESTRO_V2, SimuladorBahia.SEGUNDOS_BARRIDO,
                       System.getProperty("orquita.ruidoRoi", "1,1,1")))
               .append("## Pruebas completas (").append(pruebas).append(" bahías nuevas, 7 min, 3 Lobitos)\n\n")
               .append("| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |\n|---|---|---|---|---|---|\n")
               .append(maestro.fila(MAESTRO_V2 == null ? "Maestro (geometría real)" : "Maestro v2 (geometría real)")).append('\n')
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

    // ---------------------------------------------------------------- Argumentos

    /** Hilos de simulación por defecto: los núcleos disponibles menos 1 (queda uno para el resto de la PC), mínimo 1. */
    static int hilosPorDefecto() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    /** Cantidad de hilos de simulación: la del argumento --hilos=N o, si no está, hilosPorDefecto(). */
    static int hilos(String[] args) {
        for (String a : args) {
            if (!a.startsWith("--hilos=")) continue;
            int n;
            try {
                n = Integer.parseInt(a.substring("--hilos=".length()));
            } catch (NumberFormatException e) {
                n = 0;
            }
            if (n < 1) throw new IllegalArgumentException("--hilos tiene que ser un número entero mayor o igual que 1: " + a);
            return n;
        }
        return hilosPorDefecto();
    }

    /** Los argumentos que no son opciones (episodios, épocas, carpeta y rondas), en orden. */
    /** Opciones de los experimentos: --entradas=VARIANTE, --semilla=N, --percepcion-reforzada. */
    static void opciones(String[] args) {
        for (String a : args) {
            if (a.startsWith("--entradas=")) VARIANTE = RedMovimiento.Variante.valueOf(a.substring(11).toUpperCase(Locale.ROOT));
            else if (a.startsWith("--semilla=")) SEMILLA = Long.parseLong(a.substring(10));
            else if (a.equals("--percepcion-reforzada")) PERCEPCION_REFORZADA = true;
            else if (a.startsWith("--repeticion=")) REPETICION_MAXIMA = Math.max(1, Integer.parseInt(a.substring(13)));
            else if (a.startsWith("--pruebas=")) PRUEBAS = Math.max(1, Integer.parseInt(a.substring(10)));
            else if (a.startsWith("--error=")) {
                String e = a.substring(8).toLowerCase(Locale.ROOT);
                if (!e.equals("entropia") && !e.equals("cuadratico")) throw new IllegalArgumentException("--error=entropia o --error=cuadratico");
                ENTROPIA = e.equals("entropia");
            }
            else if (a.equals("--mm-solo-avance")) MM_SOLO_AVANCE = true;
            else if (a.startsWith("--peso-mm=")) PESO_MM = Double.parseDouble(a.substring(10));
            else if (a.startsWith("--escapes=")) ESCAPES = Math.max(0, Integer.parseInt(a.substring(10)));
            else if (a.startsWith("--maestro=")) {
                String m = a.substring(10).toLowerCase(Locale.ROOT);
                MAESTRO_BARRIDO = m.equals("barrido");
                MAESTRO_V2 = m.equals("actual") || MAESTRO_BARRIDO ? null : MaestroV2.Opciones.desde(m.equals("v2") ? "giro+seguro+embudo" : m);
            }
            else if (a.startsWith("--reales=")) REALES = Path.of(a.substring(9));
            else if (a.startsWith("--peso-reales=")) PESO_REALES = Math.max(1, Integer.parseInt(a.substring(14)));
        }
    }

    /** 48 ocultas si la red recibe la telemetría cruda (como la corrida A), 20 si no. */
    static int ocultasMovimiento() {
        return VARIANTE.entradas >= RedMovimiento.CRUDAS ? OCULTAS_MOVIMIENTO_CRUDA : OCULTAS_MOVIMIENTO;
    }

    static List<String> posicionales(String[] args) {
        List<String> posicionales = new ArrayList<>();
        for (String a : args) {
            if (!a.startsWith("--")) posicionales.add(a);
            else if (!a.startsWith("--hilos=") && !a.startsWith("--entradas=") && !a.startsWith("--semilla=")
                    && !a.equals("--percepcion-reforzada") && !a.startsWith("--repeticion=") && !a.startsWith("--pruebas=")
                    && !a.startsWith("--error=") && !a.equals("--mm-solo-avance") && !a.startsWith("--peso-mm=")
                    && !a.startsWith("--escapes=") && !a.startsWith("--maestro=") && !a.startsWith("--reales=")
                    && !a.startsWith("--peso-reales=")) throw new IllegalArgumentException("Argumento desconocido: " + a);
        }
        return posicionales;
    }

    // ---------------------------------------------------------------- Reparto entre hilos

    /**
     * Corre tarea(contexto, i) para i = 0..cantidad-1 repartida en hasta `hilos` hilos y devuelve los
     * resultados en el orden del índice (no en el que terminan los hilos). Cada hilo recibe su propio
     * contexto (por ejemplo, sus copias de las redes), creado acá antes de empezar; los hilos van tomando
     * el siguiente índice libre. Si la tarea depende solo del índice y del contexto, el resultado es el
     * mismo con cualquier cantidad de hilos. Con 1 hilo corre en el hilo que llama.
     */
    static <C, R> List<R> repartir(int cantidad, int hilos, Supplier<C> contextoPorHilo, BiFunction<C, Integer, R> tarea) {
        int usados = Math.max(1, Math.min(hilos, cantidad));
        List<C> contextos = new ArrayList<>();
        for (int h = 0; h < usados; h++) contextos.add(contextoPorHilo.get());
        AtomicReferenceArray<R> resultados = new AtomicReferenceArray<>(cantidad);
        AtomicInteger siguiente = new AtomicInteger();
        if (usados == 1) {
            trabajar(contextos.get(0), cantidad, siguiente, resultados, tarea);
        } else {
            ExecutorService servicio = Executors.newFixedThreadPool(usados, r -> {
                Thread hilo = new Thread(r, "Simulacion");
                hilo.setDaemon(true);
                return hilo;
            });
            try {
                List<Future<?>> trabajos = new ArrayList<>();
                for (C contexto : contextos)
                    trabajos.add(servicio.submit(() -> trabajar(contexto, cantidad, siguiente, resultados, tarea)));
                for (Future<?> trabajo : trabajos) trabajo.get();
            } catch (ExecutionException e) {
                throw new IllegalStateException("Falló una prueba simulada: " + e.getCause(), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Se interrumpió la simulación", e);
            } finally {
                servicio.shutdownNow();
            }
        }
        List<R> enOrden = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) enOrden.add(resultados.get(i));
        return enOrden;
    }

    /** Como repartir, para tareas que no necesitan nada propio de cada hilo. */
    static <R> List<R> repartir(int cantidad, int hilos, IntFunction<R> tarea) {
        return repartir(cantidad, hilos, () -> Boolean.TRUE, (sinContexto, i) -> tarea.apply(i));
    }

    /** Lo que hace cada hilo: toma el siguiente índice libre y guarda su resultado en ese lugar. */
    private static <C, R> void trabajar(C contexto, int cantidad, AtomicInteger siguiente, AtomicReferenceArray<R> resultados,
                                        BiFunction<C, Integer, R> tarea) {
        for (int i = siguiente.getAndIncrement(); i < cantidad; i = siguiente.getAndIncrement())
            resultados.set(i, tarea.apply(contexto, i));
    }

    /**
     * Fábrica de copias de las redes tal como están ahora, una por hilo: las redes de Neuroph no son
     * seguras entre hilos (guardan sus entradas y salidas adentro). Se serializan una sola vez, acá, y
     * cada copia sale de deserializar esos bytes: los mismos pesos, así que las mismas salidas.
     */
    static Supplier<Redes> copiasDe(NeuralNetwork<?> percepcion, NeuralNetwork<?> movimiento) {
        byte[] bytesPercepcion = serializar(percepcion), bytesMovimiento = serializar(movimiento);
        return () -> new Redes(new RedPercepcion(deserializar(bytesPercepcion)), new RedMovimiento(deserializar(bytesMovimiento)));
    }

    static byte[] serializar(NeuralNetwork<?> red) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream salida = new ObjectOutputStream(bytes)) {
            salida.writeObject(red);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo copiar la red", e);
        }
        return bytes.toByteArray();
    }

    static NeuralNetwork<?> deserializar(byte[] bytes) {
        try (ObjectInputStream entrada = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (NeuralNetwork<?>) entrada.readObject();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo copiar la red", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("No se pudo copiar la red", e);
        }
    }

    // ---------------------------------------------------------------- Redes y datos (un solo hilo)

    static MultiLayerPerceptron nuevaRed(int entradas, int ocultas, int salidas) {
        MultiLayerPerceptron red = new MultiLayerPerceptron(TransferFunctionType.SIGMOID, entradas, ocultas, salidas);
        red.randomizeWeights(new Random(SEMILLA));
        return red;
    }

    /** Capa de salida lineal (para --error=entropia): la red da puntajes y la clase es la de mayor puntaje. */
    static void salidaLineal(MultiLayerPerceptron red) {
        for (Neuron n : red.getOutputNeurons()) n.setTransferFunction(new Linear(1));
    }

    static void entrenarMovimiento(MultiLayerPerceptron red, DataSet datos, int epocas) {
        if (!ENTROPIA && (MM_SOLO_AVANCE || PESO_MM != 1))
            throw new IllegalArgumentException("--mm-solo-avance y --peso-mm requieren --error=entropia");
        entrenar(red, datos, epocas, "movimiento", ENTROPIA ? new EntropiaCruzadaMovimiento(RedMovimiento.CLASES,
                MM_SOLO_AVANCE, PESO_MM, MetaMovimiento.FRENTE, MetaMovimiento.ATRAS) : null);
    }

    static void entrenar(MultiLayerPerceptron red, DataSet datos, int epocas, String nombre) {
        entrenar(red, datos, epocas, nombre, null);
    }

    static void entrenar(MultiLayerPerceptron red, DataSet datos, int epocas, String nombre, ErrorFunction error) {
        MomentumBackpropagation regla = new MomentumBackpropagation();
        if (error != null) regla.setErrorFunction(error);
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
    static DataSet datosPercepcion(List<List<Ejemplo>> episodios) {
        List<DataSetRow> filas = new ArrayList<>();
        Random azar = new Random(SEMILLA);
        for (List<Ejemplo> episodio : episodios) {
            for (Ejemplo e : episodio) {
                double[] entradas = PreprocesadorTelemetria.entradas(e.telemetria());
                for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
                    EtiquetaSector et = e.etiquetas()[s];
                    int veces = et == EtiquetaSector.LOBITO ? 4 : et == EtiquetaSector.PARED ? (azar.nextInt(2) == 0 ? 1 : 0) : 1;
                    if (PERCEPCION_REFORZADA && (et == EtiquetaSector.OBSTACULO || et == EtiquetaSector.SALIDA)) veces = 3;
                    if (veces == 0) continue;
                    DataSetRow fila = new DataSetRow(PreprocesadorTelemetria.entradasVentana(entradas, s), RedPercepcion.salidaDeseada(et));
                    for (int i = 0; i < veces; i++) filas.add(fila);
                }
            }
        }
        if (REALES != null) filas.addAll(filasReales(REALES, PESO_REALES));
        return mezclado(RedPercepcion.ENTRADAS, RedPercepcion.SALIDAS_RED, filas);
    }

    /**
     * Filas de percepción de barridos reales etiquetados: cada línea tiene los 39 valores (en el orden del
     * protocolo: sector 0 = derecha) y las 13 etiquetas; se ignoran las líneas que empiezan con #. Cada
     * sector se repite peso veces (los LOBITO, 4 veces más, como en los simulados).
     */
    static List<DataSetRow> filasReales(Path archivo, int peso) {
        List<DataSetRow> filas = new ArrayList<>();
        int barridos = 0;
        try {
            for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
                if (linea.isBlank() || linea.startsWith("#") || linea.startsWith("v0")) continue;
                String[] c = linea.split(",");
                int[] t = new int[PreprocesadorTelemetria.VALORES];
                for (int i = 0; i < t.length; i++) t[i] = Integer.parseInt(c[i].trim());
                double[] entradas = PreprocesadorTelemetria.entradas(t);
                for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
                    EtiquetaSector et = EtiquetaSector.valueOf(c[PreprocesadorTelemetria.VALORES + s].trim());
                    DataSetRow fila = new DataSetRow(PreprocesadorTelemetria.entradasVentana(entradas, s), RedPercepcion.salidaDeseada(et));
                    int veces = peso * (et == EtiquetaSector.LOBITO ? 4 : 1);
                    for (int i = 0; i < veces; i++) filas.add(fila);
                }
                barridos++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log("Percepción: " + barridos + " barridos reales de " + archivo + " (x" + peso + ")");
        return filas;
    }

    /**
     * Movimiento: reproduce cada episodio en orden, con una MemoriaNavegacion nueva por episodio y el
     * mismo ciclo que el Servidor (RedMovimiento.entradasDelCiclo y memoria.registrarOrden). La
     * representación sale de la red de percepción ya entrenada (no de la verdad) y la memoria avanza con
     * la orden que realmente se ejecutó: la red entrena con lo mismo que va a ver al decidir.
     * Balanceo: cada clase de meta (13 sectores + ATRAS) se repite hasta pesar lo mismo que la más común
     * (con tope REPETICION_MAXIMA). Sin esto la red aprende a decir casi siempre "frente", porque cada
     * giro en particular es raro.
     */
    static DataSet datosMovimiento(List<List<Ejemplo>> episodios, RedPercepcion percepcion) {
        return mezclado(VARIANTE.entradas, RedMovimiento.SALIDAS, filasMovimiento(episodios, percepcion));
    }

    /** Las filas de datosMovimiento, en el orden de los episodios (sin mezclar). */
    static List<DataSetRow> filasMovimiento(List<List<Ejemplo>> episodios, RedPercepcion percepcion) {
        int[] cuenta = new int[RedMovimiento.CLASES];
        for (List<Ejemplo> episodio : episodios)
            for (Ejemplo e : episodio) cuenta[e.meta().clase()]++;
        int maximo = 0;
        for (int c : cuenta) maximo = Math.max(maximo, c);
        List<DataSetRow> filas = new ArrayList<>();
        for (List<Ejemplo> episodio : episodios) {
            MemoriaNavegacion memoria = new MemoriaNavegacion(); // Una por prueba, como en el Servidor.
            for (Ejemplo e : episodio) {
                double[] p = percepcion.probabilidades(e.telemetria());
                double[] entradas = RedMovimiento.entradasDelCicloDe(VARIANTE, e.telemetria(), p, e.modo(), memoria);
                memoria.registrarOrden(e.ejecutada());
                if (e.avanceEjecutado() != null) memoria.registrarOrden(e.avanceEjecutado());
                DataSetRow fila = new DataSetRow(entradas, RedMovimiento.salidaDeseada(e.meta()));
                int c = cuenta[e.meta().clase()];
                int veces = Math.max(1, Math.min(REPETICION_MAXIMA, Math.round(maximo / (float) c)));
                for (int i = 0; i < veces; i++) filas.add(fila);
            }
        }
        return filas;
    }

    /** Cantidad total de ejemplos (barridos) de los episodios. */
    static int contar(List<List<Ejemplo>> episodios) {
        int total = 0;
        for (List<Ejemplo> episodio : episodios) total += episodio.size();
        return total;
    }

    // ---------------------------------------------------------------- Simulación (repartida entre hilos)

    /**
     * Azar propio de un episodio (el umbral de capturas y qué orden se ejecuta): sale solo de la semilla
     * del episodio, no de los episodios anteriores, así no importa en qué hilo ni en qué orden se corre.
     * Es distinto del azar de la bahía y del sensor (el de SimuladorBahia.alAzar con la misma semilla).
     */
    static Random azarDelEpisodio(long semillaEpisodio) {
        return new Random(semillaEpisodio * 1_000_003L + 17);
    }

    /**
     * El maestro maneja; con cierta probabilidad se ejecuta una orden al azar (pero se enseña la del
     * maestro). Devuelve un episodio por prueba (el episodio i usa la semilla SEMILLA + i), en el orden
     * del índice y con sus ejemplos en orden.
     */
    static List<List<Ejemplo>> recolectarConMaestro(int episodios, RobotSimulado.Calibracion cal, int hilos) {
        return repartir(episodios, hilos, i -> episodioConMaestro(SEMILLA + i, cal));
    }

    static List<Ejemplo> episodioConMaestro(long semilla, RobotSimulado.Calibracion cal) {
        return episodioConMaestro(semilla, cal, false);
    }

    /** Simulador del episodio: normal, o (escape = true) arrancando en Modo Escape desde un lugar al azar. */
    static SimuladorBahia simuladorDelEpisodio(long semilla, int umbral, RobotSimulado.Calibracion cal, boolean escape) {
        return escape ? SimuladorBahia.enEscapeAlAzar(semilla, umbral, cal, SensorSimulado.Ruido.NORMAL)
                : SimuladorBahia.alAzar(semilla, CANICAS, umbral, cal, SensorSimulado.Ruido.NORMAL);
    }

    static List<Ejemplo> episodioConMaestro(long semilla, RobotSimulado.Calibracion cal, boolean escape) {
        Random azar = azarDelEpisodio(semilla);
        int umbral = 1 + azar.nextInt(UMBRAL); // También pruebas con umbral 1 y 2 (más escapes).
        SimuladorBahia sim = simuladorDelEpisodio(semilla, umbral, cal, escape);
        Piloto maestro = maestroDelEpisodio();
        List<Ejemplo> episodio = new ArrayList<>();
        while (!sim.terminada()) {
            SensorSimulado.Barrido b = sim.medir();
            Piloto.Decision d = maestro.decidir(sim, b);
            Piloto.Decision ejecutada = azar.nextDouble() < PROBABILIDAD_ORDEN_AL_AZAR ? new Piloto.Decision(ordenAlAzar(azar), null) : d;
            episodio.add(ejemplo(b, sim.modo(), d, ejecutada));
            ejecutada.ejecutarEn(sim);
            maestro.registrar(ejecutada); // El maestro barrido recuerda lo que se movió (los otros no hacen nada).
        }
        return episodio;
    }

    /**
     * Las redes manejan (la mitad de las veces el maestro, para no quedar trabadas); se enseña lo que
     * diría el maestro. Cada hilo usa sus copias de las redes (redesPorHilo) y cada episodio su propio
     * piloto, con su memoria. El episodio i usa la semilla semilla + i.
     */
    static List<List<Ejemplo>> recolectarConRedes(int episodios, RobotSimulado.Calibracion cal, Supplier<Redes> redesPorHilo,
                                                  long semilla, int hilos) {
        return repartir(episodios, hilos, redesPorHilo, (redes, i) -> episodioConRedes(semilla + i, cal, redes));
    }

    /**
     * El piloto de redes decide en todos los barridos y se le avisa la orden que realmente se ejecutó,
     * así su memoria sigue lo que pasó en la prueba aunque haya manejado el maestro.
     */
    static List<Ejemplo> episodioConRedes(long semilla, RobotSimulado.Calibracion cal, Redes redes) {
        return episodioConRedes(semilla, cal, redes, false);
    }

    static List<Ejemplo> episodioConRedes(long semilla, RobotSimulado.Calibracion cal, Redes redes, boolean escape) {
        Random azar = azarDelEpisodio(semilla);
        Piloto piloto = pilotoDeRedes(redes);
        int umbral = 1 + azar.nextInt(UMBRAL);
        SimuladorBahia sim = simuladorDelEpisodio(semilla, umbral, cal, escape);
        Piloto maestro = maestroDelEpisodio();
        List<Ejemplo> episodio = new ArrayList<>();
        while (!sim.terminada()) {
            SensorSimulado.Barrido b = sim.medir();
            Piloto.Decision delMaestro = maestro.decidir(sim, b);
            Piloto.Decision deLasRedes = piloto.decidir(sim, b);
            Piloto.Decision ejecutada = azar.nextDouble() < 0.5 ? deLasRedes : delMaestro;
            episodio.add(ejemplo(b, sim.modo(), delMaestro, ejecutada));
            ejecutada.ejecutarEn(sim);
            piloto.registrar(ejecutada);
            maestro.registrar(ejecutada);
        }
        return episodio;
    }

    /** Maestro que enseña: el actual o el v2 (uno por episodio). */
    static Piloto maestroDelEpisodio() {
        if (MAESTRO_BARRIDO) return Piloto.barrido(null);
        return MAESTRO_V2 == null ? Piloto.maestro() : Piloto.maestroV2(MAESTRO_V2);
    }

    /** Piloto de redes: con "girar y avanzar" si el maestro v2 lo usa. */
    static Piloto pilotoDeRedes(Redes redes) {
        return Piloto.redes(redes.percepcion(), redes.movimiento(), MAESTRO_V2 != null && MAESTRO_V2.giroYAvance());
    }

    /** Ejemplo de un barrido: se enseña la decisión del maestro (giro y avance juntos) y se recuerda la ejecutada. */
    static Ejemplo ejemplo(SensorSimulado.Barrido b, ModoOperativo modo, Piloto.Decision maestro, Piloto.Decision ejecutada) {
        return new Ejemplo(b.telemetria(), b.etiquetas(), modo, maestro.orden(), ejecutada.orden(),
                MetaMovimiento.desdePlan(maestro.orden(), maestro.avanceDespues()), ejecutada.avanceDespues());
    }

    static OrdenMovimiento ordenAlAzar(Random azar) {
        Movimiento[] m = {Movimiento.AVANZAR, Movimiento.GIRAR_IZQ, Movimiento.GIRAR_DER, Movimiento.RETROCEDER};
        return new OrdenMovimiento(m[azar.nextInt(m.length)], 100 + azar.nextInt(900));
    }

    /** Pruebas completas con el maestro (la prueba i usa la semilla SEMILLA_EVALUACION + i). */
    static Resumen evaluarMaestro(int pruebas, RobotSimulado.Calibracion cal, int hilos) {
        return resumir(repartir(pruebas, hilos, i -> pruebaCompleta(SEMILLA_EVALUACION + i, maestroDelEpisodio(), cal)));
    }

    /** Las mismas pruebas con las redes: un piloto nuevo por prueba (con su memoria y su detector de atasco). */
    static Resumen evaluarRedes(Supplier<Redes> redesPorHilo, int pruebas, RobotSimulado.Calibracion cal, int hilos) {
        return resumir(repartir(pruebas, hilos, redesPorHilo,
                (redes, i) -> pruebaCompleta(SEMILLA_EVALUACION + i, pilotoDeRedes(redes), cal)));
    }

    static Prueba pruebaCompleta(long semilla, Piloto piloto, RobotSimulado.Calibracion cal) {
        SimuladorBahia sim = SimuladorBahia.alAzar(semilla, CANICAS, UMBRAL, cal, SensorSimulado.Ruido.NORMAL);
        while (!sim.terminada()) {
            Piloto.Decision d = piloto.decidir(sim, sim.medir());
            d.ejecutarEn(sim);
            piloto.registrar(d);
        }
        return new Prueba(sim.modo() == ModoOperativo.LIBERADA, sim.capturas(), sim.choques(), sim.puntaje(), sim.segundos());
    }

    /** Promedios de las pruebas, sumadas en el orden del índice. */
    static Resumen resumir(List<Prueba> pruebas) {
        int liberadas = 0;
        double capturas = 0, choques = 0, puntaje = 0, segundos = 0;
        for (Prueba p : pruebas) {
            if (p.liberada()) liberadas++;
            capturas += p.capturas();
            choques += p.choques();
            puntaje += p.puntaje();
            segundos += p.segundos();
        }
        int n = pruebas.size();
        return new Resumen(n, liberadas, capturas / n, choques / n, puntaje / n, segundos / n);
    }

    /** Matriz de confusión de la percepción en barridos nuevos (el maestro maneja). */
    static int[][] confusionPercepcion(Supplier<Redes> redesPorHilo, int pruebas, RobotSimulado.Calibracion cal, int hilos) {
        int n = EtiquetaSector.values().length;
        int[][] m = new int[n][n];
        List<int[][]> porPrueba = repartir(pruebas, hilos, redesPorHilo, (redes, i) -> confusionDePrueba(i, redes.percepcion(), cal));
        for (int[][] parcial : porPrueba)
            for (int f = 0; f < n; f++)
                for (int c = 0; c < n; c++) m[f][c] += parcial[f][c];
        return m;
    }

    static int[][] confusionDePrueba(int i, RedPercepcion rp, RobotSimulado.Calibracion cal) {
        int n = EtiquetaSector.values().length;
        int[][] m = new int[n][n];
        SimuladorBahia sim = SimuladorBahia.alAzar(SEMILLA_EVALUACION + 500 + i, CANICAS, 1 + i % UMBRAL, cal, SensorSimulado.Ruido.NORMAL);
        while (!sim.terminada()) {
            SensorSimulado.Barrido b = sim.medir();
            EtiquetaSector[] red = RedPercepcion.etiquetas(rp.probabilidades(b.telemetria()));
            for (int s = 0; s < red.length; s++) m[b.etiquetas()[s].ordinal()][red[s].ordinal()]++;
            sim.ejecutar(Maestro.decidir(sim, b));
        }
        return m;
    }

    /**
     * Fracción de barridos nuevos en que la red elige la misma dirección que el maestro. Maneja el
     * maestro; la red decide con su memoria, que avanza con las órdenes del maestro (las ejecutadas).
     */
    static double acuerdoDireccion(Supplier<Redes> redesPorHilo, int pruebas, RobotSimulado.Calibracion cal, int hilos) {
        long iguales = 0, total = 0;
        for (int[] parcial : repartir(pruebas, hilos, redesPorHilo, (redes, i) -> acuerdoDePrueba(i, redes, cal))) {
            iguales += parcial[0];
            total += parcial[1];
        }
        return total == 0 ? 0 : iguales / (double) total;
    }

    /** Devuelve {barridos en que coinciden, barridos} de una prueba. */
    static int[] acuerdoDePrueba(int i, Redes redes, RobotSimulado.Calibracion cal) {
        int iguales = 0, total = 0;
        SimuladorBahia sim = SimuladorBahia.alAzar(SEMILLA_EVALUACION + 800 + i, CANICAS, 1 + i % UMBRAL, cal, SensorSimulado.Ruido.NORMAL);
        MemoriaNavegacion memoria = new MemoriaNavegacion();
        Piloto delMaestro = maestroDelEpisodio();
        while (!sim.terminada()) {
            SensorSimulado.Barrido b = sim.medir();
            Piloto.Decision dm = delMaestro.decidir(sim, b);
            OrdenMovimiento maestro = dm.orden();
            double[] p = redes.percepcion().probabilidades(b.telemetria());
            OrdenMovimiento red = redes.movimiento().decidir(
                    RedMovimiento.entradasDelCicloDe(redes.movimiento().variante(), b.telemetria(), p, sim.modo(), memoria));
            if (red.movimiento() == maestro.movimiento()) iguales++;
            total++;
            dm.ejecutarEn(sim);
            delMaestro.registrar(dm);
            memoria.registrarOrden(maestro);
            if (dm.avanceDespues() != null) memoria.registrarOrden(dm.avanceDespues());
        }
        return new int[] {iguales, total};
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
