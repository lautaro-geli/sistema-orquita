package orquitas.servidor.navegacion;

import java.util.Objects;

/** CU-07 y CU-11. Decide el movimiento con la RedNeuronal según el modo operativo, y pasa a ESCAPE cuando el Auditor lo pide. */
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
        // La red clasifica en los dos modos; el modo cambia la meta: busqueda persigue al lobito y
        // evalua obstaculos; escape ignora al lobito, esquiva y no se detiene. Las decisiones usan
        // las distancias crudas en mm, no la cercania que alimenta a la red.
        double derecha = telemetria.sumarDerecha(), izquierda = telemetria.sumarIzquierda();
        Movimiento haciaLoAbierto = izquierda >= derecha ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER;
        if (modoOperativo == ModoOperativo.ESCAPE) {
            // Ya tiene las 3 capturas: LOBITO cuenta como LIBRE. No localiza la salida (sin posicion ni rumbo).
            return entorno == EntornoClasificado.OBJETO ? haciaLoAbierto : Movimiento.AVANZAR;
        }
        switch (entorno) {
            case LOBITO: return movimientoHaciaSector(telemetria.sectorMasCercano()); // Lo persigue.
            case OBJETO: return derecha < UMBRAL_LADO && izquierda < UMBRAL_LADO ? Movimiento.DETENER : haciaLoAbierto;
            default: return Movimiento.AVANZAR;
        }
    }
    /** Orienta hacia un sector: 4..7 frente, 0..3 derecha, 8..11 izquierda. */
    private Movimiento movimientoHaciaSector(int sector) {
        if (sector <= 3) return Movimiento.GIRAR_DER;
        if (sector >= 8) return Movimiento.GIRAR_IZQ;
        return Movimiento.AVANZAR;
    }
    public synchronized Movimiento obtenerMovimientoDecidido() { return movimientoDecidido; }
    /** CU-11. Idempotente: el Auditor lo invoca en cada ciclo y el cambio a ESCAPE ocurre una sola vez. */
    public synchronized void activarModoEscape() {
        if (modoOperativo == ModoOperativo.ESCAPE) return;
        establecerModoOperativo(ModoOperativo.ESCAPE);
    }
    public void establecerModoOperativo(ModoOperativo modo) {
        modoOperativo = Objects.requireNonNull(modo);
        System.out.println("[Navegacion] Modo operativo actualizado a: " + modo);
    }
    public ModoOperativo getModoOperativo() { return modoOperativo; }
}
