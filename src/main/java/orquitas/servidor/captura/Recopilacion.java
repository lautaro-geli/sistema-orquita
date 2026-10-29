package orquitas.servidor.captura;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.Navegacion;
import orquitas.servidor.navegacion.Telemetria;
import orquitas.servidor.persistencia.Estomago;

/** CU-06. Trama recibida de la Orquita: telemetría y, si la hay, la captura que avisa. */
public final class Recopilacion {
    /** Largo máximo de una trama, en caracteres, sin contar el fin de línea. */
    public static final int LARGO_MAXIMO = 256;
    private static final Recopilacion INVALIDA = new Recopilacion(null, null);
    private final Telemetria telemetria;
    private final Captura captura;

    private Recopilacion(Telemetria telemetria, Captura captura) {
        this.telemetria = telemetria; this.captura = captura;
    }

    /** Nunca lanza: cualquier campo mal formado invalida TODA la trama (sin efectos parciales). */
    public static Recopilacion decodificar(String trama) {
        try { return interpretar(trama); }
        catch (IllegalArgumentException | DateTimeException e) { return INVALIDA; }
    }

    private static Recopilacion interpretar(String trama) {
        if (trama == null || trama.length() > LARGO_MAXIMO) throw new IllegalArgumentException("Trama invalida");
        String[] c = trama.split("\\|", -1);
        if (c.length != 4 || !c[0].equals("RECOPILACION")) throw new IllegalArgumentException("Tipo/campos invalidos");
        LocalDateTime fecha = LocalDateTime.parse(c[1]);
        String[] valores = c[2].split(";", -1);
        if (valores.length != Telemetria.SECTORES) throw new IllegalArgumentException("Faltan distancias");
        double[] distancias = new double[Telemetria.SECTORES];
        for (int i = 0; i < Telemetria.SECTORES; i++) {
            if (!valores[i].matches("[0-9]+(?:\\.[0-9]+)?"))
                throw new IllegalArgumentException("Distancia decimal invalida");
            distancias[i] = Double.parseDouble(valores[i]);
        }
        Captura captura = c[3].isEmpty() ? null : new Captura(c[3], fecha); // Valida el id con la regla del archivo.
        return new Recopilacion(new Telemetria(distancias), captura);
    }

    public boolean esValida() { return telemetria != null; }

    public boolean tieneAvisoCaptura() { return captura != null; }

    /** Registra la captura (si la hay) y devuelve la orden de navegación. */
    public Movimiento procesar(Estomago estomago, Navegacion navegacion) {
        if (!esValida()) throw new IllegalStateException("No se procesa una recopilacion invalida");
        if (tieneAvisoCaptura()) estomago.registrarCaptura(captura);
        return navegacion.determinarMovimiento(telemetria);
    }
}
