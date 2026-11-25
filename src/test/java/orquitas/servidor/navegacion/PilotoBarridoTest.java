package orquitas.servidor.navegacion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

/** Modo barrido (zigzag) sin hardware: telemetrías armadas a mano. */
public class PilotoBarridoTest {

    /** Todo a d mm (los 3 ROI) y todo PARED. */
    private static int[] todo(int d) {
        int[] t = new int[PreprocesadorTelemetria.VALORES];
        Arrays.fill(t, d);
        return t;
    }

    private static EtiquetaSector[] etiquetas(EtiquetaSector e) {
        EtiquetaSector[] r = new EtiquetaSector[PreprocesadorTelemetria.SECTORES];
        Arrays.fill(r, e);
        return r;
    }

    private static void sector(int[] t, int s, int d4, int d8, int d16) {
        t[s * 3] = d4; t[s * 3 + 1] = d8; t[s * 3 + 2] = d16;
    }

    @Test
    public void conLugarAdelanteAvanzaElCarrilDeAPasos() {
        PilotoBarrido p = new PilotoBarrido();
        OrdenMovimiento o = p.decidir(todo(800), etiquetas(EtiquetaSector.PARED), ModoOperativo.BUSQUEDA, 0);
        assertEquals(Movimiento.AVANZAR, o.movimiento());
        assertTrue(CalibracionRobot.mmEn(o.milisegundos()) <= PilotoBarrido.PASO_MM + 1);
        assertEquals(PilotoBarrido.Fase.CARRIL, p.fase());
    }

    @Test
    public void alFinalDelCarrilGiraNoventaGradosHaciaElLadoMasAbiertoYDespuesVuelve() {
        PilotoBarrido p = new PilotoBarrido();
        int[] t = todo(600);
        for (int s = 5; s <= 7; s++) sector(t, s, 180, 180, 180); // Pared cerca adelante.
        for (int s = 0; s <= 2; s++) sector(t, s, 300, 300, 300);  // Derecha más cerrada que la izquierda.
        OrdenMovimiento o = p.decidir(t, etiquetas(EtiquetaSector.PARED), ModoOperativo.BUSQUEDA, 0);
        assertEquals(Movimiento.GIRAR_IZQ, o.movimiento());
        assertEquals(90, o.milisegundos() / 1000.0 * CalibracionRobot.GRADOS_POR_SEGUNDO, 3);
        assertEquals(PilotoBarrido.Fase.GIRO_A_CAMBIO, p.fase());
        p.registrarOrden(o);
        // Ya mirando al costado: avanza el ancho de un carril.
        OrdenMovimiento cambio = p.decidir(todo(800), // (cada barrido distinto: si no, el DetectorAtasco cree que no se movió)
                 etiquetas(EtiquetaSector.PARED), ModoOperativo.BUSQUEDA, 0);
        assertEquals(Movimiento.AVANZAR, cambio.movimiento());
        assertEquals(PilotoBarrido.CARRIL_MM, CalibracionRobot.mmEn(cambio.milisegundos()), 2);
        p.registrarOrden(cambio);
        // Y otra vez a la izquierda: el carril nuevo va en sentido contrario.
        OrdenMovimiento vuelta = p.decidir(todo(700), etiquetas(EtiquetaSector.PARED), ModoOperativo.BUSQUEDA, 0);
        assertEquals(Movimiento.GIRAR_IZQ, vuelta.movimiento());
        p.registrarOrden(vuelta);
        p.decidir(todo(900), etiquetas(EtiquetaSector.PARED), ModoOperativo.BUSQUEDA, 0);
        assertEquals(PilotoBarrido.Fase.CARRIL, p.fase());
        assertEquals(180, Math.toDegrees(p.seguimiento().rumbo()), 3);
    }

    @Test
    public void vaALaCanicaCercaSiTieneFormaDeCanica() {
        PilotoBarrido p = new PilotoBarrido();
        int[] t = todo(600);
        sector(t, 6, 110, 125, 145); // Canica a 11 cm al frente (como en la toma real).
        EtiquetaSector[] e = etiquetas(EtiquetaSector.PARED);
        e[6] = EtiquetaSector.LOBITO;
        OrdenMovimiento o = p.decidir(t, e, ModoOperativo.BUSQUEDA, 0);
        assertEquals(Movimiento.AVANZAR, o.movimiento());
        assertEquals(110 + PilotoBarrido.PASAR_CANICA_MM, CalibracionRobot.mmEn(o.milisegundos()), 5);
    }

    @Test
    public void noVaHaciaUnLobitoQueEsLaCaraDeUnObstaculo() {
        int[] t = todo(600);
        for (int s = 5; s <= 7; s++) sector(t, s, 120, 121, 119); // Tres ROI iguales y vecinos iguales: un obstáculo.
        EtiquetaSector[] e = etiquetas(EtiquetaSector.PARED);
        e[6] = EtiquetaSector.LOBITO;
        assertEquals(-1, PilotoBarrido.canicaCerca(PreprocesadorTelemetria.corregir(t), e));
        OrdenMovimiento o = new PilotoBarrido().decidir(t, e, ModoOperativo.BUSQUEDA, 0);
        assertNotEquals(Movimiento.AVANZAR, o.movimiento());
    }

    @Test
    public void conElFrenteCiegoEsperaYDespuesRetrocede() {
        PilotoBarrido p = new PilotoBarrido();
        int[] t = todo(600);
        for (int s = 4; s <= 8; s++) sector(t, s, -1, -1, -1);
        assertEquals(Movimiento.DETENER, p.decidir(t, etiquetas(EtiquetaSector.PARED), ModoOperativo.BUSQUEDA, 0).movimiento());
        assertEquals(Movimiento.RETROCEDER, p.decidir(t, etiquetas(EtiquetaSector.PARED), ModoOperativo.BUSQUEDA, 0).movimiento());
    }

    @Test
    public void enBusquedaNoAvanzaHaciaLaSalida() {
        int[] t = todo(600);
        EtiquetaSector[] e = etiquetas(EtiquetaSector.PARED);
        for (int s = 5; s <= 7; s++) { sector(t, s, 900, 900, 900); e[s] = EtiquetaSector.SALIDA; }
        OrdenMovimiento o = new PilotoBarrido().decidir(t, e, ModoOperativo.BUSQUEDA, 0);
        assertNotEquals(Movimiento.AVANZAR, o.movimiento());
    }

    @Test
    public void liberadaSeQuedaQuietaYEscapeDecideAlgo() {
        assertEquals(Movimiento.DETENER, new PilotoBarrido().decidir(todo(600), etiquetas(EtiquetaSector.PARED), ModoOperativo.LIBERADA, 3).movimiento());
        int[] t = todo(600);
        EtiquetaSector[] e = etiquetas(EtiquetaSector.PARED);
        for (int s = 5; s <= 7; s++) { sector(t, s, 900, 900, 900); e[s] = EtiquetaSector.SALIDA; }
        assertEquals(Movimiento.AVANZAR, new PilotoBarrido().decidir(t, e, ModoOperativo.ESCAPE, 3).movimiento());
    }
}
