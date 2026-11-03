package orquitas.servidor.navegacion;

import java.util.Objects;

/**
 * CU-07 y CU-11. Decide el movimiento de la Orquita a partir de la clasificación de la red y
 * del modo operativo. La telemetría son 13 sectores (0° a 180°, cada 15°) x 3 ROI (4x4, 8x8,
 * 16x16) en orden de sector; las decisiones usan el ROI 16x16 en mm. Convención [A CONFIRMAR]
 * con el robot: el sector 0 es la derecha, el 6 el frente y el 12 la izquierda.
 * La red clasifica en todos los modos; el modo cambia la meta: búsqueda persigue al Lobito y
 * esquiva obstáculos, escape ignora al Lobito y busca la salida, liberada se queda quieta.
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

    private final RedNeuronal redNeuronal;
    private volatile ModoOperativo modoOperativo = ModoOperativo.BUSQUEDA;
    private Movimiento movimientoDecidido = Movimiento.DETENER;
    private int lecturasNoReconocidas;
    /** Último de los 6 estados de la consigna informado por consola. */
    private String estadoInformado = "BUSQUEDA";

    public Navegacion(RedNeuronal redNeuronal) {
        this.redNeuronal = Objects.requireNonNull(redNeuronal);
    }

    /** CU-07. Clasifica el entorno y deja el resultado en movimientoDecidido (CU-08 lo envía). */
    public synchronized void determinarMovimiento(int[] telemetria) {
        EntornoClasificado entorno = redNeuronal.clasificarEntorno(telemetria);
        if (entorno == null) {
            movimientoDecidido = resolverNoReconocido();
            return;
        }
        lecturasNoReconocidas = 0;
        movimientoDecidido = calcularMovimiento(entorno, telemetria);
        if (modoOperativo == ModoOperativo.BUSQUEDA) {
            if (entorno == EntornoClasificado.LOBITO) informarEstado("APROXIMACION", "la red ve un Lobito");
            else informarEstado("BUSQUEDA", "ya no ve un Lobito");
        }
    }

    public synchronized Movimiento obtenerMovimientoDecidido() {
        return movimientoDecidido;
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
        switch (modo) {
            case ESCAPE: informarEstado("ESCAPE", "se alcanzó el umbral de capturas"); break;
            case LIBERADA: informarEstado("FINALIZACION", "campo libre: la Orquita salió de la bahía"); break;
            default: informarEstado("BUSQUEDA", "modo búsqueda");
        }
    }

    /** Entorno no reconocido: DETENER y, tras MAX_NO_RECONOCIDAS seguidas, RETROCEDER. */
    private Movimiento resolverNoReconocido() {
        lecturasNoReconocidas++;
        return lecturasNoReconocidas >= MAX_NO_RECONOCIDAS ? Movimiento.RETROCEDER : Movimiento.DETENER;
    }

    private Movimiento calcularMovimiento(EntornoClasificado entorno, int[] telemetria) {
        switch (modoOperativo) {
            case ESCAPE:
                if (campoLibre(telemetria)) {
                    establecerModoOperativo(ModoOperativo.LIBERADA);
                    return Movimiento.DETENER;
                }
                if (entorno == EntornoClasificado.SALIDA) return movimientoHaciaSector(sectorMasAbierto(telemetria));
                if (entorno == EntornoClasificado.OBJETO) return girarHaciaLoAbierto(telemetria); // Sin frenar.
                return Movimiento.AVANZAR; // LIBRE, y LOBITO que en escape se ignora.
            case BUSQUEDA:
                if (entorno == EntornoClasificado.LOBITO) return movimientoHaciaSector(sectorMasCercano(telemetria));
                if (entorno == EntornoClasificado.OBJETO) {
                    double minimo = UMBRAL_LADO_MM * SECTORES_POR_LADO;
                    if (sumarDerecha(telemetria) < minimo && sumarIzquierda(telemetria) < minimo)
                        return Movimiento.RETROCEDER; // Encerrada.
                    return girarHaciaLoAbierto(telemetria);
                }
                return Movimiento.AVANZAR; // LIBRE y SALIDA.
            default:
                return Movimiento.DETENER; // LIBERADA.
        }
    }

    private Movimiento girarHaciaLoAbierto(int[] telemetria) {
        return sumarDerecha(telemetria) > sumarIzquierda(telemetria) ? Movimiento.GIRAR_DER : Movimiento.GIRAR_IZQ;
    }

    /** Ningún sector ve un obstáculo dentro de ALCANCE_LIBRE_MM. */
    private boolean campoLibre(int[] telemetria) {
        for (int s = 0; s < SECTORES; s++) if (distanciaSector(telemetria, s) < ALCANCE_LIBRE_MM) return false;
        return true;
    }

    /** Suma de los sectores 0 a 4 (lado derecho). */
    private double sumarDerecha(int[] telemetria) {
        double suma = 0;
        for (int s = 0; s < SECTORES_POR_LADO; s++) suma += distanciaSector(telemetria, s);
        return suma;
    }

    /** Suma de los sectores 8 a 12 (lado izquierdo). */
    private double sumarIzquierda(int[] telemetria) {
        double suma = 0;
        for (int s = SECTORES - SECTORES_POR_LADO; s < SECTORES; s++) suma += distanciaSector(telemetria, s);
        return suma;
    }

    /** Sector con la menor distancia; ante empate, el de menor índice. */
    private int sectorMasCercano(int[] telemetria) {
        int cercano = 0;
        for (int s = 1; s < SECTORES; s++)
            if (distanciaSector(telemetria, s) < distanciaSector(telemetria, cercano)) cercano = s;
        return cercano;
    }

    /** Sector con la mayor distancia; ante empate, el de menor índice. */
    private int sectorMasAbierto(int[] telemetria) {
        int abierto = 0;
        for (int s = 1; s < SECTORES; s++)
            if (distanciaSector(telemetria, s) > distanciaSector(telemetria, abierto)) abierto = s;
        return abierto;
    }

    /** Distancia del ROI 16x16 del sector: índice sector*3 + 2. */
    private double distanciaSector(int[] telemetria, int sector) {
        return telemetria[sector * ROI_POR_SECTOR + ROI_16X16];
    }

    /** Frente (5 a 7) avanza; derecha (menor que 5) gira a la derecha; izquierda (mayor que 7), a la izquierda. */
    private Movimiento movimientoHaciaSector(int sector) {
        if (sector < PRIMER_SECTOR_FRENTE) return Movimiento.GIRAR_DER;
        if (sector > ULTIMO_SECTOR_FRENTE) return Movimiento.GIRAR_IZQ;
        return Movimiento.AVANZAR;
    }

    /** Imprime "[Estado] ANTERIOR -> NUEVO (motivo)" solo cuando el estado cambia. */
    private void informarEstado(String nuevo, String motivo) {
        if (nuevo.equals(estadoInformado)) return;
        System.out.println("[Estado] " + estadoInformado + " -> " + nuevo + " (" + motivo + ")");
        estadoInformado = nuevo;
    }
}
