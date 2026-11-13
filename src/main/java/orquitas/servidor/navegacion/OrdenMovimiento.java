package orquitas.servidor.navegacion;

import java.util.Objects;

/**
 * Orden completa para la Orquita: dirección y duración en milisegundos (ORDEN|movimiento|ms).
 * La duración se acota a [MINIMO_MS, MAXIMO_MS] para que un paso nunca supere el Safe Mode (3000 ms).
 */
public record OrdenMovimiento(Movimiento movimiento, int milisegundos) {
    public static final int MINIMO_MS = 50;
    public static final int MAXIMO_MS = 1500;
    /** Duraciones fijas del protocolo anterior, para la navegación por reglas. */
    public static final int PASO_RECTO_MS = 400;
    public static final int PASO_GIRO_MS = 300;

    public OrdenMovimiento {
        Objects.requireNonNull(movimiento);
        milisegundos = movimiento == Movimiento.DETENER ? 0 : Math.max(MINIMO_MS, Math.min(MAXIMO_MS, milisegundos));
    }

    /** Orden con la duración fija de antes (400 ms recto, 300 ms al girar). */
    public static OrdenMovimiento conPasoFijo(Movimiento movimiento) {
        boolean giro = movimiento == Movimiento.GIRAR_IZQ || movimiento == Movimiento.GIRAR_DER;
        return new OrdenMovimiento(movimiento, giro ? PASO_GIRO_MS : PASO_RECTO_MS);
    }

    /** Duración llevada a [0, 1] para la salida de la red. */
    public double duracionNormalizada() {
        return (milisegundos - MINIMO_MS) / (double) (MAXIMO_MS - MINIMO_MS);
    }

    /** Inversa de duracionNormalizada. */
    public static int desnormalizar(double valor) {
        double v = Math.max(0, Math.min(1, valor));
        return (int) Math.round(MINIMO_MS + v * (MAXIMO_MS - MINIMO_MS));
    }

    /** Texto del protocolo v3: ORDEN|movimiento|ms. */
    public String comoTrama() {
        return "ORDEN|" + movimiento.name() + "|" + milisegundos;
    }
}
