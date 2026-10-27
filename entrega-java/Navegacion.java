package orquitas.servidor.navegacion;

import java.util.Objects;

/** CU-07. Decide el movimiento con la RedNeuronal y el modo operativo propio. */
public class Navegacion {
    private volatile ModoOperativo modoOperativo = ModoOperativo.BUSQUEDA;
    private Movimiento movimientoDecidido = Movimiento.DETENER;
    private final RedNeuronal redNeuronal;
    public Navegacion(RedNeuronal redNeuronal) { this.redNeuronal = Objects.requireNonNull(redNeuronal); }
    /** Devuelve la decision para que el enlace responda al cliente correcto. */
    public synchronized Movimiento determinarMovimiento(Telemetria telemetria) {
        EntornoClasificado entorno = redNeuronal.clasificarEntorno(telemetria);
        if (entorno == null) return obtenerMovimientoDecidido(); // Sin clasificacion: conserva la ultima.
        movimientoDecidido = calcularMovimiento(entorno, telemetria);
        return movimientoDecidido;
    }
    /** Encerrada: cada lado (sectores 0..5 y 6..11) suma menos de 6 x 200 mm = 1200 mm. */
    private static final double UMBRAL_LADO = 6*200;
    private Movimiento calcularMovimiento(EntornoClasificado entorno, Telemetria telemetria) {
        // La red clasifica el entorno en todo momento; el modo operativo cambia la meta
        // (busqueda evalua/caza, escape esquiva y sale), no la clasificacion.
        if (entorno == EntornoClasificado.LIBRE) return Movimiento.AVANZAR;
        if (modoOperativo == ModoOperativo.BUSQUEDA) return Movimiento.DETENER; // Frena a evaluar el objeto.
        // ESCAPE: esquiva sin frenar hacia el lado mas abierto. No localiza la salida (sin posicion ni rumbo).
        double derecha = telemetria.sumarDerecha(), izquierda = telemetria.sumarIzquierda();
        if (derecha < UMBRAL_LADO && izquierda < UMBRAL_LADO) return Movimiento.DETENER; // Encerrada.
        return izquierda >= derecha ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER;
    }
    public synchronized Movimiento obtenerMovimientoDecidido() { return movimientoDecidido; }
    public void establecerModoOperativo(ModoOperativo modo) {
        modoOperativo = Objects.requireNonNull(modo);
        System.out.println("[Navegacion] Modo operativo actualizado a: " + modo);
    }
    public ModoOperativo getModoOperativo() { return modoOperativo; }
}
