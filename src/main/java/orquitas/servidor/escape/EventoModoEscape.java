package orquitas.servidor.escape;

import java.time.LocalDateTime;

/**
 * Protocolo de mensajes interno acordado en la revisión del hito para la
 * notificación de LectorEstomago hacia ActivadorModoEscape (punto 5 de la
 * Fase 1).
 *
 * Aunque la comunicación es intra-proceso (misma JVM), se formaliza como un
 * mensaje/evento explícito -en vez de una llamada a método sin parámetros-
 * para dejar documentado un contrato claro y reutilizable el día de mañana
 * si esta notificación necesita viajar por un canal real (por ejemplo,
 * hacia la Orquita vía Bluetooth).
 */
public class EventoModoEscape {

    public static final String TIPO_MODO_ESCAPE_ACTIVADO = "MODO_ESCAPE_ACTIVADO";

    private final String tipoEvento;
    private final int cantidadCapturas;
    private final LocalDateTime timestamp;

    public EventoModoEscape(String tipoEvento, int cantidadCapturas, LocalDateTime timestamp) {
        if (tipoEvento == null || cantidadCapturas < 0 || timestamp == null) {
            throw new IllegalArgumentException("Evento incompleto");
        }
        this.tipoEvento = tipoEvento;
        this.cantidadCapturas = cantidadCapturas;
        this.timestamp = timestamp;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public int getCantidadCapturas() {
        return cantidadCapturas;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "EventoModoEscape{tipoEvento='" + tipoEvento + "', cantidadCapturas=" + cantidadCapturas
                + ", timestamp=" + timestamp + "}";
    }
}
