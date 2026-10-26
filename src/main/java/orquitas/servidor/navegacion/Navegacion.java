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
    private Movimiento calcularMovimiento(EntornoClasificado entorno, Telemetria telemetria) {
        // Hito 2 preliminar: BUSQUEDA y ESCAPE (modoOperativo) comparten evitacion; no se localiza la salida.
        if (entorno == EntornoClasificado.LIBRE) return Movimiento.AVANZAR;
        double derecha = telemetria.sumarDerecha(), izquierda = telemetria.sumarIzquierda();
        if (derecha < 6*200 && izquierda < 6*200) return Movimiento.DETENER;
        return izquierda >= derecha ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER;
    }
    public synchronized Movimiento obtenerMovimientoDecidido() { return movimientoDecidido; }
    public void establecerModoOperativo(ModoOperativo modo) {
        modoOperativo = Objects.requireNonNull(modo);
        System.out.println("[Navegacion] Modo operativo actualizado a: " + modo);
    }
    public ModoOperativo getModoOperativo() { return modoOperativo; }
}
