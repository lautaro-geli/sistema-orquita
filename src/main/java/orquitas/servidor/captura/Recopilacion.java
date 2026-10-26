package orquitas.servidor.captura;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.Navegacion;
import orquitas.servidor.navegacion.Telemetria;
import orquitas.servidor.persistencia.Estomago;

/** CU-06. Trama recibida de la Orquita: telemetría y aviso de captura opcional. */
public final class Recopilacion {
    private static final int LARGO_MAXIMO = 256;
    private static final Recopilacion INVALIDA = new Recopilacion(null, null, null);
    private final LocalDateTime fecha;
    private final Telemetria telemetria;
    private final String idCaptura;

    private Recopilacion(LocalDateTime fecha, Telemetria telemetria, String idCaptura) {
        this.fecha = fecha; this.telemetria = telemetria; this.idCaptura = idCaptura;
    }

    /** Nunca lanza: cualquier campo mal formado invalida TODA la trama (sin efectos parciales). */
    public static Recopilacion decodificar(String linea) {
        try { return interpretar(linea); }
        catch (IllegalArgumentException | DateTimeException e) { return INVALIDA; }
    }

    private static Recopilacion interpretar(String linea) {
        if (linea == null || linea.length() > LARGO_MAXIMO) throw new IllegalArgumentException("Trama invalida");
        String[] c = linea.split("\\|", -1);
        if (c.length != 4 || !c[0].equals("RECOPILACION")) throw new IllegalArgumentException("Tipo/campos invalidos");
        LocalDateTime fecha = LocalDateTime.parse(c[1]);
        String[] valores = c[2].split(";", -1);
        if (valores.length != 12) throw new IllegalArgumentException("Faltan distancias");
        double[] distancias = new double[12];
        for (int i = 0; i < 12; i++) {
            if (!valores[i].matches("[0-9]+(?:\\.[0-9]+)?"))
                throw new IllegalArgumentException("Distancia decimal invalida");
            distancias[i] = Double.parseDouble(valores[i]);
        }
        Telemetria telemetria = new Telemetria(distancias);
        String idCaptura = c[3].isEmpty() ? null : c[3];
        if (idCaptura != null) new Captura(idCaptura, fecha); // Misma regla de id que el archivo.
        return new Recopilacion(fecha, telemetria, idCaptura);
    }

    public boolean esValida() { return telemetria != null; }

    public boolean tieneAvisoCaptura() { return idCaptura != null; }

    /** Registra la captura (si la hay) y devuelve la orden de navegación. */
    public Movimiento procesar(Estomago estomago, Navegacion navegacion) {
        if (!esValida()) throw new IllegalStateException("No se procesa una recopilacion invalida");
        if (tieneAvisoCaptura()) estomago.registrarCaptura(new Captura(idCaptura, fecha));
        return navegacion.determinarMovimiento(telemetria);
    }
}
