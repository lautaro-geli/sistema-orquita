package orquitas.herramientas;

import com.fazecast.jSerialComm.SerialPort;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.RedPercepcion;

/**
 * Prueba de detección real (herramienta, no forma parte de los diagramas). Uso:
 * {@code PruebaDeteccion <COMx> <percepcion.nnet> [deteccion.csv]}.
 * El robot queda quieto (responde CONFIRMACION + ORDEN|DETENER a cada TELEMETRIA y LATIDO cada 1 s)
 * y por cada barrido muestra, sector por sector, las 3 distancias y lo que cree la red de percepción.
 * Lo que se escribe en la consola (y Enter) queda como nota de los barridos siguientes en el CSV,
 * por ejemplo "canica a 20 cm al frente": sirve después para medir aciertos con datos reales.
 */
public class PruebaDeteccion {
    private static final int SECTORES = 13, VALORES = 39;
    private static final long PERIODO_LATIDO_MS = 1000;
    private static volatile String nota = "";
    static final int INTENTOS_ABRIR = 6;
    static final long ESPERA_ENTRE_INTENTOS_MS = 3000;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Uso: PruebaDeteccion <COMx> <percepcion.nnet> [deteccion.csv]");
            System.exit(2);
        }
        RedPercepcion red = new RedPercepcion(Path.of(args[1]));
        Path csv = Path.of(args.length > 2 ? args[2] : "deteccion.csv");
        SerialPort puerto = abrir(args[0]);
        if (puerto == null) System.exit(1);
        Thread teclado = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) {
                    nota = l.trim().replace(',', ' ');
                    System.out.println(">> Nota para los proximos barridos: \"" + nota + "\"");
                    NotaEscena leida = NotaEscena.leer(nota);
                    System.out.println(">> Entiendo: " + leida.resumen());
                    for (String problema : leida.problemas())
                        System.out.println(">> OJO, revisa la nota: " + problema + ". Escribila de nuevo y Enter.");
                }
            } catch (IOException ignorada) { }
        });
        teclado.setDaemon(true);
        teclado.start();
        System.out.println("Conectado a " + args[0] + ". El robot queda quieto y barre una y otra vez.");
        System.out.println("Escribi que pusiste en la escena y Enter: tipo, angulo (+ a la izquierda, 0 al frente) y distancia en cm.");
        System.out.println("  lobito -15 20  |  obstaculo +30 15  |  salida -30 +15  |  obstaculo 0 15 y lobito -30 10");
        System.out.println("Esperar 3 barridos con cada nota. Ctrl+C para salir.");
        System.out.println("Se guarda todo en " + csv.toAbsolutePath());
        try {
            atender(puerto.getInputStream(), puerto.getOutputStream(), red, csv);
        } finally {
            puerto.closePort();
        }
    }

    /** Abre el puerto del HC-06 con reintentos (Windows a veces tarda en armar el enlace). null si no pudo. */
    static SerialPort abrir(String nombre) throws InterruptedException {
        SerialPort puerto = SerialPort.getCommPort(nombre);
        puerto.setBaudRate(9600);
        puerto.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 200, 0);
        for (int intento = 1; intento <= INTENTOS_ABRIR; intento++) {
            System.out.println("Abriendo " + nombre + " (intento " + intento + " de " + INTENTOS_ABRIR + ")...");
            if (puerto.openPort()) {
                System.out.println("Conectado.");
                return puerto;
            }
            if (intento < INTENTOS_ABRIR) Thread.sleep(ESPERA_ENTRE_INTENTOS_MS);
        }
        System.err.println("No se pudo abrir " + nombre + ". Revisar: robot prendido y LED del HC-06 parpadeando;"
                + " ninguna otra ventana (PuTTY, otro .bat) con el puerto abierto. Si el LED queda fijo sin conectar,"
                + " cortar la energia del robot unos segundos y volver a probar.");
        return null;
    }

    static void atender(InputStream entrada, OutputStream salida, RedPercepcion red, Path csv) throws Exception {
        StringBuilder linea = new StringBuilder();
        long ultimoLatido = 0;
        int barrido = 0;
        while (true) {
            long ahora = System.currentTimeMillis();
            if (ahora - ultimoLatido >= PERIODO_LATIDO_MS) {
                enviar(salida, "LATIDO");
                ultimoLatido = ahora;
            }
            if (entrada.available() == 0) {
                Thread.sleep(10);
                continue;
            }
            int dato = entrada.read();
            if (dato < 0) throw new IOException("el canal se cerró");
            if (dato != '\n') {
                if (linea.length() < 400) linea.append((char) dato);
                continue;
            }
            String trama = linea.toString().replace("\r", "");
            linea.setLength(0);
            int[] t = telemetria(trama);
            if (t != null) {
                enviar(salida, "CONFIRMACION");
                enviar(salida, "ORDEN|DETENER");
                barrido++;
                double[] p = red.probabilidades(t);
                EtiquetaSector[] et = RedPercepcion.etiquetas(p);
                System.out.print(tabla(barrido, nota, t, p, et));
                guardar(csv, barrido, nota, t, p, et);
            } else if (trama.startsWith("CAPTURA|")) {
                enviar(salida, "CONFIRMACION");
                System.out.println("*** CAPTURA (el FC-51 detecto algo) ***");
            } else if (!trama.isBlank()) {
                enviar(salida, "RECHAZO");
            }
        }
    }

    static int[] telemetria(String trama) {
        if (!trama.startsWith("TELEMETRIA|")) return null;
        String[] c = trama.substring(11).split(";", -1);
        if (c.length != VALORES) return null;
        int[] t = new int[VALORES];
        try {
            for (int i = 0; i < VALORES; i++) t[i] = Integer.parseInt(c[i].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return t;
    }

    /** Tabla de un barrido, sector por sector, como se ve parado detrás del robot. */
    static String tabla(int barrido, String nota, int[] t, double[] p, EtiquetaSector[] et) {
        int sinDato = 0;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%n== Barrido %d (%s)%s%n", barrido, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                nota.isEmpty() ? "" : "  nota: " + nota));
        sb.append(" sector  angulo    4x4   8x8 16x16   la red ve\n");
        for (int s = SECTORES - 1; s >= 0; s--) { // De izquierda a derecha, como se ve parado detrás del robot.
            int a = 15 * s - 90;
            int v4 = t[s * 3], v8 = t[s * 3 + 1], v16 = t[s * 3 + 2];
            if (v4 < 0 && v8 < 0 && v16 < 0) sinDato++;
            double prob = p[s * RedPercepcion.ETIQUETAS + et[s].ordinal()];
            String lado = a == 0 ? "frente" : (a > 0 ? "izq " + a : "der " + (-a));
            sb.append(String.format("   %2d   %-8s %5s %5s %5s   %-9s %3.0f%%%s%n", s, lado, mm(v4), mm(v8), mm(v16),
                    et[s], 100 * prob, et[s] == EtiquetaSector.LOBITO ? "   <- LOBITO" : ""));
        }
        sb.append(String.format(" Sectores sin ningun dato: %d de 13%n", sinDato));
        return sb.toString();
    }

    private static String mm(int v) {
        return v < 0 ? "-" : Integer.toString(v);
    }

    /** Agrega un barrido al CSV (lo crea con encabezado si no existe). */
    static void guardar(Path csv, int barrido, String nota, int[] t, double[] p, EtiquetaSector[] et) throws IOException {
        boolean nuevo = !Files.exists(csv);
        try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (nuevo) {
                StringBuilder h = new StringBuilder("hora,barrido,nota");
                for (int i = 0; i < VALORES; i++) h.append(",v").append(i);
                for (int s = 0; s < SECTORES; s++) h.append(",etiqueta").append(s).append(",prob").append(s);
                w.write(h + "\n");
            }
            StringBuilder f = new StringBuilder(LocalTime.now().toString()).append(',').append(barrido).append(',').append(nota);
            for (int v : t) f.append(',').append(v);
            for (int s = 0; s < SECTORES; s++)
                f.append(',').append(et[s]).append(',').append(String.format(java.util.Locale.ROOT, "%.3f", p[s * RedPercepcion.ETIQUETAS + et[s].ordinal()]));
            w.write(f + "\n");
        }
    }

    static void enviar(OutputStream salida, String linea) throws IOException {
        salida.write((linea + "\n").getBytes(StandardCharsets.US_ASCII));
        salida.flush();
    }
}
