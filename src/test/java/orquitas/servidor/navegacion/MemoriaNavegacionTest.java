package orquitas.servidor.navegacion;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.*;
import static orquitas.servidor.navegacion.MemoriaNavegacion.*;

import org.junit.Test;

/** Memoria de la navegación: las 10 entradas que se suman a la representación intermedia. */
public class MemoriaNavegacionTest {
    private static final double[] INICIAL = {0, 0, 0, 0, 0, 0, 1, 0.5, 0.5, 1};

    /** Representación mínima: solo importa si hay meta, su ángulo (grados, + = izquierda) y el modo. */
    private static double[] representacion(boolean hayMeta, double grados, ModoOperativo modo) {
        double[] r = new double[RepresentacionIntermedia.ENTRADAS];
        r[RepresentacionIntermedia.HAY_META] = hayMeta ? 1 : 0;
        r[RepresentacionIntermedia.ANGULO_META] = hayMeta ? (grados + 90) / 180 : 0.5;
        r[RepresentacionIntermedia.MODO_BUSQUEDA] = modo == ModoOperativo.BUSQUEDA ? 1 : 0;
        r[RepresentacionIntermedia.MODO_ESCAPE] = modo == ModoOperativo.ESCAPE ? 1 : 0;
        return r;
    }

    private static double[] conMeta(double grados) {
        return representacion(true, grados, ModoOperativo.BUSQUEDA);
    }

    private static double[] sinMeta() {
        return representacion(false, 0, ModoOperativo.BUSQUEDA);
    }

    private static OrdenMovimiento girarIzq(double grados) {
        return new OrdenMovimiento(Movimiento.GIRAR_IZQ, (int) Math.round(grados / CalibracionRobot.GRADOS_POR_SEGUNDO * 1000));
    }

    private static OrdenMovimiento girarDer(double grados) {
        return new OrdenMovimiento(Movimiento.GIRAR_DER, (int) Math.round(grados / CalibracionRobot.GRADOS_POR_SEGUNDO * 1000));
    }

    @Test public void estadoInicial() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        assertEquals(MemoriaNavegacion.ENTRADAS, m.entradas().length);
        assertArrayEquals(INICIAL, m.entradas(), 1e-12);
        // Ver un barrido sin meta y sin haberse movido no cambia nada.
        m.observar(todo(500), sinMeta());
        assertArrayEquals(INICIAL, m.entradas(), 1e-12);
    }

    @Test public void trasGirarALaIzquierda90Grados() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        m.registrarOrden(new OrdenMovimiento(Movimiento.GIRAR_IZQ, 750)); // 750 ms a 120 °/s = 90°.
        double[] e = m.entradas();
        assertArrayEquals(new double[] {0, 0, 1, 0}, java.util.Arrays.copyOf(e, 4), 0);
        assertEquals(0.5, e[CUANTO_ANTERIOR], 1e-12);
        assertEquals(1, e[RUMBO_SENO], 1e-12);     // ψ = +90°.
        assertEquals(0.5, e[RUMBO_COSENO], 1e-12);
        assertEquals(1, e[CICLO_DESDE_META], 0);   // Nunca vio la meta: no cambia.
        assertEquals(0.5, e[ANGULO_META_ANTERIOR], 0);
    }

    @Test public void ordenAnteriorTieneUnaEntradaPorTipoYDetenerVaEnCero() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        Movimiento[] tipos = {Movimiento.AVANZAR, Movimiento.RETROCEDER, Movimiento.GIRAR_IZQ, Movimiento.GIRAR_DER};
        for (int i = 0; i < tipos.length; i++) {
            m.registrarOrden(new OrdenMovimiento(tipos[i], 300));
            double[] e = m.entradas();
            for (int k = 0; k < 4; k++) assertEquals(tipos[i] + " en " + k, k == i ? 1 : 0, e[k], 0);
            assertEquals(300 / 1500.0, e[CUANTO_ANTERIOR], 1e-12);
        }
        m.registrarOrden(new OrdenMovimiento(Movimiento.AVANZAR, 1500));
        assertEquals(1, m.entradas()[CUANTO_ANTERIOR], 0);
        m.registrarOrden(new OrdenMovimiento(Movimiento.DETENER, 0));
        for (int k = 0; k <= CUANTO_ANTERIOR; k++) assertEquals(0, m.entradas()[k], 0);
    }

    @Test public void elRumboAcumulaLosGirosYNoCambiaAlAvanzar() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        m.registrarOrden(girarIzq(60));
        m.registrarOrden(new OrdenMovimiento(Movimiento.AVANZAR, 800));
        m.registrarOrden(girarDer(15));
        m.registrarOrden(new OrdenMovimiento(Movimiento.RETROCEDER, 400));
        double psi = Math.toRadians(45);
        assertEquals((Math.sin(psi) + 1) / 2, m.entradas()[RUMBO_SENO], 1e-9);
        assertEquals((Math.cos(psi) + 1) / 2, m.entradas()[RUMBO_COSENO], 1e-9);
        // Media vuelta más un cuarto: ψ = 45 + 180 + 90 = 315° = -45°.
        m.registrarOrden(girarIzq(180));
        m.registrarOrden(girarIzq(90));
        assertEquals((Math.sin(Math.toRadians(-45)) + 1) / 2, m.entradas()[RUMBO_SENO], 1e-9);
        assertEquals((Math.cos(Math.toRadians(-45)) + 1) / 2, m.entradas()[RUMBO_COSENO], 1e-9);
    }

    @Test public void laMetaVistaAMas30VuelveAlFrenteDespuesDeGirar30ALaIzquierda() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        m.observar(todo(500), conMeta(30));
        assertEquals(0, m.entradas()[CICLO_DESDE_META], 0);
        assertEquals((30 + 180) / 360.0, m.entradas()[ANGULO_META_ANTERIOR], 1e-12);
        m.registrarOrden(girarIzq(30));
        assertEquals(0.5, m.entradas()[ANGULO_META_ANTERIOR], 1e-9); // Quedó de frente (0°).
        // Girar a la derecha la deja a la izquierda; avanzar no cambia el ángulo.
        m.registrarOrden(girarDer(45));
        assertEquals((45 + 180) / 360.0, m.entradas()[ANGULO_META_ANTERIOR], 1e-9);
        m.registrarOrden(new OrdenMovimiento(Movimiento.AVANZAR, 500));
        assertEquals((45 + 180) / 360.0, m.entradas()[ANGULO_META_ANTERIOR], 1e-9);
        // Si la vuelve a ver, vale el ángulo nuevo.
        m.observar(todo(700), conMeta(-15));
        assertEquals((-15 + 180) / 360.0, m.entradas()[ANGULO_META_ANTERIOR], 1e-12);
    }

    @Test public void elAnguloDeLaMetaSeAcotaAMediaVuelta() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        m.observar(todo(500), conMeta(90));
        m.registrarOrden(girarDer(60));
        m.registrarOrden(girarDer(60)); // 90 + 120 = 210° -> 180°.
        assertEquals(1, m.entradas()[ANGULO_META_ANTERIOR], 1e-12);
        MemoriaNavegacion otra = new MemoriaNavegacion();
        otra.observar(todo(500), conMeta(-90));
        otra.registrarOrden(girarIzq(150)); // -90 - 150 = -240° -> -180°.
        assertEquals(0, otra.entradas()[ANGULO_META_ANTERIOR], 1e-12);
    }

    @Test public void cicloDesdeMetaCreceDeAUnQuintoYSeSatura() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        assertEquals(1, m.entradas()[CICLO_DESDE_META], 0); // Nunca la vio.
        m.observar(todo(500), conMeta(0));
        assertEquals(0, m.entradas()[CICLO_DESDE_META], 0);
        double[] esperados = {0.2, 0.4, 0.6, 0.8, 1, 1, 1};
        for (double esperado : esperados) {
            m.observar(todo(500), sinMeta());
            assertEquals(esperado, m.entradas()[CICLO_DESDE_META], 1e-12);
        }
        m.observar(todo(500), conMeta(15)); // La vuelve a ver.
        assertEquals(0, m.entradas()[CICLO_DESDE_META], 0);
    }

    @Test public void elAtascoSubeConBarridosIgualesTrasMoverseYVuelveACeroCuandoCambia() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        OrdenMovimiento avanzar = new OrdenMovimiento(Movimiento.AVANZAR, 600);
        m.observar(todo(400), sinMeta());
        assertEquals(0, m.entradas()[ATASCO], 0); // No hay barrido anterior.
        double[] esperados = {1 / 3.0, 2 / 3.0, 1, 1};
        for (double esperado : esperados) {
            m.registrarOrden(avanzar);
            m.observar(con(todo(400), 3, 405), sinMeta()); // Casi igual (ruido chico): no se movió.
            assertEquals(esperado, m.entradas()[ATASCO], 1e-12);
        }
        m.registrarOrden(avanzar);
        m.observar(todo(900), sinMeta()); // El barrido cambió.
        assertEquals(0, m.entradas()[ATASCO], 0);
    }

    @Test public void noHayAtascoDespuesDeDetenerNiConPocosDatos() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        m.observar(todo(400), sinMeta());
        m.registrarOrden(new OrdenMovimiento(Movimiento.DETENER, 0));
        m.observar(todo(400), sinMeta()); // Igual, pero porque estaba quieta.
        assertEquals(0, m.entradas()[ATASCO], 0);
        MemoriaNavegacion sinDatos = new MemoriaNavegacion();
        sinDatos.observar(todo(-1), sinMeta());
        sinDatos.registrarOrden(new OrdenMovimiento(Movimiento.AVANZAR, 600));
        sinDatos.observar(todo(-1), sinMeta()); // Sin valores para comparar.
        assertEquals(0, sinDatos.entradas()[ATASCO], 0);
        // Usa la misma comparación que el DetectorAtasco.
        assertTrue(DetectorAtasco.iguales(PreprocesadorTelemetria.corregir(todo(400)), PreprocesadorTelemetria.corregir(con(todo(400), 3, 405))));
        assertFalse(DetectorAtasco.iguales(PreprocesadorTelemetria.corregir(todo(400)), PreprocesadorTelemetria.corregir(todo(900))));
    }

    @Test public void alPasarAEscapeSeOlvidaLaMetaPeroNoElRumbo() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        m.observar(todo(500), conMeta(30)); // Un Lobito a +30°.
        m.registrarOrden(girarIzq(30));
        m.observar(todo(600), sinMeta());
        assertEquals(0.2, m.entradas()[CICLO_DESDE_META], 1e-12);
        assertEquals(0.5, m.entradas()[ANGULO_META_ANTERIOR], 1e-9);
        // Primer barrido en ESCAPE: la meta ahora es la salida y todavía no se vio.
        m.observar(todo(700), representacion(false, 0, ModoOperativo.ESCAPE));
        double[] e = m.entradas();
        assertEquals(1, e[CICLO_DESDE_META], 0);
        assertEquals(0.5, e[ANGULO_META_ANTERIOR], 0);
        assertEquals((Math.sin(Math.toRadians(30)) + 1) / 2, e[RUMBO_SENO], 1e-9); // El rumbo sigue.
        assertEquals((Math.cos(Math.toRadians(30)) + 1) / 2, e[RUMBO_COSENO], 1e-9);
        assertEquals(1, e[ORDEN_GIRAR_IZQ], 0);                                    // Y la orden anterior también.
        // Ve la salida a -45° y la sigue recordando en los ciclos siguientes de ESCAPE.
        m.observar(todo(800), representacion(true, -45, ModoOperativo.ESCAPE));
        assertEquals(0, m.entradas()[CICLO_DESDE_META], 0);
        assertEquals((-45 + 180) / 360.0, m.entradas()[ANGULO_META_ANTERIOR], 1e-12);
        m.observar(todo(900), representacion(false, 0, ModoOperativo.ESCAPE));
        assertEquals(0.2, m.entradas()[CICLO_DESDE_META], 1e-12);
        assertEquals((-45 + 180) / 360.0, m.entradas()[ANGULO_META_ANTERIOR], 1e-12);
    }

    @Test public void siElPrimerBarridoEnEscapeYaVeLaSalidaLaRecuerda() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        m.observar(todo(500), conMeta(60));
        m.observar(todo(600), representacion(true, 15, ModoOperativo.ESCAPE));
        assertEquals(0, m.entradas()[CICLO_DESDE_META], 0);
        assertEquals((15 + 180) / 360.0, m.entradas()[ANGULO_META_ANTERIOR], 1e-12);
    }

    @Test public void reiniciarLaDejaComoAlPrincipio() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        m.observar(todo(400), conMeta(45));
        m.registrarOrden(girarIzq(75));
        m.observar(todo(400), representacion(true, -30, ModoOperativo.ESCAPE));
        m.registrarOrden(new OrdenMovimiento(Movimiento.AVANZAR, 900));
        m.observar(todo(400), representacion(false, 0, ModoOperativo.ESCAPE));
        assertFalse(java.util.Arrays.equals(INICIAL, m.entradas()));
        m.reiniciar();
        assertArrayEquals(INICIAL, m.entradas(), 1e-12);
        assertEquals(new MemoriaNavegacion().describir(), m.describir());
        // Después de reiniciar, el primer barrido no cuenta como atasco ni como cambio a ESCAPE.
        m.observar(todo(400), sinMeta());
        assertArrayEquals(INICIAL, m.entradas(), 1e-12);
    }

    @Test public void todasLasEntradasQuedanEntreCeroYUno() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        OrdenMovimiento[] ordenes = {girarIzq(170), girarIzq(170), girarDer(90), new OrdenMovimiento(Movimiento.AVANZAR, 1500),
            new OrdenMovimiento(Movimiento.RETROCEDER, 50), new OrdenMovimiento(Movimiento.DETENER, 0), girarDer(180), girarDer(180)};
        for (int i = 0; i < 40; i++) {
            m.observar(todo(300 + 10 * (i % 3)), i % 7 == 0 ? conMeta(90 - 30 * (i % 6)) : sinMeta());
            for (double v : m.entradas()) assertTrue(v >= 0 && v <= 1);
            m.registrarOrden(ordenes[i % ordenes.length]);
            for (double v : m.entradas()) assertTrue(v >= 0 && v <= 1);
        }
    }

    @Test public void describirDaUnTextoLegibleParaLaConsola() {
        MemoriaNavegacion m = new MemoriaNavegacion();
        assertEquals("sin orden anterior; meta no vista; rumbo +0°", m.describir());
        m.observar(todo(500), conMeta(55));
        m.registrarOrden(new OrdenMovimiento(Movimiento.GIRAR_IZQ, 333)); // 39,96°.
        assertEquals("última GIRAR_IZQ 333 ms; meta vista hace 0 ciclos; rumbo +40°", m.describir());
        m.observar(todo(500), sinMeta());
        assertEquals("última GIRAR_IZQ 333 ms; meta vista hace 1 ciclo, quedó a +15°; atasco 1; rumbo +40°", m.describir());
    }
}
