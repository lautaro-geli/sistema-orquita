package orquitas.servidor.navegacion;

import java.util.Locale;

/**
 * Representación intermedia entre la percepción y el movimiento: resume "dónde está lo importante" en
 * 14 números, todos en [0, 1]. No es una red: se calcula con reglas fijas a partir de la telemetría
 * (corregida con la regla del -1), de las probabilidades de la red de percepción y del modo. Es la
 * misma función para entrenar y para decidir.
 *
 * Notación: cerc(d) = PreprocesadorTelemetria.cercania(d) (pegado = 1, lejos o desconocido = 0);
 * p[s][e] = probabilidad de la etiqueta e en el sector s; etiqueta[s] = la etiqueta más probable del
 * sector; ángulo del sector s = 15·s − 90 grados (negativo = derecha, positivo = izquierda).
 * Meta M: LOBITO en BUSQUEDA, SALIDA en ESCAPE; en LIBERADA no hay meta (se devuelve como ausente).
 *
 * <pre>
 *  0 hayMeta               s* = sector con mayor p[s][M]; 1 si p[s*][M] >= 0,5, si no 0
 *  1 anguloMeta            promedio de los ángulos de s*-1, s*, s*+1 ponderado por p[s][M], como (a + 90)/180; sin meta: 0,5
 *  2 distanciaMeta         max(cerc 4x4, cerc 8x8) del sector s*; sin meta: 0
 *  3 confianzaMeta         p[s*][M] (aunque sea menor que 0,5)
 *  4 anchoMeta             sectores seguidos con p[s][M] >= 0,5 que contienen a s*, dividido 13; sin meta: 0
 *  5 obstaculoFrente       zona {5, 6, 7}
 *  6 obstaculoDiagonalIzq  zona {8, 9, 10}
 *  7 obstaculoDiagonalDer  zona {2, 3, 4}
 *  8 obstaculoCostadoIzq   zona {11, 12}
 *  9 obstaculoCostadoDer   zona {0, 1}
 * 10 ladoMasAbierto        libreIzq = 1 − max(#6, #8); libreDer = 1 − max(#7, #9); (libreIzq − libreDer + 1)/2
 * 11 modoBusqueda          1 si el modo es BUSQUEDA
 * 12 modoEscape            1 si el modo es ESCAPE
 * 13 fraccionDesconocidos  sectores con los 3 ROI desconocidos / 13
 * </pre>
 * Zona: lo más cercano que no sea la meta. Es el máximo, sobre los sectores de la zona con
 * etiqueta[s] distinta de M, de max(cerc 8x8, cerc 16x16); si no queda ningún sector, 0.
 */
public final class RepresentacionIntermedia {
    public static final int ENTRADAS = 14;
    public static final int HAY_META = 0, ANGULO_META = 1, DISTANCIA_META = 2, CONFIANZA_META = 3, ANCHO_META = 4;
    public static final int OBSTACULO_FRENTE = 5, OBSTACULO_DIAGONAL_IZQ = 6, OBSTACULO_DIAGONAL_DER = 7,
            OBSTACULO_COSTADO_IZQ = 8, OBSTACULO_COSTADO_DER = 9, LADO_MAS_ABIERTO = 10;
    public static final int MODO_BUSQUEDA = 11, MODO_ESCAPE = 12, FRACCION_DESCONOCIDOS = 13;
    /** Probabilidad mínima de la etiqueta meta para decir que la meta está a la vista. */
    public static final double UMBRAL_META = 0.5;

    private static final int SECTORES = PreprocesadorTelemetria.SECTORES;
    private static final int ROIS = PreprocesadorTelemetria.ROIS;
    /** Sectores de cada zona, en el orden de las entradas 5 a 9. */
    private static final int[][] ZONAS = {{5, 6, 7}, {8, 9, 10}, {2, 3, 4}, {11, 12}, {0, 1}};
    /** Solo para el texto de la consola: el frente está "libre" más allá de esto y un lado "cerrado" más acá. */
    private static final double FRENTE_LIBRE_MM = 400, LADO_CERRADO_MM = 250;
    private static final Locale ESPANOL = Locale.forLanguageTag("es-AR");

    private RepresentacionIntermedia() { }

    /** Calcula los 14 números a partir de la telemetría cruda (39), las probabilidades de la percepción (65) y el modo. */
    public static double[] calcular(int[] telemetria, double[] probabilidadesPercepcion, ModoOperativo modo) {
        int[] c = PreprocesadorTelemetria.corregir(telemetria);
        if (probabilidadesPercepcion == null || probabilidadesPercepcion.length != RedPercepcion.SALIDAS)
            throw new IllegalArgumentException("Se esperan " + RedPercepcion.SALIDAS + " probabilidades");
        EtiquetaSector[] etiquetas = RedPercepcion.etiquetas(probabilidadesPercepcion);
        EtiquetaSector meta = metaDelModo(modo);
        double[] r = new double[ENTRADAS];
        r[ANGULO_META] = 0.5;
        if (meta != null) {
            int mejor = sectorMeta(probabilidadesPercepcion, c, meta);
            r[CONFIANZA_META] = probabilidad(probabilidadesPercepcion, mejor, meta);
            if (r[CONFIANZA_META] >= UMBRAL_META) {
                r[HAY_META] = 1;
                r[ANGULO_META] = (anguloMeta(probabilidadesPercepcion, mejor, meta) + 90) / 180;
                r[DISTANCIA_META] = cercaniaMeta(c, mejor);
                r[ANCHO_META] = anchoMeta(probabilidadesPercepcion, mejor, meta) / (double) SECTORES;
            }
        }
        for (int z = 0; z < ZONAS.length; z++) r[OBSTACULO_FRENTE + z] = zona(c, etiquetas, meta, ZONAS[z]);
        double libreIzq = 1 - Math.max(r[OBSTACULO_DIAGONAL_IZQ], r[OBSTACULO_COSTADO_IZQ]);
        double libreDer = 1 - Math.max(r[OBSTACULO_DIAGONAL_DER], r[OBSTACULO_COSTADO_DER]);
        r[LADO_MAS_ABIERTO] = (libreIzq - libreDer + 1) / 2;
        r[MODO_BUSQUEDA] = modo == ModoOperativo.BUSQUEDA ? 1 : 0;
        r[MODO_ESCAPE] = modo == ModoOperativo.ESCAPE ? 1 : 0;
        int desconocidos = 0;
        for (int s = 0; s < SECTORES; s++) {
            boolean alguno = false;
            for (int roi = 0; roi < ROIS; roi++) if (!PreprocesadorTelemetria.esDesconocido(c[s * ROIS + roi])) alguno = true;
            if (!alguno) desconocidos++;
        }
        r[FRACCION_DESCONOCIDOS] = desconocidos / (double) SECTORES;
        return r;
    }

    /** Etiqueta que se busca en cada modo; null si no hay meta (LIBERADA). */
    public static EtiquetaSector metaDelModo(ModoOperativo modo) {
        if (modo == ModoOperativo.BUSQUEDA) return EtiquetaSector.LOBITO;
        if (modo == ModoOperativo.ESCAPE) return EtiquetaSector.SALIDA;
        return null;
    }

    /** Ángulo del centro del sector, en grados relativos al robot: 0 = frente, negativo = derecha, positivo = izquierda. */
    public static double anguloSector(int sector) {
        return MetaMovimiento.GRADOS_POR_SECTOR * sector - 90;
    }

    /** Ángulo de la meta en grados (el inverso de la entrada anguloMeta). */
    public static double anguloMetaGrados(double[] r) {
        return r[ANGULO_META] * 180 - 90;
    }

    private static double probabilidad(double[] p, int sector, EtiquetaSector etiqueta) {
        return Math.max(0, Math.min(1, p[sector * RedPercepcion.ETIQUETAS + etiqueta.ordinal()]));
    }

    /**
     * Sector con mayor probabilidad de meta. Con probabilidades iguales (por ejemplo, una percepción
     * todo o nada que ve la salida en tres sectores seguidos) gana el que tiene vecinos más probables,
     * o sea el del medio de la mancha; si sigue el empate, el más cercano; después, el de menor índice.
     */
    private static int sectorMeta(double[] p, int[] c, EtiquetaSector meta) {
        int mejor = 0;
        for (int s = 1; s < SECTORES; s++) if (esMejorMeta(p, c, meta, s, mejor)) mejor = s;
        return mejor;
    }

    private static boolean esMejorMeta(double[] p, int[] c, EtiquetaSector meta, int a, int b) {
        double pa = probabilidad(p, a, meta), pb = probabilidad(p, b, meta);
        if (pa != pb) return pa > pb;
        double va = probabilidadVecinos(p, a, meta), vb = probabilidadVecinos(p, b, meta);
        if (va != vb) return va > vb;
        return cercaniaMeta(c, a) > cercaniaMeta(c, b);
    }

    private static double probabilidadVecinos(double[] p, int sector, EtiquetaSector meta) {
        double suma = 0;
        if (sector > 0) suma += probabilidad(p, sector - 1, meta);
        if (sector < SECTORES - 1) suma += probabilidad(p, sector + 1, meta);
        return suma;
    }

    /** Promedio de los ángulos del sector y de sus dos vecinos, ponderado por la probabilidad de meta (grados). */
    private static double anguloMeta(double[] p, int sector, EtiquetaSector meta) {
        double suma = 0, peso = 0;
        for (int s = Math.max(0, sector - 1); s <= Math.min(SECTORES - 1, sector + 1); s++) {
            double w = probabilidad(p, s, meta);
            suma += w * anguloSector(s);
            peso += w;
        }
        return suma / peso; // peso > 0: el sector elegido tiene al menos UMBRAL_META.
    }

    /** Cercanía de la meta: la canica se ve mejor en los ROI angostos (4x4 y 8x8). */
    private static double cercaniaMeta(int[] c, int sector) {
        return Math.max(PreprocesadorTelemetria.cercania(c[sector * ROIS + PreprocesadorTelemetria.ROI_4X4]),
                PreprocesadorTelemetria.cercania(c[sector * ROIS + PreprocesadorTelemetria.ROI_8X8]));
    }

    /** Largo de la racha de sectores seguidos con probabilidad de meta >= UMBRAL_META que contiene al sector. */
    private static int anchoMeta(double[] p, int sector, EtiquetaSector meta) {
        int desde = sector, hasta = sector;
        while (desde > 0 && probabilidad(p, desde - 1, meta) >= UMBRAL_META) desde--;
        while (hasta < SECTORES - 1 && probabilidad(p, hasta + 1, meta) >= UMBRAL_META) hasta++;
        return hasta - desde + 1;
    }

    /** Cercanía de lo más cercano de la zona que no sea la meta (ROI 8x8 y 16x16). */
    private static double zona(int[] c, EtiquetaSector[] etiquetas, EtiquetaSector meta, int[] sectores) {
        double maximo = 0;
        for (int s : sectores) {
            if (etiquetas[s] == meta) continue;
            maximo = Math.max(maximo, PreprocesadorTelemetria.cercania(c[s * ROIS + PreprocesadorTelemetria.ROI_8X8]));
            maximo = Math.max(maximo, PreprocesadorTelemetria.cercania(c[s * ROIS + PreprocesadorTelemetria.ROI_16X16]));
        }
        return maximo;
    }

    /**
     * Texto para la consola del Servidor, por ejemplo
     * "Lobito a +30°, 25 cm (0,99); frente libre; izq. cerrado". Solo lee los 14 primeros valores.
     */
    public static String describir(double[] r, ModoOperativo modo) {
        StringBuilder s = new StringBuilder();
        if (modo == ModoOperativo.LIBERADA) {
            s.append("liberada, sin meta");
        } else {
            String meta = modo == ModoOperativo.ESCAPE ? "Salida" : "Lobito";
            if (r[HAY_META] >= 0.5) {
                s.append(meta).append(String.format(ESPANOL, " a %+d°, ", Math.round(anguloMetaGrados(r))));
                s.append(r[DISTANCIA_META] > 0 ? centimetros(r[DISTANCIA_META]) : "sin distancia");
            } else {
                s.append("sin ").append(meta);
            }
            s.append(String.format(ESPANOL, " (%.2f)", r[CONFIANZA_META]));
        }
        double frenteMm = milimetros(r[OBSTACULO_FRENTE]);
        s.append("; frente ").append(frenteMm > FRENTE_LIBRE_MM ? "libre" : "a " + centimetros(r[OBSTACULO_FRENTE]));
        boolean izqCerrado = milimetros(Math.max(r[OBSTACULO_DIAGONAL_IZQ], r[OBSTACULO_COSTADO_IZQ])) < LADO_CERRADO_MM;
        boolean derCerrado = milimetros(Math.max(r[OBSTACULO_DIAGONAL_DER], r[OBSTACULO_COSTADO_DER])) < LADO_CERRADO_MM;
        if (izqCerrado && derCerrado) s.append("; izq. y der. cerrados");
        else if (izqCerrado) s.append("; izq. cerrado");
        else if (derCerrado) s.append("; der. cerrado");
        else if (r[LADO_MAS_ABIERTO] > 0.55) s.append("; más abierto a la izq.");
        else if (r[LADO_MAS_ABIERTO] < 0.45) s.append("; más abierto a la der.");
        else s.append("; lados parejos");
        int desconocidos = (int) Math.round(r[FRACCION_DESCONOCIDOS] * SECTORES);
        if (desconocidos > 0) s.append("; ").append(desconocidos).append(desconocidos == 1 ? " sector sin dato" : " sectores sin dato");
        return s.toString();
    }

    /** Distancia en mm que corresponde a una cercanía (el inverso de PreprocesadorTelemetria.cercania). */
    private static double milimetros(double cercania) {
        return PreprocesadorTelemetria.ALCANCE_MM * (1 - cercania);
    }

    private static String centimetros(double cercania) {
        return String.format(ESPANOL, "%.0f cm", milimetros(cercania) / 10);
    }
}
