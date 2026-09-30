package orquitas.servidor.captura;

import java.time.LocalDateTime;

/**
 * Aviso de captura recibido crudo desde el punto de entrada (en este hito,
 * el listener TCP de ingesta simulada). Corresponde al "avisoCaptura" que
 * en la vista de secuencia de CU-06/CU-09 viaja desde Recopilador
 * hacia ReceptorCapturas.
 *
 * No tiene diseño POO del lado del emisor (el simulador no se documenta,
 * según la consigna del hito); esta clase es exclusivamente del lado
 * Servidor.
 */
public class AvisoCaptura {

    private final String idCaptura;
    private final LocalDateTime timestamp;

    public AvisoCaptura(String idCaptura, LocalDateTime timestamp) {
        if (idCaptura == null || !idCaptura.matches("[A-Za-z0-9_-]{1,64}") || timestamp == null) {
            throw new IllegalArgumentException("Id o fecha de captura inválidos");
        }
        this.idCaptura = idCaptura;
        this.timestamp = timestamp;
    }

    public String getIdCaptura() {
        return idCaptura;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "AvisoCaptura{idCaptura='" + idCaptura + "', timestamp=" + timestamp + "}";
    }
}
