package orquitas.servidor.navegacion;

import java.util.Objects;

/**
 * CU-07 y CU-11. Decide el movimiento de la Orquita a partir de la clasificación de la red y
 * del modo operativo. La telemetría son 13 sectores (0° a 180°, cada 15°) x 3 ROI (4x4, 8x8,
 * 16x16) en orden de sector; las decisiones usan el ROI 16x16 en mm. Convención [A CONFIRMAR]
 * con el robot: el sector 0 es la derecha, el 6 el frente y el 12 la izquierda.
 * La red clasifica en todos los modos; el modo cambia la meta: búsqueda persigue al Lobito y
 * esquiva obstáculos, escape ignora al Lobito y busca la salida, liberada se queda quieta.
 *
 * Dos formas de decidir:
 * <ul>
 *   <li>Con redes (RedPercepcion + RedMovimiento, entrenadas en la bahía simulada): la percepción
 *       etiqueta cada sector y la red de movimiento elige dirección y milisegundos.</li>
 *   <li>Por reglas (RedNeuronal de una sola clase por barrido): la forma anterior, con paso fijo.</li>
 * </ul>
 * En los dos casos la telemetría pasa antes por la regla del -1 (PreprocesadorTelemetria).
 */
public class Navegacion {
    private static final int SECTORES = 13;
    private static final int ROI_POR_SECTOR = 3;
    /** Un lado (5 sectores) está "cerrado" si su suma es menor que este valor por sector. */
    private static final double UMBRAL_LADO_MM = 1200;
    /** Campo libre: ningún sector ve un obstáculo más cerca que esto. [A CONFIRMAR] en la pista. */
    private static final double ALCANCE_LIBRE_MM = 1500;
    /** Lecturas seguidas sin reconocer a partir de las que se retrocede. */
    private static final int MAX_NO_RECONOCIDAS = 3;
    private static final int ROI_16X16 = 2;
    private static final int PRIMER_SECTOR_FRENTE = 5;
    private static final int ULTIMO_SECTOR_FRENTE = 7;
    private static final int SECTORES_POR_LADO = 5;

    /** Distancia que se usa en las reglas para un sector sin dato (desconocido): se toma como lejos. */
    static final int SIN_ECO_MM = 4000;

    private final RedNeuronal redNeuronal;
    private final RedPercepcion redPercepcion;
    private final RedMovimiento redMovimiento;
    private volatile ModoOperativo modoOperativo = ModoOperativo.BUSQUEDA;
    private OrdenMovimiento ordenDecidida = new OrdenMovimiento(Movimiento.DETENER, 0);
    private EtiquetaSector[] ultimasEtiquetas;
    private int lecturasNoReconocidas;

    /** Navegación por reglas con la red de una sola clase (paso fijo). */
    public Navegacion(RedNeuronal redNeuronal) {
        this.redNeuronal = Objects.requireNonNull(redNeuronal);
        this.redPercepcion = null;
        this.redMovimiento = null;
    }

    /** Navegación con las redes de percepción por sector y de movimiento (dirección y ms). */
    public Navegacion(RedPercepcion redPercepcion, RedMovimiento redMovimiento) {
        this.redNeuronal = null;
        this.redPercepcion = Objects.requireNonNull(redPercepcion);
        this.redMovimiento = Objects.requireNonNull(redMovimiento);
    }

    /** CU-07. Decide la orden (dirección y duración) y la deja en ordenDecidida (CU-08 la envía). */
    public synchronized void determinarMovimiento(int[] telemetria) {
        int[] corregida = PreprocesadorTelemetria.corregir(telemetria);
        if (redPercepcion != null) {
            ordenDecidida = decidirConRedes(telemetria, corregida);
            return;
        }
        EntornoClasificado entorno = redNeuronal.clasificarEntorno(telemetria);
        if (entorno == null) {
            ordenDecidida = OrdenMovimiento.conPasoFijo(resolverNoReconocido());
            return;
        }
        lecturasNoReconocidas = 0;
        ordenDecidida = OrdenMovimiento.conPasoFijo(calcularMovimiento(entorno, corregida));
    }

    private OrdenMovimiento decidirConRedes(int[] telemetria, int[] corregida) {
        double[] percepcion = redPercepcion.probabilidades(telemetria);
        ultimasEtiquetas = RedPercepcion.etiquetas(percepcion);
        if (modoOperativo == ModoOperativo.ESCAPE && campoLibre(corregida)) {
            establecerModoOperativo(ModoOperativo.LIBERADA);
        }
        if (modoOperativo == ModoOperativo.LIBERADA) return new OrdenMovimiento(Movimiento.DETENER, 0);
        return redMovimiento.decidir(telemetria, percepcion, modoOperativo);
    }

    /** Dirección de la última orden decidida. */
    public synchronized Movimiento obtenerMovimientoDecidido() {
        return ordenDecidida.movimiento();
    }

    /** Última orden decidida, con su duración en ms. */
    public synchronized OrdenMovimiento obtenerOrdenDecidida() {
        return ordenDecidida;
    }

    /** Etiquetas por sector del último barrido (solo con redes; null con reglas). */
    public synchronized EtiquetaSector[] obtenerUltimasEtiquetas() {
        return ultimasEtiquetas == null ? null : ultimasEtiquetas.clone();
    }

    /** CU-11. Idempotente: solo pasa de BUSQUEDA a ESCAPE; en ESCAPE o LIBERADA no hace nada. */
    public synchronized void activarModoEscape() {
        if (modoOperativo == ModoOperativo.BUSQUEDA) establecerModoOperativo(ModoOperativo.ESCAPE);
    }

    /** Cambia el modo e informa el cambio de estado en la consola del Servidor (no hay mensaje ESTADO). */
    public synchronized void establecerModoOperativo(ModoOperativo modo) {
        ModoOperativo anterior = modoOperativo;
        modoOperativo = Objects.requireNonNull(modo);
        if (anterior == modo) return;
        System.out.println("[Estado] " + anterior + " -> " + modo);
    }

    /** Entorno no reconocido: DETENER y, tras MAX_NO_RECONOCIDAS seguidas, RETROCEDER. */
    private Movimiento resolverNoReconocido() {
        lecturasNoReconocidas++;
        return lecturasNoReconocidas >= MAX_NO_RECONOCIDAS ? Movimiento.RETROCEDER : Movimiento.DETENER;
    }

    private Movimiento calcularMovimiento(EntornoClasificado entorno, int[] corregida) {
        switch (modoOperativo) {
            case ESCAPE:
                if (campoLibre(corregida)) {
                    establecerModoOperativo(ModoOperativo.LIBERADA);
                    return Movimiento.DETENER;
                }
                if (entorno == EntornoClasificado.SALIDA) return movimientoHaciaSector(sectorMasAbierto(corregida));
                if (entorno == EntornoClasificado.OBJETO) return girarHaciaLoAbierto(corregida); // Sin frenar.
                return Movimiento.AVANZAR; // LIBRE, y LOBITO que en escape se ignora.
            case BUSQUEDA:
                if (entorno == EntornoClasificado.LOBITO) return movimientoHaciaSector(sectorMasCercano(corregida));
                if (entorno == EntornoClasificado.OBJETO) {
                    double minimo = UMBRAL_LADO_MM * SECTORES_POR_LADO;
                    if (sumarDerecha(corregida) < minimo && sumarIzquierda(corregida) < minimo)
                        return Movimiento.RETROCEDER; // Encerrada.
                    return girarHaciaLoAbierto(corregida);
                }
                return Movimiento.AVANZAR; // LIBRE y SALIDA.
            default:
                return Movimiento.DETENER; // LIBERADA.
        }
    }

    private Movimiento girarHaciaLoAbierto(int[] corregida) {
        return sumarDerecha(corregida) > sumarIzquierda(corregida) ? Movimiento.GIRAR_DER : Movimiento.GIRAR_IZQ;
    }

    /** Ningún sector ve un obstáculo dentro de ALCANCE_LIBRE_MM (los desconocidos cuentan como lejos). */
    private boolean campoLibre(int[] corregida) {
        for (int s = 0; s < SECTORES; s++) if (distanciaSector(corregida, s) < ALCANCE_LIBRE_MM) return false;
        return true;
    }

    /** Suma de los sectores 0 a 4 (lado derecho). */
    private double sumarDerecha(int[] corregida) {
        double suma = 0;
        for (int s = 0; s < SECTORES_POR_LADO; s++) suma += distanciaSector(corregida, s);
        return suma;
    }

    /** Suma de los sectores 8 a 12 (lado izquierdo). */
    private double sumarIzquierda(int[] corregida) {
        double suma = 0;
        for (int s = SECTORES - SECTORES_POR_LADO; s < SECTORES; s++) suma += distanciaSector(corregida, s);
        return suma;
    }

    /** Sector con la menor distancia; ante empate, el de menor índice. */
    private int sectorMasCercano(int[] corregida) {
        int cercano = 0;
        for (int s = 1; s < SECTORES; s++)
            if (distanciaSector(corregida, s) < distanciaSector(corregida, cercano)) cercano = s;
        return cercano;
    }

    /** Sector con la mayor distancia; ante empate, el de menor índice. */
    private int sectorMasAbierto(int[] corregida) {
        int abierto = 0;
        for (int s = 1; s < SECTORES; s++)
            if (distanciaSector(corregida, s) > distanciaSector(corregida, abierto)) abierto = s;
        return abierto;
    }

    /**
     * Distancia del ROI 16x16 del sector (índice sector*3 + 2) ya corregida por la regla del -1.
     * Desconocido se toma como SIN_ECO_MM: no es el "más cercano" ni frena el campo libre.
     */
    private double distanciaSector(int[] corregida, int sector) {
        int d = corregida[sector * ROI_POR_SECTOR + ROI_16X16];
        return PreprocesadorTelemetria.esDesconocido(d) ? SIN_ECO_MM : d;
    }

    /** Frente (5 a 7) avanza; derecha (menor que 5) gira a la derecha; izquierda (mayor que 7), a la izquierda. */
    private Movimiento movimientoHaciaSector(int sector) {
        if (sector < PRIMER_SECTOR_FRENTE) return Movimiento.GIRAR_DER;
        if (sector > ULTIMO_SECTOR_FRENTE) return Movimiento.GIRAR_IZQ;
        return Movimiento.AVANZAR;
    }

}
