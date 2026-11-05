package orquitas.servidor.comunicacion;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.Navegacion;
import orquitas.servidor.persistencia.Estomago;

/**
 * CU-06, CU-08 y CU-12. Hilo que atiende el enlace Bluetooth (RFCOMM/SPP) con la Orquita: recibe y
 * decodifica tramas, responde CONFIRMACION, RECHAZO u ORDEN, envía el LATIDO cada PERIODO_LATIDO_MS y
 * supervisa el silencio. El emparejamiento del HC-06 y la creación del puerto COM son manuales (los
 * hace Windows); acá solo se abre el puerto ya creado. Si el enlace falla, registrarFalla() lo deja
 * DESCONECTADO y el hilo termina: se restablece a mano (vincular de nuevo y reiniciar el Servidor).
 */
public class EnlaceOrquita extends Thread {
    /** Silencio máximo de la Orquita antes de declarar la falla del enlace. */
    public static final long TIMEOUT_MAXIMO_MS = 15000;
    /** Período del LATIDO y espera máxima de cada lectura. */
    public static final long PERIODO_LATIDO_MS = 1000;
    /** Caracteres por línea, sin contar el fin de línea. */
    public static final int LARGO_MAXIMO = 320;
    /** Valores de una TELEMETRIA: 13 sectores x 3 ROI. */
    public static final int VALORES = 39;
    /** Valor que envía la Orquita cuando un ROI no recibió eco. */
    public static final int SIN_ECO = -1;
    /** Distancia máxima en mm; reemplaza a SIN_ECO en la telemetría decodificada. */
    public static final int ALCANCE_MM = 4000;

    private static final String CAPTURA = "CAPTURA";
    private static final String TELEMETRIA = "TELEMETRIA";
    private static final String INVALIDA = "INVALIDA";
    private static final int BAUDIOS = 9600;
    private static final long ESPERA_SONDEO_MS = 10;

    private final String nombrePuerto;
    private final InputStream entrada;
    private final OutputStream salida;
    private final Estomago estomago;
    private final Navegacion navegacion;
    /** Puerto de jSerialComm; null cuando el enlace se arma sobre streams (pruebas). */
    private final SerialPort puerto;
    private final long timeoutMaximoMs;
    private final long periodoLatidoMs;
    private volatile EstadoEnlace estado = EstadoEnlace.CONECTADO;
    private volatile long tiempoUltimaRecepcion = System.currentTimeMillis();
    private long tiempoUltimoLatido = System.currentTimeMillis();
    private String identificadorCaptura;
    private int[] telemetria = new int[VALORES];
    private volatile boolean sistemaActivo = true;
    /** Línea en armado entre lecturas (una trama puede llegar cortada). */
    private final StringBuilder lineaPendiente = new StringBuilder();
    private String causaFalla;

    /**
     * Abre el puerto serie ya creado por Windows (por ejemplo COM5) a 9600 baudios, con lectura
     * semibloqueante de hasta PERIODO_LATIDO_MS.
     *
     * @throws IllegalStateException si el puerto no existe o no se puede abrir
     */
    public EnlaceOrquita(String nombrePuerto, Estomago estomago, Navegacion navegacion) {
        super("EnlaceOrquita");
        this.nombrePuerto = nombrePuerto;
        this.estomago = Objects.requireNonNull(estomago);
        this.navegacion = Objects.requireNonNull(navegacion);
        this.timeoutMaximoMs = TIMEOUT_MAXIMO_MS;
        this.periodoLatidoMs = PERIODO_LATIDO_MS;
        try {
            puerto = SerialPort.getCommPort(nombrePuerto);
        } catch (SerialPortInvalidPortException e) {
            throw new IllegalStateException(mensajeSinPuerto(nombrePuerto, e.getMessage()), e);
        }
        puerto.setBaudRate(BAUDIOS);
        puerto.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, (int) PERIODO_LATIDO_MS, 0);
        if (!puerto.openPort())
            throw new IllegalStateException(mensajeSinPuerto(nombrePuerto, "código de error " + puerto.getLastErrorCode()));
        entrada = puerto.getInputStream();
        salida = puerto.getOutputStream();
    }

    /** Solo para pruebas: el enlace se arma sobre streams y con tiempos chicos. */
    EnlaceOrquita(InputStream entrada, OutputStream salida, Estomago estomago, Navegacion navegacion,
                  long timeoutMaximoMs, long periodoLatidoMs) {
        super("EnlaceOrquita-prueba");
        this.nombrePuerto = "prueba";
        this.entrada = Objects.requireNonNull(entrada);
        this.salida = Objects.requireNonNull(salida);
        this.estomago = Objects.requireNonNull(estomago);
        this.navegacion = Objects.requireNonNull(navegacion);
        this.puerto = null;
        this.timeoutMaximoMs = timeoutMaximoMs;
        this.periodoLatidoMs = periodoLatidoMs;
    }

    private static String mensajeSinPuerto(String nombrePuerto, String causa) {
        return "No se pudo abrir el puerto " + nombrePuerto + " (" + causa + "). "
                + "¿Está emparejado el HC-06 y creado el puerto COM en Windows?";
    }

    /** CU-12. Recibe tramas, supervisa el silencio y envía el LATIDO hasta que el enlace falle o se detenga. */
    @Override
    public void run() {
        System.out.println("[Enlace] Escuchando en " + nombrePuerto);
        while (sistemaActivo) {
            String trama;
            try {
                trama = leerTrama();
            } catch (IOException e) {
                if (!sistemaActivo) break; // Cierre ordenado con detener().
                causaFalla = "falló la lectura del puerto: " + e.getMessage();
                registrarFalla();
                break;
            }
            if (trama != null) recibirTrama(trama);
            if (!sistemaActivo) break;
            long tiempoSinRecepcion = calcularTiempoSinRecepcion();
            if (tiempoSinRecepcion > timeoutMaximoMs) {
                causaFalla = "sin recibir nada de la Orquita durante " + tiempoSinRecepcion + " ms";
                registrarFalla();
            } else if (System.currentTimeMillis() - tiempoUltimoLatido >= periodoLatidoMs) {
                enviarLatido();
            }
        }
    }

    /** CU-06. Decodifica la trama y responde según su tipo. */
    public void recibirTrama(String trama) {
        switch (decodificar(trama)) {
            case CAPTURA: {
                String identificador = identificadorCaptura;
                if (estomago.registrarCaptura(identificador)) { // CU-09: espera al Escritor.
                    System.out.println("[Captura] id=" + identificador + " anotada en el estómago");
                    enviarConfirmacion();
                } else {
                    enviarRechazo();
                }
                break;
            }
            case TELEMETRIA:
                enviarConfirmacion();
                navegacion.determinarMovimiento(telemetria); // CU-07
                enviarOrdenNavegacion(navegacion.obtenerMovimientoDecidido()); // CU-08
                break;
            default:
                enviarRechazo();
        }
    }

    /**
     * Valida la trama de forma estricta y guarda lo leído (el identificador o las 39 distancias, con
     * SIN_ECO ya reemplazado por ALCANCE_MM). Devuelve "CAPTURA", "TELEMETRIA" o "INVALIDA"; nunca
     * lanza excepciones por datos malos.
     */
    public String decodificar(String trama) {
        if (trama == null || trama.length() > LARGO_MAXIMO) return INVALIDA;
        int separador = trama.indexOf('|');
        if (separador < 0) return INVALIDA;
        String tipo = trama.substring(0, separador);
        String cuerpo = trama.substring(separador + 1);
        if (tipo.equals(CAPTURA)) {
            if (!cuerpo.matches("[A-Za-z0-9_-]{1,16}")) return INVALIDA;
            identificadorCaptura = cuerpo;
            return CAPTURA;
        }
        if (tipo.equals(TELEMETRIA)) {
            String[] campos = cuerpo.split(";", -1);
            if (campos.length != VALORES) return INVALIDA;
            int[] valores = new int[VALORES];
            for (int i = 0; i < VALORES; i++) {
                if (campos[i].equals(String.valueOf(SIN_ECO))) {
                    valores[i] = ALCANCE_MM;
                    continue;
                }
                if (!campos[i].matches("[0-9]{1,4}")) return INVALIDA; // Sin signos, espacios ni decimales.
                valores[i] = Integer.parseInt(campos[i]);
                if (valores[i] > ALCANCE_MM) return INVALIDA;
            }
            telemetria = valores;
            return TELEMETRIA;
        }
        return INVALIDA;
    }

    public void enviarConfirmacion() {
        transmitir("CONFIRMACION");
    }

    public void enviarRechazo() {
        transmitir("RECHAZO");
    }

    /** CU-08. Envía ORDEN|movimiento. */
    public void enviarOrdenNavegacion(Movimiento movimiento) {
        String orden = codificarOrden(movimiento);
        System.out.println("[Orden] " + movimiento);
        transmitir(orden);
    }

    /** CU-12. Señal periódica con la que la Orquita sabe que el Servidor sigue vivo. */
    public void enviarLatido() {
        tiempoUltimoLatido = System.currentTimeMillis();
        transmitir("LATIDO");
    }

    /** Milisegundos desde el último byte recibido de la Orquita. */
    public long calcularTiempoSinRecepcion() {
        return System.currentTimeMillis() - tiempoUltimaRecepcion;
    }

    /** Informa ERROR_SEGURO, deja el enlace DESCONECTADO y termina el hilo. No reabre el puerto. */
    public synchronized void registrarFalla() {
        if (estado == EstadoEnlace.DESCONECTADO) return;
        String causa = causaFalla != null ? causaFalla : "falla del enlace";
        System.out.println("[Estado] ERROR_SEGURO (" + causa + ")");
        System.out.println("[Enlace] Restablecer a mano: vincular de nuevo el HC-06 y reiniciar el Servidor.");
        estado = EstadoEnlace.DESCONECTADO;
        sistemaActivo = false;
    }

    /** Cierre ordenado: termina el bucle y cierra el puerto (o los streams en las pruebas). */
    public void detener() {
        sistemaActivo = false;
        if (puerto != null) {
            puerto.closePort();
            return;
        }
        try { entrada.close(); } catch (IOException e) { /* Ya cerrado. */ }
        try { salida.close(); } catch (IOException e) { /* Ya cerrado. */ }
    }

    /**
     * Espera una línea completa hasta periodoLatidoMs y anota la hora de cada byte recibido. Devuelve
     * null si no se completó ninguna. Una línea de más de LARGO_MAXIMO se descarta hasta el próximo fin
     * de línea y se devuelve recortada a LARGO_MAXIMO + 1 caracteres, para que decodificar la rechace.
     */
    private String leerTrama() throws IOException {
        long limite = System.currentTimeMillis() + periodoLatidoMs;
        while (sistemaActivo) {
            while (entrada.available() > 0) {
                int dato = entrada.read();
                if (dato < 0) throw new IOException("el canal se cerró");
                tiempoUltimaRecepcion = System.currentTimeMillis();
                if (dato == '\n') {
                    int largo = lineaPendiente.length();
                    if (largo > 0 && lineaPendiente.charAt(largo - 1) == '\r') lineaPendiente.setLength(largo - 1);
                    String linea = lineaPendiente.toString();
                    lineaPendiente.setLength(0);
                    return linea;
                }
                if (lineaPendiente.length() <= LARGO_MAXIMO) lineaPendiente.append((char) dato);
            }
            if (System.currentTimeMillis() >= limite) return null;
            try {
                Thread.sleep(ESPERA_SONDEO_MS);
            } catch (InterruptedException e) {
                sistemaActivo = false;
                return null;
            }
        }
        return null;
    }

    private String codificarOrden(Movimiento movimiento) {
        return "ORDEN|" + movimiento.name();
    }

    /** Escribe la línea con fin de línea y flush; si falla registra la falla del enlace. */
    private synchronized void transmitir(String trama) {
        if (estado == EstadoEnlace.DESCONECTADO) return; // La trama queda vencida.
        try {
            salida.write((trama + "\n").getBytes(StandardCharsets.US_ASCII));
            salida.flush();
        } catch (IOException e) {
            causaFalla = "falló el envío al puerto: " + e.getMessage();
            registrarFalla();
        }
    }
}
