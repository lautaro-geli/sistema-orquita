package orquitas.servidor.navegacion;

/**
 * Medidas del robot real que usan la navegación y la bahía simulada (medidas por el equipo el
 * 15/11/2026, ± 10 %). Si cambian, hay que volver a entrenar las redes (./ejecutar.ps1
 * simular-entrenar), porque los ms que deciden dependen de esto.
 */
public final class CalibracionRobot {
    /** Avance (y retroceso) en mm por segundo: 10 cm en 0,5 s. */
    public static final double MM_POR_SEGUNDO = 200;
    /** Giro sobre su propio eje, en grados por segundo: 360° en ~1,7 s. */
    public static final double GRADOS_POR_SEGUNDO = 212;
    /** Largo y ancho del robot en mm. */
    public static final double LARGO_MM = 200, ANCHO_MM = 150;
    /**
     * Del eje de las ruedas (centro de giro, centrado a lo ancho) al frente, en mm. Quedan
     * LARGO_MM - EJE_AL_FRENTE_MM = 120 mm del eje a la cola.
     */
    public static final double EJE_AL_FRENTE_MM = 80;
    /** Ancho de la boca (embudo) en el frente, en mm: lleva la canica hasta la puerta, sobre el eje. */
    public static final double BOCA_MM = 150;
    /** Distancia del sensor (punta, mirando al frente) al frente del robot, en mm: queda 40 mm delante del eje. */
    public static final double SENSOR_AL_FRENTE_MM = 40;

    private CalibracionRobot() { }

    /** ms para recorrer mm en línea recta. */
    public static int msParaAvanzar(double mm) {
        return (int) Math.round(mm / MM_POR_SEGUNDO * 1000);
    }

    /** mm que se recorren en ms. */
    public static double mmEn(int ms) {
        return MM_POR_SEGUNDO * ms / 1000.0;
    }
}
