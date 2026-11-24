package orquitas.herramientas;

import com.fazecast.jSerialComm.SerialPort;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.RedPercepcion;

/**
 * Toma guiada de datos etiquetados (pendiente 1 del traspaso; herramienta, no forma parte de los diagramas).
 * Uso: {@code TomaGuiada <COMx> <percepcion.nnet> <carpeta> [--desde=N]}.
 * <p>
 * El robot queda quieto, como en PruebaDeteccion (CONFIRMACION + ORDEN|DETENER a cada TELEMETRIA y LATIDO
 * cada 1 s). La herramienta recorre la lista de escenas y, para cada una:
 * <ol>
 *   <li>Muestra qué poner y dónde, y espera Enter (S = saltear). Mientras espera no guarda nada.</li>
 *   <li>Con Enter descarta el barrido en curso (pudo empezar antes de acomodar la escena) y guarda los
 *       3 barridos completos siguientes con la nota de la escena, en el mismo formato que deteccion.csv.</li>
 *   <li>Muestra un resumen de lo que vio la red y deja elegir: Enter = siguiente, R = repetir, S = saltear.
 *       Con R o S se borran del CSV los barridos de esa escena.</li>
 * </ol>
 * Cada barrido se escribe en el CSV apenas llega: si se corta, se sigue con {@code --desde=N} en la misma carpeta.
 * Los mensajes de consola van sin tildes (la consola de Windows las muestra mal).
 */
public class TomaGuiada {
    static final int BARRIDOS_POR_ESCENA = 3;
    private static final long PERIODO_LATIDO_MS = 1000;

    /** Una escena: la nota exacta (ver NotaEscena) y la instrucción para el que la arma. */
    record Escena(String nota, String instruccion) { }

    enum Estado { INSTRUCCION, DESCARTANDO, GUARDANDO, ELIGIENDO, TERMINADO }

    static final List<Escena> ESCENAS = List.of(
            // A. Lobito solo.
            cosa("lobito 0 5", "una CANICA", 0, 5),
            cosa("lobito 0 10", "una CANICA", 0, 10),
            cosa("lobito 0 20", "una CANICA", 0, 20),
            cosa("lobito 0 30", "una CANICA", 0, 30),
            cosa("lobito +30 15", "una CANICA", 30, 15),
            cosa("lobito -45 20", "una CANICA", -45, 20),
            // B. Obstáculo solo.
            cosa("obstaculo 0 10", "un OBSTACULO", 0, 10),
            cosa("obstaculo 0 20", "un OBSTACULO", 0, 20),
            cosa("obstaculo 0 35", "un OBSTACULO", 0, 35),
            cosa("obstaculo +30 15", "un OBSTACULO", 30, 15),
            cosa("obstaculo -45 20", "un OBSTACULO", -45, 20),
            cosa("obstaculo +60 25", "un OBSTACULO", 60, 25),
            // C. Salida (robot mirando al estrecho, sin canicas ni obstáculos).
            new Escena("salida -15 +15", "Pone el robot mirando DERECHO A LA SALIDA (el estrecho), a unos 30 cm de la boca."
                    + " Sin canicas ni obstaculos."),
            new Escena("salida -15 +15", "Pone el robot mirando DERECHO A LA SALIDA (el estrecho), a unos 60 cm de la boca."
                    + " Sin canicas ni obstaculos."),
            new Escena("salida +30 +60", "Gira el robot para que la SALIDA le quede a la IZQUIERDA (entre 30 y 60 grados)."
                    + " Sin canicas ni obstaculos."),
            new Escena("salida -60 -30", "Gira el robot para que la SALIDA le quede a la DERECHA (entre 30 y 60 grados)."
                    + " Sin canicas ni obstaculos."),
            new Escena("salida -30 +30", "Pone el robot justo EN LA BOCA de la salida, mirando hacia afuera."
                    + " Sin canicas ni obstaculos."),
            // D. Juntos.
            juntas("lobito 0 15 ; obstaculo +30 20", 0, 15, 30, 20),
            juntas("lobito -30 20 ; obstaculo +15 15", -30, 20, 15, 15),
            juntas("lobito 0 20 ; obstaculo 0 40", 0, 20, 0, 40),
            juntas("lobito +45 15 ; obstaculo -45 15", 45, 15, -45, 15),
            juntas("lobito 0 10 ; obstaculo -30 10", 0, 10, -30, 10),
            // E. Vacío (solo paredes).
            vacio(1), vacio(2), vacio(3));

    private record Fila(int[] t, EtiquetaSector[] et) { }

    private final List<Escena> escenas;
    private final RedPercepcion red;
    private final Path csv;
    private final PrintStream out;
    private int indice;
    private Estado estado = Estado.INSTRUCCION;
    private int recibidos;
    private int guardadas;
    private final List<Fila> tomadas = new ArrayList<>();
    /** Tamaño del CSV antes de la escena en curso (-1 si no existía): para borrarla con R o S. */
    private long tamanoAntes;

    TomaGuiada(List<Escena> escenas, int desde, RedPercepcion red, Path csv, PrintStream out) {
        if (desde < 1 || desde > escenas.size())
            throw new IllegalArgumentException("La escena inicial tiene que estar entre 1 y " + escenas.size());
        this.escenas = escenas;
        this.red = red;
        this.csv = csv;
        this.out = out;
        this.indice = desde - 1;
        mostrarInstruccion();
    }

    // ---------------------------------------------------------------- Eventos

    /** Una línea del teclado (Enter = línea vacía). */
    synchronized void tecla(String linea) throws IOException {
        String c = linea == null ? "" : linea.trim().toLowerCase(Locale.ROOT);
        switch (estado) {
            case INSTRUCCION -> {
                if (c.isEmpty()) {
                    estado = Estado.DESCARTANDO;
                    out.println();
                    out.println(">> Listo. Descarto el barrido que esta en curso y guardo los " + BARRIDOS_POR_ESCENA
                            + " siguientes. No muevas nada.");
                } else if (c.equals("s")) {
                    out.println(">> Escena " + numero() + " salteada.");
                    siguiente();
                } else {
                    out.println(">> Apreta Enter cuando la escena este lista (o S y Enter para saltearla).");
                }
            }
            case DESCARTANDO, GUARDANDO -> out.println(">> Espera: estoy tomando los barridos de esta escena ("
                    + tomadas.size() + "/" + BARRIDOS_POR_ESCENA + ").");
            case ELIGIENDO -> {
                if (c.isEmpty()) {
                    guardadas++;
                    out.println(">> Escena " + numero() + " guardada.");
                    siguiente();
                } else if (c.equals("r")) {
                    borrarEscena();
                    out.println(">> Repetimos la escena " + numero() + " (lo de recien se borro).");
                    estado = Estado.INSTRUCCION;
                    mostrarInstruccion();
                } else if (c.equals("s")) {
                    borrarEscena();
                    out.println(">> Escena " + numero() + " salteada (lo de recien se borro).");
                    siguiente();
                } else {
                    out.println(">> Enter = guardar y seguir, R = repetir, S = saltear.");
                }
            }
            case TERMINADO -> { }
        }
    }

    /** Un barrido completo que llegó del robot. */
    synchronized void telemetria(int[] t) throws IOException {
        recibidos++;
        switch (estado) {
            case INSTRUCCION, ELIGIENDO -> {
                out.print('.'); // Cada punto: un barrido que llegó y no se guarda.
                out.flush();
            }
            case DESCARTANDO -> {
                out.println(">> Barrido en curso descartado (empezo antes de acomodar la escena).");
                tamanoAntes = Files.exists(csv) ? Files.size(csv) : -1;
                estado = Estado.GUARDANDO;
            }
            case GUARDANDO -> {
                double[] p = red.probabilidades(t);
                EtiquetaSector[] et = RedPercepcion.etiquetas(p);
                PruebaDeteccion.guardar(csv, recibidos, nota(), t, p, et);
                tomadas.add(new Fila(t.clone(), et));
                out.println(">> Guardado " + tomadas.size() + "/" + BARRIDOS_POR_ESCENA + "   " + lineaCorta(t, et));
                if (tomadas.size() == BARRIDOS_POR_ESCENA) {
                    out.print(resumen());
                    out.println(">> Enter = guardar y seguir   |   R + Enter = repetir   |   S + Enter = saltear");
                    estado = Estado.ELIGIENDO;
                }
            }
            case TERMINADO -> { }
        }
    }

    synchronized Estado estado() { return estado; }
    synchronized int numero() { return indice + 1; }
    synchronized boolean terminada() { return estado == Estado.TERMINADO; }
    synchronized int guardadas() { return guardadas; }

    // ---------------------------------------------------------------- Pasos

    private String nota() {
        return escenas.get(indice).nota();
    }

    private void siguiente() {
        tomadas.clear();
        indice++;
        if (indice >= escenas.size()) {
            estado = Estado.TERMINADO;
            out.println();
            out.println("==================================================================");
            out.println(" Listo: se guardaron " + guardadas + " escenas en " + csv.toAbsolutePath());
            out.println("==================================================================");
            return;
        }
        estado = Estado.INSTRUCCION;
        mostrarInstruccion();
    }

    private void mostrarInstruccion() {
        Escena e = escenas.get(indice);
        out.println();
        out.println("==================================================================");
        out.println(" Escena " + (indice + 1) + " de " + escenas.size() + "   (nota: " + e.nota() + ")");
        out.println(" " + e.instruccion());
        out.println(" Cuando este lista, apreta Enter.   (S + Enter = saltear esta escena)");
        out.println(" Cada punto que aparece abajo es un barrido que llega del robot (todavia no se guarda).");
        out.println("==================================================================");
    }

    /** Borra del CSV los barridos de la escena en curso (vuelve al tamaño que tenía antes). */
    private void borrarEscena() throws IOException {
        tomadas.clear();
        if (tamanoAntes < 0) {
            Files.deleteIfExists(csv);
        } else if (Files.exists(csv)) {
            try (FileChannel canal = FileChannel.open(csv, StandardOpenOption.WRITE)) {
                canal.truncate(tamanoAntes);
            }
        }
    }

    // ---------------------------------------------------------------- Resumen

    private static String lado(int sector) {
        return NotaEscena.lado(NotaEscena.angulo(sector));
    }

    /** Una línea por barrido: dónde ve LOBITO la red y cuántos sectores salieron sin dato. */
    static String lineaCorta(int[] t, EtiquetaSector[] et) {
        List<String> lobitos = new ArrayList<>();
        int sinDato = 0;
        for (int s = NotaEscena.SECTORES - 1; s >= 0; s--) {
            if (et[s] == EtiquetaSector.LOBITO) lobitos.add(lado(s));
            if (t[s * 3] < 0 && t[s * 3 + 1] < 0 && t[s * 3 + 2] < 0) sinDato++;
        }
        return "la red ve LOBITO en: " + (lobitos.isEmpty() ? "ningun sector" : String.join(", ", lobitos))
                + "   |   sectores sin dato: " + sinDato + " de 13";
    }

    /** Resumen de los barridos de la escena: qué dijo la red donde está lo anotado y dónde vio lobitos de más. */
    private String resumen() {
        NotaEscena n = NotaEscena.leer(nota());
        NotaEscena.Tipo[] verdad = n.verdad(), vecinos = n.vecinos();
        int total = tomadas.size();
        StringBuilder sb = new StringBuilder();
        sb.append(">> Resumen de la escena ").append(numero()).append(" (").append(total).append(" barridos):\n");
        for (int s = NotaEscena.SECTORES - 1; s >= 0; s--) {
            if (verdad[s] == null) continue;
            Map<EtiquetaSector, Integer> cuenta = new LinkedHashMap<>();
            for (Fila f : tomadas) cuenta.merge(f.et()[s], 1, Integer::sum);
            List<String> partes = new ArrayList<>();
            cuenta.forEach((e, k) -> partes.add(e + " " + k + " de " + total));
            sb.append("   - ").append(lado(s)).append(" (anotado ").append(verdad[s]).append("): la red dijo ")
              .append(String.join(", ", partes)).append('\n');
        }
        List<String> falsos = new ArrayList<>(), alLado = new ArrayList<>();
        double sinDato = 0;
        for (int s = NotaEscena.SECTORES - 1; s >= 0; s--) {
            int k = 0;
            for (Fila f : tomadas) if (f.et()[s] == EtiquetaSector.LOBITO) k++;
            if (k > 0 && verdad[s] != NotaEscena.Tipo.LOBITO) {
                String texto = lado(s) + " (" + k + " de " + total + ")";
                if (vecinos[s] == NotaEscena.Tipo.LOBITO) alLado.add(texto); else falsos.add(texto);
            }
        }
        for (Fila f : tomadas)
            for (int s = 0; s < NotaEscena.SECTORES; s++)
                if (f.t()[s * 3] < 0 && f.t()[s * 3 + 1] < 0 && f.t()[s * 3 + 2] < 0) sinDato++;
        sinDato /= total;
        sb.append("   - LOBITO donde no hay canica: ").append(falsos.isEmpty() ? "ninguno" : String.join(", ", falsos)).append('\n');
        if (!alLado.isEmpty()) sb.append("   - LOBITO al lado de la canica: ").append(String.join(", ", alLado)).append('\n');
        sb.append(String.format(Locale.ROOT, "   - Sectores sin dato: %.1f de 13 en promedio%n", sinDato));
        if (sinDato > 6)
            sb.append("   OJO: muchos sectores sin dato. Revisa el sensor (cables, pilas) y repeti la escena con R.\n");
        return sb.toString();
    }

    // ---------------------------------------------------------------- Lista de escenas

    private static String donde(int angulo, int cm) {
        String dir = angulo == 0 ? "DERECHO ADELANTE"
                : (angulo > 0 ? "a la IZQUIERDA (" + angulo + " grados)" : "a la DERECHA (" + (-angulo) + " grados)");
        return dir + ", a " + cm + " cm del sensor";
    }

    private static Escena cosa(String nota, String que, int angulo, int cm) {
        return new Escena(nota, "Pone " + que + " " + donde(angulo, cm) + ". Nada mas cerca del robot."
                + " (La distancia va desde el frente del sensor hasta el borde mas cercano.)");
    }

    private static Escena juntas(String nota, int aLobito, int cmLobito, int aObstaculo, int cmObstaculo) {
        return new Escena(nota, "Pone una CANICA " + donde(aLobito, cmLobito) + ", y un OBSTACULO "
                + donde(aObstaculo, cmObstaculo) + ".");
    }

    private static Escena vacio(int lugar) {
        return new Escena("vacio", "Saca todas las canicas y obstaculos. Pone el robot en un lugar de la bahia"
                + " (lugar " + lugar + " de 3, distinto de los otros), lejos de la salida. Que solo vea paredes.");
    }

    // ---------------------------------------------------------------- Programa

    public static void main(String[] args) throws Exception {
        List<String> posicionales = new ArrayList<>();
        int desde = 1;
        for (String a : args) {
            if (a.startsWith("--desde=")) desde = Integer.parseInt(a.substring(8).trim());
            else posicionales.add(a);
        }
        if (posicionales.size() < 3) {
            System.err.println("Uso: TomaGuiada <COMx> <percepcion.nnet> <carpeta> [--desde=N]");
            System.exit(2);
        }
        if (desde < 1 || desde > ESCENAS.size()) {
            System.err.println("--desde tiene que estar entre 1 y " + ESCENAS.size());
            System.exit(2);
        }
        RedPercepcion red = new RedPercepcion(Path.of(posicionales.get(1)));
        Path carpeta = Path.of(posicionales.get(2));
        Files.createDirectories(carpeta);
        Path csv = carpeta.resolve("deteccion.csv");
        SerialPort puerto = PruebaDeteccion.abrir(posicionales.get(0));
        if (puerto == null) System.exit(1);
        System.out.println("Toma guiada: " + ESCENAS.size() + " escenas, " + BARRIDOS_POR_ESCENA
                + " barridos por escena. El robot queda quieto. Ctrl+C para cortar.");
        System.out.println("Se guarda en " + csv.toAbsolutePath()
                + (desde > 1 ? " (sigo desde la escena " + desde + ")" : ""));
        TomaGuiada toma = new TomaGuiada(ESCENAS, desde, red, csv, System.out);
        Thread teclado = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) toma.tecla(l);
            } catch (IOException e) {
                System.out.println("Error con el teclado o el archivo: " + e.getMessage());
            }
        });
        teclado.setDaemon(true);
        teclado.start();
        try {
            atender(puerto.getInputStream(), puerto.getOutputStream(), toma);
        } finally {
            puerto.closePort();
        }
        System.exit(0);
    }

    /** Mismo protocolo que PruebaDeteccion: el robot queda quieto. Termina cuando se terminan las escenas. */
    static void atender(InputStream entrada, OutputStream salida, TomaGuiada toma) throws Exception {
        StringBuilder linea = new StringBuilder();
        long ultimoLatido = 0;
        while (!toma.terminada()) {
            long ahora = System.currentTimeMillis();
            if (ahora - ultimoLatido >= PERIODO_LATIDO_MS) {
                PruebaDeteccion.enviar(salida, "LATIDO");
                ultimoLatido = ahora;
            }
            if (entrada.available() == 0) {
                Thread.sleep(10);
                continue;
            }
            int dato = entrada.read();
            if (dato < 0) throw new IOException("el canal se cerro");
            if (dato != '\n') {
                if (linea.length() < 400) linea.append((char) dato);
                continue;
            }
            String trama = linea.toString().replace("\r", "");
            linea.setLength(0);
            int[] t = PruebaDeteccion.telemetria(trama);
            if (t != null) {
                PruebaDeteccion.enviar(salida, "CONFIRMACION");
                PruebaDeteccion.enviar(salida, "ORDEN|DETENER");
                toma.telemetria(t);
            } else if (trama.startsWith("CAPTURA|")) {
                PruebaDeteccion.enviar(salida, "CONFIRMACION");
            } else if (!trama.isBlank()) {
                PruebaDeteccion.enviar(salida, "RECHAZO");
            }
        }
    }
}
