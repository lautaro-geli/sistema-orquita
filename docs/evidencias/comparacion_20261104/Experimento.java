import java.nio.file.*;
import java.util.*;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.data.*;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.nnet.learning.MomentumBackpropagation;
import org.neuroph.util.TransferFunctionType;
import orquitas.servidor.navegacion.EntornoClasificado;
import orquitas.simulacion.EntrenamientoDemostracion;

/** Experimento: red de cero con datos reales (A), red del repo re-entrenada con sintéticos + reales (B), red del repo sin cambios (C). */
public class Experimento {
    static final int C = 4;
    record Ej(String grupo, EntornoClasificado etiqueta, int[] v) { }

    static double[] norm(int[] t, double alcance) {
        double[] c = new double[39];
        for (int i = 0; i < 39; i++) c[i] = t[i] < 0 ? 0 : Math.min(1, Math.max(0, (alcance - t[i]) / alcance));
        return c;
    }
    static double[] onehot(EntornoClasificado e) { double[] o = new double[C]; o[e.ordinal()] = 1; return o; }

    /** Misma regla que RedNeuronal.clasificarEntorno: argmax y umbral 0,5 (null = no reconocido). */
    static EntornoClasificado clasificar(NeuralNetwork<?> red, int[] t, double alcance) {
        red.setInput(norm(t, alcance)); red.calculate();
        double[] s = red.getOutput(); int g = 0;
        for (int i = 0; i < s.length; i++) { if (!Double.isFinite(s[i])) return null; if (s[i] > s[g]) g = i; }
        return s[g] < 0.5 ? null : EntornoClasificado.values()[g];
    }

    static MomentumBackpropagation regla(int iter) {
        MomentumBackpropagation r = new MomentumBackpropagation();
        r.setLearningRate(0.1); r.setMomentum(0.7); r.setMaxIterations(iter); r.setMaxError(0.01);
        return r;
    }

    /** A: red nueva 39-16-4 con pesos al azar, solo datos reales. */
    static NeuralNetwork<?> entrenarCero(List<Ej> reales, double alcance) {
        MultiLayerPerceptron red = new MultiLayerPerceptron(TransferFunctionType.SIGMOID, 39, 16, C);
        red.randomizeWeights(new Random(20261104L));
        red.setLearningRule(regla(3000));
        DataSet d = new DataSet(39, C);
        List<Ej> copia = new ArrayList<>(reales); Collections.shuffle(copia, new Random(1));
        for (Ej e : copia) d.add(new DataSetRow(norm(e.v(), alcance), onehot(e.etiqueta())));
        red.learn(d);
        return red;
    }

    /** B: parte de los pesos del modelo del repo y sigue entrenando con sus 2000 sintéticos + los reales repetidos x20. */
    static NeuralNetwork<?> entrenarMixto(Path repo, List<Ej> reales) {
        @SuppressWarnings("unchecked")
        NeuralNetwork<MomentumBackpropagation> red = (NeuralNetwork<MomentumBackpropagation>) NeuralNetwork.createFromFile(repo.toString());
        red.setLearningRule(regla(1500));
        List<double[][]> filas = new ArrayList<>();
        for (var m : EntrenamientoDemostracion.generar(2000, 101L)) filas.add(new double[][]{norm(m.valores(), 1500), onehot(m.etiqueta())});
        for (int k = 0; k < 20; k++) for (Ej e : reales) filas.add(new double[][]{norm(e.v(), 1500), onehot(e.etiqueta())});
        Collections.shuffle(filas, new Random(2));
        DataSet d = new DataSet(39, C);
        for (double[][] f : filas) d.add(new DataSetRow(f[0], f[1]));
        red.learn(d);
        return red;
    }

    public static void main(String[] a) throws Exception {
        Path csv = Path.of(a[0]), grupos = Path.of(a[1]), repo = Path.of(a[2]), salida = Path.of(a[3]);
        List<String> fs = Files.readAllLines(csv), gs = Files.readAllLines(grupos);
        List<Ej> todos = new ArrayList<>();
        for (int i = 0; i < fs.size(); i++) {
            String[] c = fs.get(i).split(",");
            int[] v = new int[39]; for (int j = 0; j < 39; j++) v[j] = Integer.parseInt(c[j + 1]);
            todos.add(new Ej(gs.get(i), EntornoClasificado.valueOf(c[0]), v));
        }
        List<String> listaGrupos = new ArrayList<>(new LinkedHashSet<>(gs));
        String[] variantes = {"A_cero_1500", "A_cero_600", "B_repo+reales", "C_repo_sin_cambios"};
        NeuralNetwork<?> repoRed = NeuralNetwork.createFromFile(repo.toString());
        StringBuilder pred = new StringBuilder("variante;grupo;real;prediccion\n");
        Map<String, int[]> aciertos = new LinkedHashMap<>(); // [lobitoOk, lobitoN, objetoOk, objetoN]
        Map<String, Map<String, Integer>> confusion = new LinkedHashMap<>();
        for (String vr : variantes) { aciertos.put(vr, new int[4]); confusion.put(vr, new TreeMap<>()); }
        long t0 = System.currentTimeMillis();
        for (String g : listaGrupos) { // Leave-one-group-out.
            List<Ej> train = new ArrayList<>(), test = new ArrayList<>();
            for (Ej e : todos) (e.grupo().equals(g) ? test : train).add(e);
            Map<String, NeuralNetwork<?>> redes = new LinkedHashMap<>();
            redes.put("A_cero_1500", entrenarCero(train, 1500));
            redes.put("A_cero_600", entrenarCero(train, 600));
            redes.put("B_repo+reales", entrenarMixto(repo, train));
            redes.put("C_repo_sin_cambios", repoRed);
            for (var en : redes.entrySet()) {
                double alcance = en.getKey().equals("A_cero_600") ? 600 : 1500;
                for (Ej e : test) {
                    EntornoClasificado p = clasificar(en.getValue(), e.v(), alcance);
                    boolean ok = p == e.etiqueta();
                    int[] ac = aciertos.get(en.getKey());
                    if (e.etiqueta() == EntornoClasificado.LOBITO) { ac[1]++; if (ok) ac[0]++; } else { ac[3]++; if (ok) ac[2]++; }
                    confusion.get(en.getKey()).merge(e.etiqueta() + "->" + (p == null ? "NO_RECONOCIDO" : p), 1, Integer::sum);
                    pred.append(en.getKey()).append(';').append(g).append(';').append(e.etiqueta()).append(';').append(p == null ? "NO_RECONOCIDO" : p).append('\n');
                }
            }
            System.err.println("pliegue " + g + " listo (" + (System.currentTimeMillis() - t0) / 1000 + " s)");
        }
        Files.createDirectories(salida);
        Files.writeString(salida.resolve("predicciones_validacion_cruzada.csv"), pred);
        StringBuilder r = new StringBuilder();
        for (String vr : variantes) {
            int[] ac = aciertos.get(vr);
            r.append(String.format(Locale.ROOT, "%s: LOBITO nuevas %d/%d (%.1f%%), OBJETO paredes %d/%d (%.1f%%), total %d/%d (%.1f%%)%n  %s%n",
                vr, ac[0], ac[1], 100.0 * ac[0] / ac[1], ac[2], ac[3], 100.0 * ac[2] / ac[3], ac[0] + ac[2], ac[1] + ac[3],
                100.0 * (ac[0] + ac[2]) / (ac[1] + ac[3]), confusion.get(vr)));
        }
        // Modelos finales con TODOS los datos reales.
        NeuralNetwork<?> finalA = entrenarCero(todos, 1500), finalB = entrenarMixto(repo, todos);
        finalA.save(salida.resolve("entorno_cero.nnet").toString());
        finalB.save(salida.resolve("entorno_mixto.nnet").toString());
        // ¿Conservan LIBRE/SALIDA? Evaluación sobre 1000 sintéticos (semilla 202) por clase.
        for (var en : Map.of("A_cero_1500 (final)", finalA, "B_repo+reales (final)", finalB, "C_repo_sin_cambios", repoRed).entrySet()) {
            int[] ok = new int[C], n = new int[C];
            for (var m : EntrenamientoDemostracion.generar(1000, 202L)) {
                n[m.etiqueta().ordinal()]++;
                if (clasificar(en.getValue(), m.valores(), 1500) == m.etiqueta()) ok[m.etiqueta().ordinal()]++;
            }
            r.append(en.getKey()).append(" sobre sinteticos:");
            for (EntornoClasificado e : EntornoClasificado.values())
                r.append(String.format(Locale.ROOT, " %s %.1f%%", e, 100.0 * ok[e.ordinal()] / n[e.ordinal()]));
            r.append('\n');
        }
        // Ajuste de los modelos finales sobre los 44 reales (no es validación: los vieron al entrenar).
        for (var en : Map.of("A_cero_1500 (final)", finalA, "B_repo+reales (final)", finalB).entrySet()) {
            int ok = 0; for (Ej e : todos) if (clasificar(en.getValue(), e.v(), 1500) == e.etiqueta()) ok++;
            r.append(en.getKey()).append(" ajuste sobre los 44 reales usados para entrenar: ").append(ok).append("/44\n");
        }
        Files.writeString(salida.resolve("resultados.txt"), r);
        System.out.print(r);
    }
}
