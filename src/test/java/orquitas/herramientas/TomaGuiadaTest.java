package orquitas.herramientas;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.todo;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Toma guiada sin hardware: teclado y barridos simulados. */
public class TomaGuiadaTest {
    @Rule public TemporaryFolder carpeta = new TemporaryFolder();

    private final ByteArrayOutputStream consola = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(consola, true, StandardCharsets.UTF_8);

    private static final List<TomaGuiada.Escena> DOS = List.of(
            new TomaGuiada.Escena("lobito 0 10", "Pone una CANICA adelante a 10 cm."),
            new TomaGuiada.Escena("obstaculo +30 15", "Pone un OBSTACULO a la izquierda a 15 cm."));

    /** Barrido con una canica al frente a 10 cm (la RedFalsa la marca LOBITO) y paredes a 60 cm. */
    private static int[] barrido() {
        int[] t = todo(600);
        t[6 * 3] = 100; t[6 * 3 + 1] = 110; t[6 * 3 + 2] = 130;
        return t;
    }

    private List<String> filas(Path csv) throws Exception {
        return Files.exists(csv) ? Files.readAllLines(csv, StandardCharsets.UTF_8) : new ArrayList<>();
    }

    private String texto() {
        return consola.toString(StandardCharsets.UTF_8);
    }

    @Test public void enterDescartaElBarridoEnCursoYGuardaTres() throws Exception {
        Path csv = carpeta.getRoot().toPath().resolve("deteccion.csv");
        TomaGuiada toma = new TomaGuiada(DOS, 1, new AnalisisDeteccionTest.RedFalsa(), csv, out);
        assertTrue(texto().contains("Escena 1 de 2"));
        assertEquals(TomaGuiada.Estado.INSTRUCCION, toma.estado());

        // Mientras espera Enter no guarda nada.
        toma.telemetria(barrido());
        toma.telemetria(barrido());
        assertFalse(Files.exists(csv));

        toma.tecla("");
        assertEquals(TomaGuiada.Estado.DESCARTANDO, toma.estado());
        toma.telemetria(barrido()); // El barrido en curso: se descarta.
        assertFalse(Files.exists(csv));
        assertTrue(texto().contains("Barrido en curso descartado"));

        toma.tecla("x"); // Teclas mientras guarda: no cambian nada.
        for (int i = 1; i <= 3; i++) {
            toma.telemetria(barrido());
            assertTrue(texto().contains("Guardado " + i + "/3"));
        }
        assertEquals(TomaGuiada.Estado.ELIGIENDO, toma.estado());
        assertTrue(texto().contains("frente (anotado LOBITO): la red dijo LOBITO 3 de 3"));
        assertTrue(texto().contains("LOBITO donde no hay canica: ninguno"));

        // Encabezado + 3 barridos, con la nota de la escena y el número de barrido recibido (4, 5 y 6).
        List<String> f = filas(csv);
        assertEquals(4, f.size());
        assertTrue(f.get(0).startsWith("hora,barrido,nota,v0"));
        for (int i = 1; i <= 3; i++) {
            String[] c = f.get(i).split(",", -1);
            assertEquals(String.valueOf(3 + i), c[1]);
            assertEquals("lobito 0 10", c[2]);
            assertEquals(3 + 39 + 26, c.length);
        }

        // Mientras elige tampoco guarda.
        toma.telemetria(barrido());
        assertEquals(4, filas(csv).size());

        toma.tecla("");
        assertEquals(2, toma.numero());
        assertEquals(1, toma.guardadas());
        assertTrue(texto().contains("Escena 2 de 2"));
    }

    @Test public void repetirYSaltearBorranLaEscenaDelArchivo() throws Exception {
        Path csv = carpeta.getRoot().toPath().resolve("deteccion.csv");
        TomaGuiada toma = new TomaGuiada(DOS, 1, new AnalisisDeteccionTest.RedFalsa(), csv, out);
        // Escena 1 con R: se borra y se repite.
        toma.tecla("");
        for (int i = 0; i < 4; i++) toma.telemetria(barrido());
        assertEquals(4, filas(csv).size());
        toma.tecla("R");
        assertFalse(Files.exists(csv)); // Era la primera escena: el archivo no existía antes.
        assertEquals(1, toma.numero());
        assertEquals(TomaGuiada.Estado.INSTRUCCION, toma.estado());

        // Escena 1 otra vez, ahora con Enter.
        toma.tecla("");
        for (int i = 0; i < 4; i++) toma.telemetria(barrido());
        toma.tecla("");
        assertEquals(4, filas(csv).size());

        // Escena 2 con S después de tomarla: vuelve a quedar solo la escena 1, y termina.
        toma.tecla("");
        for (int i = 0; i < 4; i++) toma.telemetria(barrido());
        assertEquals(7, filas(csv).size());
        toma.tecla("s");
        assertEquals(4, filas(csv).size());
        assertTrue(toma.terminada());
        assertEquals(1, toma.guardadas());
        for (String l : filas(csv).subList(1, 4)) assertTrue(l.contains(",lobito 0 10,"));
    }

    @Test public void salteaAntesDeEmpezarYSigueDesdeUnaEscena() throws Exception {
        Path csv = carpeta.getRoot().toPath().resolve("deteccion.csv");
        TomaGuiada toma = new TomaGuiada(DOS, 2, new AnalisisDeteccionTest.RedFalsa(), csv, out);
        assertEquals(2, toma.numero());
        assertTrue(texto().contains("Escena 2 de 2"));
        toma.tecla("s");
        assertTrue(toma.terminada());
        assertFalse(Files.exists(csv));
        try {
            new TomaGuiada(DOS, 3, new AnalisisDeteccionTest.RedFalsa(), csv, out);
            fail("--desde fuera de rango");
        } catch (IllegalArgumentException esperado) { }
    }

    @Test public void lasEscenasDeLaListaSonNotasValidas() {
        assertEquals(25, TomaGuiada.ESCENAS.size());
        for (TomaGuiada.Escena e : TomaGuiada.ESCENAS) {
            NotaEscena n = NotaEscena.leer(e.nota());
            assertTrue(e.nota(), n.valida());
            assertTrue(e.nota(), n.anotada());
            assertFalse(e.instruccion().isBlank());
        }
    }
}
