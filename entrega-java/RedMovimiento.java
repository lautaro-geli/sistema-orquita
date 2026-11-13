package orquitas.servidor.navegacion;

import java.nio.file.Path;
import org.neuroph.core.NeuralNetwork;

/**
 * Red de movimiento (MLP de Neuroph): decide la dirección y cuántos milisegundos moverse.
 * Entradas (184): las 117 de PreprocesadorTelemetria, las 65 salidas de la red de percepción y el modo
 * (BUSQUEDA, ESCAPE) como dos entradas 0/1.
 * Salidas (6): AVANZAR, RETROCEDER, GIRAR_IZQ, GIRAR_DER, DETENER (gana la mayor) y la duración
 * normalizada en [0, 1] (ver OrdenMovimiento.desnormalizar).
 */
public class RedMovimiento {
    public static final int ENTRADAS = PreprocesadorTelemetria.ENTRADAS + RedPercepcion.SALIDAS + 2;
    public static final int DIRECCIONES = Movimiento.values().length;
    public static final int SALIDAS = DIRECCIONES + 1;
    private final NeuralNetwork<?> red;

    public RedMovimiento(Path ruta) {
        this(NeuralNetwork.createFromFile(ruta.toString()), ruta.toString());
    }

    public RedMovimiento(NeuralNetwork<?> red) {
        this(red, "red en memoria");
    }

    private RedMovimiento(NeuralNetwork<?> red, String origen) {
        if (red.getInputsCount() != ENTRADAS || red.getOutputsCount() != SALIDAS)
            throw new IllegalArgumentException("Modelo de movimiento incompatible (" + origen + "): se esperan " + ENTRADAS
                    + " entradas y " + SALIDAS + " salidas, tiene " + red.getInputsCount() + " y " + red.getOutputsCount());
        this.red = red;
    }

    /** Solo para subclases de prueba. */
    protected RedMovimiento() {
        red = null;
    }

    /** Arma las 184 entradas (la misma función para entrenar y para decidir). */
    public static double[] entradas(int[] telemetria, double[] percepcion, ModoOperativo modo) {
        double[] tel = PreprocesadorTelemetria.entradas(telemetria);
        double[] e = new double[ENTRADAS];
        System.arraycopy(tel, 0, e, 0, tel.length);
        System.arraycopy(percepcion, 0, e, tel.length, RedPercepcion.SALIDAS);
        e[ENTRADAS - 2] = modo == ModoOperativo.BUSQUEDA ? 1 : 0;
        e[ENTRADAS - 1] = modo == ModoOperativo.ESCAPE ? 1 : 0;
        return e;
    }

    /** Salida deseada para entrenar: dirección one-hot y duración normalizada. */
    public static double[] salidaDeseada(OrdenMovimiento orden) {
        double[] d = new double[SALIDAS];
        d[orden.movimiento().ordinal()] = 1;
        d[DIRECCIONES] = orden.movimiento() == Movimiento.DETENER ? 0 : orden.duracionNormalizada();
        return d;
    }

    public synchronized OrdenMovimiento decidir(int[] telemetria, double[] percepcion, ModoOperativo modo) {
        red.setInput(entradas(telemetria, percepcion, modo));
        red.calculate();
        return interpretar(red.getOutput());
    }

    /** Dirección = salida mayor entre las 5 primeras; duración = la sexta. */
    public static OrdenMovimiento interpretar(double[] salida) {
        int mejor = 0;
        for (int i = 1; i < DIRECCIONES; i++) if (salida[i] > salida[mejor]) mejor = i;
        Movimiento m = Movimiento.values()[mejor];
        return new OrdenMovimiento(m, OrdenMovimiento.desnormalizar(salida[DIRECCIONES]));
    }
}
