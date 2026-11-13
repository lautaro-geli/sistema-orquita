package orquitas.servidor.navegacion;

/**
 * Reflejo de seguridad que se aplica a la orden de la red de movimiento (en el Servidor y en la
 * simulación por igual). Solo mira la telemetría: si la red pide AVANZAR más de lo que el frente ve
 * libre, acorta el avance para frenar a MARGEN_MM; si el frente ya está encima, retrocede. Los sectores
 * que la percepción marca como LOBITO no cuentan (hay que llegar a tocar la canica para tragarla).
 * Cada choque resta 5 puntos: es más barato frenar antes que confiar en una sola salida de la red.
 */
public final class ReflejoSeguridad {
    /** Distancia que se deja libre delante del robot al frenar. */
    static final double MARGEN_MM = 60;
    /** Sectores que cubren el frente del robot (75° a 105°). */
    static final int PRIMER_FRENTE = 5, ULTIMO_FRENTE = 7;
    /** Por debajo de esto (frente a obstáculo) se retrocede. */
    static final double PEGADO_MM = 40;
    static final double RETROCESO_MM = 120;

    private ReflejoSeguridad() { }

    public static OrdenMovimiento ajustar(OrdenMovimiento orden, int[] telemetria, EtiquetaSector[] etiquetas) {
        if (orden.movimiento() != Movimiento.AVANZAR) return orden;
        int[] corregida = PreprocesadorTelemetria.corregir(telemetria);
        double libre = Double.MAX_VALUE;
        for (int s = PRIMER_FRENTE; s <= ULTIMO_FRENTE; s++) {
            if (etiquetas != null && etiquetas[s] == EtiquetaSector.LOBITO) continue;
            for (int r = PreprocesadorTelemetria.ROI_8X8; r <= PreprocesadorTelemetria.ROI_16X16; r++) {
                int d = corregida[s * PreprocesadorTelemetria.ROIS + r];
                if (!PreprocesadorTelemetria.esDesconocido(d)) libre = Math.min(libre, d - CalibracionRobot.SENSOR_AL_FRENTE_MM);
            }
        }
        if (libre == Double.MAX_VALUE) return orden;
        if (libre < PEGADO_MM) return new OrdenMovimiento(Movimiento.RETROCEDER, CalibracionRobot.msParaAvanzar(RETROCESO_MM));
        double permitido = libre - MARGEN_MM;
        if (CalibracionRobot.mmEn(orden.milisegundos()) <= permitido) return orden;
        if (permitido < CalibracionRobot.mmEn(OrdenMovimiento.MINIMO_MS))
            return new OrdenMovimiento(Movimiento.RETROCEDER, CalibracionRobot.msParaAvanzar(RETROCESO_MM));
        return new OrdenMovimiento(Movimiento.AVANZAR, CalibracionRobot.msParaAvanzar(permitido));
    }
}
