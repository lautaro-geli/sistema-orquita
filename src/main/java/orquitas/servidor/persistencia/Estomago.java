package orquitas.servidor.persistencia;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * CU-09 y CU-10. Archivo estomago.txt: una línea "identificador;fechaISO" por captura. Es la única
 * fuente del conteo de capturas (nunca una variable en memoria). Escritores y Auditor acceden a él
 * dentro de la sección crítica protegida por un semáforo justo.
 */
public class Estomago {
    private final String rutaArchivo;
    private final Semaphore mutex = new Semaphore(1, true);

    public Estomago(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    /**
     * CU-09. Crea y arranca un Escritor para la captura y espera su resultado: devuelve true si la
     * captura quedó en el archivo (nueva o repetida) y false si no se pudo escribir.
     */
    public boolean registrarCaptura(String identificador) {
        Escritor escritor = new Escritor(this, identificador);
        escritor.start();
        return escritor.esperarResultado();
    }

    /**
     * Sección crítica de escritura: si el identificador ya está en el archivo no escribe (captura
     * repetida); si es nuevo agrega su línea al final. Propaga IOException; el mutex se libera siempre.
     */
    public void escribirRegistro(String identificador) throws IOException, InterruptedException {
        mutex.acquire();
        try {
            for (String linea : leerDelArchivo()) {
                if (identificador.equals(identificadorDeLinea(linea))) return; // Captura repetida.
            }
            try (BufferedWriter salida = Files.newBufferedWriter(Path.of(rutaArchivo), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                salida.write(armarLinea(identificador));
                salida.write('\n');
                salida.flush();
            }
        } finally {
            mutex.release();
        }
    }

    /** CU-10. Cuenta, en una sola sección crítica, las líneas con formato válido. Archivo inexistente: 0. */
    public int contarRegistrosValidos() throws IOException, InterruptedException {
        mutex.acquire();
        try {
            List<String> identificadores = new ArrayList<>();
            for (String linea : leerDelArchivo()) {
                String id = identificadorDeLinea(linea);
                if (id != null && !identificadores.contains(id)) identificadores.add(id);
            }
            return identificadores.size();
        } finally {
            mutex.release();
        }
    }

    /** Vacía el archivo: se llama al arrancar el Servidor (la misión empieza de cero). */
    public void reiniciar() throws IOException, InterruptedException {
        mutex.acquire();
        try {
            Files.write(Path.of(rutaArchivo), new byte[0]);
        } finally {
            mutex.release();
        }
    }

    /** Líneas del archivo tal como están; se llama siempre con el mutex tomado. */
    private List<String> leerDelArchivo() throws IOException {
        try {
            return Files.readAllLines(Path.of(rutaArchivo), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return new ArrayList<>(); // Misión nueva: todavía no hay capturas.
        }
    }

    /** "identificador;fechaISO" con la hora del Servidor (el Arduino no tiene reloj). */
    private String armarLinea(String identificador) {
        return identificador + ";" + LocalDateTime.now();
    }

    /** Identificador de una línea válida, o null si la línea está vacía, cortada o alterada. */
    private static String identificadorDeLinea(String linea) {
        if (linea == null) return null;
        String[] partes = linea.split(";", -1);
        if (partes.length != 2 || !partes[0].matches("[A-Za-z0-9_-]{1,16}")) return null;
        try {
            LocalDateTime.parse(partes[1]);
            return partes[0];
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
