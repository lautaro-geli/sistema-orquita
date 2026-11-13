package orquitas.servidor.navegacion;

import java.nio.file.Path;
import org.neuroph.core.NeuralNetwork;

/**
 * Red de percepción (MLP de Neuroph): pone una etiqueta a cada uno de los 13 sectores del barrido.
 * Entradas (78): las 39 cercanías y las 39 marcas de desconocido de PreprocesadorTelemetria.
 * Salidas (65): 13 sectores x 5 etiquetas (LIBRE, PARED, OBSTACULO, LOBITO, SALIDA), en orden de sector.
 */
public class RedPercepcion {
    public static final int ENTRADAS = PreprocesadorTelemetria.ENTRADAS;
    public static final int ETIQUETAS = EtiquetaSector.values().length;
    public static final int SALIDAS = PreprocesadorTelemetria.SECTORES * ETIQUETAS;
    private final NeuralNetwork<?> red;

    public RedPercepcion(Path ruta) {
        this(NeuralNetwork.createFromFile(ruta.toString()), ruta.toString());
    }

    public RedPercepcion(NeuralNetwork<?> red) {
        this(red, "red en memoria");
    }

    private RedPercepcion(NeuralNetwork<?> red, String origen) {
        if (red.getInputsCount() != ENTRADAS || red.getOutputsCount() != SALIDAS)
            throw new IllegalArgumentException("Modelo de percepción incompatible (" + origen + "): se esperan " + ENTRADAS
                    + " entradas y " + SALIDAS + " salidas, tiene " + red.getInputsCount() + " y " + red.getOutputsCount());
        this.red = red;
    }

    /** Solo para subclases de prueba. */
    protected RedPercepcion() {
        red = null;
    }

    /** Las 65 salidas de la red (certeza de cada etiqueta en cada sector). */
    public synchronized double[] probabilidades(int[] telemetria) {
        red.setInput(PreprocesadorTelemetria.entradas(telemetria));
        red.calculate();
        return red.getOutput().clone();
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
