package orquitas.servidor.persistencia;

import orquitas.servidor.captura.Captura;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Semaphore;

/** CU-09/CU-10. Una única instancia compartida por escritor y lector. */
public class ArchivoEstomago {
    private final String rutaArchivo;
    private final Semaphore mutex = new Semaphore(1, true);
    private final Object controlEscrituras = new Object();
    // Coordinación de trabajo pendiente; NO es la cantidad de Lobitos capturados.
    private int escriturasPendientes;

    public ArchivoEstomago(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    public void anunciarEscritura() {
        synchronized (controlEscrituras) {
            escriturasPendientes++;
        }
    }

    public void finalizarEscritura() {
        synchronized (controlEscrituras) {
            if (escriturasPendientes <= 0) throw new IllegalStateException("Sin escritura pendiente");
            escriturasPendientes--;
            controlEscrituras.notifyAll();
        }
    }

    public boolean escribirRegistro(Captura captura) throws IOException, InterruptedException {
        mutex.acquire();
        try {
            // El archivo sigue siendo la autoridad, incluso ante una retransmisión.
            for (Captura registrada : leerDelArchivo()) {
                if (registrada.getIdCaptura().equals(captura.getIdCaptura())) return false;
            }
            // BufferedWriter propaga errores de escritura/cierre; PrintWriter los ocultaba.
            try (BufferedWriter salida = Files.newBufferedWriter(Path.of(rutaArchivo),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                salida.write(captura.aLinea());
                salida.newLine();
            }
            return true; // Confirmación solamente después del cierre correcto.
        } finally {
            mutex.release(); // CRITICAL: se libera también si falla el archivo.
        }
    }

    public List<Captura> leerRegistros() throws IOException, InterruptedException {
        synchronized (controlEscrituras) {
            while (escriturasPendientes > 0) controlEscrituras.wait();
            // La admisión y la toma del permiso son atómicas respecto a nuevos avisos.
            mutex.acquire();
        }
        try {
            return leerDelArchivo();
        } finally {
            mutex.release();
        }
    }

    private List<Captura> leerDelArchivo() throws IOException {
        List<String> lineas;
        try {
            lineas = Files.readAllLines(Path.of(rutaArchivo), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return new ArrayList<>(); // Una misión nueva todavía no tiene capturas.
        }
        var unicas = new LinkedHashMap<String, Captura>();
        for (String linea : lineas) {
            Captura captura = Captura.desdeLinea(linea);
            if (captura != null) unicas.putIfAbsent(captura.getIdCaptura(), captura);
        }
        return new ArrayList<>(unicas.values());
    }
}
