package orquitas.servidor.navegacion;

/**
 * Detecta que la Orquita quedó trabada (por ejemplo, contra una pared): si después de una orden de
 * movimiento el barrido llega casi igual al anterior, el movimiento no tuvo efecto. En ese caso cambia
 * la orden para salir: después de un giro, el giro contrario; después de avanzar, retrocede; después de
 * retroceder, gira. Repetir la misma orden trabada solo suma choques (-5 cada uno).
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

    /** Orden para salir del atasco; si se repite, alterna para no volver a lo mismo. */
    static OrdenMovimiento salida(OrdenMovimiento trabada, int vez) {
        int ms = Math.max(300, trabada.milisegundos());
        switch (trabada.movimiento()) {
            case GIRAR_IZQ: return vez % 2 == 1 ? new OrdenMovimiento(Movimiento.GIRAR_DER, ms) : retroceder();
            case GIRAR_DER: return vez % 2 == 1 ? new OrdenMovimiento(Movimiento.GIRAR_IZQ, ms) : retroceder();
            case AVANZAR: return retroceder();
            default: return new OrdenMovimiento(vez % 2 == 1 ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER, 400);
        }
    }

    private static OrdenMovimiento retroceder() {
        return new OrdenMovimiento(Movimiento.RETROCEDER, CalibracionRobot.msParaAvanzar(100));
    }
}
