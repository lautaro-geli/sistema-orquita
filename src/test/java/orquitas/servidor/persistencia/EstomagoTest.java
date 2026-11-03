package orquitas.servidor.persistencia;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import orquitas.servidor.apoyo.RedFija;
import orquitas.servidor.navegacion.EntornoClasificado;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Navegacion;

/** Estómago, Escritor y Auditor (CU-09, CU-10, CU-11): persistencia, concurrencia y umbral. */
public class EstomagoTest {

    private static Path ruta() throws IOException {
        return Files.createTempDirectory("orquita-estomago-").resolve("estomago.txt");
    }

    @Test public void archivoInexistenteCuentaCeroYRegistrarCreaLaLinea() throws Exception {
        Path ruta = ruta();
        Estomago estomago = new Estomago(ruta.toString());
        assertEquals(0, estomago.contarRegistrosValidos());
        assertTrue(estomago.registrarCaptura("1"));
        List<String> lineas = Files.readAllLines(ruta);
        assertEquals(1, lineas.size());
        assertTrue(lineas.get(0).matches("1;\\d{4}-\\d{2}-\\d{2}T.*")); // Hora del Servidor.
        assertEquals(1, estomago.contarRegistrosValidos());
    }

    @Test public void idRepetidoSeConsideraRegistradoYNoDuplica() throws Exception {
        Path ruta = ruta();
        Estomago estomago = new Estomago(ruta.toString());
        assertTrue(estomago.registrarCaptura("A"));
        assertTrue(estomago.registrarCaptura("A"));
        assertEquals(1, Files.readAllLines(ruta).size());
        assertEquals(1, estomago.contarRegistrosValidos());
    }

    @Test public void lineasCorruptasNoCuentanYElConteoSaleDelArchivo() throws Exception {
        Path ruta = ruta();
        Files.writeString(ruta, "1;2026-11-04T10:00:00\n\ncortada\n2;fecha-mala\n;2026-11-04T10:00:00\n"
                + "3;2026-11-04T10:00:01;extra\n4;2026-11-04T10:00:02\n");
        Estomago estomago = new Estomago(ruta.toString());
        assertEquals(2, estomago.contarRegistrosValidos());
        Files.writeString(ruta, "9;2026-11-04T10:00:03\n", java.nio.file.StandardOpenOption.APPEND); // Otro proceso.
        assertEquals(3, estomago.contarRegistrosValidos());
    }

    @Test public void reiniciarVaciaElArchivo() throws Exception {
        Path ruta = ruta();
        Estomago estomago = new Estomago(ruta.toString());
        estomago.registrarCaptura("1");
        estomago.registrarCaptura("2");
        estomago.reiniciar();
        assertEquals(0, estomago.contarRegistrosValidos());
        assertEquals(0, Files.size(ruta));
    }

    @Test public void escrituraFallidaDevuelveFalseYLiberaElMutex() throws Exception {
        Path ruta = ruta();
        Files.createDirectory(ruta); // La ruta es una carpeta: escribir falla.
        Estomago estomago = new Estomago(ruta.toString());
        assertFalse(estomago.registrarCaptura("1"));
        Semaphore mutex = leer(estomago, "mutex");
        assertEquals(1, mutex.availablePermits());
        Files.delete(ruta);
        assertTrue(estomago.registrarCaptura("2"));
    }

    @Test public void elMutexEsJustoYExcluyeAlAuditor() throws Exception {
        Estomago estomago = new Estomago(ruta().toString());
        Semaphore mutex = leer(estomago, "mutex");
        assertTrue(mutex.isFair());
        mutex.acquire(); // Mientras alguien tiene la sección crítica, nadie lee ni escribe.
        Thread escritor = new Thread(() -> estomago.registrarCaptura("1"));
        AtomicInteger conteo = new AtomicInteger(-1);
        Thread lector = new Thread(() -> {
            try { conteo.set(estomago.contarRegistrosValidos()); } catch (Exception e) { throw new AssertionError(e); }
        });
        escritor.start(); lector.start();
        esperar(() -> mutex.getQueueLength() == 2, 2000);
        assertEquals(-1, conteo.get());
        mutex.release();
        escritor.join(2000); lector.join(2000);
        assertEquals(1, estomago.contarRegistrosValidos());
    }

    @Test(timeout = 20000) public void escritoresSimultaneosYAuditorLeyendoSinPerdidasNiDuplicados() throws Exception {
        Path ruta = ruta();
        Estomago estomago = new Estomago(ruta.toString());
        Navegacion navegacion = new Navegacion(new RedFija(EntornoClasificado.LIBRE));
        Auditor auditor = new Auditor(estomago, navegacion, 1, 1000); // Lee sin parar; umbral inalcanzable.
        auditor.start();
        List<Thread> hilos = new ArrayList<>();
        AtomicInteger fallidas = new AtomicInteger();
        for (int i = 0; i < 40; i++) {
            String id = "L" + (i % 30); // 10 ids repetidos a propósito.
            Thread h = new Thread(() -> { if (!estomago.registrarCaptura(id)) fallidas.incrementAndGet(); });
            hilos.add(h); h.start();
        }
        for (Thread h : hilos) h.join();
        auditor.detener(); auditor.join(2000);
        assertFalse(auditor.isAlive());
        assertEquals(0, fallidas.get());
        List<String> lineas = Files.readAllLines(ruta);
        assertEquals(30, lineas.size());
        HashSet<String> ids = new HashSet<>();
        for (String l : lineas) ids.add(l.split(";")[0]);
        assertEquals(30, ids.size());
        assertEquals(30, estomago.contarRegistrosValidos());
    }

    @Test(timeout = 10000) public void auditorActivaEscapeConUmbralUnoYTresYEsIdempotente() throws Exception {
        for (int umbral : new int[]{1, 3}) {
            Estomago estomago = new Estomago(ruta().toString());
            AtomicInteger cambios = new AtomicInteger();
            Navegacion navegacion = new Navegacion(new RedFija(EntornoClasificado.LIBRE)) {
                @Override public synchronized void establecerModoOperativo(ModoOperativo modo) {
                    super.establecerModoOperativo(modo); cambios.incrementAndGet();
                }
            };
            Auditor auditor = new Auditor(estomago, navegacion, 10, umbral);
            auditor.start();
            try {
                for (int i = 1; i < umbral; i++) estomago.registrarCaptura("c" + i);
                Thread.sleep(100); // Varios ciclos del Auditor por debajo del umbral.
                assertEquals("umbral " + umbral, ModoOperativo.BUSQUEDA, leer(navegacion, "modoOperativo"));
                estomago.registrarCaptura("c" + umbral);
                esperar(() -> leer(navegacion, "modoOperativo") == ModoOperativo.ESCAPE, 2000);
                Thread.sleep(100); // Más ciclos: no vuelve a cambiar.
                assertEquals(1, cambios.get());
            } finally {
                auditor.detener(); auditor.join(2000);
            }
            assertFalse(auditor.isAlive());
        }
    }

    @Test public void auditorRechazaParametrosInvalidos() throws Exception {
        Estomago estomago = new Estomago(ruta().toString());
        Navegacion navegacion = new Navegacion(new RedFija(EntornoClasificado.LIBRE));
        try { new Auditor(estomago, navegacion, 0, 1); fail(); } catch (IllegalArgumentException esperado) { }
        try { new Auditor(estomago, navegacion, 10, 0); fail(); } catch (IllegalArgumentException esperado) { }
    }
}
