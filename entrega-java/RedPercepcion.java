package orquitas.servidor.navegacion;

import java.nio.file.Path;
import org.neuroph.core.NeuralNetwork;

/**
 * Red de percepción (MLP de Neuroph) por ventana: la misma red se aplica a cada uno de los 13 sectores
 * y mira el sector, sus 2 vecinos de cada lado y la posición (PreprocesadorTelemetria.entradasVentana).
 * Cada aplicación da 5 salidas (LIBRE, PARED, OBSTACULO, LOBITO, SALIDA); juntas son 65, en orden de sector.
 * Al mirar un sector por vez, entrena con 13 ejemplos por barrido y se pueden balancear los Lobitos.
 */
public class RedPercepcion {
    /** Entradas del modelo .nnet (una ventana). */
    public static final int ENTRADAS = PreprocesadorTelemetria.ENTRADAS_VENTANA;
    public static final int ETIQUETAS = EtiquetaSector.values().length;
    /** Salidas del modelo .nnet (un sector). */
    public static final int SALIDAS_RED = ETIQUETAS;
    /** Salidas de probabilidades(): 13 sectores x 5 etiquetas. */
    public static final int SALIDAS = PreprocesadorTelemetria.SECTORES * ETIQUETAS;
    private final NeuralNetwork<?> red;

    public RedPercepcion(Path ruta) {
        this(NeuralNetwork.createFromFile(ruta.toString()), ruta.toString());
    }

    public RedPercepcion(NeuralNetwork<?> red) {
        this(red, "red en memoria");
    }

    private RedPercepcion(NeuralNetwork<?> red, String origen) {
        if (red.getInputsCount() != ENTRADAS || red.getOutputsCount() != SALIDAS_RED)
            throw new IllegalArgumentException("Modelo de percepción incompatible (" + origen + "): se esperan " + ENTRADAS
                    + " entradas y " + SALIDAS_RED + " salidas, tiene " + red.getInputsCount() + " y " + red.getOutputsCount());
        this.red = red;
    }

    /** Solo para subclases de prueba. */
    protected RedPercepcion() {
        red = null;
    }

    /** Certeza de cada etiqueta en cada sector (65 valores): la red aplicada a las 13 ventanas. */
    public synchronized double[] probabilidades(int[] telemetria) {
        double[] entradas = PreprocesadorTelemetria.entradas(telemetria);
        double[] r = new double[SALIDAS];
        for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
            red.setInput(PreprocesadorTelemetria.entradasVentana(entradas, s));
            red.calculate();
            System.arraycopy(red.getOutput(), 0, r, s * ETIQUETAS, ETIQUETAS);
        }
        return r;
    }

    /** Salida deseada de la red para un sector (one-hot). */
    public static double[] salidaDeseada(EtiquetaSector etiqueta) {
        double[] d = new double[ETIQUETAS];
        d[etiqueta.ordinal()] = 1;
        return d;
    }

    /** Etiqueta ganadora de cada sector. */
    public static EtiquetaSector[] etiquetas(double[] probabilidades) {
        EtiquetaSector[] r = new EtiquetaSector[PreprocesadorTelemetria.SECTORES];
        for (int s = 0; s < r.length; s++) {
            int mejor = 0;
            for (int e = 1; e < ETIQUETAS; e++)
                if (probabilidades[s * ETIQUETAS + e] > probabilidades[s * ETIQUETAS + mejor]) mejor = e;
            r[s] = EtiquetaSector.values()[mejor];
        }
        return r;
    }

    /** Salida deseada (one-hot por sector) para entrenar. */
    public static double[] salidaDeseada(EtiquetaSector[] etiquetas) {
        double[] d = new double[SALIDAS];
        for (int s = 0; s < etiquetas.length; s++) d[s * ETIQUETAS + etiquetas[s].ordinal()] = 1;
        return d;
    }
}
