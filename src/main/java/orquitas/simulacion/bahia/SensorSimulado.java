// Archivo de simulacion
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
 * <p>
 * Con -Dorquita.simRealista=true el sensor se parece al real de la toma guiada del 24/11/2026
 * (docs/evidencias/prueba_real/Sim_Realista_20261124.md):
 * <ul>
 *   <li>la canica se ve bien solo cerca: entre CANICA_DESDE_MM y CANICA_BIEN_MM pesa entera; hasta
 *       CANICA_HASTA_MM se mezcla cada vez más con lo que hay detrás; más lejos (o más cerca) el rayo
 *       pasa de largo y mide el fondo;</li>
 *   <li>el 8x8 y el 16x16 ven además el piso a 5–8 cm, con un peso que cambia de barrido en barrido
 *       (cuánto se inclina el sensor), así que miden menos que el 4x4, más cuanto más lejos está la pared;</li>
 *   <li>el 16x16 pierde más lecturas y los bloques sin eco son más largos (el sensor real perdía media vuelta).</li>
 * </ul>
 */
public final class SensorSimulado {
    public static final int SECTORES = PreprocesadorTelemetria.SECTORES;
    public static final int ROIS = PreprocesadorTelemetria.ROIS;
    /** Campo de visión de cada ROI en grados: 4x4, 8x8, 16x16. [A CONFIRMAR] con la hoja de datos. */
    public static final double[] CAMPO_GRADOS = {15, 20, 27};
    public static final double MINIMO_MM = 40, MAXIMO_MM = 1300;
    private static final int RAYOS = 15;
    private static final double REFLEXION_CANICA = 0.6;
    /** Parte de la señal que tiene que venir de la canica para etiquetar el sector como LOBITO. */
    static final double PARTE_CANICA_LOBITO = 0.5;

    /** Simulación parecida al sensor real (ver la clase). Por defecto apagada: así se entrenó y midió E2. */
    public static final boolean REALISTA = Boolean.parseBoolean(System.getProperty("orquita.simRealista", "false"));
    static final double CANICA_DESDE_MM = 70, CANICA_BIEN_MM = 125, CANICA_HASTA_MM = 220;
    /** Peso del piso por rayo para 4x4, 8x8 y 16x16 (en unidades de señal, como 1/d² de una pared de frente). */
    static final double[] PISO_PESO = {0, Double.parseDouble(System.getProperty("orquita.pisoPeso8", "2e-7")),
            Double.parseDouble(System.getProperty("orquita.pisoPeso16", "2e-6"))};
    static final double PISO_DESDE_MM = 50, PISO_HASTA_MM = 80;
    /** Desvío (log) del peso del piso entre barridos. */
    static final double PISO_VARIACION = Double.parseDouble(System.getProperty("orquita.pisoVariacion", "1.0"));
    /** Pérdidas extra del 8x8 y del 16x16 (en la toma real: 18 % y 25 % de lecturas sin eco). */
    static final double PERDIDA_8X8_REALISTA = 0.08, PERDIDA_16X16_REALISTA = 0.17;
    static final int BLOQUE_MAXIMO_REALISTA = 9;
    static final double PROB_BLOQUE_REALISTA = 0.25;

    /** Cuánto pesa la canica a esa distancia del sensor (1 = entera; 0 = no se ve y se mide el fondo). */
    static double visibilidadCanica(double d) {
        if (!REALISTA) return 1;
        if (d < CANICA_DESDE_MM || d >= CANICA_HASTA_MM) return 0;
        if (d <= CANICA_BIEN_MM) return 1;
        return (CANICA_HASTA_MM - d) / (CANICA_HASTA_MM - CANICA_BIEN_MM);
    }

    /** Barrido simulado: telemetría como la manda el robot y la etiqueta verdadera de cada sector. */
    public record Barrido(int[] telemetria, EtiquetaSector[] etiquetas, double[] distanciasReales) { }

    /** Parámetros de ruido. */
    public record Ruido(double sigmaBaseMm, double sigmaRelativa, double perdida4x4, double perdida,
                        double probBloqueSinEco) {
        /**
         * Ruido por defecto: desvío de 4 mm + 2 % de la distancia. Se puede cambiar con
         * -Dorquita.ruidoBase y -Dorquita.ruidoRelativo. La prueba real del 18/11 (VL53L1X, modo Short,
         * 20–75 ms por medición) dio 1–2 mm de desvío a 20–45 cm: ruidoBase=1.5 y ruidoRelativo=0.005
         * se parecen más al sensor real.
         */
        public static final Ruido NORMAL = new Ruido(
                Double.parseDouble(System.getProperty("orquita.ruidoBase", "4")),
                Double.parseDouble(System.getProperty("orquita.ruidoRelativo", "0.02")), 0.04, 0.01, 0.12);
        public static final Ruido NINGUNO = new Ruido(0, 0, 0, 0, 0);
    }

    private final Ruido ruido;
    /**
     * Multiplica el ruido (desvío y pérdidas) de cada ROI (4x4, 8x8, 16x16) para simular un tiempo de
     * medición más corto: el desvío crece como la raíz de (75 ms / tiempo nuevo). Por ejemplo
     * -Dorquita.ruidoRoi=1.51,1.73,1.94 para 33/25/20 ms. Por defecto 1,1,1 (75 ms).
     */
    static final double[] FACTOR_RUIDO_ROI = leerFactores(System.getProperty("orquita.ruidoRoi", "1,1,1"));

    private static double[] leerFactores(String texto) {
        String[] p = texto.split(",");
        double[] f = new double[ROIS];
        for (int r = 0; r < ROIS; r++) f[r] = Double.parseDouble(p[Math.min(r, p.length - 1)].trim());
        return f;
    }

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
        double pisoBarrido = REALISTA ? Math.exp(PISO_VARIACION * azar.nextGaussian()) : 0;
        double pisoDistancia = PISO_DESDE_MM + (PISO_HASTA_MM - PISO_DESDE_MM) * (REALISTA ? azar.nextDouble() : 0);
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
                    double parte = 1;
                    if (imp.tipo() == EtiquetaSector.LOBITO && REALISTA) {
                        parte = visibilidadCanica(imp.distancia());
                        if (parte < 1) { // Lo que hay detrás de la canica, con el resto del peso.
                            Bahia.Impacto fondo = bahia.lanzarRayo(sx, sy, ang, MAXIMO_MM * 1.15, false);
                            if (fondo.tipo() != EtiquetaSector.LIBRE) {
                                double df = Math.max(fondo.distancia(), 25);
                                double pf = (1 - parte) * Math.max(fondo.coseno(), 0.15) / (df * df);
                                suma += pf * fondo.distancia();
                                sumaPeso += pf;
                                cosenoMedio += (1 - parte) * fondo.coseno();
                                if (r == PreprocesadorTelemetria.ROI_8X8) pesoPorTipo[fondo.tipo().ordinal()] += pf;
                            }
                            if (parte <= 0) continue;
                        }
                    }
                    if (imp.tipo() == EtiquetaSector.LIBRE) continue;
                    double refl = imp.tipo() == EtiquetaSector.LOBITO ? REFLEXION_CANICA * parte : 1.0;
                    double d = Math.max(imp.distancia(), 25);
                    double peso = refl * Math.max(imp.coseno(), 0.15) / (d * d);
                    suma += peso * imp.distancia();
                    sumaPeso += peso;
                    cosenoMedio += parte * imp.coseno();
                    if (imp.tipo() == EtiquetaSector.LOBITO) pesoCanica += peso;
                    if (r == PreprocesadorTelemetria.ROI_8X8) pesoPorTipo[imp.tipo().ordinal()] += peso;
                }
                double sinPiso = sumaPeso;
                if (REALISTA && PISO_PESO[r] > 0) { // El piso, a 5–8 cm, en los ROI grandes.
                    double pp = PISO_PESO[r] * pisoBarrido * RAYOS;
                    suma += pp * pisoDistancia;
                    sumaPeso += pp;
                }
                int valor;
                if (sumaPeso <= 0) {
                    valor = -1;
                } else {
                    double d = suma / sumaPeso;
                    d += azar.nextGaussian() * (ruido.sigmaBaseMm() + ruido.sigmaRelativa() * d) * FACTOR_RUIDO_ROI[r];
                    valor = (d < MINIMO_MM || d > MAXIMO_MM) ? -1 : (int) Math.round(d);
                    if (sinPiso > 0) maxParteCanica = Math.max(maxParteCanica, pesoCanica / sinPiso);
                    double perdida = (r == 0 ? ruido.perdida4x4() : ruido.perdida()) * FACTOR_RUIDO_ROI[r];
                    if (REALISTA && r > 0) perdida += (r == PreprocesadorTelemetria.ROI_8X8 ? PERDIDA_8X8_REALISTA : PERDIDA_16X16_REALISTA) * Math.signum(ruido.perdida());
                    if (cosenoMedio / RAYOS < 0.25) perdida += 0.2 * Math.signum(ruido.perdida()); // Muy oblicuo.
                    if (azar.nextDouble() < perdida) valor = -1;
                }
                tel[s * ROIS + r] = valor;
            }
            Bahia.Impacto central = bahia.lanzarRayo(sx, sy, centro, 4000, false);
            reales[s] = central.distancia();
            etiquetas[s] = etiquetaVerdadera(maxParteCanica, pesoPorTipo, central, rayosPorSalida / (double) RAYOS);
        }
        double probBloque = REALISTA && ruido.probBloqueSinEco() > 0 ? PROB_BLOQUE_REALISTA : ruido.probBloqueSinEco();
        if (azar.nextDouble() < probBloque) { // Sensor inclinado (o que pierde media vuelta): bloque sin eco.
            int largo = 2 + azar.nextInt(REALISTA ? BLOQUE_MAXIMO_REALISTA - 1 : 4), desde = azar.nextInt(SECTORES - largo + 1);
            for (int s = desde; s < desde + largo; s++)
                for (int r = 0; r < ROIS; r++) tel[s * ROIS + r] = -1;
        }
        return new Barrido(tel, etiquetas, reales);
    }

    /**
     * Etiqueta verdadera del sector: LOBITO si la canica pesa al menos PARTE_CANICA_LOBITO de la señal en algún ROI;
     * SALIDA si el rayo central o al menos un 30 % del cono del 8x8 pasa por el estrecho; si no, lo que
     * más pesa en el 8x8 (PARED u OBSTACULO); LIBRE si no hay nada al alcance.
     */
    private static EtiquetaSector etiquetaVerdadera(double parteCanica, double[] pesoPorTipo, Bahia.Impacto central,
                                                    double parteSalida) {
        if (parteCanica >= PARTE_CANICA_LOBITO) return EtiquetaSector.LOBITO;
        if (central.cruzaSalida() || parteSalida >= 0.3) return EtiquetaSector.SALIDA;
        double pared = pesoPorTipo[EtiquetaSector.PARED.ordinal()], obst = pesoPorTipo[EtiquetaSector.OBSTACULO.ordinal()];
        if (pared <= 0 && obst <= 0) return EtiquetaSector.LIBRE;
        return obst >= pared ? EtiquetaSector.OBSTACULO : EtiquetaSector.PARED;
    }
}
