package orquitas.servidor.persistencia;

import java.io.IOException;

/** CU-09. Hilo de vida corta: escribe una captura en el estómago y termina. Lo crea Estomago. */
public class Escritor extends Thread {
    private final Estomago estomago;
    private final String identificador;
    private volatile boolean registrada;

    Escritor(Estomago estomago, String identificador) {
        super("Escritor-" + identificador);
        this.estomago = estomago;
        this.identificador = identificador;
    }

    @Override
    public void run() {
        try {
            estomago.escribirRegistro(identificador);
            registrada = true; // Nueva o repetida: está en el archivo.
        } catch (IOException e) {
            System.err.println("[Escritor] No se pudo anotar la captura " + identificador + ": " + e.getMessage());
            registrada = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            registrada = false;
        }
    }

    /** Espera a que el hilo termine (join) y devuelve si la captura quedó registrada. */
    public boolean esperarResultado() {
        try {
            join();
            return registrada;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
