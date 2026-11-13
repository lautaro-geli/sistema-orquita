package orquitas.servidor.navegacion;

/**
 * Regla del -1 (sin eco) por sector, acordada con el equipo:
 * <ul>
 *   <li>Si el 4x4 o el 16x16 tienen -1 y el 8x8 tiene un valor, se usa el valor del 8x8.</li>
 *   <li>Si el 8x8 tiene -1, el 8x8 queda DESCONOCIDO (el 4x4 y el 16x16 conservan su valor si lo tienen).</li>
 *   <li>Si los tres tienen -1, los tres quedan DESCONOCIDOS.</li>
 * </ul>
 * Desconocido no es "lejos" ni "cerca": la red recibe cercanía 0 y una marca de desconocido aparte,
 * así aprende a no apostar en esa dirección.
 * Orden de la telemetría: sector 0..12, y por sector 4x4, 8x8, 16x16 (índice = sector*3 + roi).
 */
public final class PreprocesadorTelemetria {
    public static final int SECTORES = 13;
    public static final int ROIS = 3;
    public static final int VALORES = SECTORES * ROIS;
    /** Entradas de la red: 39 cercanías + 39 marcas de desconocido. */
    public static final int ENTRADAS = VALORES * 2;
    public static final int SIN_ECO = -1;
    /** Valor de "desconocido" en el arreglo de distancias corregidas. */
    public static final int DESCONOCIDO = -1;
    public static final int ROI_4X4 = 0, ROI_8X8 = 1, ROI_16X16 = 2;
    /** Distancia a partir de la cual la cercanía vale 0 (alcance útil del sensor en modo Short). */
    public static final double ALCANCE_MM = 1300;

    private PreprocesadorTelemetria() { }

    /** Aplica la regla del -1. Devuelve 39 distancias en mm; DESCONOCIDO (-1) donde no hay dato. */
    public static int[] corregir(int[] telemetria) {
        if (telemetria == null || telemetria.length != VALORES)
            throw new IllegalArgumentException("Se esperan " + VALORES + " valores");
        int[] corregida = telemetria.clone();
        for (int s = 0; s < SECTORES; s++) {
            int i4 = s * ROIS + ROI_4X4, i8 = s * ROIS + ROI_8X8, i16 = s * ROIS + ROI_16X16;
            for (int i : new int[] {i4, i8, i16}) if (corregida[i] < 0) corregida[i] = DESCONOCIDO;
            if (corregida[i8] != DESCONOCIDO) {
                if (corregida[i4] == DESCONOCIDO) corregida[i4] = corregida[i8];
                if (corregida[i16] == DESCONOCIDO) corregida[i16] = corregida[i8];
            }
        }
        return corregida;
    }

    /** True si la distancia corregida es desconocida. */
    public static boolean esDesconocido(int distanciaCorregida) {
        return distanciaCorregida < 0;
    }

    /**
     * Entradas de las redes: para cada valor, cercanía = clamp((ALCANCE - d) / ALCANCE, 0, 1)
     * (pegado = 1, lejos = 0; desconocido = 0), y después las 39 marcas (1 = desconocido).
     */
    public static double[] entradas(int[] telemetria) {
        int[] corregida = corregir(telemetria);
        double[] entradas = new double[ENTRADAS];
        for (int i = 0; i < VALORES; i++) {
            if (esDesconocido(corregida[i])) {
                entradas[i] = 0;
                entradas[VALORES + i] = 1;
            } else {
                entradas[i] = Math.min(1, Math.max(0, (ALCANCE_MM - corregida[i]) / ALCANCE_MM));
            }
        }
        return entradas;
    }

    /** Distancia del 8x8 corregida del sector, o DESCONOCIDO. */
    public static int distancia8x8(int[] corregida, int sector) {
        return corregida[sector * ROIS + ROI_8X8];
    }
}
