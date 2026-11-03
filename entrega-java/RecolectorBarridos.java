package orquitas.herramientas;

import com.fazecast.jSerialComm.SerialPort;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;

/**
 * Herramienta de toma de datos (no forma parte de los diagramas). Uso:
 * {@code RecolectorBarridos <COMx> <ETIQUETA> <cantidad> [datos/barridos.csv]}.
 * Recibe las TELEMETRIA reales del robot, responde CONFIRMACION + ORDEN|DETENER (el robot no se mueve)
 * y LATIDO cada 1 s (no entra en Safe Mode), y agrega al CSV una fila ETIQUETA,v0,...,v38 con el -1 crudo.
 * Etiquetas: LIBRE, OBJETO, LOBITO, SALIDA. Colocar el robot en la escena antes de empezar.
 */
public class RecolectorBarridos {
    static final String[] ETIQUETAS = {"LIBRE", "OBJETO", "LOBITO", "SALIDA"};
    private static final int VALORES = 39;
    private static final long PERIODO_LATIDO_MS = 1000;
    private static final long ESPERA_SONDEO_MS = 10;
    private static final int LARGO_MAXIMO = 320;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Uso: RecolectorBarridos <COMx> <LIBRE|OBJETO|LOBITO|SALIDA> <cantidad> [datos/barridos.csv]");
            System.exit(2);
        }
        String etiqueta = args[1].toUpperCase();
        if (!java.util.Arrays.asList(ETIQUETAS).contains(etiqueta)) {
            System.err.println("Etiqueta inválida: " + args[1]);
            System.exit(2);
        }
        int cantidad = Integer.parseInt(args[2]);
        Path csv = Path.of(args.length > 3 ? args[3] : "datos/barridos.csv");
        SerialPort puerto = SerialPort.getCommPort(args[0]);
        puerto.setBaudRate(9600);
        puerto.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, (int) PERIODO_LATIDO_MS, 0);
        if (!puerto.openPort()) {
            System.err.println("No se pudo abrir " + args[0] + ". ¿Está emparejado el HC-06 y creado el puerto COM?");
            System.exit(1);
        }
        try {
            System.out.println("Recolectando " + cantidad + " barridos de " + etiqueta + " en " + csv);
            recolectar(puerto.getInputStream(), puerto.getOutputStream(), etiqueta, cantidad, csv, 15000);
        } finally {
            puerto.closePort();
        }
        System.out.println("Resumen de " + csv + ": " + resumir(csv));
    }

    /**
     * Atiende al robot hasta juntar la cantidad pedida. Termina con IOException si el robot calla más
     * de silencioMaximoMs.
     */
    static void recolectar(InputStream entrada, OutputStream salida, String etiqueta, int cantidad, Path csv,
                           long silencioMaximoMs) throws IOException, InterruptedException {
        Path carpeta = csv.toAbsolutePath().getParent();
        if (carpeta != null) Files.createDirectories(carpeta);
        StringBuilder linea = new StringBuilder();
        long ultimoLatido = 0, ultimaRecepcion = System.currentTimeMillis();
        int guardados = 0;
        while (guardados < cantidad) {
            long ahora = System.currentTimeMillis();
            if (ahora - ultimoLatido >= PERIODO_LATIDO_MS) {
                enviar(salida, "LATIDO");
                ultimoLatido = ahora;
            }
            if (ahora - ultimaRecepcion > silencioMaximoMs) throw new IOException("el robot dejó de transmitir");
            if (entrada.available() == 0) {
                Thread.sleep(ESPERA_SONDEO_MS);
                continue;
            }
            int dato = entrada.read();
            if (dato < 0) throw new IOException("el canal se cerró");
            ultimaRecepcion = System.currentTimeMillis();
            if (dato != '\n') {
                if (linea.length() <= LARGO_MAXIMO) linea.append((char) dato);
                continue;
            }
            String trama = linea.toString().replace("\r", "");
            linea.setLength(0);
            String valores = valoresDeTelemetria(trama);
            if (valores != null) {
                try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    w.write(etiqueta + "," + valores + "\n");
                }
                guardados++;
                System.out.println("[" + guardados + "/" + cantidad + "] " + etiqueta);
                enviar(salida, "CONFIRMACION");
                enviar(salida, "ORDEN|DETENER");
            } else if (trama.matches("CAPTURA\\|[A-Za-z0-9_-]{1,16}")) {
                enviar(salida, "CONFIRMACION"); // No se anota: solo evita que el robot reintente.
            } else {
                enviar(salida, "RECHAZO");
            }
        }
    }

    /** Los 39 valores separados por coma (con el -1 crudo), o null si la trama no es una TELEMETRIA válida. */
    static String valoresDeTelemetria(String trama) {
        if (!trama.startsWith("TELEMETRIA|")) return null;
        String[] campos = trama.substring(11).split(";", -1);
        if (campos.length != VALORES) return null;
        for (String c : campos) {
            if (c.equals("-1")) continue;
            if (!c.matches("[0-9]{1,4}") || Integer.parseInt(c) > 4000) return null;
        }
        return String.join(",", campos);
    }

    /** Cantidad de filas por etiqueta en el CSV. */
    static Map<String, Integer> resumir(Path csv) throws IOException {
        Map<String, Integer> conteo = new TreeMap<>();
        if (!Files.exists(csv)) return conteo;
        for (String fila : Files.readAllLines(csv, StandardCharsets.UTF_8)) {
            if (fila.isBlank()) continue;
            conteo.merge(fila.substring(0, fila.indexOf(',') < 0 ? fila.length() : fila.indexOf(',')), 1, Integer::sum);
        }
        return conteo;
    }

    private static void enviar(OutputStream salida, String linea) throws IOException {
        salida.write((linea + "\n").getBytes(StandardCharsets.US_ASCII));
        salida.flush();
    }
}
