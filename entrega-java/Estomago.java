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
import java.util.Objects;
import java.util.concurrent.Semaphore;

/** CU-09/CU-10. Una única instancia compartida por escritor y lector. */
public class Estomago {
    private final String rutaArchivo;
    private final Semaphore mutex = new Semaphore(1, true);
    private final Object controlEscrituras = new Object();
    // Escritores en curso, para el apagado ordenado (esperarEscrituras); NO es la cantidad de Lobitos
    // capturados ni da prioridad a las escrituras sobre las lecturas.
    private int escriturasPendientes;

    public Estomago(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    /** CU-09. Un hilo Escritor por captura; la recepción no espera al disco. */
    public void registrarCaptura(Captura captura) {
        Escritor escritor = new Escritor(this, Objects.requireNonNull(captura));
        anunciarEscritura();
        try {
            escritor.start(); // JVM invoca run() en el hilo escritor.
        } catch (RuntimeException | Error e) {
            finalizarEscritura();
            throw e;
        }
    }

    /** Bloquea hasta que terminen todos los Escritor iniciados (apagado y pruebas). */
    public void esperarEscrituras() throws InterruptedException {
        synchronized (controlEscrituras) {
            while (escriturasPendientes > 0) controlEscrituras.wait();
        }
    }

    void anunciarEscritura() {
        synchronized (controlEscrituras) {
            escriturasPendientes++;
        }
    }

    void finalizarEscritura() {
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
                if (registrada.getIdentificador().equals(captura.getIdentificador())) return false;
            }
            // BufferedWriter propaga los errores de escritura y de cierre.
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
        // Lecturas y escrituras comparten el mismo mutex justo (FIFO): el auditor espera a lo sumo las
        // escrituras que ya estaban en cola, nunca queda postergado. Si lee entre dos escrituras cuenta de
        // menos y vuelve a verificar en el proximo ciclo; no hay inconsistencia porque el archivo es la autoridad.
        mutex.acquire();
        try {
            return leerDelArchivo();
        } finally {
            mutex.release();
        }
    }

    /** Vacía estomago.txt: una misión nueva empieza sin capturas. Entra en la misma sección crítica que lecturas y escrituras. */
    public void reiniciar() throws IOException, InterruptedException {
        mutex.acquire();
        try {
            Files.deleteIfExists(Path.of(rutaArchivo));
        } finally {
            mutex.release();
        }
    }

    /** Cantidad de capturas de una lista obtenida con leerRegistros(), que ya trae solo las bien formadas y sin repetir. */
    public int contarRegistrosValidos(List<Captura> registros) {
        return registros.size();
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
            if (captura != null) unicas.putIfAbsent(captura.getIdentificador(), captura);
        }
        return new ArrayList<>(unicas.values());
    }
}
