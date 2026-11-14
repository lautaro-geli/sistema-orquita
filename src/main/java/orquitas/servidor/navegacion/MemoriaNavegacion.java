package orquitas.servidor.navegacion;

import java.util.Locale;

/**
 * Memoria explícita de la navegación (no es una red recurrente): lo que el Servidor ya sabe de los
 * ciclos anteriores, en 10 números entre 0 y 1 que se suman a los 14 de la RepresentacionIntermedia
 * como entradas de la red de movimiento. Se usa una por misión (una por Navegacion y una por piloto
 * simulado); no se comparte entre hilos.
 *
 * Ciclo (el orden importa):
 * <ol>
 *   <li>Llega la telemetría.</li>
 *   <li>Se calcula la representación intermedia R.</li>
 *   <li>observar(telemetría, R): actualiza lo que depende de lo que se ve ahora (meta y atasco).</li>
 *   <li>Entradas de la red de movimiento = R (14) + entradas() (10).</li>
 *   <li>Red de movimiento, reflejo de seguridad y detector de atasco: orden final (la que se envía).</li>
 *   <li>registrarOrden(orden final): actualiza lo que depende de lo que se hizo.</li>
 * </ol>
 *
 * <pre>
 * 0-3 ordenAnterior       una entrada por tipo de la última orden final: AVANZAR, RETROCEDER, GIRAR_IZQ,
 *                         GIRAR_DER (DETENER o ninguna: todo 0)
 * 4   cuantoAnterior      ms de esa orden / 1500
 * 5   atasco              min(k, 3)/3, con k = barridos seguidos iguales al anterior después de una orden
 *                         distinta de DETENER (la misma comparación del DetectorAtasco)
 * 6   cicloDesdeMeta      min(n, 5)/5, con n = ciclos desde la última vez que se vio la meta (0 = la ve
 *                         ahora); si nunca la vio, 1
 * 7   anguloMetaAnterior  (a + 180)/360, con a = ángulo de la meta la última vez que se vio, corregido por
 *                         los giros hechos desde entonces y acotado a [-180, 180]; si nunca la vio, 0,5
 * 8-9 rumboEstimado       (sen ψ + 1)/2 y (cos ψ + 1)/2, con ψ = giro acumulado desde la partida
 *                         (izquierda suma, derecha resta)
 * </pre>
 * Al pasar a ESCAPE la meta cambia de Lobito a salida: se olvida la meta anterior (entradas 6 y 7),
 * pero no el rumbo.
 */
public final class MemoriaNavegacion {
    public static final int ENTRADAS = 10;
    public static final int ORDEN_AVANZAR = 0, ORDEN_RETROCEDER = 1, ORDEN_GIRAR_IZQ = 2, ORDEN_GIRAR_DER = 3;
    public static final int CUANTO_ANTERIOR = 4, ATASCO = 5, CICLO_DESDE_META = 6, ANGULO_META_ANTERIOR = 7;
    public static final int RUMBO_SENO = 8, RUMBO_COSENO = 9;
    /** Barridos iguales seguidos a partir de los que la entrada atasco vale 1. */
    public static final int ATASCO_MAXIMO = 3;
    /** Ciclos sin ver la meta a partir de los que la entrada cicloDesdeMeta vale 1. */
    public static final int CICLOS_MAXIMOS_SIN_META = 5;
    private static final Locale ESPANOL = Locale.forLanguageTag("es-AR");

    private OrdenMovimiento ordenAnterior;
    private int[] corregidaAnterior;
    private int barridosIguales;
    private boolean metaVista;
    private int ciclosDesdeMeta;
    /** Grados relativos al robot, positivo = izquierda. Solo vale si metaVista. */
    private double anguloMeta;
    /** Giro acumulado desde la partida en grados, positivo = izquierda. */
    private double rumbo;
    private boolean enEscape;

    /** Deja la memoria como al empezar la misión. */
    public void reiniciar() {
        ordenAnterior = null;
        corregidaAnterior = null;
        barridosIguales = 0;
        olvidarMeta();
        rumbo = 0;
        enEscape = false;
    }

    private void olvidarMeta() {
        metaVista = false;
        ciclosDesdeMeta = 0;
        anguloMeta = 0;
    }

    /**
     * Paso 3 del ciclo. Recibe la telemetría cruda del barrido y su representación intermedia (o las
     * entradas completas de la red, que empiezan con ella) y actualiza el atasco y lo que se sabe de la meta.
     */
    public void observar(int[] telemetria, double[] representacion) {
        int[] actual = PreprocesadorTelemetria.corregir(telemetria);
        boolean seMovio = ordenAnterior != null && ordenAnterior.movimiento() != Movimiento.DETENER;
        if (corregidaAnterior != null && seMovio && DetectorAtasco.iguales(corregidaAnterior, actual)) barridosIguales++;
        else barridosIguales = 0;
        corregidaAnterior = actual;

        boolean escape = representacion[RepresentacionIntermedia.MODO_ESCAPE] >= 0.5;
        if (escape && !enEscape) olvidarMeta(); // La meta pasa de Lobito a salida; el rumbo se conserva.
        enEscape = escape;

        if (representacion[RepresentacionIntermedia.HAY_META] >= 0.5) {
            metaVista = true;
            ciclosDesdeMeta = 0;
            anguloMeta = RepresentacionIntermedia.anguloMetaGrados(representacion);
        } else if (metaVista) {
            ciclosDesdeMeta++;
        }
    }

    /** Paso 6 del ciclo. Recibe la orden final (la que se envía o, en la simulación, la que se ejecutó). */
    public void registrarOrden(OrdenMovimiento ordenFinal) {
        ordenAnterior = ordenFinal;
        double grados = ordenFinal.milisegundos() * CalibracionRobot.GRADOS_POR_SEGUNDO / 1000.0;
        if (ordenFinal.movimiento() == Movimiento.GIRAR_IZQ) girar(grados);
        else if (ordenFinal.movimiento() == Movimiento.GIRAR_DER) girar(-grados);
    }

    /** Giro del robot en grados (positivo = izquierda): el rumbo lo suma y la meta queda del otro lado. */
    private void girar(double grados) {
        rumbo = normalizar(rumbo + grados);
        if (metaVista) anguloMeta = Math.max(-180, Math.min(180, anguloMeta - grados));
    }

    private static double normalizar(double grados) {
        double g = grados % 360;
        if (g > 180) g -= 360;
        if (g <= -180) g += 360;
        return g;
    }

    /** Las 10 entradas de la memoria, en el orden de la tabla de la clase. */
    public double[] entradas() {
        double[] e = new double[ENTRADAS];
        if (ordenAnterior != null) {
            switch (ordenAnterior.movimiento()) {
                case AVANZAR: e[ORDEN_AVANZAR] = 1; break;
                case RETROCEDER: e[ORDEN_RETROCEDER] = 1; break;
                case GIRAR_IZQ: e[ORDEN_GIRAR_IZQ] = 1; break;
                case GIRAR_DER: e[ORDEN_GIRAR_DER] = 1; break;
                default: break; // DETENER: todo 0.
            }
            e[CUANTO_ANTERIOR] = ordenAnterior.milisegundos() / (double) OrdenMovimiento.MAXIMO_MS;
        }
        e[ATASCO] = Math.min(barridosIguales, ATASCO_MAXIMO) / (double) ATASCO_MAXIMO;
        e[CICLO_DESDE_META] = metaVista ? Math.min(ciclosDesdeMeta, CICLOS_MAXIMOS_SIN_META) / (double) CICLOS_MAXIMOS_SIN_META : 1;
        e[ANGULO_META_ANTERIOR] = metaVista ? (anguloMeta + 180) / 360 : 0.5;
        e[RUMBO_SENO] = (Math.sin(Math.toRadians(rumbo)) + 1) / 2;
        e[RUMBO_COSENO] = (Math.cos(Math.toRadians(rumbo)) + 1) / 2;
        return e;
    }

    /**
     * Lo principal de la memoria para la consola del Servidor, por ejemplo
     * "última GIRAR_IZQ 250 ms; meta vista hace 0 ciclos; rumbo +40°".
     */
    public String describir() {
        StringBuilder s = new StringBuilder();
        if (ordenAnterior == null) s.append("sin orden anterior");
        else if (ordenAnterior.movimiento() == Movimiento.DETENER) s.append("última DETENER");
        else s.append("última ").append(ordenAnterior.movimiento()).append(' ').append(ordenAnterior.milisegundos()).append(" ms");
        if (!metaVista) s.append("; meta no vista");
        else {
            s.append("; meta vista hace ").append(ciclosDesdeMeta).append(ciclosDesdeMeta == 1 ? " ciclo" : " ciclos");
            if (ciclosDesdeMeta > 0) s.append(String.format(ESPANOL, ", quedó a %+d°", Math.round(anguloMeta)));
        }
        if (barridosIguales > 0) s.append("; atasco ").append(barridosIguales);
        s.append(String.format(ESPANOL, "; rumbo %+d°", Math.round(rumbo)));
        return s.toString();
    }
}
