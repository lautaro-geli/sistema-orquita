package orquitas.servidor.navegacion;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

/** Candado de la salida: en BUSQUEDA no se sale de la bahía (descalifica). */
public class ReflejoSeguridadCandadoTest {
    private static final OrdenMovimiento AVANZAR = new OrdenMovimiento(Movimiento.AVANZAR, 1000);

    private static int[] telemetria(int... porSector) {
        int[] t = new int[PreprocesadorTelemetria.SECTORES * PreprocesadorTelemetria.ROIS];
        for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++)
            for (int r = 0; r < PreprocesadorTelemetria.ROIS; r++) t[s * PreprocesadorTelemetria.ROIS + r] = porSector[s];
        return t;
    }

    private static EtiquetaSector[] etiquetas(String codigo) {
        EtiquetaSector[] e = new EtiquetaSector[codigo.length()];
        for (int i = 0; i < e.length; i++)
            e[i] = switch (codigo.charAt(i)) {
                case 'P' -> EtiquetaSector.PARED;
                case 'O' -> EtiquetaSector.OBSTACULO;
                case 'S' -> EtiquetaSector.SALIDA;
                case 'K' -> EtiquetaSector.LOBITO;
                default -> EtiquetaSector.LIBRE;
            };
        return e;
    }

    @Test
    public void enBusquedaNoAvanzaHaciaLaSalida() {
        int[] tel = telemetria(900, 900, 900, 900, 900, 1200, 1200, 1200, 900, 900, 900, 900, 900);
        OrdenMovimiento o = ReflejoSeguridad.ajustar(AVANZAR, tel, etiquetas("PPPPPSSSPPPPP"), ModoOperativo.BUSQUEDA);
        assertNotEquals(Movimiento.AVANZAR, o.movimiento());
        if (o.movimiento() == Movimiento.GIRAR_IZQ || o.movimiento() == Movimiento.GIRAR_DER)
            assertTrue(o.milisegundos() <= Math.round(ReflejoSeguridad.GIRO_CANDADO_GRADOS / CalibracionRobot.GRADOS_POR_SEGUNDO * 1000));
    }

    @Test
    public void enEscapeSiAvanza() {
        int[] tel = telemetria(900, 900, 900, 900, 900, 1200, 1200, 1200, 900, 900, 900, 900, 900);
        assertEquals(AVANZAR, ReflejoSeguridad.ajustar(AVANZAR, tel, etiquetas("PPPPPSSSPPPPP"), ModoOperativo.ESCAPE));
    }

    @Test
    public void enElEstrechoMirandoAfueraRetrocede() {
        int[] tel = telemetria(120, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 120);
        OrdenMovimiento o = ReflejoSeguridad.candadoSalida(AVANZAR, tel, etiquetas("PLLLLLLLLLLLP"), ModoOperativo.BUSQUEDA);
        assertEquals(Movimiento.RETROCEDER, o.movimiento());
    }

    @Test
    public void unLobitoLejanoNoAbreElCandado() {
        // Mirando por el estrecho la red a veces ve Lobitos donde no hay nada (sin dato del sensor).
        int[] tel = telemetria(120, 400, 400, 400, -1, -1, -1, -1, -1, -1, -1, -1, -1);
        OrdenMovimiento o = ReflejoSeguridad.candadoSalida(AVANZAR, tel, etiquetas("OPPPPKKLLLLLL"), ModoOperativo.BUSQUEDA);
        assertEquals(Movimiento.RETROCEDER, o.movimiento());
    }

    @Test
    public void unLobitoCercaSiSeVaABuscar() {
        int[] tel = telemetria(900, 900, 900, 900, 900, 1200, 300, 1200, 900, 900, 900, 900, 900);
        assertEquals(AVANZAR, ReflejoSeguridad.candadoSalida(AVANZAR, tel, etiquetas("PPPPPSKSPPPPP"), ModoOperativo.BUSQUEDA));
    }

    @Test
    public void sinSalidaNoCambiaNada() {
        int[] tel = telemetria(900, 900, 900, 900, 900, 900, 900, 900, 900, 900, 900, 900, 900);
        EtiquetaSector[] e = new EtiquetaSector[13];
        Arrays.fill(e, EtiquetaSector.PARED);
        assertEquals(AVANZAR, ReflejoSeguridad.candadoSalida(AVANZAR, tel, e, ModoOperativo.BUSQUEDA));
    }
}
