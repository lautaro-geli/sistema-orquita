package orquitas.servidor.navegacion;

import java.util.Objects;

public class Navegacion {
    private volatile ModoOperativo modoOperativo = ModoOperativo.BUSQUEDA;
    private Movimiento ultimaDecision = Movimiento.DETENER;
    private final RedNeuronal redNeuronal;
    public Navegacion(RedNeuronal redNeuronal) { this.redNeuronal = Objects.requireNonNull(redNeuronal); }
    /** Devuelve la decision para que el enlace responda al cliente correcto. */
    public synchronized Movimiento determinarMovimiento(Telemetria telemetria) {
        EntornoClasificado entorno = redNeuronal.clasificarEntorno(telemetria);
        if (entorno == null) return mantenerUltimaDecision();
        ModoOperativo modoActual = obtenerModoOperativo();
        ultimaDecision = calcularMovimiento(entorno, modoActual, telemetria);
        return ultimaDecision;
    }
    private Movimiento calcularMovimiento(EntornoClasificado entorno, ModoOperativo modoActual, Telemetria telemetria) {
        // Hito 2 preliminar: BUSQUEDA y ESCAPE comparten evitacion; no se localiza la salida.
        if (entorno == EntornoClasificado.LIBRE) return Movimiento.AVANZAR;
        double derecha = telemetria.sumarDerecha(), izquierda = telemetria.sumarIzquierda();
        if (derecha < 6*200 && izquierda < 6*200) return Movimiento.DETENER;
        return izquierda >= derecha ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER;
    }
    private Movimiento mantenerUltimaDecision() { return ultimaDecision; }
    private ModoOperativo obtenerModoOperativo() { return modoOperativo; }
    public void establecerModoOperativo(ModoOperativo modo) {
        modoOperativo = Objects.requireNonNull(modo);
        System.out.println("[Navegacion] Modo operativo actualizado a: " + modo);
    }
    public ModoOperativo getModoOperativo() { return modoOperativo; }
}
