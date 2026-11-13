package orquitas.servidor.navegacion;

/**
 * Detecta que la Orquita quedó trabada (por ejemplo, contra una pared): si después de una orden de
 * movimiento el barrido llega casi igual al anterior, el movimiento no tuvo efecto. En ese caso cambia
 * la orden para salir: primero lo contrario de lo que hizo y, si sigue trabada, todas las opciones de a una.
 * Repetir la misma orden trabada solo suma choques (-5 cada uno).
 * Tiene memoria (el barrido y la orden anteriores): se usa uno por misión.
 */
public final class DetectorAtasco {
    /** Diferencia media (mm) por debajo de la cual dos barridos se consideran iguales. */
    static final double DIFERENCIA_MAXIMA_MM = 20;
    /** Mínimo de valores conocidos en los dos barridos para poder comparar. */
    static final int MINIMO_COMPARABLES = 15;

    private int[] anterior;
    private OrdenMovimiento ordenAnterior;
    private int atascosSeguidos;

    /** Revisa si la orden anterior no tuvo efecto y, si es así, devuelve otra; si no, la de la red. */
    public synchronized OrdenMovimiento revisar(int[] telemetria, OrdenMovimiento propuesta) {
        int[] actual = PreprocesadorTelemetria.corregir(telemetria);
        OrdenMovimiento elegida = propuesta;
        if (anterior != null && ordenAnterior != null && ordenAnterior.movimiento() != Movimiento.DETENER
                && iguales(anterior, actual)) {
            atascosSeguidos++;
            elegida = salida(ordenAnterior, atascosSeguidos);
        } else {
            atascosSeguidos = 0;
        }
        anterior = actual;
        ordenAnterior = elegida;
        return elegida;
    }

    /** Cuántos barridos seguidos se detectó atasco (0 = se movió). */
    public synchronized int atascosSeguidos() {
        return atascosSeguidos;
    }

    static boolean iguales(int[] a, int[] b) {
        double suma = 0;
        int n = 0;
        for (int i = 0; i < a.length; i++) {
            if (PreprocesadorTelemetria.esDesconocido(a[i]) || PreprocesadorTelemetria.esDesconocido(b[i])) continue;
            suma += Math.abs(a[i] - b[i]);
            n++;
        }
        return n >= MINIMO_COMPARABLES && suma / n < DIFERENCIA_MAXIMA_MM;
    }

    /**
     * Orden para salir del atasco. La primera vez prueba lo contrario de la orden trabada (giro opuesto,
     * o retroceder si avanzaba); si sigue trabada, recorre todas las opciones en orden (retroceder, girar
     * a la derecha, girar a la izquierda, avanzar un poco) para no quedar alternando entre dos que chocan.
     */
    static OrdenMovimiento salida(OrdenMovimiento trabada, int vez) {
        if (vez == 1) {
            int ms = Math.max(300, trabada.milisegundos());
            switch (trabada.movimiento()) {
                case GIRAR_IZQ: return new OrdenMovimiento(Movimiento.GIRAR_DER, ms);
                case GIRAR_DER: return new OrdenMovimiento(Movimiento.GIRAR_IZQ, ms);
                case AVANZAR: return retroceder();
                default: return new OrdenMovimiento(Movimiento.GIRAR_DER, 300);
            }
        }
        switch ((vez - 2) % 4) {
            case 0: return retroceder();
            case 1: return new OrdenMovimiento(Movimiento.GIRAR_DER, 300);
            case 2: return new OrdenMovimiento(Movimiento.GIRAR_IZQ, 300);
            default: return new OrdenMovimiento(Movimiento.AVANZAR, CalibracionRobot.msParaAvanzar(30));
        }
    }

    private static OrdenMovimiento retroceder() {
        return new OrdenMovimiento(Movimiento.RETROCEDER, CalibracionRobot.msParaAvanzar(100));
    }
}
