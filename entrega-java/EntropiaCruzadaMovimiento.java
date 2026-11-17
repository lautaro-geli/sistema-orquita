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
    /** Si es true, el error de los mm solo cuenta cuando la clase deseada es avanzar o retroceder. */
    private final boolean mmSoloAvance;
    /** Peso del error de los mm frente al de la dirección. */
    private final double pesoMm;
    /** Clases en las que los mm importan (avanzar y retroceder). */
    private final int frente, atras;
    private transient double total;
    private transient long patrones;

    public EntropiaCruzadaMovimiento(int clases) {
        this(clases, false, 1, -1, -1);
    }

    /**
     * @param mmSoloAvance si es true, en las filas cuya clase deseada no es {@code frente} ni {@code atras}
     *                     (los giros, con mm deseados = 0) el error de los mm vale 0: la salida aprende
     *                     "cuánto avanzar si avanza" en lugar de un promedio con ceros.
     * @param pesoMm       multiplica el error de los mm (para equilibrarlo con el de la dirección).
     */
    public EntropiaCruzadaMovimiento(int clases, boolean mmSoloAvance, double pesoMm, int frente, int atras) {
        this.clases = clases;
        this.mmSoloAvance = mmSoloAvance;
        this.pesoMm = pesoMm;
        this.frente = frente;
        this.atras = atras;
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
        int deseadaClase = 0;
        for (int i = 1; i < clases; i++) if (deseada[i] > deseada[deseadaClase]) deseadaClase = i;
        boolean cuentanMm = !mmSoloAvance || deseadaClase == frente || deseadaClase == atras;
        for (int i = clases; i < salida.length; i++) {
            if (!cuentanMm) continue;
            double e = salida[i] - deseada[i];
            error[i] = pesoMm * e;
            total += 0.5 * pesoMm * e * e;
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
