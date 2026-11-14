package orquitas.servidor.navegacion;

import java.nio.file.Path;
import org.neuroph.core.NeuralNetwork;

/**
 * Red de movimiento (MLP de Neuroph): decide a qué sector ir (o retroceder) y cuántos mm; el Servidor
 * lo traduce a una orden con milisegundos (ver MetaMovimiento).
 * Entradas (24): las 14 de la representación intermedia (RepresentacionIntermedia: dónde está la meta,
 * qué tan cerca hay obstáculos por zona y el modo) y las 10 de la memoria (MemoriaNavegacion: orden
 * anterior, atasco, hace cuánto y dónde se vio la meta, rumbo estimado).
 * Salidas (15): una por sector meta 0..12 y una para ATRAS (gana la mayor), y la distancia normalizada
 * en [0, 1] (x MetaMovimiento.MAXIMO_MM), que se usa al avanzar o retroceder.
 */
public class RedMovimiento {
    public static final int ENTRADAS = RepresentacionIntermedia.ENTRADAS + MemoriaNavegacion.ENTRADAS;
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

    /**
     * Arma las 24 entradas de la red: la representación intermedia (14) seguida de la memoria (10).
     * Es la única función que las arma, para entrenar y para decidir.
     */
    public static double[] entradas(double[] representacion, double[] memoria) {
        if (representacion == null || representacion.length != RepresentacionIntermedia.ENTRADAS
                || memoria == null || memoria.length != MemoriaNavegacion.ENTRADAS)
            throw new IllegalArgumentException("Se esperan " + RepresentacionIntermedia.ENTRADAS + " valores de representación y "
                    + MemoriaNavegacion.ENTRADAS + " de memoria");
        double[] e = new double[ENTRADAS];
        System.arraycopy(representacion, 0, e, 0, representacion.length);
        System.arraycopy(memoria, 0, e, representacion.length, memoria.length);
        return e;
    }

    /**
     * Pasos 2 a 4 del ciclo de la memoria (ver MemoriaNavegacion): calcula la representación intermedia
     * del barrido, se la hace observar a la memoria y arma las 24 entradas. La usan Navegacion, el piloto
     * simulado y el entrenamiento, así la memoria se calcula igual en los tres. Después de decidir hay
     * que llamar a memoria.registrarOrden con la orden final.
     */
    public static double[] entradasDelCiclo(int[] telemetria, double[] percepcion, ModoOperativo modo, MemoriaNavegacion memoria) {
        double[] representacion = RepresentacionIntermedia.calcular(telemetria, percepcion, modo);
        memoria.observar(telemetria, representacion);
        return entradas(representacion, memoria.entradas());
    }

    /** Salida deseada para entrenar: la orden (del maestro) pasada a sector meta one-hot y distancia. */
    public static double[] salidaDeseada(OrdenMovimiento orden) {
        MetaMovimiento meta = MetaMovimiento.desde(orden);
        double[] d = new double[SALIDAS];
        d[meta.clase()] = 1;
        d[CLASES] = meta.mm() / MetaMovimiento.MAXIMO_MM;
        return d;
    }

    /** Decide la orden a partir de las 24 entradas ya armadas con entradas() o entradasDelCiclo(). */
    public synchronized OrdenMovimiento decidir(double[] entradas) {
        if (entradas == null || entradas.length != ENTRADAS)
            throw new IllegalArgumentException("Se esperan " + ENTRADAS + " entradas");
        red.setInput(entradas);
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
