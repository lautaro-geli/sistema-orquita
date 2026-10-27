// Archivo de simulacion
package orquitas.verificacion;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import orquitas.servidor.captura.*;
import orquitas.servidor.comunicacion.*;
import orquitas.servidor.escape.ModoEscape;
import orquitas.servidor.navegacion.*;
import orquitas.servidor.persistencia.*;

/** Pruebas agregadas con la correccion de diseno v2: CU-08, CU-09, CU-11 y CU-12. */
public class CorreccionDisenoTest {
    private static final String DISTANCIAS = String.join(";", Collections.nCopies(12, "2500"));

    private static String trama(String id) { return "RECOPILACION|2026-09-30T12:00|" + DISTANCIAS + "|" + id; }
    private static Captura captura(String id) { return new Captura(id, LocalDateTime.of(2026, 9, 30, 12, 0)); }
    private static Path archivoNuevo() throws IOException {
        return Files.createTempDirectory("orquitas-diseno-").resolve("estomago.txt");
    }
    private static Navegacion navegacion() throws IOException { return new Navegacion(ModeloPrueba.red()); }

    /** Writer que falla las primeras N escrituras, como un socket caido. */
    private static final class SalidaInestable extends Writer {
        private final StringBuilder enviado = new StringBuilder();
        private final int fallas;
        private int intentos;
        SalidaInestable(int fallas) { this.fallas = fallas; }
        @Override public void write(char[] c, int desde, int largo) throws IOException {
            if (++intentos <= fallas) throw new IOException("socket caido");
            enviado.append(c, desde, largo);
        }
        @Override public void flush() { }
        @Override public void close() { }
    }

    @Test(timeout = 15000) public void timeoutDisparaFallaSenalReconexionYCierreDuro() throws Exception {
        CanalIngestaTCP rx = new CanalIngestaTCP(0, new Estomago(archivoNuevo().toString()), navegacion(), 300);
        Thread servidor = new Thread(rx::iniciar); servidor.start();
        try {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (rx.obtenerPuerto() < 0 && System.nanoTime() < limite) Thread.sleep(5);
            try (Socket s = new Socket("127.0.0.1", rx.obtenerPuerto())) {
                s.setSoTimeout(5000);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
                long inicio = System.nanoTime();
                assertEquals("ESTADO|RECONECTAR", in.readLine());
                assertTrue("Senal antes de TIMEOUT_MAXIMO", System.nanoTime() - inicio >= TimeUnit.MILLISECONDS.toNanos(300));
                assertEquals("ESTADO|RECONECTAR", in.readLine()); // Se repite mientras no haya trafico.
                out.write(trama("") + "\n"); out.flush();
                String respuesta;
                do { respuesta = in.readLine(); } while ("ESTADO|RECONECTAR".equals(respuesta));
                assertEquals("ORDEN|AVANZAR", respuesta); // El trafico restablece la operacion.
                // Sin trafico: senales hasta el cierre duro (6 x TIMEOUT_MAXIMO).
                while ((respuesta = in.readLine()) != null) assertEquals("ESTADO|RECONECTAR", respuesta);
            }
        } finally { rx.detener(); servidor.join(1000); }
    }

    @Test public void registrarFallaEsIdempotenteYElTraficoRestableceLaConexion() throws Exception {
        StringWriter salida = new StringWriter();
        EnlaceOrquita enlace = new EnlaceOrquita(new Estomago(archivoNuevo().toString()), navegacion(), salida);
        Thread.sleep(60);
        assertTrue(enlace.calcularTiempoSinRecepcion() >= 50);
        assertTrue(enlace.isConexionActiva());
        enlace.registrarFalla(); enlace.registrarFalla();
        assertFalse(enlace.isConexionActiva());
        enlace.enviarSenalReconexion();
        assertEquals("ESTADO|RECONECTAR\n", salida.toString());
        enlace.enviarRecopilacion(trama(""));
        assertTrue(enlace.isConexionActiva());
        assertTrue(enlace.calcularTiempoSinRecepcion() < 50);
        assertEquals("ESTADO|RECONECTAR\nORDEN|AVANZAR\n", salida.toString());
    }

    @Test public void senalDeReconexionIgnoraErroresDeSocket() throws Exception {
        EnlaceOrquita enlace = new EnlaceOrquita(new Estomago(archivoNuevo().toString()), navegacion(), new SalidaInestable(Integer.MAX_VALUE));
        enlace.enviarSenalReconexion(); // No debe lanzar.
    }

    @Test public void timeoutMaximoQuedaPorDebajoDelReglamento() throws Exception {
        assertTrue(EnlaceOrquita.TIMEOUT_MAXIMO > 0 && EnlaceOrquita.TIMEOUT_MAXIMO < 10000);
        try { new CanalIngestaTCP(0, new Estomago(archivoNuevo().toString()), navegacion(), 10000); fail("Acepto 10 s"); }
        catch (IllegalArgumentException esperado) { }
    }

    @Test(timeout = 5000) public void reintentoAcotadoRecuperaUnaFallaTransitoria() throws Exception {
        SalidaInestable salida = new SalidaInestable(2);
        EnlaceOrquita enlace = new EnlaceOrquita(new Estomago(archivoNuevo().toString()), navegacion(), salida);
        enlace.enviarOrdenNavegacion(Movimiento.GIRAR_IZQ);
        assertEquals("ORDEN|GIRAR_IZQ\n", salida.enviado.toString());
        assertEquals(3, salida.intentos);
        assertTrue(enlace.isConexionActiva());
    }

    @Test(timeout = 5000) public void reintentoAcotadoSeRindeTrasTresIntentos() throws Exception {
        SalidaInestable salida = new SalidaInestable(Integer.MAX_VALUE);
        EnlaceOrquita enlace = new EnlaceOrquita(new Estomago(archivoNuevo().toString()), navegacion(), salida);
        try { enlace.enviarOrdenNavegacion(Movimiento.AVANZAR); fail("Debia informar la falla"); }
        catch (IOException esperado) { }
        assertEquals(1 + 3, salida.intentos); // Envio original + 3 reintentos, nunca indefinido.
        assertFalse(enlace.isConexionActiva());
        assertFalse(enlace.reintentarConexion()); // Sin trama pendiente no reintenta.
    }

    @Test public void tramaInvalidaSeDescartaYRecibeErrorTramaInvalida() throws Exception {
        Path ruta = archivoNuevo();
        Estomago estomago = new Estomago(ruta.toString());
        StringWriter salida = new StringWriter();
        EnlaceOrquita enlace = new EnlaceOrquita(estomago, navegacion(), salida);
        List<String> invalidas = List.of("ESTADO|RECONECTAR", "ORDEN|AVANZAR", trama("X").replace(DISTANCIAS, "1;2;3"),
                "RECOPILACION|2026-09-30T12:00|" + DISTANCIAS);
        for (String t : invalidas) enlace.enviarRecopilacion(t);
        assertEquals("ERROR|TRAMA_INVALIDA\n".repeat(invalidas.size()), salida.toString());
        estomago.esperarEscrituras();
        assertFalse(Files.exists(ruta));
    }

    @Test public void recopilacionConAvisoRegistraCapturaYDevuelveMovimiento() throws Exception {
        Path ruta = archivoNuevo();
        Estomago estomago = new Estomago(ruta.toString());
        Navegacion navegacion = navegacion();
        Recopilacion sinAviso = Recopilacion.decodificar(trama(""));
        assertTrue(sinAviso.esValida()); assertFalse(sinAviso.tieneAvisoCaptura());
        assertEquals(Movimiento.AVANZAR, sinAviso.procesar(estomago, navegacion));
        Recopilacion conAviso = Recopilacion.decodificar(trama("Z9"));
        assertTrue(conAviso.tieneAvisoCaptura());
        assertEquals(Movimiento.AVANZAR, conAviso.procesar(estomago, navegacion));
        estomago.esperarEscrituras();
        assertEquals(List.of(captura("Z9").aLinea()), Files.readAllLines(ruta));
        try { Recopilacion.decodificar("basura").procesar(estomago, navegacion); fail("Proceso una trama invalida"); }
        catch (IllegalStateException esperado) { }
    }

    @Test(timeout = 5000) public void escritorPorCapturaPersisteEnEstomagoTxt() throws Exception {
        Path ruta = archivoNuevo();
        Set<String> hilos = ConcurrentHashMap.newKeySet();
        Estomago estomago = new Estomago(ruta.toString()) {
            @Override public boolean escribirRegistro(Captura c) throws IOException, InterruptedException {
                Thread actual = Thread.currentThread();
                hilos.add((actual instanceof Escritor ? "Escritor:" : "otro:") + actual.getId());
                return super.escribirRegistro(c);
            }
        };
        estomago.registrarCaptura(captura("A"));
        estomago.registrarCaptura(captura("B"));
        estomago.esperarEscrituras();
        assertEquals(2, hilos.size()); // Un hilo distinto por captura.
        for (String h : hilos) assertTrue(h, h.startsWith("Escritor:"));
        assertEquals(Set.of(captura("A").aLinea(), captura("B").aLinea()), new HashSet<>(Files.readAllLines(ruta)));
        assertEquals(2, estomago.contarRegistrosValidos(estomago.leerRegistros()));
    }

    @Test(timeout = 5000) public void registroDuplicadoDevuelveFalseYNoEscribe() throws Exception {
        Path ruta = archivoNuevo();
        Estomago estomago = new Estomago(ruta.toString());
        List<Thread> productores = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(() -> estomago.registrarCaptura(captura("D")));
            productores.add(t); t.start();
        }
        for (Thread t : productores) t.join();
        estomago.esperarEscrituras();
        assertEquals(1, Files.readAllLines(ruta).size());
        assertFalse(estomago.escribirRegistro(captura("D")));
        assertEquals(1, Files.readAllLines(ruta).size());
    }

    @Test public void modoEscapeConDosCapturasNoActivaYConTresSi() throws Exception {
        AtomicInteger cambios = new AtomicInteger();
        Navegacion navegacion = new Navegacion(ModeloPrueba.red()) {
            @Override public void establecerModoOperativo(ModoOperativo modo) {
                super.establecerModoOperativo(modo); cambios.incrementAndGet();
            }
        };
        ModoEscape modoEscape = new ModoEscape(navegacion);
        assertEquals(3, ModoEscape.UMBRAL_CAPTURAS);
        modoEscape.notificarCapturasCompletas(2);
        assertEquals(ModoOperativo.BUSQUEDA, navegacion.getModoOperativo());
        modoEscape.notificarCapturasCompletas(3);
        assertEquals(ModoOperativo.ESCAPE, navegacion.getModoOperativo());
        modoEscape.notificarCapturasCompletas(4);
        assertEquals(1, cambios.get()); // activar() es idempotente.
        try { modoEscape.notificarCapturasCompletas(-1); fail("Acepto cantidad negativa"); }
        catch (IllegalArgumentException esperado) { }
    }

    @Test public void navegacionConservaElMovimientoDecidido() throws Exception {
        Navegacion navegacion = navegacion();
        assertEquals(Movimiento.DETENER, navegacion.obtenerMovimientoDecidido());
        double[] cerca = new double[12]; Arrays.fill(cerca, 80);
        assertEquals(Movimiento.DETENER, navegacion.determinarMovimiento(new Telemetria(cerca)));
        double[] libre = new double[12]; Arrays.fill(libre, 2500);
        assertEquals(Movimiento.AVANZAR, navegacion.determinarMovimiento(new Telemetria(libre)));
        assertEquals(Movimiento.AVANZAR, navegacion.obtenerMovimientoDecidido());
        navegacion.establecerModoOperativo(ModoOperativo.ESCAPE);
        assertEquals(Movimiento.AVANZAR, navegacion.determinarMovimiento(null));
    }

    @Test public void cambioDeModoSeInformaUnaVezAntesDeLaOrden() throws Exception {
        StringWriter salida = new StringWriter();
        Navegacion navegacion = navegacion();
        EnlaceOrquita enlace = new EnlaceOrquita(new Estomago(archivoNuevo().toString()), navegacion, salida);
        enlace.enviarRecopilacion(trama(""));
        assertEquals("ORDEN|AVANZAR\n", salida.toString()); // Estado inicial: ningun ESTADO|.
        navegacion.establecerModoOperativo(ModoOperativo.ESCAPE);
        enlace.enviarRecopilacion(trama(""));
        enlace.enviarRecopilacion(trama(""));
        navegacion.establecerModoOperativo(ModoOperativo.BUSQUEDA);
        enlace.enviarRecopilacion(trama(""));
        assertEquals("ORDEN|AVANZAR\nESTADO|ESCAPE\nORDEN|AVANZAR\nORDEN|AVANZAR\nESTADO|BUSQUEDA\nORDEN|AVANZAR\n",
                salida.toString());
    }

    @Test(timeout = 15000) public void tresCapturasPorTcpEmitenEstadoEscapeEnLaSiguienteRecopilacion() throws Exception {
        Estomago estomago = new Estomago(archivoNuevo().toString());
        Navegacion navegacion = navegacion();
        Auditor auditor = new Auditor(estomago, new ModoEscape(navegacion), 20);
        CanalIngestaTCP rx = new CanalIngestaTCP(0, estomago, navegacion);
        Thread servidor = new Thread(rx::iniciar);
        auditor.start(); servidor.start();
        try {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (rx.obtenerPuerto() < 0 && System.nanoTime() < limite) Thread.sleep(5);
            try (Socket s = new Socket("127.0.0.1", rx.obtenerPuerto())) {
                s.setSoTimeout(3000);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
                for (String id : List.of("E1", "E2", "E3")) {
                    out.write(trama(id) + "\n"); out.flush();
                    assertEquals("ORDEN|AVANZAR", in.readLine()); // Sin ESTADO| mientras se busca.
                }
                limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (navegacion.getModoOperativo() != ModoOperativo.ESCAPE && System.nanoTime() < limite) Thread.sleep(10);
                assertEquals(ModoOperativo.ESCAPE, navegacion.getModoOperativo());
                out.write(trama("") + "\n"); out.flush();
                assertEquals("ESTADO|ESCAPE", in.readLine());
                assertEquals("ORDEN|AVANZAR", in.readLine());
                out.write(trama("") + "\n"); out.flush();
                assertEquals("ORDEN|AVANZAR", in.readLine()); // No se repite el estado.
            }
        } finally { rx.detener(); servidor.join(1000); estomago.esperarEscrituras(); auditor.detener(); auditor.join(1000); }
    }

    @Test public void telemetriaSumaLosCuatroSectoresFrontales() {
        assertEquals(4 + 5 + 6 + 7, new Telemetria(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11).sumarFrente(), 0);
    }

    @Test public void escapeAvanzaConFrenteAbiertoAunqueBusquedaGire() throws Exception {
        // Objeto frontal angosto: BUSQUEDA lo evita, ESCAPE sigue hacia el espacio abierto.
        Telemetria t = new Telemetria(400, 400, 400, 400, 120, 150, 2200, 2200, 2500, 2500, 2500, 2500);
        Navegacion navegacion = navegacion();
        assertEquals(Movimiento.GIRAR_IZQ, navegacion.determinarMovimiento(t));
        navegacion.establecerModoOperativo(ModoOperativo.ESCAPE);
        assertEquals(Movimiento.AVANZAR, navegacion.determinarMovimiento(t)); // El modo tiene efecto.
    }

    @Test public void escapeConFrenteBloqueadoGiraHaciaElLadoMasAbierto() throws Exception {
        Navegacion navegacion = navegacion();
        navegacion.establecerModoOperativo(ModoOperativo.ESCAPE);
        assertEquals(Movimiento.GIRAR_DER, navegacion.determinarMovimiento(
                new Telemetria(2500, 2500, 2500, 2500, 100, 100, 100, 100, 300, 300, 300, 300)));
        assertEquals(Movimiento.GIRAR_IZQ, navegacion.determinarMovimiento(
                new Telemetria(300, 300, 300, 300, 100, 100, 100, 100, 2500, 2500, 2500, 2500)));
        double[] encerrado = new double[12]; Arrays.fill(encerrado, 80);
        assertEquals(Movimiento.DETENER, navegacion.determinarMovimiento(new Telemetria(encerrado)));
    }

    @Test(timeout = 15000) public void auditorLeeMientrasHayEscriturasConcurrentes() throws Exception {
        Estomago estomago = new Estomago(archivoNuevo().toString());
        AtomicInteger siguiente = new AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean seguir = new java.util.concurrent.atomic.AtomicBoolean(true);
        List<Thread> productores = new ArrayList<>();
        for (int p = 0; p < 4; p++) { // Flujo continuo de capturas mientras el auditor lee.
            Thread t = new Thread(() -> { while (seguir.get()) estomago.registrarCaptura(captura("S" + siguiente.getAndIncrement())); });
            productores.add(t); t.start();
        }
        try {
            int anterior = -1;
            for (int ciclo = 0; ciclo < 5; ciclo++) {
                FutureTask<List<Captura>> lectura = new FutureTask<>(estomago::leerRegistros);
                new Thread(lectura).start();
                int cantidad = estomago.contarRegistrosValidos(lectura.get(3, TimeUnit.SECONDS)); // Sin postergacion.
                assertTrue(cantidad >= anterior); // El conteo del archivo nunca retrocede.
                anterior = cantidad;
                Thread.sleep(20);
            }
        } finally {
            seguir.set(false);
            for (Thread t : productores) t.join(2000);
            estomago.esperarEscrituras();
        }
        assertEquals(siguiente.get(), estomago.contarRegistrosValidos(estomago.leerRegistros()));
    }
}
