package orquitas.herramientas;

import java.io.IOException;
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
import orquitas.servidor.navegacion.EntornoClasificado;
import orquitas.servidor.navegacion.RedNeuronal;

/**
 * Entrena la red con los barridos REALES de la pista (no forma parte de los diagramas). Uso:
 * {@code EntrenamientoReal [datos/barridos.csv] [modelos/entorno.nnet] [ocultas]}.
 * Mezcla con semilla fija, separa 80/20, normaliza con RedNeuronal.normalizar (la misma función que la
 * inferencia), entrena un MLP 39 -> ocultas -> 4 con MomentumBackpropagation, evalúa en validación y
 * guarda el modelo y un reporte en docs/evidencias/entrenamiento_AAAAMMDD.md (evidencia de la consigna).
 */
public class EntrenamientoReal {
    static final long SEMILLA = 20261104L;
    private static final int VALORES = 39;

    record Ejemplo(EntornoClasificado etiqueta, int[] valores) { }

    public static void main(String[] args) throws Exception {
        Path csv = Path.of(args.length > 0 ? args[0] : "datos/barridos.csv");
        Path modelo = Path.of(args.length > 1 ? args[1] : "modelos/entorno.nnet");
        int ocultas = args.length > 2 ? Integer.parseInt(args[2]) : 16;
        Path reporte = Path.of("docs/evidencias/entrenamiento_"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".md");
        double exactitud = entrenar(csv, modelo, ocultas, reporte);
        System.out.printf(Locale.ROOT, "Exactitud de validación: %.2f%%. Modelo: %s. Reporte: %s%n", exactitud, modelo, reporte);
    }

    /** Entrena, guarda el modelo y el reporte; devuelve la exactitud de validación en %. */
    static double entrenar(Path csv, Path modelo, int ocultas, Path reporte) throws IOException {
        List<Ejemplo> ejemplos = leer(csv);
        if (ejemplos.size() < 10) throw new IllegalStateException("Muy pocos ejemplos en " + csv + ": " + ejemplos.size());
        Collections.shuffle(ejemplos, new Random(SEMILLA));
        int corte = (int) Math.round(ejemplos.size() * 0.8);
        List<Ejemplo> entrenamiento = ejemplos.subList(0, corte), validacion = ejemplos.subList(corte, ejemplos.size());

        int clases = EntornoClasificado.values().length;
        MultiLayerPerceptron red = new MultiLayerPerceptron(TransferFunctionType.SIGMOID, VALORES, ocultas, clases);
        red.randomizeWeights(new Random(SEMILLA));
        MomentumBackpropagation regla = new MomentumBackpropagation();
        regla.setLearningRate(0.1); regla.setMomentum(0.7);
        regla.setMaxIterations(5000); regla.setMaxError(0.01);
        red.setLearningRule(regla);
        DataSet datos = new DataSet(VALORES, clases);
        for (Ejemplo e : entrenamiento) {
            double[] esperado = new double[clases];
            esperado[e.etiqueta().ordinal()] = 1;
            datos.add(new DataSetRow(RedNeuronal.normalizar(e.valores()), esperado));
        }
        System.out.println("Entrenando MLP 39-" + ocultas + "-" + clases + " con " + entrenamiento.size() + " ejemplos...");
        red.learn(datos);
        Path carpeta = modelo.toAbsolutePath().getParent();
        if (carpeta != null) Files.createDirectories(carpeta);
        red.save(modelo.toString());

        // Evaluación con la misma clase que usa el Servidor (incluye el umbral de confianza).
        RedNeuronal evaluada = new RedNeuronal(modelo);
        int[][] matriz = new int[clases][clases + 1]; // Última columna: no reconocido.
        int aciertos = 0;
        for (Ejemplo e : validacion) {
            EntornoClasificado p = evaluada.clasificarEntorno(e.valores());
            matriz[e.etiqueta().ordinal()][p == null ? clases : p.ordinal()]++;
            if (p == e.etiqueta()) aciertos++;
        }
        double exactitud = validacion.isEmpty() ? 0 : 100.0 * aciertos / validacion.size();
        String texto = armarReporte(csv, ejemplos, entrenamiento.size(), validacion.size(), ocultas,
                regla.getCurrentIteration(), regla.getTotalNetworkError(), matriz, exactitud, modelo);
        Path carpetaReporte = reporte.toAbsolutePath().getParent();
        if (carpetaReporte != null) Files.createDirectories(carpetaReporte);
        Files.writeString(reporte, texto, StandardCharsets.UTF_8);
        System.out.print(texto);
        return exactitud;
    }

    /** Filas "ETIQUETA,v0,...,v38"; las filas mal formadas se informan y se saltean. */
    static List<Ejemplo> leer(Path csv) throws IOException {
        List<Ejemplo> ejemplos = new ArrayList<>();
        int numero = 0;
        for (String fila : Files.readAllLines(csv, StandardCharsets.UTF_8)) {
            numero++;
            if (fila.isBlank()) continue;
            String[] c = fila.split(",", -1);
            try {
                if (c.length != VALORES + 1) throw new IllegalArgumentException("cantidad de columnas");
                EntornoClasificado etiqueta = EntornoClasificado.valueOf(c[0].trim());
                int[] v = new int[VALORES];
                for (int i = 0; i < VALORES; i++) v[i] = Integer.parseInt(c[i + 1].trim());
                ejemplos.add(new Ejemplo(etiqueta, v));
            } catch (IllegalArgumentException e) {
                System.err.println("Fila " + numero + " ignorada: " + e.getMessage());
            }
        }
        return ejemplos;
    }

    private static String armarReporte(Path csv, List<Ejemplo> todos, int nEntrenamiento, int nValidacion, int ocultas,
                                       int iteraciones, double error, int[][] matriz, double exactitud, Path modelo) {
        StringBuilder r = new StringBuilder();
        r.append("# Entrenamiento de la red neuronal - ").append(LocalDate.now()).append("\n\n");
        r.append("- Datos: `").append(csv).append("` (barridos reales del robot, ").append(todos.size()).append(" ejemplos).\n");
        r.append("- Categorías y ejemplos:");
        for (EntornoClasificado e : EntornoClasificado.values())
            r.append(" ").append(e).append("=").append(todos.stream().filter(x -> x.etiqueta() == e).count());
        r.append("\n- Entrada: 39 valores (13 sectores x ROI 4x4, 8x8, 16x16), normalizados como cercanía con RedNeuronal.normalizar (-1 = sin eco = 0).\n");
        r.append("- Red: MLP 39-").append(ocultas).append("-4 sigmoide, MomentumBackpropagation (tasa 0,1, momento 0,7, máx. 5000 iteraciones, error objetivo 0,01).\n");
        r.append(String.format(Locale.ROOT, "- Iteraciones: %d; error final: %.6f.%n", iteraciones, error));
        r.append("- Validación: mezcla con semilla ").append(SEMILLA).append(", 80 % entrenamiento (").append(nEntrenamiento)
                .append(") y 20 % validación (").append(nValidacion).append("), sin ejemplos compartidos.\n");
        r.append("- Criterio: exactitud = aciertos / ejemplos de validación; con umbral de confianza ")
                .append(RedNeuronal.UMBRAL_CONFIANZA).append(" (por debajo, \"no reconocido\" cuenta como error).\n");
        r.append(String.format(Locale.ROOT, "- **Exactitud de validación: %.2f %%**%n%n", exactitud));
        r.append("Matriz de confusión (filas = real, columnas = predicción):\n\n| real \\ pred |");
        for (EntornoClasificado e : EntornoClasificado.values()) r.append(" ").append(e).append(" |");
        r.append(" NO RECONOCIDO |\n|---|");
        for (int i = 0; i <= EntornoClasificado.values().length; i++) r.append("---|");
        r.append("\n");
        for (EntornoClasificado e : EntornoClasificado.values()) {
            r.append("| ").append(e).append(" |");
            for (int v : matriz[e.ordinal()]) r.append(" ").append(v).append(" |");
            r.append("\n");
        }
        r.append("\nModelo guardado en `").append(modelo).append("`.\n");
        return r.toString();
    }
}
