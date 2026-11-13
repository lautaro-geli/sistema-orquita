package orquitas.servidor.comunicacion;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import orquitas.servidor.apoyo.RedFija;
import orquitas.servidor.apoyo.SalidaRegistrada;
import orquitas.servidor.navegacion.EntornoClasificado;
import orquitas.servidor.navegacion.Navegacion;
import orquitas.servidor.persistencia.Estomago;

/** Protocolo v2, respuestas y supervisión del enlace (CU-06, CU-08, CU-12) sin hardware. */
public class EnlaceOrquitaTest {
    private Path archivo;

    private Estomago estomago() throws IOException {
        archivo = Files.createTempDirectory("orquita-enlace-").resolve("estomago.txt");
        return new Estomago(archivo.toString());
    }

    /** Enlace sin hilo: se llama a recibirTrama directamente. */
    private EnlaceOrquita enlace(SalidaRegistrada salida, EntornoClasificado entorno) throws IOException {
        return new EnlaceOrquita(new ByteArrayInputStream(new byte[0]), salida, estomago(),
                new Navegacion(new RedFija(entorno)), 2000, 100);
    }

    // ---------------- decodificar ----------------

    @Test public void telemetriaValidaConservaSinEco() throws IOException {
        EnlaceOrquita e = enlace(new SalidaRegistrada(), EntornoClasificado.LIBRE);
        int[] t = todo(1234);
        t[0] = -1; t[38] = 0; t[5] = 4000;
        assertEquals("TELEMETRIA", e.decodificar(trama(t)));
        int[] guardada = leer(e, "telemetria");
        assertEquals(EnlaceOrquita.SIN_ECO, guardada[0]); // El -1 llega intacto a la navegación.
        assertEquals(0, guardada[38]);
        assertEquals(4000, guardada[5]);
        assertEquals(1234, guardada[1]);
    }

    @Test public void telemetriaMalFormadaEsInvalida() throws IOException {
        EnlaceOrquita e = enlace(new SalidaRegistrada(), EntornoClasificado.LIBRE);
        String ok = trama(todo(1000));
        String treintaYOcho = ok.substring(0, ok.lastIndexOf(';'));
        for (String mala : List.of(treintaYOcho, ok + ";1000", ok.replaceFirst("1000", "4001"),
                ok.replaceFirst("1000", "-2"), ok.replaceFirst("1000", "+100"), ok.replaceFirst("1000", "1e3"),
                ok.replaceFirst("1000", "10.5"), ok.replaceFirst("1000", " 100"), ok.replaceFirst("1000", "abc"),
                ok.replaceFirst("1000", ""), ok.replaceFirst("1000", "-1-1"), ok + "|extra", "TELEMETRIA",
                "TELEMETRIA|", "telemetria|" + ok.substring(11)))
            assertEquals(mala, "INVALIDA", e.decodificar(mala));
    }

    @Test public void capturaValidaEInvalida() throws IOException {
        EnlaceOrquita e = enlace(new SalidaRegistrada(), EntornoClasificado.LIBRE);
        assertEquals("CAPTURA", e.decodificar("CAPTURA|1"));
        assertEquals("1", leer(e, "identificadorCaptura"));
        assertEquals("CAPTURA", e.decodificar("CAPTURA|Lob_ito-16caract"));
        for (String mala : List.of("CAPTURA|", "CAPTURA", "CAPTURA|a b", "CAPTURA|12345678901234567",
                "CAPTURA|1|2", "CAPTURA|1;2", "CAPTURA|ñ"))
            assertEquals(mala, "INVALIDA", e.decodificar(mala));
    }

    @Test public void tiposDesconocidosVaciosYLargosSonInvalidos() throws IOException {
        EnlaceOrquita e = enlace(new SalidaRegistrada(), EntornoClasificado.LIBRE);
        for (String mala : List.of("", "ESTADO|ESCAPE", "ORDEN|AVANZAR", "LATIDO", "CONFIRMACION", "RECOPILACION|x|y|z",
                "CAPTURA|" + "x".repeat(EnlaceOrquita.LARGO_MAXIMO)))
            assertEquals(mala, "INVALIDA", e.decodificar(mala));
        assertEquals("INVALIDA", e.decodificar(null));
    }

    // ---------------- respuestas ----------------

    @Test public void telemetriaRespondeConfirmacionYLuegoOrden() throws IOException {
        SalidaRegistrada salida = new SalidaRegistrada();
        EnlaceOrquita e = enlace(salida, EntornoClasificado.LIBRE);
        e.recibirTrama(trama(todo(3000)));
        assertEquals(List.of("CONFIRMACION", "ORDEN|AVANZAR"), salida.lineas());
    }

    @Test public void invalidaRespondeRechazo() throws IOException {
        SalidaRegistrada salida = new SalidaRegistrada();
        EnlaceOrquita e = enlace(salida, EntornoClasificado.LIBRE);
        e.recibirTrama("HOLA|1");
        e.recibirTrama("");
        assertEquals(List.of("RECHAZO", "RECHAZO"), salida.lineas());
    }

    @Test public void capturaConfirmaDespuesDeEscribirYNoDuplica() throws IOException {
        Estomago estomago = estomago();
        Path ruta = archivo;
        // La salida verifica, en el momento de escribir CONFIRMACION, que la línea ya está en el archivo.
        SalidaRegistrada salida = new SalidaRegistrada() {
            @Override public synchronized void write(int b) throws IOException {
                if (b == 'C') assertTrue("CONFIRMACION antes de escribir", Files.readString(ruta).startsWith("7;"));
                super.write(b);
            }
        };
        EnlaceOrquita e = new EnlaceOrquita(new ByteArrayInputStream(new byte[0]), salida, estomago,
                new Navegacion(new RedFija(EntornoClasificado.LIBRE)), 2000, 100);
        e.recibirTrama("CAPTURA|7");
        e.recibirTrama("CAPTURA|7"); // Reenvío: se confirma sin duplicar la línea.
        assertEquals(List.of("CONFIRMACION", "CONFIRMACION"), salida.lineas());
        assertEquals(1, Files.readAllLines(ruta).size());
    }

    @Test public void capturaQueNoSePuedeAnotarRespondeRechazo() throws IOException {
        SalidaRegistrada salida = new SalidaRegistrada();
        Path carpeta = Files.createTempDirectory("orquita-sin-archivo-");
        // La ruta es una carpeta: escribir falla y el Escritor devuelve registrada = false.
        EnlaceOrquita e = new EnlaceOrquita(new ByteArrayInputStream(new byte[0]), salida, new Estomago(carpeta.toString()),
                new Navegacion(new RedFija(EntornoClasificado.LIBRE)), 2000, 100);
        e.recibirTrama("CAPTURA|1");
        assertEquals(List.of("RECHAZO"), salida.lineas());
    }

    // ---------------- hilo: lectura, latido y fallas ----------------

    @Test(timeout = 10000) public void lineasPegadasCortadasCrlfYDemasiadoLargas() throws Exception {
        PipedOutputStream robot = new PipedOutputStream();
        PipedInputStream entrada = new PipedInputStream(robot, 8192);
        SalidaRegistrada salida = new SalidaRegistrada();
        EnlaceOrquita e = new EnlaceOrquita(entrada, salida, estomago(),
                new Navegacion(new RedFija(EntornoClasificado.LIBRE)), 5000, 200);
        e.start();
        try {
            String t = trama(todo(3000));
            robot.write((t + "\r\nBASURA\n" + t.substring(0, 20)).getBytes(StandardCharsets.US_ASCII)); // Pegadas y una cortada.
            robot.flush();
            esperar(() -> salida.respuestas().size() >= 3, 3000);
            robot.write((t.substring(20) + "\n" + "X".repeat(EnlaceOrquita.LARGO_MAXIMO + 50) + "\n" + t + "\n")
                    .getBytes(StandardCharsets.US_ASCII));
            robot.flush();
            esperar(() -> salida.respuestas().size() >= 8, 3000);
            assertEquals(List.of("CONFIRMACION", "ORDEN|AVANZAR", "RECHAZO", "CONFIRMACION", "ORDEN|AVANZAR",
                    "RECHAZO", "CONFIRMACION", "ORDEN|AVANZAR"), salida.respuestas());
        } finally {
            e.detener();
            e.join(2000);
        }
        assertFalse(e.isAlive());
    }

    @Test(timeout = 10000) public void latidoCadaPeriodoMientrasHayaTrafico() throws Exception {
        PipedOutputStream robot = new PipedOutputStream();
        SalidaRegistrada salida = new SalidaRegistrada();
        EnlaceOrquita e = new EnlaceOrquita(new PipedInputStream(robot, 8192), salida, estomago(),
                new Navegacion(new RedFija(EntornoClasificado.LIBRE)), 5000, 100);
        long inicio = System.currentTimeMillis();
        e.start();
        try {
            esperar(() -> salida.contar("LATIDO") >= 5, 3000);
            long transcurrido = System.currentTimeMillis() - inicio;
            assertTrue("5 latidos en " + transcurrido + " ms", transcurrido >= 450); // Uno por período, no en ráfaga.
            assertEquals(EstadoEnlace.CONECTADO, leer(e, "estado"));
        } finally {
            e.detener();
            e.join(2000);
        }
    }

    @Test(timeout = 10000) public void silencioMayorQueElMaximoRegistraFallaYTerminaElHilo() throws Exception {
        PipedOutputStream robot = new PipedOutputStream();
        EnlaceOrquita e = new EnlaceOrquita(new PipedInputStream(robot), new SalidaRegistrada(), estomago(),
                new Navegacion(new RedFija(EntornoClasificado.LIBRE)), 300, 50);
        e.start();
        e.join(3000);
        assertFalse("el hilo debía terminar", e.isAlive());
        assertEquals(EstadoEnlace.DESCONECTADO, leer(e, "estado"));
        assertTrue(e.calcularTiempoSinRecepcion() >= 300);
    }

    @Test(timeout = 10000) public void traficoMantieneVivoElEnlace() throws Exception {
        PipedOutputStream robot = new PipedOutputStream();
        EnlaceOrquita e = new EnlaceOrquita(new PipedInputStream(robot, 8192), new SalidaRegistrada(), estomago(),
                new Navegacion(new RedFija(EntornoClasificado.LIBRE)), 300, 50);
        e.start();
        try {
            long fin = System.currentTimeMillis() + 900; // Tres veces el tiempo máximo, con tráfico cada 100 ms.
            while (System.currentTimeMillis() < fin) {
                robot.write((trama(todo(3000)) + "\n").getBytes(StandardCharsets.US_ASCII));
                robot.flush();
                Thread.sleep(100);
            }
            assertTrue(e.isAlive());
            assertEquals(EstadoEnlace.CONECTADO, leer(e, "estado"));
        } finally {
            e.detener();
            e.join(2000);
        }
    }

    @Test(timeout = 10000) public void escrituraFallidaDejaDesconectadoYTerminaElHilo() throws Exception {
        PipedOutputStream robot = new PipedOutputStream();
        SalidaRegistrada salida = new SalidaRegistrada();
        EnlaceOrquita e = new EnlaceOrquita(new PipedInputStream(robot, 8192), salida, estomago(),
                new Navegacion(new RedFija(EntornoClasificado.LIBRE)), 5000, 100);
        e.start();
        salida.caer();
        robot.write((trama(todo(3000)) + "\n").getBytes(StandardCharsets.US_ASCII));
        robot.flush();
        e.join(3000);
        assertFalse(e.isAlive());
        assertEquals(EstadoEnlace.DESCONECTADO, leer(e, "estado"));
        assertTrue(salida.lineas().isEmpty());
    }
}
