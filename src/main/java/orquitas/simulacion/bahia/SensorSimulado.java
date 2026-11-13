package orquitas.simulacion.bahia;

import java.util.Random;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.PreprocesadorTelemetria;

/**
 * VL53L1X simulado sobre el servo de barrido: 13 sectores (0° = derecha del robot, 90° = frente,
 * 180° = izquierda, cada 15°) x 3 ROI (4x4, 8x8, 16x16). Cada ROI es un cono (campo de visión) más
 * angosto o más ancho. La distancia informada es un promedio ponderado por señal de lo que hay en el
 * cono (señal ~ reflectividad x incidencia / d²): así, con una canica muy cerca el 4x4 marca menos que
 * el 16x16, y con una pared oblicua el 16x16 marca menos que el 4x4, como en los barridos reales.
 * Fuera de [40, 1300] mm o sin señal: -1. Además hay pérdidas al azar y, con cierta probabilidad,
 * un bloque de sectores sin eco (sensor inclinado), para que la red aprenda a no confiar en eso.
 */
public final class SensorSimulado {
    public static final int SECTORES = PreprocesadorTelemetria.SECTORES;
    public static final int ROIS = PreprocesadorTelemetria.ROIS;
    /** Campo de visión de cada ROI en grados: 4x4, 8x8, 16x16. [A CONFIRMAR] con la hoja de datos. */
    public static final double[] CAMPO_GRADOS = {15, 20, 27};
    public static final double MINIMO_MM = 40, MAXIMO_MM = 1300;
    private static final int RAYOS = 15;
    private static final double REFLEXION_CANICA = 0.6;

    /** Barrido simulado: telemetría como la manda el robot y la etiqueta verdadera de cada sector. */
    public record Barrido(int[] telemetria, EtiquetaSector[] etiquetas, double[] distanciasReales) { }

    /** Parámetros de ruido. */
    public record Ruido(double sigmaBaseMm, double sigmaRelativa, double perdida4x4, double perdida,
                        double probBloqueSinEco) {
        public static final Ruido NORMAL = new Ruido(4, 0.02, 0.04, 0.01, 0.12);
        public static final Ruido NINGUNO = new Ruido(0, 0, 0, 0, 0);
    }

    private final Ruido ruido;

    public SensorSimulado(Ruido ruido) {
        this.ruido = ruido;
    }

    /** Ángulo absoluto (radianes) del centro del sector s para un robot con ese rumbo. */
    public static double anguloSector(double rumbo, int sector) {
        return rumbo - Math.PI / 2 + Math.toRadians(15.0 * sector);
    }

    public Barrido medir(Bahia bahia, RobotSimulado robot, Random azar) {
        int[] tel = new int[SECTORES * ROIS];
        EtiquetaSector[] etiquetas = new EtiquetaSector[SECTORES];
        double[] reales = new double[SECTORES];
        double sx = robot.sensorX(), sy = robot.sensorY();
        for (int s = 0; s < SECTORES; s++) {
            double centro = anguloSector(robot.rumbo(), s);
            double maxParteCanica = 0;
            double[] pesoPorTipo = new double[EtiquetaSector.values().length];
            int rayosPorSalida = 0;
            for (int r = 0; r < ROIS; r++) {
                double campo = Math.toRadians(CAMPO_GRADOS[r]);
                double suma = 0, sumaPeso = 0, pesoCanica = 0, cosenoMedio = 0;
                for (int k = 0; k < RAYOS; k++) {
                    double ang = centro - campo / 2 + campo * k / (RAYOS - 1);
                    Bahia.Impacto imp = bahia.lanzarRayo(sx, sy, ang, MAXIMO_MM * 1.15, true);
                    if (r == PreprocesadorTelemetria.ROI_8X8
                            && bahia.lanzarRayo(sx, sy, ang, 4000, false).cruzaSalida()) rayosPorSalida++;
                    if (imp.tipo() == EtiquetaSector.LIBRE) continue;
                    double refl = imp.tipo() == EtiquetaSector.LOBITO ? REFLEXION_CANICA : 1.0;
                    double d = Math.max(imp.distancia(), 25);
                    double peso = refl * Math.max(imp.coseno(), 0.15) / (d * d);
                    suma += peso * imp.distancia();
                    sumaPeso += peso;
                    cosenoMedio += imp.coseno();
                    if (imp.tipo() == EtiquetaSector.LOBITO) pesoCanica += peso;
                    if (r == PreprocesadorTelemetria.ROI_8X8) pesoPorTipo[imp.tipo().ordinal()] += peso;
                }
                int valor;
                if (sumaPeso <= 0) {
                    valor = -1;
                } else {
                    double d = suma / sumaPeso;
                    d += azar.nextGaussian() * (ruido.sigmaBaseMm() + ruido.sigmaRelativa() * d);
                    valor = (d < MINIMO_MM || d > MAXIMO_MM) ? -1 : (int) Math.round(d);
                    maxParteCanica = Math.max(maxParteCanica, pesoCanica / sumaPeso);
                    double perdida = (r == 0 ? ruido.perdida4x4() : ruido.perdida());
                    if (cosenoMedio / RAYOS < 0.25) perdida += 0.2 * Math.signum(ruido.perdida()); // Muy oblicuo.
                    if (azar.nextDouble() < perdida) valor = -1;
                }
                tel[s * ROIS + r] = valor;
            }
            Bahia.Impacto central = bahia.lanzarRayo(sx, sy, centro, 4000, false);
            reales[s] = central.distancia();
            etiquetas[s] = etiquetaVerdadera(maxParteCanica, pesoPorTipo, central, rayosPorSalida / (double) RAYOS);
        }
        if (azar.nextDouble() < ruido.probBloqueSinEco()) { // Sensor inclinado: bloque sin eco.
            int largo = 2 + azar.nextInt(4), desde = azar.nextInt(SECTORES - largo + 1);
            for (int s = desde; s < desde + largo; s++)
                for (int r = 0; r < ROIS; r++) tel[s * ROIS + r] = -1;
        }
        return new Barrido(tel, etiquetas, reales);
    }

    /**
     * Etiqueta verdadera del sector: LOBITO si la canica pesa al menos un 35 % de la señal en algún ROI;
     * SALIDA si el rayo central o al menos un 30 % del cono del 8x8 pasa por el estrecho; si no, lo que
     * más pesa en el 8x8 (PARED u OBSTACULO); LIBRE si no hay nada al alcance.
     */
    private static EtiquetaSector etiquetaVerdadera(double parteCanica, double[] pesoPorTipo, Bahia.Impacto central,
                                                    double parteSalida) {
        if (parteCanica >= 0.35) return EtiquetaSector.LOBITO;
        if (central.cruzaSalida() || parteSalida >= 0.3) return EtiquetaSector.SALIDA;
        double pared = pesoPorTipo[EtiquetaSector.PARED.ordinal()], obst = pesoPorTipo[EtiquetaSector.OBSTACULO.ordinal()];
        if (pared <= 0 && obst <= 0) return EtiquetaSector.LIBRE;
        return obst >= pared ? EtiquetaSector.OBSTACULO : EtiquetaSector.PARED;
    }
}
