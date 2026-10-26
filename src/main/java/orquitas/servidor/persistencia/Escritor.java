package orquitas.servidor.persistencia;

import orquitas.servidor.captura.Captura;
import java.io.IOException;

/** CU-09. Hilo de un solo uso: persiste una captura y termina. Lo crea Estomago.registrarCaptura. */
public class Escritor extends Thread {
    private final Estomago estomago;
    private final Captura captura;

    Escritor(Estomago estomago, Captura captura) {
        super("Escritor-" + captura.getIdCaptura());
        this.estomago = estomago;
        this.captura = captura;
    }

    @Override
    public void run() {
        try {
            boolean nueva = estomago.escribirRegistro(captura);
            System.out.println("[Escritor] " + (nueva ? "Registrada: " : "Duplicada omitida: ")
                    + captura.getIdCaptura());
        } catch (IOException e) {
            // El error de E/S se registra; no se propaga ni detiene la recepción.
            System.err.println("[Escritor] Captura NO confirmada: "
                    + captura.getIdCaptura() + "; " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("[Escritor] Interrumpido antes de escribir: " + captura.getIdCaptura());
            Thread.currentThread().interrupt();
        } finally {
            estomago.finalizarEscritura();
        }
    }
}
