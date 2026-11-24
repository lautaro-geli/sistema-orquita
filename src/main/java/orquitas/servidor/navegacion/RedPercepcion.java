package orquitas.servidor.navegacion;

import java.nio.file.Path;
import org.neuroph.core.NeuralNetwork;

/**
 * Red de percepción (MLP de Neuroph) por ventana: la misma red se aplica a cada uno de los 13 sectores
 * y mira el sector, sus 2 vecinos de cada lado y la posición (PreprocesadorTelemetria.entradasVentana).
 * Cada aplicación da 5 salidas (LIBRE, PARED, OBSTACULO, LOBITO, SALIDA); juntas son 65, en orden de sector.
 * Al mirar un sector por vez, entrena con 13 ejemplos por barrido y se pueden balancear los Lobitos.
 * <p>
 * Filtro para LOBITO, después de la red (ver docs/evidencias/prueba_real/Toma_Guiada_20261124.md). Se
 * elige con -Dorquita.filtroLobito:
 * <ul>
 *   <li>{@code eco} (por defecto): un sector solo puede ser LOBITO si el 4x4 corregido tiene eco. En la toma
 *       real la red marcaba LOBITO en sectores ciegos (sin ningún dato); en la simulación es neutro.</li>
 *   <li>{@code forma}: además, el 4x4 a menos de FILTRO_4X4_MM, el 16x16 al menos FILTRO_FORMA_MM más lejos
 *       (o sin eco: objeto chico) y ningún ROI de los sectores vecinos a menos de FILTRO_AISLADO_MM del 4x4
 *       (aislado). En la toma real saca casi todos los LOBITO falsos, pero en la simulación baja mucho la
 *       misión porque ahí la canica se ve distinta (lejos, ancha y pareja en los 3 ROI). Para probar en la pista.</li>
 *   <li>{@code no}: la red sola.</li>
 * </ul>
 * Si un sector no pasa, su certeza de LOBITO queda en 0 y gana la etiqueta que sigue.
 */
public class RedPercepcion {
    /** Entradas del modelo .nnet (una ventana). */
    public static final int ENTRADAS = PreprocesadorTelemetria.ENTRADAS_VENTANA;
    public static final int ETIQUETAS = EtiquetaSector.values().length;
    /** Salidas del modelo .nnet (un sector). */
    public static final int SALIDAS_RED = ETIQUETAS;
    /** Salidas de probabilidades(): 13 sectores x 5 etiquetas. */
    public static final int SALIDAS = PreprocesadorTelemetria.SECTORES * ETIQUETAS;
    /** Modos del filtro para LOBITO (ver la clase). */
    public enum FiltroLobito { NO, ECO, FORMA }
    public static final FiltroLobito FILTRO_LOBITO =
            FiltroLobito.valueOf(System.getProperty("orquita.filtroLobito", "eco").trim().toUpperCase(java.util.Locale.ROOT));
    public static final int FILTRO_4X4_MM = 250;
    public static final int FILTRO_FORMA_MM = 10;
    public static final int FILTRO_AISLADO_MM = 50;
    private final NeuralNetwork<?> red;

    public RedPercepcion(Path ruta) {
        this(NeuralNetwork.createFromFile(ruta.toString()), ruta.toString());
    }

    public RedPercepcion(NeuralNetwork<?> red) {
        this(red, "red en memoria");
    }

    private RedPercepcion(NeuralNetwork<?> red, String origen) {
        if (red.getInputsCount() != ENTRADAS || red.getOutputsCount() != SALIDAS_RED)
            throw new IllegalArgumentException("Modelo de percepción incompatible (" + origen + "): se esperan " + ENTRADAS
                    + " entradas y " + SALIDAS_RED + " salidas, tiene " + red.getInputsCount() + " y " + red.getOutputsCount());
        this.red = red;
    }

    /** Solo para subclases de prueba. */
    protected RedPercepcion() {
        red = null;
    }

    /** Certeza de cada etiqueta en cada sector (65 valores): la red aplicada a las 13 ventanas. */
    public synchronized double[] probabilidades(int[] telemetria) {
        double[] entradas = PreprocesadorTelemetria.entradas(telemetria);
        double[] r = new double[SALIDAS];
        for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
            red.setInput(PreprocesadorTelemetria.entradasVentana(entradas, s));
            red.calculate();
            System.arraycopy(red.getOutput(), 0, r, s * ETIQUETAS, ETIQUETAS);
        }
        if (FILTRO_LOBITO != FiltroLobito.NO) filtrarLobitos(r, PreprocesadorTelemetria.corregir(telemetria), FILTRO_LOBITO);
        return r;
    }

    /** Pone en 0 la certeza de LOBITO de los sectores que no pasan el filtro (ver la clase). */
    public static void filtrarLobitos(double[] probabilidades, int[] corregida, FiltroLobito modo) {
        for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++)
            if (!pareceLobito(corregida, s, modo)) probabilidades[s * ETIQUETAS + EtiquetaSector.LOBITO.ordinal()] = 0;
    }

    /** True si el sector s de la telemetría corregida puede ser LOBITO según el modo del filtro. */
    public static boolean pareceLobito(int[] corregida, int s, FiltroLobito modo) {
        int d4 = corregida[s * 3], d16 = corregida[s * 3 + 2];
        if (modo == FiltroLobito.NO) return true;
        if (d4 == PreprocesadorTelemetria.DESCONOCIDO) return false;
        if (modo == FiltroLobito.ECO) return true;
        if (d4 >= FILTRO_4X4_MM) return false;
        if (d16 != PreprocesadorTelemetria.DESCONOCIDO && d16 - d4 < FILTRO_FORMA_MM) return false;
        for (int v = s - 1; v <= s + 1; v += 2) {
            if (v < 0 || v >= PreprocesadorTelemetria.SECTORES) continue;
            for (int r = 0; r < 3; r++) {
                int d = corregida[v * 3 + r];
                if (d != PreprocesadorTelemetria.DESCONOCIDO && Math.abs(d - d4) < FILTRO_AISLADO_MM) return false;
            }
        }
        return true;
    }

    /** Salida deseada de la red para un sector (one-hot). */
    public static double[] salidaDeseada(EtiquetaSector etiqueta) {
        double[] d = new double[ETIQUETAS];
        d[etiqueta.ordinal()] = 1;
        return d;
    }

    /** Etiqueta ganadora de cada sector. */
    public static EtiquetaSector[] etiquetas(double[] probabilidades) {
        EtiquetaSector[] r = new EtiquetaSector[PreprocesadorTelemetria.SECTORES];
        for (int s = 0; s < r.length; s++) {
            int mejor = 0;
            for (int e = 1; e < ETIQUETAS; e++)
                if (probabilidades[s * ETIQUETAS + e] > probabilidades[s * ETIQUETAS + mejor]) mejor = e;
            r[s] = EtiquetaSector.values()[mejor];
        }
        return r;
    }

    /** Salida deseada (one-hot por sector) para entrenar. */
    public static double[] salidaDeseada(EtiquetaSector[] etiquetas) {
        double[] d = new double[SALIDAS];
        for (int s = 0; s < etiquetas.length; s++) d[s * ETIQUETAS + etiquetas[s].ordinal()] = 1;
        return d;
    }
}
