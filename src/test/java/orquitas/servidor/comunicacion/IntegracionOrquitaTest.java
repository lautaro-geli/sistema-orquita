package orquitas.servidor.comunicacion;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.*;

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
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Navegacion;
import orquitas.servidor.persistencia.Auditor;
import orquitas.servidor.persistencia.Estomago;

/**
 * Integración sin hardware: una Orquita simulada (solo de pruebas) hace el ciclo real sobre los streams
 * del enlace: telemetrías, una captura, paso a ESCAPE con umbral 1 y salida a campo libre.
 */
public class IntegracionOrquitaTest {

    /** Orquita simulada: escribe tramas como el sketch y espera la respuesta del Servidor. */
    private static final class OrquitaSimulada {
        private final PipedOutputStream haciaServidor;
        private final SalidaRegistrada desdeServidor;

        OrquitaSimulada(PipedOutputStream haciaServidor, SalidaRegistrada desdeServidor) {
            this.haciaServidor = haciaServidor;
            this.desdeServidor = desdeServidor;
        }

        /** Envía la línea y devuelve las respuestas nuevas (sin LATIDO) cuando llegan las esperadas. */
        List<String> enviar(String linea, int respuestasEsperadas) throws Exception {
            int antes = desdeServidor.respuestas().size();
            haciaServidor.write((linea + "\n").getBytes(StandardCharsets.US_ASCII));
            haciaServidor.flush();
            esperar(() -> desdeServidor.respuestas().size() >= antes + respuestasEsperadas, 3000);
            List<String> todas = desdeServidor.respuestas();
            return todas.subList(antes, todas.size());
        }
    }

    /** Red de prueba: un solo sector cercano es LOBITO; nada cerca es LIBRE; el resto, OBJETO. */
    private static EntornoClasificado regla(int[] t) {
        int cercanos = 0;
        for (int s = 0; s < 13; s++) if (t[s * 3 + 2] < 900) cercanos++;
        return cercanos == 0 ? EntornoClasificado.LIBRE : cercanos == 1 ? EntornoClasificado.LOBITO : EntornoClasificado.OBJETO;
    }

    @Test(timeout = 20000) public void cicloCompletoBusquedaCapturaEscapeYLiberada() throws Exception {
        Path ruta = Files.createTempDirectory("orquita-integracion-").resolve("estomago.txt");
        Estomago estomago = new Estomago(ruta.toString());
        estomago.reiniciar();
        Navegacion navegacion = new Navegacion(new RedFija(IntegracionOrquitaTest::regla));
        Auditor auditor = new Auditor(estomago, navegacion, 20, 1); // Umbral de la prueba de campo.
        PipedOutputStream robot = new PipedOutputStream();
        SalidaRegistrada salida = new SalidaRegistrada();
        EnlaceOrquita enlace = new EnlaceOrquita(new PipedInputStream(robot, 8192), salida, estomago, navegacion, 3000, 100);
        OrquitaSimulada orquita = new OrquitaSimulada(robot, salida);
        auditor.start();
        enlace.start();
        try {
            int[] lobitoDerecha = con(todo(1400), 1, 300);
            // BUSQUEDA: persigue al Lobito que ve a la derecha.
            assertEquals(List.of("CONFIRMACION", "ORDEN|GIRAR_DER|300"), orquita.enviar(trama(lobitoDerecha), 2));
            assertEquals(List.of("CONFIRMACION", "ORDEN|AVANZAR|400"), orquita.enviar(trama(con(todo(1400), 6, 300)), 2));
            // Captura: se confirma recién con la línea escrita; el reenvío no duplica.
            assertEquals(List.of("CONFIRMACION"), orquita.enviar("CAPTURA|1", 1));
            assertEquals(List.of("CONFIRMACION"), orquita.enviar("CAPTURA|1", 1));
            assertEquals(1, Files.readAllLines(ruta).size());
            // El Auditor ve 1 captura en el archivo (umbral 1) y pasa a ESCAPE.
            esperar(() -> leer(navegacion, "modoOperativo") == ModoOperativo.ESCAPE, 3000);
            // ESCAPE: el mismo Lobito a la derecha ahora se ignora; la orden cambia.
            assertEquals(List.of("CONFIRMACION", "ORDEN|AVANZAR|400"), orquita.enviar(trama(lobitoDerecha), 2));
            // Trama inválida: RECHAZO y la conexión sigue.
            assertEquals(List.of("RECHAZO"), orquita.enviar("TELEMETRIA|1;2;3", 1));
            // Campo libre: LIBERADA y DETENER.
            assertEquals(List.of("CONFIRMACION", "ORDEN|DETENER|0"), orquita.enviar(trama(todo(4000)), 2));
            assertEquals(ModoOperativo.LIBERADA, leer(navegacion, "modoOperativo"));
            esperar(() -> salida.contar("LATIDO") >= 1, 2000); // El latido sigue saliendo cada período.
            assertEquals(EstadoEnlace.CONECTADO, leer(enlace, "estado"));
        } finally {
            enlace.detener(); enlace.join(2000);
            auditor.detener(); auditor.join(2000);
        }
        assertFalse(enlace.isAlive());
        assertFalse(auditor.isAlive());
    }

    @Test(timeout = 20000) public void siLaOrquitaCallaElServidorQuedaEnErrorSeguro() throws Exception {
        Estomago estomago = new Estomago(Files.createTempDirectory("orquita-integracion-").resolve("e.txt").toString());
        Navegacion navegacion = new Navegacion(new RedFija(EntornoClasificado.LIBRE));
        PipedOutputStream robot = new PipedOutputStream();
        SalidaRegistrada salida = new SalidaRegistrada();
        EnlaceOrquita enlace = new EnlaceOrquita(new PipedInputStream(robot, 8192), salida, estomago, navegacion, 400, 100);
        OrquitaSimulada orquita = new OrquitaSimulada(robot, salida);
        enlace.start();
        assertEquals(List.of("CONFIRMACION", "ORDEN|AVANZAR|400"), orquita.enviar(trama(todo(3000)), 2));
        enlace.join(3000); // La Orquita deja de transmitir.
        assertFalse(enlace.isAlive());
        assertEquals(EstadoEnlace.DESCONECTADO, leer(enlace, "estado"));
        try { robot.close(); } catch (IOException e) { /* Ya no importa. */ }
    }
}
