package orquitas.servidor.navegacion;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.*;
import static orquitas.servidor.navegacion.EntornoClasificado.*;
import static orquitas.servidor.navegacion.Movimiento.*;

import org.junit.Test;
import orquitas.servidor.apoyo.RedFija;

/** CU-07 y CU-11: tabla de casos por modo con una red de prueba que devuelve el entorno deseado. */
public class NavegacionTest {
    private static final int CERCA = 300, LEJOS = 3000;

    private static Movimiento decidir(ModoOperativo modo, EntornoClasificado entorno, int[] t) {
        Navegacion n = new Navegacion(new RedFija(entorno));
        if (modo != ModoOperativo.BUSQUEDA) n.establecerModoOperativo(modo);
        n.determinarMovimiento(t);
        return n.obtenerMovimientoDecidido();
    }

    /** Un sector cercano; el resto, cerca de una pared (para que el campo no esté libre). */
    private static int[] cercanoEn(int sector) {
        return con(todo(1400), sector, CERCA);
    }

    @Test public void busquedaPersigueAlLobitoHaciaSuSector() {
        for (int s = 0; s < 13; s++) {
            Movimiento esperado = s < 5 ? GIRAR_DER : s > 7 ? GIRAR_IZQ : AVANZAR;
            assertEquals("sector " + s, esperado, decidir(ModoOperativo.BUSQUEDA, LOBITO, cercanoEn(s)));
        }
    }

    @Test public void busquedaAnteObjetoGiraHaciaLoAbiertoORetrocedeEncerrada() {
        int[] derechaAbierta = todo(CERCA);
        for (int s = 0; s < 5; s++) con(derechaAbierta, s, LEJOS);
        int[] izquierdaAbierta = todo(CERCA);
        for (int s = 8; s < 13; s++) con(izquierdaAbierta, s, LEJOS);
        assertEquals(GIRAR_DER, decidir(ModoOperativo.BUSQUEDA, OBJETO, derechaAbierta));
        assertEquals(GIRAR_IZQ, decidir(ModoOperativo.BUSQUEDA, OBJETO, izquierdaAbierta));
        assertEquals(GIRAR_IZQ, decidir(ModoOperativo.BUSQUEDA, OBJETO, todo(LEJOS))); // Empate: izquierda.
        assertEquals(RETROCEDER, decidir(ModoOperativo.BUSQUEDA, OBJETO, todo(CERCA))); // Encerrada.
    }

    @Test public void busquedaLibreYSalidaAvanzan() {
        assertEquals(AVANZAR, decidir(ModoOperativo.BUSQUEDA, LIBRE, todo(LEJOS)));
        assertEquals(AVANZAR, decidir(ModoOperativo.BUSQUEDA, SALIDA, cercanoEn(0)));
    }

    @Test public void escapeIgnoraAlLobitoBuscaLaSalidaYEsquivaSinFrenar() {
        assertEquals(AVANZAR, decidir(ModoOperativo.ESCAPE, LOBITO, cercanoEn(1)));
        assertEquals(AVANZAR, decidir(ModoOperativo.ESCAPE, LIBRE, cercanoEn(1)));
        int[] salidaIzquierda = con(todo(CERCA), 10, LEJOS);
        assertEquals(GIRAR_IZQ, decidir(ModoOperativo.ESCAPE, SALIDA, salidaIzquierda));
        assertEquals(GIRAR_DER, decidir(ModoOperativo.ESCAPE, SALIDA, con(todo(CERCA), 2, LEJOS)));
        assertEquals(AVANZAR, decidir(ModoOperativo.ESCAPE, SALIDA, con(todo(CERCA), 6, LEJOS)));
        assertEquals(GIRAR_IZQ, decidir(ModoOperativo.ESCAPE, OBJETO, todo(CERCA))); // Encerrada: no frena.
    }

    @Test public void escapeConCampoLibrePasaALiberadaYDetiene() {
        Navegacion n = new Navegacion(new RedFija(LIBRE));
        n.establecerModoOperativo(ModoOperativo.ESCAPE);
        n.determinarMovimiento(todo(LEJOS));
        assertEquals(DETENER, n.obtenerMovimientoDecidido());
        assertEquals(ModoOperativo.LIBERADA, leer(n, "modoOperativo"));
        n.determinarMovimiento(cercanoEn(6)); // LIBERADA: siempre DETENER.
        assertEquals(DETENER, n.obtenerMovimientoDecidido());
    }

    @Test public void liberadaSiempreDetiene() {
        for (EntornoClasificado e : EntornoClasificado.values())
            assertEquals(e.name(), DETENER, decidir(ModoOperativo.LIBERADA, e, cercanoEn(3)));
    }

    @Test public void noReconocidoDetieneYLuegoRetrocedeYSeReiniciaAlReconocer() {
        RedFija red = new RedFija((EntornoClasificado) null);
        Navegacion n = new Navegacion(red);
        Movimiento[] esperados = {DETENER, DETENER, RETROCEDER, RETROCEDER};
        for (Movimiento esperado : esperados) {
            n.determinarMovimiento(todo(LEJOS));
            assertEquals(esperado, n.obtenerMovimientoDecidido());
        }
        red.cambiar(LIBRE);
        n.determinarMovimiento(todo(LEJOS));
        assertEquals(AVANZAR, n.obtenerMovimientoDecidido());
        red.cambiar(null);
        n.determinarMovimiento(todo(LEJOS));
        assertEquals(DETENER, n.obtenerMovimientoDecidido()); // El contador volvió a 0.
    }

    @Test public void activarModoEscapeEsIdempotenteYNoSaleDeLiberada() {
        Navegacion n = new Navegacion(new RedFija(LIBRE));
        assertEquals(DETENER, n.obtenerMovimientoDecidido()); // Antes de la primera telemetría.
        n.activarModoEscape();
        n.activarModoEscape();
        assertEquals(ModoOperativo.ESCAPE, leer(n, "modoOperativo"));
        n.establecerModoOperativo(ModoOperativo.LIBERADA);
        n.activarModoEscape();
        assertEquals(ModoOperativo.LIBERADA, leer(n, "modoOperativo"));
    }
}
