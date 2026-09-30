package orquitas.servidor.persistencia;

import orquitas.servidor.captura.Captura;
import orquitas.servidor.escape.ActivadorModoEscape;
import orquitas.servidor.escape.EventoModoEscape;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/** CU-10. Auditor independiente: obtiene la cantidad exclusivamente del archivo. */
public class LectorEstomago extends Thread {
    private static final int UMBRAL_CAPTURAS = 3;
    private final ArchivoEstomago archivoEstomago;
    private final ActivadorModoEscape activadorModoEscape;
    private final long intervaloAuditoria;
    private volatile boolean activo = true;
    private boolean modoEscapeNotificado;

    public LectorEstomago(ArchivoEstomago archivoEstomago,
            ActivadorModoEscape activadorModoEscape, long intervaloAuditoria) {
        super("LectorEstomago");
        if (intervaloAuditoria <= 0) throw new IllegalArgumentException("Intervalo positivo requerido");
        this.archivoEstomago = archivoEstomago;
        this.activadorModoEscape = activadorModoEscape;
        this.intervaloAuditoria = intervaloAuditoria;
    }

    @Override
    public void run() {
        while (sistemaActivo()) { // LOOP de CU-10
            try {
                List<Captura> registros = archivoEstomago.leerRegistros(); // CRITICAL interno
                int cantidadCapturas = contarRegistrosValidos(registros);
                if (cantidadCapturas >= UMBRAL_CAPTURAS && !modoEscapeNotificado) {
                    EventoModoEscape evento = new EventoModoEscape(
                            EventoModoEscape.TIPO_MODO_ESCAPE_ACTIVADO,
                            cantidadCapturas, LocalDateTime.now());
                    activadorModoEscape.notificarCapturasCompletas(evento);
                    modoEscapeNotificado = true; // Sólo después de notificar correctamente.
                }
            } catch (IOException e) {
                // Nunca decidir con una lectura parcial ni sustituirla por un contador en RAM.
                System.err.println("[LectorEstomago] Lectura fallida, se reintentará: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                activo = false;
            }
            if (sistemaActivo()) esperarProximoCiclo(intervaloAuditoria);
        }
    }

    public boolean sistemaActivo() {
        return activo;
    }

    public void detener() {
        activo = false;
        interrupt();
    }

    private int contarRegistrosValidos(List<Captura> registros) {
        return registros.size();
    }

    private void esperarProximoCiclo(long intervaloAuditoria) {
        try {
            Thread.sleep(intervaloAuditoria);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            activo = false;
        }
    }
}
