package orquitas.servidor.captura;

import orquitas.servidor.navegacion.*;

public class Recopilador {
    private final ReceptorCapturas receptorCapturas;
    private final Navegador navegador;
    public Recopilador(ReceptorCapturas receptor) { this(receptor, new Navegador()); }
    public Recopilador(ReceptorCapturas receptor, Navegador navegador) {
        this.receptorCapturas=receptor; this.navegador=navegador;
    }
    /** Adaptador legado: true significa aceptado, no persistido. */
    public boolean procesarRecopilacion(String mensaje) {
        PaqueteRecopilacion paquete;
        try { paquete=PaqueteRecopilacion.decodificar(mensaje); }
        catch (IllegalArgumentException | java.time.DateTimeException e) { descartarPaquete(null); return false; }
        procesarRecopilacion(paquete); return true;
    }
    public Movimiento procesarRecopilacion(PaqueteRecopilacion paqueteRecopilacion) {
        if(paqueteRecopilacion==null || !paqueteRecopilacion.esValido()) { descartarPaquete(paqueteRecopilacion); return null; }
        Telemetria telemetria=extraerTelemetria(paqueteRecopilacion);
        AvisoCaptura aviso=extraerAvisoCaptura(paqueteRecopilacion);
        if(aviso!=null) receptorCapturas.notificarCaptura(aviso);
        return telemetria==null ? null : navegador.determinarMovimiento(telemetria);
    }
    private Telemetria extraerTelemetria(PaqueteRecopilacion p) { return p.getTelemetria(); }
    private AvisoCaptura extraerAvisoCaptura(PaqueteRecopilacion p) { return p.getAvisoCaptura(); }
    private void descartarPaquete(PaqueteRecopilacion p) { System.err.println("[Recopilador] Paquete invalido: descartado"); }
}
