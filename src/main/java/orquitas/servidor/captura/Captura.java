package orquitas.servidor.captura;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Registro de una captura, tal como queda persistido en estomago.txt.
 *
 * Se mantiene deliberadamente mínimo (solo lo necesario para poder contar
 * capturas válidas y probar la concurrencia lector/escritor), tal como se
 * definió en la revisión: "lo más simple y corto posible, lo importante es
 * la concurrencia".
 *
 * Formato de línea en el archivo: idCaptura;timestamp
 */
public class Captura {

    private final String idCaptura;
    private final LocalDateTime timestamp;

    public Captura(String idCaptura, LocalDateTime timestamp) {
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

    /** Serializa esta captura a la línea que se escribe en estomago.txt. */
    public String aLinea() {
        return idCaptura + ";" + timestamp;
    }

    /**
     * Reconstruye una Captura a partir de una línea leída de estomago.txt.
     * Devuelve null si la línea está vacía, mal formada o corrupta, para que
     * quien la lea (ArchivoEstomago) pueda descartarla sin romper
     * la lectura del resto del archivo.
     */
    public static Captura desdeLinea(String linea) {
        if (linea == null || linea.isBlank()) {
            return null;
        }
        String[] partes = linea.split(";", -1);
        if (partes.length != 2) {
            return null;
        }
        try {
            String idCaptura = partes[0].trim();
            LocalDateTime timestamp = LocalDateTime.parse(partes[1].trim());
            if (idCaptura.isEmpty()) {
                return null;
            }
            return new Captura(idCaptura, timestamp);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "Captura{idCaptura='" + idCaptura + "', timestamp=" + timestamp + "}";
    }
}
