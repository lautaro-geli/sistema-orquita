// Archivo de simulacion
package orquitas.cliente;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Simulador de ingesta de Lobitos por socket TCP.
 *
 * Según la consigna del hito, el simulador NO requiere documentación
 * (no tiene diseño POO ni UML asociado): es una rutina simple que abre
 * una conexión TCP hacia el Servidor y envía el mensaje de captura según
 * el protocolo acordado, simulando lo que en el sistema final haría la
 * Orquita real vía Bluetooth.
 *
 * Protocolo de mensajes: CAPTURA|&lt;idCaptura&gt;|&lt;timestamp ISO-8601&gt;
 *
 * Uso:
 *   java orquitas.cliente.SimuladorIngestaTCP [host] [puerto] [cantidadCapturas] [delayMs]
 *
 * Por defecto: host=localhost, puerto=5000, cantidadCapturas=3, delayMs=1000.
 * Cada captura se envía en una conexión TCP nueva y separada, para además
 * ejercitar la concurrencia del lado del Servidor (varias conexiones
 * entrantes pudiendo solaparse en el tiempo).
 */
public class SimuladorIngestaTCP {

    public static void main(String[] args) throws InterruptedException {
        String host = args.length > 0 ? args[0] : "localhost";
        int puerto = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        int cantidadCapturas = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        long delayMs = args.length > 3 ? Long.parseLong(args[3]) : 1000;

        System.out.println("[SimuladorIngestaTCP] Enviando " + cantidadCapturas
                + " captura(s) simulada(s) a " + host + ":" + puerto);

        for (int i = 1; i <= cantidadCapturas; i++) {
            String idCaptura = String.format("%03d", i);
            String timestamp = LocalDateTime.now().toString();
            String mensaje = "CAPTURA|" + idCaptura + "|" + timestamp;

            enviarMensaje(host, puerto, mensaje);

            if (i < cantidadCapturas) {
                Thread.sleep(delayMs);
            }
        }

        System.out.println("[SimuladorIngestaTCP] Simulación finalizada.");
    }

    private static void enviarMensaje(String host, int puerto, String mensaje) {
        try (Socket socket = new Socket(host, puerto);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            out.println(mensaje);
            if (out.checkError()) throw new IOException("Falló el envío TCP");
            System.out.println("[SimuladorIngestaTCP] Enviado: " + mensaje);
        } catch (IOException e) {
            System.err.println("[SimuladorIngestaTCP] No se pudo enviar el mensaje '" + mensaje
                    + "': " + e.getMessage());
        }
    }
}
