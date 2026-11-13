package orquitas.servidor.navegacion;

import java.nio.file.Path;
import org.neuroph.core.NeuralNetwork;

/**
 * Red de movimiento (MLP de Neuroph): decide a qué sector ir (o retroceder) y cuántos mm; el Servidor
 * lo traduce a una orden con milisegundos (ver MetaMovimiento).
 * Entradas (184): las 117 de PreprocesadorTelemetria, las 65 salidas de la red de percepción y el modo
 * (BUSQUEDA, ESCAPE) como dos entradas 0/1.
 * Salidas (15): una por sector meta 0..12 y una para ATRAS (gana la mayor), y la distancia normalizada
 * en [0, 1] (x MetaMovimiento.MAXIMO_MM), que se usa al avanzar o retroceder.
 */
public class RedMovimiento {
    public static final int ENTRADAS = PreprocesadorTelemetria.ENTRADAS + RedPercepcion.SALIDAS + 2;
    public static final int CLASES = MetaMovimiento.CLASES;
    public static final int SALIDAS = CLASES + 1;
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

    /** Salida deseada para entrenar: la orden (del maestro) pasada a sector meta one-hot y distancia. */
    public static double[] salidaDeseada(OrdenMovimiento orden) {
        MetaMovimiento meta = MetaMovimiento.desde(orden);
        double[] d = new double[SALIDAS];
        d[meta.clase()] = 1;
        d[CLASES] = meta.mm() / MetaMovimiento.MAXIMO_MM;
        return d;
    }

    public synchronized OrdenMovimiento decidir(int[] telemetria, double[] percepcion, ModoOperativo modo) {
        red.setInput(entradas(telemetria, percepcion, modo));
        red.calculate();
        return interpretar(red.getOutput());
    }

    /** Meta = salida mayor entre las 14 primeras; distancia = la última. */
    public static MetaMovimiento meta(double[] salida) {
        int mejor = 0;
        for (int i = 1; i < CLASES; i++) if (salida[i] > salida[mejor]) mejor = i;
        double mm = Math.max(0, Math.min(1, salida[CLASES])) * MetaMovimiento.MAXIMO_MM;
        return new MetaMovimiento(mejor, mm);
    }

    /** Orden con milisegundos a partir de la salida de la red. */
    public static OrdenMovimiento interpretar(double[] salida) {
        return meta(salida).comoOrden();
    }
}
