// Archivo de simulacion
package orquitas.herramientas;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Supplier;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.learning.error.MeanSquaredError;
import org.neuroph.nnet.learning.BackPropagation;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.RedMovimiento;
import orquitas.servidor.navegacion.RedPercepcion;
import orquitas.simulacion.bahia.Piloto;
import orquitas.simulacion.bahia.RobotSimulado;
import orquitas.simulacion.bahia.SensorSimulado;
import orquitas.simulacion.bahia.SimuladorBahia;

/**
 * Aprendizaje por refuerzo de la red de movimiento por neuroevolución (estrategia evolutiva con pares
 * antitéticos, como OpenAI-ES): parte de una red ya entrenada por imitación y, en cada generación, prueba
 * variantes de sus pesos (+ruido y -ruido) en las mismas bahías y mueve los pesos hacia las que sacaron
 * más puntaje. No imita a nadie: lo único que mira es el puntaje de la grilla en el simulador (más un
 * premio chico por terminar antes). La red de percepción no cambia. La red sigue siendo de Neuroph: solo
 * se le cambian los pesos.
 *
 * <p>Uso: {@code Neuroevolucion carpetaModelos carpetaSalida [--generaciones=300] [--minutos=0] [--pares=20]
 * [--bahias=20] [--sigma=0.01] [--paso=0.01] [--peso-tiempo=2] [--tope-choques=5] [--giro] [--hilos=N]
 * [--semilla=N] [--validacion=200] [--cada=10] [--pruebas=300]}. El barrido y el ruido salen de -Dorquita.barrido y
 * -Dorquita.ruidoRoi, como en EntrenamientoSimulado.
 */
public final class Neuroevolucion {
    static int generaciones = 300, minutos = 0, pares = 20, bahias = 20, validacion = 200, cada = 10, pruebas = 300, hilos = 1;
    static double sigma = 0.01, paso = 0.01, pesoTiempo = 2;
    /**
     * En la recompensa (no en el reporte) los choques de una partida cuentan hasta este tope: una sola
     * partida trabada con decenas de choques tapaba todo lo demás y la señal quedaba en puro ruido.
     */
    static int topeChoques = 5;
    static boolean giro = false;
    static long semilla = 20261117L;
    static final long SEMILLAS_ENTRENAMIENTO = 3_000_000L, SEMILLAS_VALIDACION = 7_000_000L;

    record Contexto(RedPercepcion percepcion, NeuralNetwork<?> red, RedMovimiento movimiento) { }

    /** Resultado de varias partidas: promedio de la recompensa y métricas de la grilla. */
    record Resultado(double recompensa, double puntaje, double misiones, double enUnMinuto, double choques, double segundos) {
        String fila() {
            return String.format(Locale.ROOT, "%.0f %% | %.0f %% | %.2f | %.1f | %.0f", 100 * misiones, 100 * enUnMinuto, choques, puntaje, segundos);
        }
    }

    record Partida(double recompensa, int puntaje, boolean liberada, double segundos, int choques) { }

    private Neuroevolucion() { }

    public static void main(String[] args) throws Exception {
        List<String> pos = new ArrayList<>();
        for (String a : args) {
            if (!a.startsWith("--")) { pos.add(a); continue; }
            String v = a.contains("=") ? a.substring(a.indexOf('=') + 1) : "";
            if (a.startsWith("--generaciones=")) generaciones = Integer.parseInt(v);
            else if (a.startsWith("--minutos=")) minutos = Integer.parseInt(v);
            else if (a.startsWith("--pares=")) pares = Integer.parseInt(v);
            else if (a.startsWith("--bahias=")) bahias = Integer.parseInt(v);
            else if (a.startsWith("--sigma=")) sigma = Double.parseDouble(v);
            else if (a.startsWith("--paso=")) paso = Double.parseDouble(v);
            else if (a.startsWith("--peso-tiempo=")) pesoTiempo = Double.parseDouble(v);
            else if (a.equals("--giro")) giro = true;
            else if (a.startsWith("--hilos=")) hilos = Math.max(1, Integer.parseInt(v));
            else if (a.startsWith("--semilla=")) semilla = Long.parseLong(v);
            else if (a.startsWith("--validacion=")) validacion = Integer.parseInt(v);
            else if (a.startsWith("--cada=")) cada = Math.max(1, Integer.parseInt(v));
            else if (a.startsWith("--pruebas=")) pruebas = Integer.parseInt(v);
            else if (a.startsWith("--tope-choques=")) topeChoques = Integer.parseInt(v);
            else throw new IllegalArgumentException("Argumento desconocido: " + a);
        }
        if (pos.size() < 2) throw new IllegalArgumentException("Uso: Neuroevolucion carpetaModelos carpetaSalida [opciones]");
        Path entrada = Path.of(pos.get(0)), salida = Path.of(pos.get(1));
        Files.createDirectories(salida);
        long inicio = System.currentTimeMillis();

        NeuralNetwork<?> percepcion = NeuralNetwork.createFromFile(entrada.resolve("percepcion.nnet").toFile());
        NeuralNetwork<?> movimiento = NeuralNetwork.createFromFile(entrada.resolve("movimiento.nnet").toFile());
        byte[] bytesPercepcion = EntrenamientoSimulado.serializar(percepcion);
        byte[] bytesMovimiento = EntrenamientoSimulado.serializar(movimiento);
        Supplier<Contexto> copias = () -> {
            NeuralNetwork<?> red = EntrenamientoSimulado.deserializar(bytesMovimiento);
            return new Contexto(new RedPercepcion(EntrenamientoSimulado.deserializar(bytesPercepcion)), red, new RedMovimiento(red));
        };
        double[] theta = pesos(movimiento);
        int n = theta.length;
        log(String.format(Locale.ROOT, "Neuroevolución desde %s: %d pesos; %d pares x %d bahías por generación; sigma %.3f, paso %.3f; "
                + "peso del tiempo %.1f; girar y avanzar: %s; barrido %.2f s; %d hilo(s)",
                entrada, n, pares, bahias, sigma, paso, pesoTiempo, giro ? "sí" : "no", SimuladorBahia.SEGUNDOS_BARRIDO, hilos));

        StringBuilder tabla = new StringBuilder("| Generación | Minutos | Recompensa media (entrenamiento) | Misiones | <=1 min | Choques | Puntaje | Segundos |\n|---|---|---|---|---|---|---|---|\n");
        Resultado inicial = evaluar(copias, theta, SEMILLAS_VALIDACION, validacion);
        tabla.append(String.format(Locale.ROOT, "| 0 | 0 | - | %s |%n", inicial.fila()));
        log("Generación 0 (validación, " + validacion + " bahías): " + inicial.fila());
        double[] mejor = theta.clone();
        double mejorValidacion = inicial.recompensa();
        int mejorGeneracion = 0;

        // Adam.
        double[] m = new double[n], v = new double[n];
        double b1 = 0.9, b2 = 0.999;
        Random azar = new Random(semilla);
        int g;
        for (g = 1; g <= generaciones; g++) {
            if (minutos > 0 && (System.currentTimeMillis() - inicio) / 60000.0 >= minutos) { g--; break; }
            double[][] eps = new double[pares][n];
            for (double[] e : eps) for (int i = 0; i < n; i++) e[i] = azar.nextGaussian();
            long base = SEMILLAS_ENTRENAMIENTO + (long) g * bahias;
            double[] th = theta;
            // Individuo k: par k/2, signo + si k es par.
            List<Double> r = EntrenamientoSimulado.repartir(2 * pares, hilos, copias, (c, k) -> {
                double[] w = th.clone();
                double s = (k % 2 == 0 ? 1 : -1) * sigma;
                double[] e = eps[k / 2];
                for (int i = 0; i < n; i++) w[i] += s * e[i];
                return partidas(c, w, base, bahias).recompensa();
            });
            double[] rangos = rangosCentrados(r);
            double[] grad = new double[n];
            for (int p = 0; p < pares; p++) {
                double peso = rangos[2 * p] - rangos[2 * p + 1];
                for (int i = 0; i < n; i++) grad[i] += peso * eps[p][i];
            }
            for (int i = 0; i < n; i++) {
                double gi = grad[i] / (2 * pares * sigma);
                m[i] = b1 * m[i] + (1 - b1) * gi;
                v[i] = b2 * v[i] + (1 - b2) * gi * gi;
                double mh = m[i] / (1 - Math.pow(b1, g)), vh = v[i] / (1 - Math.pow(b2, g));
                theta[i] += paso * mh / (Math.sqrt(vh) + 1e-8);
            }
            double media = r.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (g % cada == 0) {
                Resultado val = evaluar(copias, theta, SEMILLAS_VALIDACION, validacion);
                double min = (System.currentTimeMillis() - inicio) / 60000.0;
                tabla.append(String.format(Locale.ROOT, "| %d | %.0f | %.1f | %s |%n", g, min, media, val.fila()));
                String marca = "";
                if (val.recompensa() > mejorValidacion) {
                    mejorValidacion = val.recompensa();
                    mejor = theta.clone();
                    mejorGeneracion = g;
                    guardar(movimiento, mejor, salida.resolve("movimiento.nnet"));
                    marca = "  <- mejor hasta ahora (guardada)";
                }
                log(String.format(Locale.ROOT, "Generación %d (%.0f min): entrenamiento %.1f; validación %s%s", g, min, media, val.fila(), marca));
            }
        }
        if (mejorGeneracion == 0) guardar(movimiento, mejor, salida.resolve("movimiento.nnet"));
        Files.copy(entrada.resolve("percepcion.nnet"), salida.resolve("percepcion.nnet"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Comparación final en las bahías de evaluación de siempre (las de los reportes de EntrenamientoSimulado).
        Resultado antes = evaluar(copias, pesos(NeuralNetwork.createFromFile(entrada.resolve("movimiento.nnet").toFile())),
                EntrenamientoSimulado.SEMILLA_EVALUACION, pruebas);
        Resultado despues = evaluar(copias, mejor, EntrenamientoSimulado.SEMILLA_EVALUACION, pruebas);
        double total = (System.currentTimeMillis() - inicio) / 60000.0;
        String reporte = "# Neuroevolución de la red de movimiento (" + LocalDate.now() + ")\n\n"
                + "Generado por `orquitas.herramientas.Neuroevolucion`. Datos SINTÉTICOS del simulador 2D.\n\n"
                + String.format(Locale.ROOT, "- Red de partida: `%s` (%d pesos de movimiento; la percepción no cambia).%n", entrada, n)
                + String.format(Locale.ROOT, "- %d generaciones de %d pares (+/- ruido) en %d bahías; sigma %.3f, paso %.3f (Adam); semilla %d; %.0f min.%n",
                        g > generaciones ? generaciones : g, pares, bahias, sigma, paso, semilla, total)
                + String.format(Locale.ROOT, "- Recompensa: puntaje de la grilla (choques hasta %d por partida) + %.1f puntos por cada minuto que sobra de los 7 al liberarse.%n", topeChoques, pesoTiempo)
                + String.format(Locale.ROOT, "- Girar y avanzar: %s; barrido %.2f s; ruido por ROI x%s.%n", giro ? "sí" : "no",
                        SimuladorBahia.SEGUNDOS_BARRIDO, System.getProperty("orquita.ruidoRoi", "1,1,1"))
                + String.format(Locale.ROOT, "- Mejor red: generación %d (elegida por la validación en %d bahías).%n%n", mejorGeneracion, validacion)
                + "## Evaluación final (" + pruebas + " bahías nuevas, las mismas de los reportes)\n\n"
                + "| Red | Misiones | <=1 min | Choques | Puntaje | Segundos |\n|---|---|---|---|---|---|\n"
                + "| Antes (imitación) | " + antes.fila() + " |\n| Después (neuroevolución) | " + despues.fila() + " |\n\n"
                + "## Curva (validación)\n\n" + tabla;
        Files.writeString(salida.resolve("neuroevolucion.md"), reporte, StandardCharsets.UTF_8);
        System.out.println(reporte);
        log("Listo: " + salida.resolve("movimiento.nnet") + " y " + salida.resolve("neuroevolucion.md"));
    }

    static void log(String s) {
        System.out.println("[Neuroevolución] " + s);
    }

    static double[] pesos(NeuralNetwork<?> red) {
        Double[] w = red.getWeights();
        double[] d = new double[w.length];
        for (int i = 0; i < w.length; i++) d[i] = w[i];
        return d;
    }

    static void guardar(NeuralNetwork<?> red, double[] w, Path ruta) {
        red.setWeights(w);
        if (red.getLearningRule() instanceof BackPropagation bp) bp.setErrorFunction(new MeanSquaredError());
        red.save(ruta.toString());
    }

    /** Rangos centrados en [-0,5; 0,5]: solo importa el orden, no la escala de la recompensa. */
    static double[] rangosCentrados(List<Double> r) {
        Integer[] orden = new Integer[r.size()];
        for (int i = 0; i < orden.length; i++) orden[i] = i;
        Arrays.sort(orden, (a, b) -> Double.compare(r.get(a), r.get(b)));
        double[] rangos = new double[r.size()];
        for (int k = 0; k < orden.length; k++) rangos[orden[k]] = orden.length == 1 ? 0 : k / (double) (orden.length - 1) - 0.5;
        return rangos;
    }

    static Resultado evaluar(Supplier<Contexto> copias, double[] w, long base, int cantidad) {
        List<Partida> ps = EntrenamientoSimulado.repartir(cantidad, hilos, copias, (c, i) -> {
            c.red().setWeights(w);
            return partida(c, base + i);
        });
        return resumir(ps);
    }

    /** Las mismas bahías para una variante (pesos w). */
    static Resultado partidas(Contexto c, double[] w, long base, int cantidad) {
        c.red().setWeights(w);
        List<Partida> ps = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) ps.add(partida(c, base + i));
        return resumir(ps);
    }

    static Resultado resumir(List<Partida> ps) {
        double r = 0, p = 0, mis = 0, uno = 0, ch = 0, s = 0;
        for (Partida x : ps) {
            r += x.recompensa(); p += x.puntaje(); ch += x.choques(); s += x.segundos();
            if (x.liberada()) { mis++; if (x.segundos() <= 60) uno++; }
        }
        int k = ps.size();
        return new Resultado(r / k, p / k, mis / k, uno / k, ch / k, s / k);
    }

    static Partida partida(Contexto c, long semillaBahia) {
        SimuladorBahia sim = SimuladorBahia.alAzar(semillaBahia, EntrenamientoSimulado.CANICAS, EntrenamientoSimulado.UMBRAL,
                RobotSimulado.Calibracion.ESTIMADA, SensorSimulado.Ruido.NORMAL);
        Piloto piloto = Piloto.redes(c.percepcion(), c.movimiento(), giro);
        while (!sim.terminada()) {
            Piloto.Decision d = piloto.decidir(sim, sim.medir());
            d.ejecutarEn(sim);
            piloto.registrar(d);
        }
        boolean liberada = sim.modo() == ModoOperativo.LIBERADA;
        double recompensa = sim.puntaje() + 5 * Math.max(0, sim.choques() - topeChoques) + (liberada ? pesoTiempo * Math.max(0, SimuladorBahia.SEGUNDOS_MAXIMOS - sim.segundos()) / 60 : 0);
        return new Partida(recompensa, sim.puntaje(), liberada, sim.segundos(), sim.choques());
    }
}
