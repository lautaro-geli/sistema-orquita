package orquitas.servidor.navegacion;

/**
 * Reflejos de seguridad que se aplican a la orden de la red de movimiento (en el Servidor y en la
 * simulación por igual). Solo miran la telemetría; cada choque resta 5 puntos y es más barato frenar
 * que confiar en una sola salida de la red.
 * <ul>
 *   <li>AVANZAR: si el frente ve algo más cerca que lo que la red quiere avanzar, acorta el avance para
 *       frenar a MARGEN_MM; si ya está encima, retrocede. Los sectores marcados LOBITO no cuentan (hay que
 *       tocar la canica para tragarla).</li>
 *   <li>Giros: si hay algo tan cerca que las esquinas del robot lo tocarían al girar, primero retrocede.</li>
 *   <li>Un sector sin dato (desconocido) cuyo vecino ve algo a menos de VECINO_CERCA_MM se toma como pegado:
 *       el sensor da -1 por debajo de 40 mm.</li>
 * </ul>
 */
public final class ReflejoSeguridad {
    /** Distancia que se deja libre delante del robot al frenar. */
    static final double MARGEN_MM = 60;
    /** Sectores que cubren el frente del robot (75° a 105°). */
    static final int PRIMER_FRENTE = 5, ULTIMO_FRENTE = 7;
    /** Por debajo de esto (frente a obstáculo) se retrocede. */
    static final double PEGADO_MM = 40;
    static final double RETROCESO_MM = 80;
    /** Un desconocido con un vecino más cerca que esto se toma como pegado. */
    static final double VECINO_CERCA_MM = 200;
    /**
     * Distancia mínima (desde el sensor) por sector para poder girar en el lugar sin que las esquinas
     * toquen: más en los costados que en el frente, porque el sensor está adelante del centro de giro.
     */
    static final double[] LIBRE_PARA_GIRAR_MM = {145, 140, 125, 110, 95, 85, 80, 85, 95, 110, 125, 140, 145};

    private ReflejoSeguridad() { }

    public static OrdenMovimiento ajustar(OrdenMovimiento orden, int[] telemetria, EtiquetaSector[] etiquetas) {
        int[] corregida = PreprocesadorTelemetria.corregir(telemetria);
        Movimiento m = orden.movimiento();
        if (m == Movimiento.GIRAR_IZQ || m == Movimiento.GIRAR_DER) {
            for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
                if (etiquetas != null && etiquetas[s] == EtiquetaSector.LOBITO) continue;
                if (distancia(corregida, s) < LIBRE_PARA_GIRAR_MM[s]) return retroceder();
            }
            return orden;
        }
        if (m != Movimiento.AVANZAR) return orden;
        double libre = Double.MAX_VALUE;
        for (int s = PRIMER_FRENTE; s <= ULTIMO_FRENTE; s++) {
            if (etiquetas != null && etiquetas[s] == EtiquetaSector.LOBITO) continue;
            libre = Math.min(libre, distancia(corregida, s) - CalibracionRobot.SENSOR_AL_FRENTE_MM);
        }
        if (libre == Double.MAX_VALUE) return orden;
        if (libre < PEGADO_MM) return retroceder();
        double permitido = libre - MARGEN_MM;
        if (CalibracionRobot.mmEn(orden.milisegundos()) <= permitido) return orden;
        if (permitido < CalibracionRobot.mmEn(OrdenMovimiento.MINIMO_MS)) return retroceder();
        return new OrdenMovimiento(Movimiento.AVANZAR, CalibracionRobot.msParaAvanzar(permitido));
    }

    private static OrdenMovimiento retroceder() {
        return new OrdenMovimiento(Movimiento.RETROCEDER, CalibracionRobot.msParaAvanzar(RETROCESO_MM));
    }

    /**
     * Menor distancia conocida del sector (8x8 o 16x16). Si el sector no tiene dato y un vecino ve algo
     * a menos de VECINO_CERCA_MM, se toma como pegado (0). Si no se sabe nada, MAX_VALUE.
     */
    static double distancia(int[] corregida, int s) {
        double d = Double.MAX_VALUE;
        for (int r = PreprocesadorTelemetria.ROI_8X8; r <= PreprocesadorTelemetria.ROI_16X16; r++) {
            int v = corregida[s * PreprocesadorTelemetria.ROIS + r];
            if (!PreprocesadorTelemetria.esDesconocido(v)) d = Math.min(d, v);
        }
        if (d != Double.MAX_VALUE) return d;
        for (int vecino : new int[] {s - 1, s + 1}) {
            if (vecino < 0 || vecino >= PreprocesadorTelemetria.SECTORES) continue;
            int v = corregida[vecino * PreprocesadorTelemetria.ROIS + PreprocesadorTelemetria.ROI_8X8];
            if (!PreprocesadorTelemetria.esDesconocido(v) && v < VECINO_CERCA_MM) return 0;
        }
        return Double.MAX_VALUE;
    }
}
