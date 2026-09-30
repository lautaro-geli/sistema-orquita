// Archivo de simulacion
package orquitas.verificacion;

import orquitas.servidor.captura.*;
import orquitas.servidor.comunicacion.ReceptorIngestaTCP;
import orquitas.servidor.escape.*;
import orquitas.servidor.navegacion.*;
import orquitas.servidor.persistencia.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public class LinareadaTest {
    private static Captura captura(String id) {
        return new Captura(id, LocalDateTime.of(2026, 9, 30, 12, 0));
    }

    private static Path archivoNuevo() throws IOException {
        return Files.createTempDirectory("orquitas-prueba-").resolve("estomago.txt");
    }

    private static void esperar(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condicion.getAsBoolean() && System.nanoTime() < limite) Thread.sleep(10);
        assertTrue("No se cumplió la condición dentro del plazo", condicion.getAsBoolean());
    }

    @Test(timeout=5000) public void escritorYAuditorSonHilosDistintosConRunPropio() throws Exception {
        assertEquals(Thread.class, ActualizadorEstomago.class.getSuperclass());
        assertEquals(Thread.class, LectorEstomago.class.getSuperclass());
        assertEquals(ActualizadorEstomago.class, ActualizadorEstomago.class.getDeclaredMethod("run").getDeclaringClass());
        assertEquals(LectorEstomago.class, LectorEstomago.class.getDeclaredMethod("run").getDeclaringClass());
    }

    @Test(timeout=5000) public void capturaPendienteBloqueaAlLectorHastaTerminarLaEscritura() throws Exception {
        Path ruta = archivoNuevo();
        CountDownLatch entroEscritor = new CountDownLatch(1);
        CountDownLatch permitirEscritura = new CountDownLatch(1);
        ArchivoEstomago archivo = new ArchivoEstomago(ruta.toString()) {
            @Override public boolean escribirRegistro(Captura captura) throws IOException, InterruptedException {
                entroEscritor.countDown();
                permitirEscritura.await();
                return super.escribirRegistro(captura);
            }
        };
        ActualizadorEstomago escritor = new ActualizadorEstomago(archivo);
        escritor.start();
        FutureTask<List<Captura>> lectura = new FutureTask<>(archivo::leerRegistros);
        Thread lector = new Thread(lectura);
        try {
            escritor.encolarCaptura(captura("A"));
            assertTrue(entroEscritor.await(1, TimeUnit.SECONDS));
            lector.start();
            try { lectura.get(150, TimeUnit.MILLISECONDS); fail("El lector se adelantó al escritor"); }
            catch (TimeoutException esperado) { /* Sigue esperando mientras hay trabajo pendiente. */ }
            permitirEscritura.countDown();
            assertEquals("A", lectura.get(1, TimeUnit.SECONDS).get(0).getIdCaptura());
        } finally {
            permitirEscritura.countDown();
            escritor.detener(); escritor.join(1000); lector.join(1000);
        }
    }

    @Test(timeout=5000) public void semaforoExcluyeLecturaYEscrituraConcurrentes() throws Exception {
        ArchivoEstomago archivo = new ArchivoEstomago(archivoNuevo().toString());
        var campo = ArchivoEstomago.class.getDeclaredField("mutex");
        campo.setAccessible(true);
        Semaphore mutex = (Semaphore) campo.get(archivo);
        mutex.acquire();
        FutureTask<List<Captura>> lectura = new FutureTask<>(archivo::leerRegistros);
        FutureTask<Boolean> escritura = new FutureTask<>(() -> archivo.escribirRegistro(captura("B")));
        new Thread(lectura).start(); new Thread(escritura).start();
        try {
            esperar(() -> mutex.getQueueLength() == 2);
            assertFalse(lectura.isDone()); assertFalse(escritura.isDone());
        } finally { mutex.release(); }
        lectura.get(1, TimeUnit.SECONDS); assertTrue(escritura.get(1, TimeUnit.SECONDS));
        assertEquals(1, archivo.leerRegistros().size());
    }

    @Test(timeout=5000) public void colaAceptaClientesConcurrentesYSeDrenaAlDetener() throws Exception {
        ArchivoEstomago archivo = new ArchivoEstomago(archivoNuevo().toString());
        ActualizadorEstomago escritor = new ActualizadorEstomago(archivo);
        escritor.start();
        List<Thread> productores = new ArrayList<>();
        for (int i=0; i<30; i++) {
            String id = "L" + i;
            Thread t = new Thread(() -> escritor.encolarCaptura(captura(id)));
            productores.add(t); t.start();
        }
        for (Thread t : productores) t.join();
        escritor.detener(); escritor.join(3000);
        assertFalse(escritor.isAlive());
        assertEquals(30, archivo.leerRegistros().size());
    }

    @Test(timeout=5000) public void retransmitirUnIdNoCuentaOtroLobito() throws Exception {
        Path ruta = archivoNuevo();
        ArchivoEstomago archivo = new ArchivoEstomago(ruta.toString());
        assertTrue(archivo.escribirRegistro(captura("A")));
        assertFalse(archivo.escribirRegistro(captura("A")));
        assertEquals(1, Files.readAllLines(ruta).size());
        assertEquals(1, archivo.leerRegistros().size());
    }

    @Test(timeout=5000) public void lectorCuentaArchivoDescartaCorruptosYDuplicados() throws Exception {
        Path ruta = archivoNuevo();
        Files.writeString(ruta, captura("A").aLinea()+"\ncorrupto\n"+captura("A").aLinea()
                +"\nB;fecha-invalida\n"+captura("C").aLinea()+"\n");
        assertEquals(2, new ArchivoEstomago(ruta.toString()).leerRegistros().size());
    }

    @Test(timeout=5000) public void errorDeArchivoLiberaPermisoYNoDevuelveConteoParcial() throws Exception {
        Path ruta = archivoNuevo(); Files.createDirectory(ruta);
        ArchivoEstomago archivo = new ArchivoEstomago(ruta.toString());
        try { archivo.escribirRegistro(captura("A")); fail("Debía informar IOException"); }
        catch (IOException esperado) { }
        try { archivo.leerRegistros(); fail("Debía informar IOException"); }
        catch (IOException esperado) { }
        Files.delete(ruta); // Directorio vacío creado sólo para esta prueba.
        assertTrue(archivo.escribirRegistro(captura("B")));
        assertEquals(1, archivo.leerRegistros().size());
    }

    @Test(timeout=5000) public void escritorContinuaDespuesDeUnErrorRecuperable() throws Exception {
        AtomicInteger intentos = new AtomicInteger();
        ArchivoEstomago archivo = new ArchivoEstomago(archivoNuevo().toString()) {
            @Override public boolean escribirRegistro(Captura c) throws IOException, InterruptedException {
                if (intentos.getAndIncrement()==0) throw new IOException("Error inducido");
                return super.escribirRegistro(c);
            }
        };
        ActualizadorEstomago escritor = new ActualizadorEstomago(archivo);
        escritor.start(); escritor.encolarCaptura(captura("A")); escritor.encolarCaptura(captura("B"));
        escritor.detener(); escritor.join(1000);
        assertEquals("B", archivo.leerRegistros().get(0).getIdCaptura());
    }

    @Test(timeout=5000) public void reiniciarConTresRegistrosActivaEscapeUnaSolaVez() throws Exception {
        Path ruta = archivoNuevo();
        ArchivoEstomago inicial = new ArchivoEstomago(ruta.toString());
        for (String id : List.of("A", "B", "C")) inicial.escribirRegistro(captura(id));
        AtomicInteger notificaciones = new AtomicInteger();
        Navegador navegador = new Navegador();
        ActivadorModoEscape activador = new ActivadorModoEscape(navegador) {
            @Override public void notificarCapturasCompletas(EventoModoEscape e) {
                super.notificarCapturasCompletas(e); notificaciones.incrementAndGet();
            }
        };
        LectorEstomago lector = new LectorEstomago(new ArchivoEstomago(ruta.toString()), activador, 20);
        lector.start();
        try {
            esperar(() -> navegador.getModoOperativo()==ModoOperativo.ESCAPE);
            Thread.sleep(100); assertEquals(1, notificaciones.get());
        } finally { lector.detener(); lector.join(1000); }
    }

    @Test(timeout=5000) public void dosRegistrosMantienenBusquedaYElTerceroActivaEscape() throws Exception {
        ArchivoEstomago archivo = new ArchivoEstomago(archivoNuevo().toString());
        archivo.escribirRegistro(captura("A")); archivo.escribirRegistro(captura("B"));
        Navegador navegador = new Navegador();
        LectorEstomago lector = new LectorEstomago(archivo, new ActivadorModoEscape(navegador), 20);
        lector.start();
        try {
            Thread.sleep(100); assertEquals(ModoOperativo.BUSQUEDA, navegador.getModoOperativo());
            archivo.escribirRegistro(captura("C"));
            esperar(() -> navegador.getModoOperativo()==ModoOperativo.ESCAPE);
        } finally { lector.detener(); lector.join(1000); }
    }

    @Test(timeout=5000) public void datosInvalidosNoSePersisten() throws Exception {
        ArchivoEstomago archivo = new ArchivoEstomago(archivoNuevo().toString());
        ActualizadorEstomago escritor = new ActualizadorEstomago(archivo);
        Recopilador recopilador = new Recopilador(new ReceptorCapturas(escritor));
        escritor.start();
        for (String trama : List.of("", "OTRO|A|2026-09-30T12:00", "CAPTURA|A",
                "CAPTURA|A;B|2026-09-30T12:00", "CAPTURA|A|mal", "CAPTURA||2026-09-30T12:00")) {
            assertFalse(recopilador.procesarRecopilacion(trama));
        }
        escritor.detener(); escritor.join(1000);
        assertTrue(archivo.leerRegistros().isEmpty());
    }

    @Test(timeout=5000) public void tcpRealTramasInvalidasFragmentadasReconectarYTresCapturas() throws Exception {
        ArchivoEstomago archivo = new ArchivoEstomago(archivoNuevo().toString());
        ActualizadorEstomago escritor = new ActualizadorEstomago(archivo);
        Navegador navegador = new Navegador();
        LectorEstomago lector = new LectorEstomago(archivo, new ActivadorModoEscape(navegador), 20);
        ReceptorIngestaTCP entrada = new ReceptorIngestaTCP(0, new Recopilador(new ReceptorCapturas(escritor)));
        Thread servidor = new Thread(entrada::iniciar);
        escritor.start(); lector.start(); servidor.start();
        try {
            esperar(() -> entrada.obtenerPuerto()>0);
            try (Socket s = new Socket("127.0.0.1", entrada.obtenerPuerto())) {
                s.getOutputStream().write("CAPTURA|INCOMPLETA|2026-09-30T12:00".getBytes(StandardCharsets.UTF_8));
            }
            try (Socket s = new Socket("127.0.0.1", entrada.obtenerPuerto())) {
                var salida = s.getOutputStream();
                salida.write(("OTRO|x\n"+"X".repeat(300)+"\nCAP").getBytes(StandardCharsets.UTF_8));
                salida.flush();
                salida.write(("TURA|A|2026-09-30T12:00\nCAPTURA|B|2026-09-30T12:00\r\n"
                        +"CAPTURA|C|2026-09-30T12:00\n").getBytes(StandardCharsets.UTF_8));
                s.setSoTimeout(2000);
                var respuestas = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                assertEquals("ERROR|TRAMA_INVALIDA", respuestas.readLine());
                assertEquals("ERROR|TRAMA_INVALIDA", respuestas.readLine());
            }
            esperar(() -> navegador.getModoOperativo()==ModoOperativo.ESCAPE);
            assertEquals(3, archivo.leerRegistros().size());
        } finally {
            entrada.detener(); servidor.join(1000);
            escritor.detener(); escritor.join(1000); lector.detener(); lector.join(1000);
        }
    }

    @Test(timeout=5000) public void detenerCierraClientesQueNoEnvianNada() throws Exception {
        ActualizadorEstomago escritor = new ActualizadorEstomago(new ArchivoEstomago(archivoNuevo().toString()));
        ReceptorIngestaTCP entrada = new ReceptorIngestaTCP(0, new Recopilador(new ReceptorCapturas(escritor)));
        Thread servidor = new Thread(entrada::iniciar); servidor.start();
        esperar(() -> entrada.obtenerPuerto()>0);
        try (Socket cliente = new Socket("127.0.0.1", entrada.obtenerPuerto())) {
            cliente.setSoTimeout(1000);
            // connect() no garantiza que accept() haya registrado el socket.
            esperar(() -> entrada.obtenerCantidadClientes() == 1);
            entrada.detener(); servidor.join(1000);
            assertFalse(servidor.isAlive());
            assertEquals(-1, cliente.getInputStream().read());
        }
    }
}
