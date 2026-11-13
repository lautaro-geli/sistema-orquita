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
        assertEquals(78, e.length);
        for (int i = 0; i < 3; i++) {
            assertEquals(0, e[i], 0);
            assertEquals(1, e[39 + i], 0);
        }
        assertEquals((1300 - 800) / 1300.0, e[3], 1e-9);
        assertEquals(0, e[39 + 3], 0);
    }

    @Test public void cercaniaVaDeUnoPegadoACeroLejos() {
        double[] e = PreprocesadorTelemetria.entradas(sector(5, 0, 1300, 4000));
        assertEquals(1, e[15], 1e-9);
        assertEquals(0, e[16], 1e-9);
        assertEquals(0, e[17], 1e-9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rechazaLargoIncorrecto() {
        PreprocesadorTelemetria.corregir(new int[12]);
    }
}
