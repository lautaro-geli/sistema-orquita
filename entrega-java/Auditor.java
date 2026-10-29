package orquitas.servidor.persistencia;

import orquitas.servidor.captura.Captura;
import orquitas.servidor.navegacion.Navegacion;
import java.io.IOException;
import java.util.List;

/** CU-10. Auditor independiente: obtiene la cantidad exclusivamente del archivo. */
public class Auditor extends Thread {
    /** Capturas válidas que activan el Modo Escape. */
    public static final int UMBRAL_CAPTURAS = 3;
    private final Estomago estomago;
    private final Navegacion navegacion;
    private final long intervaloAuditoria;
    private volatile boolean sistemaActivo = true;

    public Auditor(Estomago estomago, Navegacion navegacion, long intervaloAuditoria) {
        super("Auditor");
        if (intervaloAuditoria <= 0) throw new IllegalArgumentException("Intervalo positivo requerido");
        this.estomago = estomago;
        this.navegacion = navegacion;
        this.intervaloAuditoria = intervaloAuditoria;
    }

    @Override
    public void run() {
        while (sistemaActivo) { // LOOP de CU-10
            try {
                List<Captura> registros = estomago.leerRegistros(); // CRITICAL interno
                int cantidadCapturas = estomago.contarRegistrosValidos(registros);
                if (cantidadCapturas >= UMBRAL_CAPTURAS) { // OPT de CU-10: extensión CU-11
                    navegacion.activarModoEscape();
                }
            } catch (IOException e) {
                // Nunca decidir con una lectura parcial ni sustituirla por un contador en RAM.
                System.err.println("[Auditor] Lectura fallida, se reintentará: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sistemaActivo = false;
            }
            if (sistemaActivo) esperarProximoCiclo();
        }
    }

    public void detener() {
        sistemaActivo = false;
        interrupt();
    }

    private void esperarProximoCiclo() {
        try {
            Thread.sleep(intervaloAuditoria);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sistemaActivo = false;
        }
    }
}
