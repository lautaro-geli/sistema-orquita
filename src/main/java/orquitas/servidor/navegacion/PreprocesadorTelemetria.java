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
    /** Entradas de la percepción: 39 cercanías + 39 marcas de desconocido + 3 rasgos por sector (39). */
    public static final int ENTRADAS = VALORES * 3;
    public static final int SIN_ECO = -1;
    /** Valor de "desconocido" en el arreglo de distancias corregidas. */
    public static final int DESCONOCIDO = -1;
    public static final int ROI_4X4 = 0, ROI_8X8 = 1, ROI_16X16 = 2;
    /** Distancia a partir de la cual la cercanía vale 0 (alcance útil del sensor en modo Short). */
    public static final double ALCANCE_MM = 1300;
    /**
     * Lecturas por debajo de esto se toman como sin dato (-1), igual que en el simulador: en la prueba real
     * del 19/11 el sensor devolvió 0–46 mm "válidos" hacia los costados, sin nada a esa distancia.
     */
    public static final int MINIMO_MM = 40;

    /**
     * Corrección del piso. El sensor real va a ~0,8 cm del piso y el 16x16 (a veces el 8x8) ve el piso a
     * 5–8 cm (pruebas del 19 y 22/11). Ese patrón (ROI grande cerca, 4x4 lejos o sin eco) en la simulación
     * solo lo daban las canicas, y la red marcaba LOBITO en la salida. Se apaga con
     * -Dorquita.correccionPiso=false (para comparar en la simulación).
     */
    public static final boolean CORRECCION_PISO = Boolean.parseBoolean(System.getProperty("orquita.correccionPiso", "true"));
    /** Si el 8x8 o el 16x16 miden más de esto por debajo del 4x4, se toma como piso y se usa el 4x4. */
    public static final int PISO_MARGEN_MM = 20;
    /**
     * Solo un 8x8 o 16x16 más cerca que esto puede ser piso. Más lejos, que el ROI grande mida menos que
     * el 4x4 es una pared oblicua (también en el simulador) y se deja como está.
     */
    public static final int PISO_ALCANCE_MM = 200;

    private PreprocesadorTelemetria() { }

    /**
     * Aplica, por sector: lecturas de menos de MINIMO_MM como sin dato, la corrección del piso
     * (corregirPiso) y la regla del -1. Devuelve 39 distancias en mm; DESCONOCIDO (-1) donde no hay dato.
     */
    public static int[] corregir(int[] telemetria) {
        if (telemetria == null || telemetria.length != VALORES)
            throw new IllegalArgumentException("Se esperan " + VALORES + " valores");
        int[] corregida = telemetria.clone();
        for (int s = 0; s < SECTORES; s++) {
            int i4 = s * ROIS + ROI_4X4, i8 = s * ROIS + ROI_8X8, i16 = s * ROIS + ROI_16X16;
            for (int i : new int[] {i4, i8, i16}) if (corregida[i] < MINIMO_MM) corregida[i] = DESCONOCIDO;
            if (CORRECCION_PISO) corregirPiso(corregida, i4, i8, i16);
            if (corregida[i8] != DESCONOCIDO) {
                if (corregida[i4] == DESCONOCIDO) corregida[i4] = corregida[i8];
                if (corregida[i16] == DESCONOCIDO) corregida[i16] = corregida[i8];
            }
        }
        return corregida;
    }

    /**
     * Corrección del piso en un sector (antes de la regla del -1). El 4x4 es el ROI más angosto y el que
     * menos ve el piso, así que manda:
     * <ul>
     *   <li>Solo se miran el 8x8 y el 16x16 con dato y a menos de PISO_ALCANCE_MM.</li>
     *   <li>4x4 con dato: si el ROI grande mide menos que el 4x4 - PISO_MARGEN_MM, toma el valor del 4x4.</li>
     *   <li>4x4 sin eco: el ROI grande queda sin eco (como el 4x4).</li>
     * </ul>
     * Las canicas no cambian: se ven igual o más cerca en el 4x4 que en los ROI grandes.
     */
    static void corregirPiso(int[] c, int i4, int i8, int i16) {
        for (int i : new int[] {i8, i16}) {
            if (c[i] == DESCONOCIDO || c[i] >= PISO_ALCANCE_MM) continue;
            if (c[i4] == DESCONOCIDO) c[i] = DESCONOCIDO;
            else if (c[i] < c[i4] - PISO_MARGEN_MM) c[i] = c[i4];
        }
    }

    /** True si la distancia corregida es desconocida. */
    public static boolean esDesconocido(int distanciaCorregida) {
        return distanciaCorregida < 0;
    }

    /**
     * Cercanía de una distancia ya corregida: clamp((ALCANCE_MM - d) / ALCANCE_MM, 0, 1). Pegado vale 1,
     * lejos vale 0 y desconocido también vale 0. La usan las entradas de la percepción y la
     * representación intermedia.
     */
    public static double cercania(int distanciaCorregida) {
        if (esDesconocido(distanciaCorregida)) return 0;
        return Math.min(1, Math.max(0, (ALCANCE_MM - distanciaCorregida) / ALCANCE_MM));
    }

    /**
     * Entradas de la red de percepción (117):
     * <ol>
     *   <li>39 cercanías (cercania): pegado = 1, lejos = 0; desconocido = 0.</li>
     *   <li>39 marcas de desconocido (1 = desconocido).</li>
     *   <li>3 rasgos por sector, llevados de [-1, 1] a [0, 1] (0,5 = sin diferencia): cercanía 4x4 menos
     *       16x16, 4x4 menos 8x8, y 8x8 del sector menos el 8x8 más cercano de sus vecinos. Una canica
     *       cerca se ve más cerca en el ROI angosto que en el ancho y más cerca que los sectores de al lado.</li>
     * </ol>
     */
    public static double[] entradas(int[] telemetria) {
        int[] corregida = corregir(telemetria);
        double[] entradas = new double[ENTRADAS];
        for (int i = 0; i < VALORES; i++) {
            entradas[i] = cercania(corregida[i]);
            if (esDesconocido(corregida[i])) entradas[VALORES + i] = 1;
        }
        int base = VALORES * 2;
        for (int s = 0; s < SECTORES; s++) {
            double c4 = entradas[s * ROIS + ROI_4X4], c8 = entradas[s * ROIS + ROI_8X8], c16 = entradas[s * ROIS + ROI_16X16];
            double vecino = 0;
            if (s > 0) vecino = Math.max(vecino, entradas[(s - 1) * ROIS + ROI_8X8]);
            if (s < SECTORES - 1) vecino = Math.max(vecino, entradas[(s + 1) * ROIS + ROI_8X8]);
            entradas[base + s * 3] = (c4 - c16 + 1) / 2;
            entradas[base + s * 3 + 1] = (c4 - c8 + 1) / 2;
            entradas[base + s * 3 + 2] = (c8 - vecino + 1) / 2;
        }
        return entradas;
    }

    /** Sectores a cada lado que mira la red de percepción por ventana. */
    public static final int RADIO_VENTANA = 2;
    /** Valores por sector en la ventana: 3 cercanías, 3 marcas de desconocido y 3 rasgos. */
    private static final int POR_SECTOR_VENTANA = 9;
    /** Entradas de la red por ventana: 5 sectores x 9 valores + la posición del sector. */
    public static final int ENTRADAS_VENTANA = (2 * RADIO_VENTANA + 1) * POR_SECTOR_VENTANA + 1;

    /**
     * Entradas de la red de percepción para un sector: sus valores y los de RADIO_VENTANA vecinos de cada
     * lado (fuera del abanico: cercanía 0, desconocido 1, rasgos 0,5) y la posición del sector (0 = derecha,
     * 1 = izquierda). Recibe las entradas ya armadas con entradas(), para no recalcularlas 13 veces.
     */
    public static double[] entradasVentana(double[] entradas, int sector) {
        double[] v = new double[ENTRADAS_VENTANA];
        int i = 0;
        for (int k = sector - RADIO_VENTANA; k <= sector + RADIO_VENTANA; k++) {
            boolean existe = k >= 0 && k < SECTORES;
            for (int r = 0; r < ROIS; r++) v[i++] = existe ? entradas[k * ROIS + r] : 0;
            for (int r = 0; r < ROIS; r++) v[i++] = existe ? entradas[VALORES + k * ROIS + r] : 1;
            for (int r = 0; r < 3; r++) v[i++] = existe ? entradas[VALORES * 2 + k * 3 + r] : 0.5;
        }
        v[i] = sector / (double) (SECTORES - 1);
        return v;
    }

    /** Distancia del 8x8 corregida del sector, o DESCONOCIDO. */
    public static int distancia8x8(int[] corregida, int sector) {
        return corregida[sector * ROIS + ROI_8X8];
    }
}
