// Archivo de simulacion
package orquitas.servidor.comunicacion;

import orquitas.servidor.captura.Recopilador;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Adaptador TCP de La Linareada; será reemplazado por Bluetooth. */
public class ReceptorIngestaTCP {
    private final int puerto;
    private final Recopilador recopilador;
    private final Set<Socket> clientes = new HashSet<>();
    private final Set<Thread> manejadores = new HashSet<>();
    private volatile boolean activo = true;
    private volatile ServerSocket servidor;

    public ReceptorIngestaTCP(int puerto, Recopilador recopilador) {
        this.puerto = puerto;
        this.recopilador = recopilador;
    }

    public void iniciar() {
        try (ServerSocket abierto = new ServerSocket(puerto)) {
            servidor = abierto;
            System.out.println("[ReceptorIngestaTCP] Puerto " + abierto.getLocalPort());
            while (activo) {
                Socket cliente = abierto.accept();
                synchronized (clientes) {
                    if (!activo) { cliente.close(); break; }
                    clientes.add(cliente);
                    Thread manejador = new Thread(() -> manejarCliente(cliente), "ReceptorCapturaTCP");
                    manejadores.add(manejador);
                    manejador.start();
                }
            }
        } catch (IOException e) {
            if (activo) System.err.println("[ReceptorIngestaTCP] " + e.getMessage());
        } finally {
            detener();
        }
    }

    public int obtenerPuerto() {
        return servidor == null ? -1 : servidor.getLocalPort();
    }

    public void detener() {
        activo = false;
        try {
            if (servidor != null) servidor.close();
        } catch (IOException e) {
            System.err.println("[ReceptorIngestaTCP] " + e.getMessage());
        }
        Thread[] porEsperar;
        synchronized (clientes) {
            for (Socket cliente : clientes) {
                try { cliente.close(); } catch (IOException e) {
                    System.err.println("[ReceptorIngestaTCP] " + e.getMessage());
                }
            }
            porEsperar = manejadores.toArray(new Thread[0]);
        }
        for (Thread manejador : porEsperar) {
            try { manejador.join(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void manejarCliente(Socket cliente) {
        try (cliente; Reader entrada = new InputStreamReader(cliente.getInputStream(), StandardCharsets.UTF_8)) {
            StringBuilder mensaje = new StringBuilder();
            boolean excesivo = false;
            int caracter;
            while (activo && (caracter = entrada.read()) != -1) {
                if (caracter == '\n') {
                    if (!excesivo) {
                        String linea = mensaje.toString();
                        if (linea.endsWith("\r")) linea = linea.substring(0, linea.length() - 1);
                        recopilador.procesarRecopilacion(linea);
                    }
                    mensaje.setLength(0);
                    excesivo = false;
                } else if (mensaje.length() < 256) {
                    mensaje.append((char) caracter);
                } else {
                    excesivo = true;
                }
            }
            // EOF sin '\n': trama incompleta; nunca se acepta como captura.
        } catch (IOException e) {
            if (activo) System.err.println("[ReceptorIngestaTCP] Conexión interrumpida: " + e.getMessage());
        } finally {
            synchronized (clientes) {
                clientes.remove(cliente);
                manejadores.remove(Thread.currentThread());
            }
        }
    }
}
