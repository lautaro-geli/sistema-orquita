package orquitas.servidor.navegacion;

/**
 * Medidas del robot real que usan la navegación y la bahía simulada. [A CONFIRMAR]: medir cuánto
 * avanza en 1 s y cuántos grados gira en 1 s con las pilas cargadas. Si cambian, hay que volver a
 * entrenar las redes (./ejecutar.ps1 simular-entrenar), porque los ms que deciden dependen de esto.
 */
public final class CalibracionRobot {
    /** Avance (y retroceso) en mm por segundo. [A CONFIRMAR] */
    public static final double MM_POR_SEGUNDO = 250;
    /** Giro en el lugar, en grados por segundo. [A CONFIRMAR] */
    public static final double GRADOS_POR_SEGUNDO = 120;
    /** Largo y ancho del robot en mm. [A CONFIRMAR] */
    public static final double LARGO_MM = 200, ANCHO_MM = 150;
    /** Distancia del sensor al frente del robot, en mm. [A CONFIRMAR] */
    public static final double SENSOR_AL_FRENTE_MM = 30;

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
