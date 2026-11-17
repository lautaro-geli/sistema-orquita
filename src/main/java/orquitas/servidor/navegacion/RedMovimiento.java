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

    /** Memoria sin la orden anterior (ni cuánto duró): atasco, hace cuánto y dónde se vio la meta, rumbo. */
    public static final int MEMORIA_SIN_ORDEN = MemoriaNavegacion.ENTRADAS - MemoriaNavegacion.ATASCO;
    /** Telemetría preprocesada, probabilidades de la percepción y modo (BUSQUEDA, ESCAPE) como 0/1. */
    public static final int CRUDAS = PreprocesadorTelemetria.ENTRADAS + RedPercepcion.SALIDAS + 2;

    /**
     * Qué recibe la red de movimiento. La variante sale de la cantidad de entradas del modelo, así el
     * Servidor usa la misma que se usó al entrenar. REPRESENTACION_MEMORIA (24) es la normal; las otras
     * son para comparar en la simulación.
     */
    public enum Variante {
        /** 14 de la representación intermedia + 10 de la memoria. */
        REPRESENTACION_MEMORIA(ENTRADAS),
        /** Solo la representación intermedia (14). */
        REPRESENTACION(RepresentacionIntermedia.ENTRADAS),
        /** Representación (14) + memoria sin la orden anterior (5). */
        REPRESENTACION_MEMORIA_SIN_ORDEN(RepresentacionIntermedia.ENTRADAS + MEMORIA_SIN_ORDEN),
        /** Telemetría, percepción y modo (184), como antes del bloque 5. */
        CRUDA(CRUDAS),
        /** Cruda (184) + memoria sin la orden anterior (5). */
        CRUDA_MEMORIA_SIN_ORDEN(CRUDAS + MEMORIA_SIN_ORDEN),
        /** Cruda (184) + representación intermedia (14). */
        CRUDA_REPRESENTACION(CRUDAS + RepresentacionIntermedia.ENTRADAS);

        public final int entradas;

        Variante(int entradas) { this.entradas = entradas; }

        public static Variante porEntradas(int n) {
            for (Variante v : values()) if (v.entradas == n) return v;
            throw new IllegalArgumentException("Ninguna variante de la red de movimiento tiene " + n + " entradas");
        }

        /** Arma las entradas de esta variante a partir de lo que ya se calculó en el ciclo. */
        double[] armar(int[] telemetria, double[] percepcion, ModoOperativo modo, double[] representacion, double[] memoria) {
            double[] e = new double[entradas];
            int i = 0;
            if (this == CRUDA || this == CRUDA_MEMORIA_SIN_ORDEN || this == CRUDA_REPRESENTACION) {
                double[] tel = PreprocesadorTelemetria.entradas(telemetria);
                System.arraycopy(tel, 0, e, i, tel.length); i += tel.length;
                System.arraycopy(percepcion, 0, e, i, percepcion.length); i += percepcion.length;
                e[i++] = modo == ModoOperativo.BUSQUEDA ? 1 : 0;
                e[i++] = modo == ModoOperativo.ESCAPE ? 1 : 0;
            }
            if (this != CRUDA && this != CRUDA_MEMORIA_SIN_ORDEN) {
                System.arraycopy(representacion, 0, e, i, representacion.length); i += representacion.length;
            }
            if (this == REPRESENTACION_MEMORIA) {
                System.arraycopy(memoria, 0, e, i, memoria.length); i += memoria.length;
            } else if (this == REPRESENTACION_MEMORIA_SIN_ORDEN || this == CRUDA_MEMORIA_SIN_ORDEN) {
                System.arraycopy(memoria, MemoriaNavegacion.ATASCO, e, i, MEMORIA_SIN_ORDEN); i += MEMORIA_SIN_ORDEN;
            }
            if (i != entradas) throw new IllegalStateException("Variante " + this + ": armó " + i + " de " + entradas);
            return e;
        }
    }

    private final Variante variante;
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
        Variante v = null;
        for (Variante x : Variante.values()) if (x.entradas == red.getInputsCount()) v = x;
        if (v == null || red.getOutputsCount() != SALIDAS)
            throw new IllegalArgumentException("Modelo de movimiento incompatible (" + origen + "): se esperan " + ENTRADAS
                    + " entradas (u otra variante) y " + SALIDAS + " salidas, tiene " + red.getInputsCount() + " y " + red.getOutputsCount());
        this.red = red;
        this.variante = v;
    }

    /** Solo para subclases de prueba (variante normal, 24 entradas). */
    protected RedMovimiento() {
        red = null;
        variante = Variante.REPRESENTACION_MEMORIA;
    }

    /** Variante de entradas con la que se entrenó este modelo. */
    public Variante variante() { return variante; }

    /** Pasos 2 a 4 del ciclo de la memoria para la variante v (la memoria observa siempre, se use o no). */
    public static double[] entradasDelCicloDe(Variante v, int[] telemetria, double[] percepcion, ModoOperativo modo, MemoriaNavegacion memoria) {
        double[] representacion = RepresentacionIntermedia.calcular(telemetria, percepcion, modo);
        memoria.observar(telemetria, representacion);
        return v.armar(telemetria, percepcion, modo, representacion, memoria.entradas());
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
        return salidaDeseada(MetaMovimiento.desde(orden));
    }

    /** Salida deseada para una meta (con "girar y avanzar", un giro puede tener mm: el avance después). */
    public static double[] salidaDeseada(MetaMovimiento meta) {
        double[] d = new double[SALIDAS];
        d[meta.clase()] = 1;
        d[CLASES] = meta.mm() / MetaMovimiento.MAXIMO_MM;
        return d;
    }

    /** Decide la orden a partir de las 24 entradas ya armadas con entradas() o entradasDelCiclo(). */
    public synchronized OrdenMovimiento decidir(double[] entradas) {
        if (entradas == null || entradas.length != variante.entradas)
            throw new IllegalArgumentException("Se esperan " + variante.entradas + " entradas");
        red.setInput(entradas);
        red.calculate();
        return interpretar(red.getOutput());
    }

    /** Como decidir, pero devuelve la meta (sector y mm) en lugar de la orden. */
    public synchronized MetaMovimiento decidirMeta(double[] entradas) {
        if (entradas == null || entradas.length != variante.entradas)
            throw new IllegalArgumentException("Se esperan " + variante.entradas + " entradas");
        red.setInput(entradas);
        red.calculate();
        return meta(red.getOutput());
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
