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

    @Test
    public void avanceTrasGiroSeAcortaAloQueVioElSector() {
        int[] tel = telemetria(900, 900, 900, 900, 900, 900, 900, 900, 900, 900, 300, 900, 900);
        // Giro de 60° a la izquierda (sector 10) con 400 mm: el sector 10 ve algo a 300 mm.
        OrdenMovimiento a = ReflejoSeguridad.avanceTrasGiro(new MetaMovimiento(10, 400), tel, etiquetas("PPPPPPPPPPPPP"), ModoOperativo.BUSQUEDA);
        assertNotNull(a);
        assertEquals(Movimiento.AVANZAR, a.movimiento());
        assertTrue(CalibracionRobot.mmEn(a.milisegundos()) <= 300 - CalibracionRobot.SENSOR_AL_FRENTE_MM - ReflejoSeguridad.MARGEN_MM + 1);
    }

    @Test
    public void sinAvanceTrasGiroHaciaLaSalidaEnBusquedaNiSinMm() {
        int[] tel = telemetria(900, 900, 900, 900, 900, 900, 900, 900, 900, 900, 1200, 900, 900);
        assertNull(ReflejoSeguridad.avanceTrasGiro(new MetaMovimiento(10, 400), tel, etiquetas("PPPPPPPPPPSPP"), ModoOperativo.BUSQUEDA));
        assertNotNull(ReflejoSeguridad.avanceTrasGiro(new MetaMovimiento(10, 400), tel, etiquetas("PPPPPPPPPPSPP"), ModoOperativo.ESCAPE));
        assertNull(ReflejoSeguridad.avanceTrasGiro(new MetaMovimiento(10, 0), tel, etiquetas("PPPPPPPPPPPPP"), ModoOperativo.ESCAPE));
        assertNull(ReflejoSeguridad.avanceTrasGiro(new MetaMovimiento(MetaMovimiento.FRENTE, 400), tel, etiquetas("PPPPPPPPPPPPP"), ModoOperativo.ESCAPE));
    }

    @Test
    public void laMetaDeGirarYAvanzarLlevaLosMmDelAvance() {
        OrdenMovimiento giro = new OrdenMovimiento(Movimiento.GIRAR_IZQ, (int) Math.round(30 / CalibracionRobot.GRADOS_POR_SEGUNDO * 1000));
        MetaMovimiento m = MetaMovimiento.desdePlan(giro, new OrdenMovimiento(Movimiento.AVANZAR, CalibracionRobot.msParaAvanzar(250)));
        assertEquals(MetaMovimiento.FRENTE + 2, m.clase());
        assertEquals(250, m.mm(), 3);
        assertEquals(0, MetaMovimiento.desdePlan(giro, null).mm(), 0);
    }
}
