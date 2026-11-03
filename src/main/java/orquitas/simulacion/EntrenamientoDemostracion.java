// Archivo de simulacion
package orquitas.simulacion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * MODELO PROVISIONAL con datos SINTÉTICOS (no es evidencia de campo): permite probar el sistema de
 * punta a punta mientras no haya barridos reales. Se reemplaza por el modelo de EntrenamientoReal.
 * Formato de cada ejemplo: 13 sectores x 3 ROI (4x4, 8x8, 16x16), -1 = sin eco.
 * Reglas (simplificadas, a contrastar con la pista real):
 * LIBRE: todo lejos o sin eco. LOBITO: un solo sector cercano (el 16x16 de los vecinos puede verlo,
 * porque su campo es más ancho que 15°). OBJETO: un bloque contiguo de 2 a 13 sectores cercanos.
 * SALIDA: paredes cercanas a ambos lados y un hueco lejano de 2 a 4 sectores contiguos (el estrecho).
 */
public class EntrenamientoDemostracion {
    public record Muestra(int[] valores, EntornoClasificado etiqueta) { }

    private static final int SECTORES = 13, ROI = 3;
    private static final int CERCA_MIN = 60, CERCA_MAX = 900, LEJOS_MIN = 1600, LEJOS_MAX = 4000;

    public static List<Muestra> generar(int cantidad, long semilla) {
        Random azar = new Random(semilla);
        EntornoClasificado[] clases = EntornoClasificado.values();
        List<Muestra> muestras = new ArrayList<>();
        while (muestras.size() < cantidad) {
            EntornoClasificado clase = clases[muestras.size() % clases.length]; // Clases balanceadas.
            int[] d = new int[SECTORES * ROI];
            for (int i = 0; i < d.length; i++) d[i] = azar.nextInt(10) == 0 ? -1 : lejos(azar);
            switch (clase) {
                case LOBITO: {
                    int s = azar.nextInt(SECTORES), distancia = cerca(azar);
                    for (int r = 0; r < ROI; r++) d[s * ROI + r] = ruido(azar, distancia);
                    for (int vecino : new int[]{s - 1, s + 1})
                        if (vecino >= 0 && vecino < SECTORES && azar.nextBoolean()) d[vecino * ROI + 2] = ruido(azar, distancia);
                    break;
                }
                case OBJETO: {
                    int ancho = 2 + azar.nextInt(SECTORES - 1), inicio = azar.nextInt(SECTORES - ancho + 1);
                    for (int s = inicio; s < inicio + ancho; s++) cercano(d, s, azar);
                    break;
                }
                case SALIDA: {
                    int hueco = 2 + azar.nextInt(3), inicio = 2 + azar.nextInt(SECTORES - hueco - 3);
                    for (int s = 0; s < SECTORES; s++) if (s < inicio || s >= inicio + hueco) cercano(d, s, azar);
                    break;
                }
                default: // LIBRE: queda todo lejos.
            }
            muestras.add(new Muestra(d, clase));
        }
        Collections.shuffle(muestras, azar);
        return muestras;
    }

    private static void cercano(int[] d, int sector, Random azar) {
        int distancia = cerca(azar);
        for (int r = 0; r < ROI; r++) d[sector * ROI + r] = ruido(azar, distancia);
    }

    private static int cerca(Random azar) { return CERCA_MIN + azar.nextInt(CERCA_MAX - CERCA_MIN); }
    private static int lejos(Random azar) { return LEJOS_MIN + azar.nextInt(LEJOS_MAX - LEJOS_MIN); }
    private static int ruido(Random azar, int distancia) { return Math.max(0, distancia + azar.nextInt(21) - 10); }

    /** Codificación one-hot en el orden del enum (LIBRE, OBJETO, LOBITO, SALIDA). */
    static double[] salidaEsperada(EntornoClasificado clase) {
        double[] salida = new double[EntornoClasificado.values().length];
        salida[clase.ordinal()] = 1;
        return salida;
    }

    public static void entrenar(Path ruta) throws IOException {
        MultiLayerPerceptron red = new MultiLayerPerceptron(TransferFunctionType.SIGMOID, SECTORES * ROI, 16,
                EntornoClasificado.values().length);
        red.randomizeWeights(new Random(20261104L));
        MomentumBackpropagation regla = new MomentumBackpropagation();
        regla.setLearningRate(0.1); regla.setMomentum(0.7);
        regla.setMaxIterations(1500); regla.setMaxError(0.005);
        red.setLearningRule(regla);
        DataSet datos = new DataSet(SECTORES * ROI, EntornoClasificado.values().length);
        for (Muestra m : generar(2000, 101L)) datos.add(new DataSetRow(RedNeuronal.normalizar(m.valores()), salidaEsperada(m.etiqueta())));
        System.out.println("[Provisional] 2000 ejemplos SINTÉTICOS, MLP 39-16-4, semilla 101");
        red.learn(datos);
        Path carpeta = ruta.toAbsolutePath().getParent();
        if (carpeta != null) Files.createDirectories(carpeta);
        red.save(ruta.toString());
        System.out.println("[Provisional] Iteraciones=" + regla.getCurrentIteration() + " error=" + regla.getTotalNetworkError());
    }

    /** Exactitud sobre 1000 ejemplos sintéticos de otra semilla (null de la red cuenta como error). */
    public static double evaluar(Path ruta) {
        RedNeuronal red = new RedNeuronal(ruta);
        int aciertos = 0, total = 1000;
        for (Muestra m : generar(total, 202L)) if (red.clasificarEntorno(m.valores()) == m.etiqueta()) aciertos++;
        return 100.0 * aciertos / total;
    }

    public static void main(String[] args) throws Exception {
        Path ruta = Path.of(args.length > 0 ? args[0] : "modelos/entorno.nnet");
        entrenar(ruta);
        double exactitud = evaluar(ruta);
        System.out.printf(Locale.ROOT, "[Provisional] Exactitud sintética (semilla 202): %.2f%% - NO es evidencia de campo%n", exactitud);
        if (exactitud < 90) throw new IllegalStateException("El modelo provisional no alcanzó el 90 % sintético");
    }
}
