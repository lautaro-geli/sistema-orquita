package orquitas.servidor.navegacion;

import java.util.Objects;

public class Navegador {
    private volatile ModoOperativo modoOperativo = ModoOperativo.BUSQUEDA;
    private Movimiento ultimaDecision = Movimiento.DETENER;
    private final ClasificadorEntorno clasificador;
    /** Compatibilidad con las pruebas de persistencia de La Linareada. */
    public Navegador() { this(null); }
    public Navegador(ClasificadorEntorno clasificador) { this.clasificador = clasificador; }
    /** Devuelve la decision para que el comunicador responda al cliente correcto. */
    public synchronized Movimiento determinarMovimiento(Telemetria telemetria) {
        EntornoClasificado entorno = clasificador == null ? null : clasificador.clasificarEntorno(telemetria);
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
        System.out.println("[Navegador] Modo operativo actualizado a: " + modo);
    }
    public ModoOperativo getModoOperativo() { return modoOperativo; }
}
