package orquitas.servidor.persistencia;

import orquitas.servidor.captura.Captura;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Objects;

/** CU-09. Hilo escritor permanente; la recepción sólo encola y lo despierta. */
public class Escritor extends Thread {
    private final Estomago estomago;
    private final ArrayDeque<Captura> pendientes = new ArrayDeque<>();
    private boolean activo = true;

    public Escritor(Estomago estomago) {
        super("Escritor");
        this.estomago = Objects.requireNonNull(estomago);
    }

    public void encolarCaptura(Captura captura) {
        Objects.requireNonNull(captura);
        synchronized (pendientes) {
            if (!activo) throw new IllegalStateException("El escritor está detenido");
            estomago.anunciarEscritura(); // Desde aquí las nuevas lecturas esperan.
            pendientes.addLast(captura);
            pendientes.notifyAll(); // Activa al escritor que esperaba trabajo.
        }
    }

    @Override
    public void run() {
        while (true) {
            Captura captura;
            synchronized (pendientes) {
                while (pendientes.isEmpty() && activo) {
                    try {
                        pendientes.wait();
                    } catch (InterruptedException e) {
                        activo = false; // Se vacía la cola antes de terminar.
                    }
                }
                if (pendientes.isEmpty()) return;
                captura = pendientes.removeFirst();
            }
            try {
                boolean nueva = estomago.escribirRegistro(captura);
                System.out.println("[Escritor] " + (nueva ? "Registrada: " : "Duplicada omitida: ")
                        + captura.getIdCaptura());
            } catch (IOException e) {
                System.err.println("[Escritor] Captura NO confirmada: "
                        + captura.getIdCaptura() + "; " + e.getMessage());
            } catch (InterruptedException e) {
                // No se pierde un aviso al interrumpirse la espera del semáforo.
                synchronized (pendientes) {
                    estomago.anunciarEscritura();
                    pendientes.addFirst(captura);
                    activo = false;
                }
            } finally {
                estomago.finalizarEscritura();
            }
        }
    }

    public void detener() {
        synchronized (pendientes) {
            activo = false;
            pendientes.notifyAll(); // Drena lo aceptado; no interrumpe una escritura.
        }
    }
}
