package orquitas.servidor.navegacion;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.*;

import org.junit.Test;

/** Regla del -1 por sector y armado de las entradas de las redes. */
public class PreprocesadorTelemetriaTest {
    private static final int D = PreprocesadorTelemetria.DESCONOCIDO;

    private static int[] sector(int s, int v4, int v8, int v16) {
        int[] t = todo(800);
        t[s * 3] = v4; t[s * 3 + 1] = v8; t[s * 3 + 2] = v16;
        return t;
    }

    private static int[] leer(int[] corregida, int s) {
        return new int[] {corregida[s * 3], corregida[s * 3 + 1], corregida[s * 3 + 2]};
    }

    @Test public void los4x4y16x16SinEcoTomanEl8x8() {
        assertArrayEquals(new int[] {250, 250, 250}, leer(PreprocesadorTelemetria.corregir(sector(4, -1, 250, -1)), 4));
        assertArrayEquals(new int[] {250, 250, 300}, leer(PreprocesadorTelemetria.corregir(sector(4, -1, 250, 300)), 4));
    }

    @Test public void el8x8SinEcoQuedaDesconocidoYLosOtrosConservanSuValor() {
        assertArrayEquals(new int[] {200, D, 300}, leer(PreprocesadorTelemetria.corregir(sector(7, 200, -1, 300)), 7));
        assertArrayEquals(new int[] {D, D, 300}, leer(PreprocesadorTelemetria.corregir(sector(7, -1, -1, 300)), 7));
    }

    @Test public void lasTresSinEcoQuedanDesconocidas() {
        assertArrayEquals(new int[] {D, D, D}, leer(PreprocesadorTelemetria.corregir(sector(0, -1, -1, -1)), 0));
    }

    @Test public void noModificaLaTelemetriaOriginalNiLosOtrosSectores() {
        int[] t = sector(2, -1, 400, -1);
        int[] c = PreprocesadorTelemetria.corregir(t);
        assertEquals(-1, t[6]);
        assertArrayEquals(new int[] {800, 800, 800}, leer(c, 3));
    }

    @Test public void entradasMarcanDesconocidoAparteYCercaniaCero() {
        double[] e = PreprocesadorTelemetria.entradas(sector(0, -1, -1, -1));
        assertEquals(117, e.length);
        for (int i = 0; i < 3; i++) {
            assertEquals(0, e[i], 0);
            assertEquals(1, e[39 + i], 0);
        }
        assertEquals((1300 - 800) / 1300.0, e[3], 1e-9);
        assertEquals(0, e[39 + 3], 0);
    }

    @Test public void cercaniaVaDeUnoPegadoACeroLejos() {
        double[] e = PreprocesadorTelemetria.entradas(sector(5, 40, 1300, 4000));
        assertEquals((1300 - 40) / 1300.0, e[15], 1e-9);
        assertEquals(0, e[16], 1e-9);
        assertEquals(0, e[17], 1e-9);
    }

    @Test public void lecturasDeMenosDe40mmSonDesconocidas() {
        // El sensor real devolvió 0–46 mm "válidos" sin nada a esa distancia: se toman como sin dato.
        // 4x4 en 0 y 16x16 en 20: sin dato, toman el 8x8 (regla del -1).
        int[] c = PreprocesadorTelemetria.corregir(sector(5, 0, 500, 20));
        assertEquals(500, c[5 * 3]);
        assertEquals(500, c[5 * 3 + 2]);
        // 8x8 en 39: sin dato, y los otros quedan como estaban.
        c = PreprocesadorTelemetria.corregir(sector(5, 300, 39, 600));
        assertEquals(PreprocesadorTelemetria.DESCONOCIDO, c[5 * 3 + 1]);
        assertEquals(300, c[5 * 3]);
    }

    @Test public void rasgosDeCanicaCercaYDeParedPlana() {
        int[] t = todo(600);
        t[6 * 3] = 60; t[6 * 3 + 1] = 70; t[6 * 3 + 2] = 90; // Canica al frente: 4x4 < 8x8 < 16x16.
        double[] e = PreprocesadorTelemetria.entradas(t);
        int base = 78 + 6 * 3;
        assertTrue(e[base] > 0.5);      // 4x4 más cerca que 16x16.
        assertTrue(e[base + 1] > 0.5);  // 4x4 más cerca que 8x8.
        assertTrue(e[base + 2] > 0.5);  // Más cerca que los vecinos.
        double[] plana = PreprocesadorTelemetria.entradas(todo(600));
        assertEquals(0.5, plana[base], 1e-9);
        assertEquals(0.5, plana[base + 2], 1e-9);
    }

    @Test public void ventanaRellenaFueraDelAbanicoYMarcaLaPosicion() {
        double[] e = PreprocesadorTelemetria.entradas(todo(650));
        double[] v0 = PreprocesadorTelemetria.entradasVentana(e, 0);
        assertEquals(PreprocesadorTelemetria.ENTRADAS_VENTANA, v0.length);
        assertEquals(0, v0[0], 0);        // Sector -2: no existe, cercanía 0...
        assertEquals(1, v0[3], 0);        // ...y desconocido.
        assertEquals(0.5, v0[18], 1e-9);  // Sector 0 (centro de la ventana): cercanía de 650 mm.
        assertEquals(0, v0[v0.length - 1], 0);
        assertEquals(1, PreprocesadorTelemetria.entradasVentana(e, 12)[PreprocesadorTelemetria.ENTRADAS_VENTANA - 1], 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rechazaLargoIncorrecto() {
        PreprocesadorTelemetria.corregir(new int[12]);
    }
}
