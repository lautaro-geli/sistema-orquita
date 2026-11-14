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
import orquitas.simulacion.bahia.Maestro;
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
    static final String USO = "Uso: EntrenamientoSimulado [episodios=250] [epocas=60] [carpeta=modelos] [rondasDagger=2] [--hilos=N]";
    static final long SEMILLA = 20261113L;
    static final long SEMILLA_EVALUACION = 900_000L;
    static final int OCULTAS_PERCEPCION = 24;
    static final int OCULTAS_MOVIMIENTO = 20;
    static final double PROBABILIDAD_ORDEN_AL_AZAR = 0.15;
    static final int CANICAS = 3, UMBRAL = 3;
    /** Tope de repeticiones de una clase rara de meta al balancear la red de movimiento. */
    static final int REPETICION_MAXIMA = 6;

    /**
     * Un barrido con lo que se sabe de él: la telemetría, las etiquetas verdaderas, el modo, la orden del
     * maestro (lo que se enseña) y la orden que realmente se ejecutó después (la del maestro, una al azar
     * o la de las redes en DAgger), que es la que entra en la memoria del ciclo siguiente.
     */
    record Ejemplo(int[] telemetria, EtiquetaSector[] etiquetas, ModoOperativo modo, OrdenMovimiento orden,
                   OrdenMovimiento ejecutada) { }

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
        log("Ejemplos del maestro: " + contar(datos) + " en " + datos.size() + " episodios");

        MultiLayerPerceptron percepcion = nuevaRed(RedPercepcion.ENTRADAS, OCULTAS_PERCEPCION, RedPercepcion.SALIDAS_RED);
        // La percepción tiene ~8 filas por barrido (una por sector, balanceadas): necesita menos épocas.
        entrenar(percepcion, datosPercepcion(datos), Math.max(5, epocas / 3), "percepción");
        MultiLayerPerceptron movimiento = nuevaRed(RedMovimiento.ENTRADAS, OCULTAS_MOVIMIENTO, RedMovimiento.SALIDAS);
        entrenar(movimiento, datosMovimiento(datos, new RedPercepcion(percepcion)), epocas, "movimiento");

        for (int ronda = 1; ronda <= rondas; ronda++) {
            List<List<Ejemplo>> nuevos = recolectarConRedes(episodios / 2, cal, copiasDe(percepcion, movimiento),
                    SEMILLA + 10_000L * ronda, hilos);
            datos.addAll(nuevos);
            log("DAgger ronda " + ronda + ": +" + contar(nuevos) + " ejemplos (total " + contar(datos) + ")");
            entrenar(percepcion, datosPercepcion(datos), Math.max(3, epocas / 6), "percepción");
            entrenar(movimiento, datosMovimiento(datos, new RedPercepcion(percepcion)), Math.max(10, epocas / 2), "movimiento");
        }

        Path rutaPercepcion = carpeta.resolve("percepcion.nnet"), rutaMovimiento = carpeta.resolve("movimiento.nnet");
        percepcion.save(rutaPercepcion.toString());
        movimiento.save(rutaMovimiento.toString());

        // Evaluación con bahías nuevas: cada hilo carga sus redes de los archivos recién guardados.
        Supplier<Redes> guardadas = () -> new Redes(new RedPercepcion(rutaPercepcion), new RedMovimiento(rutaMovimiento));
        int pruebas = 100;
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
               .append(String.format(Locale.ROOT, "- Redes: percepción por ventana %d-%d-%d (x 13 sectores), movimiento %d-%d-%d (sigmoide, MomentumBackpropagation).%n",
                       RedPercepcion.ENTRADAS, OCULTAS_PERCEPCION, RedPercepcion.SALIDAS_RED,
                       RedMovimiento.ENTRADAS, OCULTAS_MOVIMIENTO, RedMovimiento.SALIDAS))
               .append(String.format(Locale.ROOT, "- Entradas de la red de movimiento: %d de la representación intermedia y %d de la memoria.%n%n",
                       RepresentacionIntermedia.ENTRADAS, MemoriaNavegacion.ENTRADAS))
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
    static List<String> posicionales(String[] args) {
        List<String> posicionales = new ArrayList<>();
        for (String a : args) {
            if (!a.startsWith("--")) posicionales.add(a);
            else if (!a.startsWith("--hilos=")) throw new IllegalArgumentException("Argumento desconocido: " + a);
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
    static DataSet datosPercepcion(List<List<Ejemplo>> episodios) {
        List<DataSetRow> filas = new ArrayList<>();
        Random azar = new Random(SEMILLA);
        for (List<Ejemplo> episodio : episodios) {
            for (Ejemplo e : episodio) {
                double[] entradas = PreprocesadorTelemetria.entradas(e.telemetria());
                for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
                    EtiquetaSector et = e.etiquetas()[s];
                    int veces = et == EtiquetaSector.LOBITO ? 4 : et == EtiquetaSector.PARED ? (azar.nextInt(2) == 0 ? 1 : 0) : 1;
                    if (veces == 0) continue;
                    DataSetRow fila = new DataSetRow(PreprocesadorTelemetria.entradasVentana(entradas, s), RedPercepcion.salidaDeseada(et));
                    for (int i = 0; i < veces; i++) filas.add(fila);
                }
            }
        }
        return mezclado(RedPercepcion.ENTRADAS, RedPercepcion.SALIDAS_RED, filas);
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
        return mezclado(RedMovimiento.ENTRADAS, RedMovimiento.SALIDAS, filasMovimiento(episodios, percepcion));
    }

    /** Las filas de datosMovimiento, en el orden de los episodios (sin mezclar). */
    static List<DataSetRow> filasMovimiento(List<List<Ejemplo>> episodios, RedPercepcion percepcion) {
        int[] cuenta = new int[RedMovimiento.CLASES];
        for (List<Ejemplo> episodio : episodios)
            for (Ejemplo e : episodio) cuenta[MetaMovimiento.desde(e.orden()).clase()]++;
        int maximo = 0;
        for (int c : cuenta) maximo = Math.max(maximo, c);
        List<DataSetRow> filas = new ArrayList<>();
        for (List<Ejemplo> episodio : episodios) {
            MemoriaNavegacion memoria = new MemoriaNavegacion(); // Una por prueba, como en el Servidor.
            for (Ejemplo e : episodio) {
                double[] p = percepcion.probabilidades(e.telemetria());
                double[] entradas = RedMovimiento.entradasDelCiclo(e.telemetria(), p, e.modo(), memoria);
                memoria.registrarOrden(e.ejecutada());
                DataSetRow fila = new DataSetRow(entradas, RedMovimiento.salidaDeseada(e.orden()));
                int c = cuenta[MetaMovimiento.desde(e.orden()).clase()];
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
        Random azar = azarDelEpisodio(semilla);
        int umbral = 1 + azar.nextInt(UMBRAL); // También pruebas con umbral 1 y 2 (más escapes).
        SimuladorBahia sim = SimuladorBahia.alAzar(semilla, CANICAS, umbral, cal, SensorSimulado.Ruido.NORMAL);
        List<Ejemplo> episodio = new ArrayList<>();
        while (!sim.terminada()) {
            SensorSimulado.Barrido b = sim.medir();
            OrdenMovimiento maestro = Maestro.decidir(sim, b);
            OrdenMovimiento ejecutada = azar.nextDouble() < PROBABILIDAD_ORDEN_AL_AZAR ? ordenAlAzar(azar) : maestro;
            episodio.add(new Ejemplo(b.telemetria(), b.etiquetas(), sim.modo(), maestro, ejecutada));
            sim.ejecutar(ejecutada);
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
        Random azar = azarDelEpisodio(semilla);
        Piloto piloto = Piloto.redes(redes.percepcion(), redes.movimiento());
        int umbral = 1 + azar.nextInt(UMBRAL);
        SimuladorBahia sim = SimuladorBahia.alAzar(semilla, CANICAS, umbral, cal, SensorSimulado.Ruido.NORMAL);
        List<Ejemplo> episodio = new ArrayList<>();
        while (!sim.terminada()) {
            SensorSimulado.Barrido b = sim.medir();
            OrdenMovimiento maestro = Maestro.decidir(sim, b);
            OrdenMovimiento deLasRedes = piloto.decidir(sim, b).orden();
            OrdenMovimiento ejecutada = azar.nextDouble() < 0.5 ? deLasRedes : maestro;
            episodio.add(new Ejemplo(b.telemetria(), b.etiquetas(), sim.modo(), maestro, ejecutada));
            sim.ejecutar(ejecutada);
            piloto.registrarOrden(ejecutada);
        }
        return episodio;
    }

    static OrdenMovimiento ordenAlAzar(Random azar) {
        Movimiento[] m = {Movimiento.AVANZAR, Movimiento.GIRAR_IZQ, Movimiento.GIRAR_DER, Movimiento.RETROCEDER};
        return new OrdenMovimiento(m[azar.nextInt(m.length)], 100 + azar.nextInt(900));
    }

    /** Pruebas completas con el maestro (la prueba i usa la semilla SEMILLA_EVALUACION + i). */
    static Resumen evaluarMaestro(int pruebas, RobotSimulado.Calibracion cal, int hilos) {
        return resumir(repartir(pruebas, hilos, i -> pruebaCompleta(SEMILLA_EVALUACION + i, Piloto.maestro(), cal)));
    }

    /** Las mismas pruebas con las redes: un piloto nuevo por prueba (con su memoria y su detector de atasco). */
    static Resumen evaluarRedes(Supplier<Redes> redesPorHilo, int pruebas, RobotSimulado.Calibracion cal, int hilos) {
        return resumir(repartir(pruebas, hilos, redesPorHilo,
                (redes, i) -> pruebaCompleta(SEMILLA_EVALUACION + i, Piloto.redes(redes.percepcion(), redes.movimiento()), cal)));
    }

    static Prueba pruebaCompleta(long semilla, Piloto piloto, RobotSimulado.Calibracion cal) {
        SimuladorBahia sim = SimuladorBahia.alAzar(semilla, CANICAS, UMBRAL, cal, SensorSimulado.Ruido.NORMAL);
        while (!sim.terminada()) {
            OrdenMovimiento orden = piloto.decidir(sim, sim.medir()).orden();
            sim.ejecutar(orden);
            piloto.registrarOrden(orden);
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
        while (!sim.terminada()) {
            SensorSimulado.Barrido b = sim.medir();
            OrdenMovimiento maestro = Maestro.decidir(sim, b);
            double[] p = redes.percepcion().probabilidades(b.telemetria());
            OrdenMovimiento red = redes.movimiento().decidir(RedMovimiento.entradasDelCiclo(b.telemetria(), p, sim.modo(), memoria));
            if (red.movimiento() == maestro.movimiento()) iguales++;
            total++;
            sim.ejecutar(maestro);
            memoria.registrarOrden(maestro);
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
