package orquitas.herramientas;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.todo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.RedPercepcion;

/** Notas de la prueba de detección y análisis por sector. */
public class AnalisisDeteccionTest {
    @Rule public TemporaryFolder carpeta = new TemporaryFolder();

    // ---------------------------------------------------------------- Notas

    @Test public void notasDelTraspasoVanAlSectorCorrecto() {
        NotaEscena.Tipo[] v = NotaEscena.leer("lobito -15 20").verdad();
        assertEquals(NotaEscena.Tipo.LOBITO, v[5]); // 15° a la derecha = sector 5.
        assertEquals(20, NotaEscena.leer("lobito -15 20").distanciaCm(5));
        assertEquals(NotaEscena.Tipo.OBSTACULO, NotaEscena.leer("obstaculo +30 15").verdad()[8]);
        v = NotaEscena.leer("salida -30 +15").verdad();
        for (int s = 0; s < 13; s++) assertEquals(s >= 4 && s <= 7 ? NotaEscena.Tipo.SALIDA : null, v[s]);
    }

    @Test public void variasCosasEnUnaNotaConTildesYCm() {
        NotaEscena n = NotaEscena.leer("Obstáculo 0 15cm, y canica -30 10");
        assertTrue(n.valida());
        assertEquals(2, n.objetos().size());
        assertEquals(NotaEscena.Tipo.OBSTACULO, n.verdad()[6]);
        assertEquals(NotaEscena.Tipo.LOBITO, n.verdad()[4]);
        // Vecinos: el 5 queda entre los dos; el lobito pisa al obstáculo.
        assertEquals(NotaEscena.Tipo.LOBITO, n.vecinos()[5]);
        assertEquals(NotaEscena.Tipo.OBSTACULO, n.vecinos()[7]);
        assertEquals(NotaEscena.Tipo.LOBITO, n.vecinos()[3]);
    }

    @Test public void avisaNotasIncompletasORaras() {
        assertFalse(NotaEscena.leer("obstaculo 30").valida());
        assertFalse(NotaEscena.leer("salida").valida());
        assertFalse(NotaEscena.leer("lolobito").valida());
        assertFalse(NotaEscena.leer("lobito 120 10").valida());
        assertFalse(NotaEscena.leer("lobito 0 300").valida()); // ¿Puso mm en vez de cm?
        assertTrue(NotaEscena.leer("").valida());
        assertFalse(NotaEscena.leer("").anotada());
    }

    @Test public void anguloRedondeaAlSectorMasCercano() {
        assertEquals(6, NotaEscena.sector(0));
        assertEquals(7, NotaEscena.sector(10));
        assertEquals(0, NotaEscena.sector(-90));
        assertEquals(12, NotaEscena.sector(90));
    }

    // ---------------------------------------------------------------- Rasgos

    @Test public void rasgosDeObjetoChicoYDeObstaculoAncho() {
        int[] c = todo(600);
        c[6 * 3] = 100; c[6 * 3 + 1] = 120; c[6 * 3 + 2] = 160; // Chico: 16x16 más lejos.
        assertEquals(60, AnalisisDeteccion.forma(c, 6));
        assertEquals(500, AnalisisDeteccion.aislado(c, 6));
        c[5 * 3] = 105; // Un vecino a la misma distancia: objeto ancho.
        assertEquals(5, AnalisisDeteccion.aislado(c, 6));
        c[6 * 3 + 2] = -1; // 16x16 sin eco: cuenta como objeto chico.
        assertEquals(AnalisisDeteccion.SIN_DATO, AnalisisDeteccion.forma(c, 6));
    }

    // ---------------------------------------------------------------- De punta a punta

    /** Red falsa: LOBITO donde el 4x4 está a menos de 200 mm; PARED en el resto. */
    static class RedFalsa extends RedPercepcion {
        @Override public synchronized double[] probabilidades(int[] t) {
            double[] p = new double[SALIDAS];
            for (int s = 0; s < 13; s++) {
                int e = t[s * 3] > 0 && t[s * 3] < 200 ? EtiquetaSector.LOBITO.ordinal() : EtiquetaSector.PARED.ordinal();
                p[s * ETIQUETAS + e] = 0.9;
            }
            return p;
        }
    }

    private static String fila(int barrido, String nota, int[] t) {
        StringBuilder sb = new StringBuilder("12:00:00," + barrido + "," + nota);
        for (int v : t) sb.append(',').append(v);
        for (int s = 0; s < 13; s++) sb.append(",PARED,0.5");
        return sb.append('\n').toString();
    }

    @Test public void separaLobitoDeObstaculoYElFiltroSacaElObstaculo() throws Exception {
        int[] lobito = todo(600);
        lobito[5 * 3] = 150; lobito[5 * 3 + 1] = 170; lobito[5 * 3 + 2] = 220; // Canica: chica y aislada.
        int[] obstaculo = todo(600);
        for (int s = 7; s <= 9; s++) { obstaculo[s * 3] = 150; obstaculo[s * 3 + 1] = 152; obstaculo[s * 3 + 2] = 151; }
        Path csv = carpeta.newFile("deteccion.csv").toPath();
        Files.writeString(csv, "hora,barrido,nota\n" + fila(1, "lobito -15 15", lobito) + fila(2, "obstaculo +30 15", obstaculo)
                + fila(3, "", lobito) + fila(4, "lolobito", lobito), StandardCharsets.UTF_8);

        List<String> avisos = new ArrayList<>();
        int[] barridos = new int[2];
        List<AnalisisDeteccion.Caso> casos = AnalisisDeteccion.leer(List.of(csv), new RedFalsa(), avisos, barridos);
        assertEquals(4, barridos[0]);
        assertEquals(2, barridos[1]);
        assertEquals(1, avisos.size()); // "lolobito".
        assertEquals(26, casos.size());
        AnalisisDeteccion.Caso c5 = casos.get(5);
        assertEquals(AnalisisDeteccion.LOBITO, c5.categoria());
        assertEquals(EtiquetaSector.LOBITO, c5.red());
        assertEquals(AnalisisDeteccion.OBSTACULO, casos.get(13 + 8).categoria());
        assertEquals(AnalisisDeteccion.BORDE, casos.get(13 + 7).categoria());

        // Sin filtro: 1 lobito y 3 falsos (el obstáculo y sus dos bordes). Con forma >= 30: queda el lobito, salen los 3.
        AnalisisDeteccion.Filtro f = AnalisisDeteccion.probar(casos, 400, 30, 0);
        assertEquals(1, f.lobitos());
        assertEquals(1, f.lobitosQuedan());
        assertEquals(3, f.falsos());
        assertEquals(0, f.falsosQuedan());
        assertEquals(100, AnalisisDeteccion.barrer(casos).get(0).puntaje(), 1e-9);

        String informe = AnalisisDeteccion.informe(casos, avisos, barridos, List.of(csv), "red");
        assertTrue(informe.contains("| lobito | 1 |"));
        assertTrue(informe.contains("lolobito"));
    }

    @Test public void sinNotasLoDiceClaro() {
        String informe = AnalisisDeteccion.informe(List.of(), List.of(), new int[] {5, 0}, List.of(), "red");
        assertTrue(informe.contains("No hay barridos con notas"));
    }
}
