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

    // Corrección del piso. Valores de la prueba de detección real del 22/11 (robot quieto mirando la salida).

    @Test public void pisoConEl4x4SinEcoQuedaSinEco() {
        assertTrue(PreprocesadorTelemetria.CORRECCION_PISO);
        // Frente a la salida: el 4x4 y el 8x8 sin eco, el 16x16 ve el piso a 5 cm.
        assertArrayEquals(new int[] {D, D, D}, leer(PreprocesadorTelemetria.corregir(sector(6, -1, -1, 49)), 6));
        // También el 8x8 ve el piso.
        assertArrayEquals(new int[] {D, D, D}, leer(PreprocesadorTelemetria.corregir(sector(5, -1, 111, 115)), 5));
    }

    @Test public void pisoConEl4x4ConDatoTomaEl4x4() {
        assertArrayEquals(new int[] {232, 232, 232}, leer(PreprocesadorTelemetria.corregir(sector(5, 232, 190, 170)), 5));
        // 4x4 a 11 cm y 16x16 a 8,6 cm: el 16x16 toma el 4x4; el 8x8 mide más que el 4x4 y queda igual.
        assertArrayEquals(new int[] {110, 136, 110}, leer(PreprocesadorTelemetria.corregir(sector(6, 110, 136, 86)), 6));
    }

    @Test public void pisoNoTocaParedesOblicuasLejos() {
        // ROI grande más cerca que el 4x4 pero a más de PISO_ALCANCE_MM: pared oblicua, no piso.
        assertArrayEquals(new int[] {665, 672, 590}, leer(PreprocesadorTelemetria.corregir(sector(7, 665, 672, 590)), 7));
        assertArrayEquals(new int[] {289, 219, 259}, leer(PreprocesadorTelemetria.corregir(sector(3, 289, 219, 259)), 3));
        // 4x4 sin eco y ROI grande a 25 cm: sigue la regla del -1.
        assertArrayEquals(new int[] {250, 250, 250}, leer(PreprocesadorTelemetria.corregir(sector(4, -1, 250, -1)), 4));
    }

    @Test public void pisoNoTocaCanicasNiParedes() {
        // Sector que la red marca LOBITO en el barrido 47 del 22/11 (nota "lolobito"; sin anotar si era
        // canica u obstáculo): diferencias dentro del margen, la corrección no lo toca.
        assertArrayEquals(new int[] {98, 95, 93}, leer(PreprocesadorTelemetria.corregir(sector(2, 98, 95, 93)), 2));
        // Canica cerca como la arma el simulador: 4x4 < 8x8 < 16x16.
        assertArrayEquals(new int[] {60, 70, 90}, leer(PreprocesadorTelemetria.corregir(sector(6, 60, 70, 90)), 6));
        // Pared a 8 cm: diferencias dentro del margen.
        assertArrayEquals(new int[] {86, 81, 77}, leer(PreprocesadorTelemetria.corregir(sector(0, 86, 81, 77)), 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rechazaLargoIncorrecto() {
        PreprocesadorTelemetria.corregir(new int[12]);
    }
}
