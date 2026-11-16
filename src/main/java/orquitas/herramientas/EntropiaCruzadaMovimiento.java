// Archivo de simulacion
package orquitas.herramientas;

import java.io.Serializable;

import org.neuroph.core.learning.error.ErrorFunction;

/**
 * Función de error para entrenar la red de movimiento con entropía cruzada (opción --error=entropia).
 * Las primeras {@code clases} salidas se tratan como una sola elección: se pasan por softmax (suman 1) y el
 * error es −ln(probabilidad de la clase correcta). Las salidas que quedan (los mm) siguen con error
 * cuadrático. Pensada para una capa de salida lineal: Neuroph multiplica el error que devuelve
 * addPatternError por la derivada de la salida (1 si es lineal), así la corrección queda
 * (probabilidad − deseado), que es la de softmax + entropía cruzada.
 */
public final class EntropiaCruzadaMovimiento implements ErrorFunction, Serializable {
    private static final long serialVersionUID = 1L;
    private final int clases;
    private transient double total;
    private transient long patrones;

    public EntropiaCruzadaMovimiento(int clases) {
        this.clases = clases;
    }

    /** Softmax de las primeras n salidas (restando el máximo para que no desborde). */
    static double[] softmax(double[] salida, int n) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) max = Math.max(max, salida[i]);
        double[] p = new double[n];
        double suma = 0;
        for (int i = 0; i < n; i++) { p[i] = Math.exp(salida[i] - max); suma += p[i]; }
        for (int i = 0; i < n; i++) p[i] /= suma;
        return p;
    }

    @Override
    public double[] addPatternError(double[] salida, double[] deseada) {
        double[] error = new double[salida.length];
        double[] p = softmax(salida, clases);
        for (int i = 0; i < clases; i++) {
            error[i] = p[i] - deseada[i];
            if (deseada[i] > 0) total -= deseada[i] * Math.log(Math.max(p[i], 1e-12));
        }
        for (int i = clases; i < salida.length; i++) {
            error[i] = salida[i] - deseada[i];
            total += 0.5 * error[i] * error[i];
        }
        patrones++;
        return error;
    }

    @Override
    public double getTotalError() {
        return patrones == 0 ? 0 : total / patrones;
    }

    @Override
    public void reset() {
        total = 0;
        patrones = 0;
    }
}
