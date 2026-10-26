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
 * Formato de línea en el archivo: identificador;marcaTiempo
 */
public class Captura {

    private final String identificador;
    private final LocalDateTime marcaTiempo;

    public Captura(String identificador, LocalDateTime marcaTiempo) {
        if (identificador == null || !identificador.matches("[A-Za-z0-9_-]{1,64}") || marcaTiempo == null) {
            throw new IllegalArgumentException("Id o fecha de captura inválidos");
        }
        this.identificador = identificador;
        this.marcaTiempo = marcaTiempo;
    }

    public String getIdentificador() {
        return identificador;
    }

    public LocalDateTime getMarcaTiempo() {
        return marcaTiempo;
    }

    /** Serializa esta captura a la línea que se escribe en estomago.txt. */
    public String aLinea() {
        return identificador + ";" + marcaTiempo;
    }

    /**
     * Reconstruye una Captura a partir de una línea leída de estomago.txt.
     * Devuelve null si la línea está vacía, mal formada o corrupta, para que
     * quien la lea (Estomago) pueda descartarla sin romper
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
            String identificador = partes[0].trim();
            LocalDateTime marcaTiempo = LocalDateTime.parse(partes[1].trim());
            if (identificador.isEmpty()) {
                return null;
            }
            return new Captura(identificador, marcaTiempo);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "Captura{identificador='" + identificador + "', marcaTiempo=" + marcaTiempo + "}";
    }
}
