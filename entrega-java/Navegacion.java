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
    /** Por debajo de 200 mm promedio por sector se considera bloqueado. */
    private static final double MINIMO_POR_SECTOR = 200;
    private static final double UMBRAL_LADO = 6*MINIMO_POR_SECTOR;   // sectores 0..5 o 6..11
    private static final double UMBRAL_FRENTE = 4*MINIMO_POR_SECTOR; // sectores 4..7
    private Movimiento calcularMovimiento(EntornoClasificado entorno, Telemetria telemetria) {
        double derecha = telemetria.sumarDerecha(), izquierda = telemetria.sumarIzquierda();
        if (modoOperativo == ModoOperativo.ESCAPE) {
            // Heuristica preliminar: ir hacia el espacio mas abierto y seguir avanzando, sin detenerse a
            // evaluar objetos. NO localiza la salida de la bahia (no hay posicion ni rumbo en este hito).
            if (telemetria.sumarFrente() >= UMBRAL_FRENTE) return Movimiento.AVANZAR;
            if (derecha < UMBRAL_LADO && izquierda < UMBRAL_LADO) return Movimiento.DETENER; // Encerrado.
            return izquierda >= derecha ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER;
        }
        // BUSQUEDA: evitar el objeto que detecta la red.
        if (entorno == EntornoClasificado.LIBRE) return Movimiento.AVANZAR;
        if (derecha < UMBRAL_LADO && izquierda < UMBRAL_LADO) return Movimiento.DETENER;
        return izquierda >= derecha ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER;
    }
    public synchronized Movimiento obtenerMovimientoDecidido() { return movimientoDecidido; }
    public void establecerModoOperativo(ModoOperativo modo) {
        modoOperativo = Objects.requireNonNull(modo);
        System.out.println("[Navegacion] Modo operativo actualizado a: " + modo);
    }
    public ModoOperativo getModoOperativo() { return modoOperativo; }
}
