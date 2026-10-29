// Archivo de simulacion
package orquitas.servidor.comunicacion;

import orquitas.servidor.captura.Recopilacion;
import orquitas.servidor.navegacion.Navegacion;
import orquitas.servidor.persistencia.Estomago;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Adaptador TCP del Servidor: arma las tramas, vigila la inactividad y cierra la conexión; será reemplazado por Bluetooth. */
public class CanalIngestaTCP {
    /** Sin trafico durante este multiplo de TIMEOUT_MAXIMO (5 s -> 30 s) se cierra la conexion. */
    private static final int FACTOR_CIERRE_DURO = 6;
    private final int puerto;
    private final Estomago estomago;
    private final Navegacion navegacion;
    private final long timeoutMaximo;
    private final Set<Socket> clientes = new HashSet<>();
    private final Set<Thread> manejadores = new HashSet<>();
    private volatile boolean activo = true;
    private volatile ServerSocket servidor;

    public CanalIngestaTCP(int puerto, Estomago estomago, Navegacion navegacion) {
        this(puerto, estomago, navegacion, EnlaceOrquita.TIMEOUT_MAXIMO);
    }

    /** timeoutMaximo en ms; parametro para pruebas, debe ser menor a los 10 s del reglamento. */
    public CanalIngestaTCP(int puerto, Estomago estomago, Navegacion navegacion, long timeoutMaximo) {
        if (timeoutMaximo <= 0 || timeoutMaximo >= 10000)
            throw new IllegalArgumentException("TIMEOUT_MAXIMO debe estar entre 1 y 9999 ms");
        this.puerto = puerto;
        this.estomago = estomago;
        this.navegacion = navegacion;
        this.timeoutMaximo = timeoutMaximo;
    }

    public void iniciar() {
        try (ServerSocket abierto = new ServerSocket(puerto)) {
            servidor = abierto;
            System.out.println("[CanalIngestaTCP] Puerto " + abierto.getLocalPort());
            while (activo) {
                Socket cliente = abierto.accept();
                synchronized (clientes) {
                    if (!activo) { cliente.close(); break; }
                    clientes.add(cliente);
                    Thread manejador = new Thread(() -> manejarCliente(cliente), "ConexionOrquita");
                    manejadores.add(manejador);
                    manejador.start();
                }
            }
        } catch (IOException e) {
            if (activo) System.err.println("[CanalIngestaTCP] " + e.getMessage());
        } finally {
            detener();
        }
    }

    public int obtenerPuerto() {
        return servidor == null ? -1 : servidor.getLocalPort();
    }

    /** Clientes ya aceptados y registrados; no cuenta conexiones en la cola del SO. */
    public int obtenerCantidadClientes() {
        synchronized (clientes) { return clientes.size(); }
    }

    public void detener() {
        activo = false;
        try {
            if (servidor != null) servidor.close();
        } catch (IOException e) {
            System.err.println("[CanalIngestaTCP] " + e.getMessage());
        }
        Thread[] porEsperar;
        synchronized (clientes) {
            for (Socket cliente : clientes) {
                try { cliente.close(); } catch (IOException e) {
                    System.err.println("[CanalIngestaTCP] " + e.getMessage());
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

    /** CU-12. Devuelve false cuando corresponde el cierre duro de la conexion. */
    private boolean verificarRecepcion(EnlaceOrquita enlace) {
        long tiempoSinRecepcion = enlace.calcularTiempoSinRecepcion();
        if (tiempoSinRecepcion < timeoutMaximo) {
            enlace.continuarOperacionNormal();
            return true;
        }
        enlace.registrarFalla();
        enlace.enviarSenalReconexion(); // Se repite en cada vencimiento hasta recibir trafico.
        return tiempoSinRecepcion < timeoutMaximo * FACTOR_CIERRE_DURO;
    }

    private void manejarCliente(Socket cliente) {
        try (cliente; Reader entrada = new InputStreamReader(cliente.getInputStream(), StandardCharsets.UTF_8)) {
            // Lectura con plazo corto (<= TIMEOUT_MAXIMO) para evaluar la inactividad (CU-12).
            cliente.setSoTimeout((int) Math.max(1, Math.min(1000, timeoutMaximo / 5)));
            EnlaceOrquita enlace = new EnlaceOrquita(estomago, navegacion,
                    new java.io.OutputStreamWriter(cliente.getOutputStream(), StandardCharsets.UTF_8));
            StringBuilder mensaje = new StringBuilder();
            boolean excesivo = false;
            while (activo) {
                int caracter;
                try {
                    caracter = entrada.read();
                } catch (SocketTimeoutException sinDatos) {
                    if (verificarRecepcion(enlace)) continue;
                    System.err.println("[CanalIngestaTCP] Cierre duro: sin trafico tras las senales de reconexion");
                    break;
                }
                if (caracter == -1) break;
                if (caracter == '\n') {
                    if (!excesivo) {
                        String linea = mensaje.toString();
                        if (linea.endsWith("\r")) linea = linea.substring(0, linea.length() - 1);
                        enlace.enviarRecopilacion(linea);
                    } else {
                        enlace.enviarErrorTrama();
                    }
                    mensaje.setLength(0);
                    excesivo = false;
                } else if (mensaje.length() < Recopilacion.LARGO_MAXIMO) {
                    mensaje.append((char) caracter);
                } else {
                    excesivo = true;
                }
            }
            // EOF sin '\n': trama incompleta; nunca se acepta como captura.
        } catch (IOException e) {
            if (activo) System.err.println("[CanalIngestaTCP] Conexión interrumpida: " + e.getMessage());
        } finally {
            synchronized (clientes) {
                clientes.remove(cliente);
                manejadores.remove(Thread.currentThread());
            }
        }
    }
}
