package orquitas.servidor.escape;

import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Navegacion;

/**
 * Corresponde a CU-11 (Activar Modo Escape). Recibe el EventoModoEscape
 * emitido por Auditor y, si corresponde, cambia el modo operativo
 * del sistema de BUSQUEDA a ESCAPE, avisando a Navegacion.
 */
public class ModoEscape {

    private static final int UMBRAL_CAPTURAS = 3;

    private final Navegacion navegacion;
    private volatile ModoOperativo modoActual;

    public ModoEscape(Navegacion navegacion) {
        this.navegacion = navegacion;
        this.modoActual = ModoOperativo.BUSQUEDA;
    }

    /**
     * Punto de entrada del protocolo de mensajes interno definido con
     * EventoModoEscape (ver clase EventoModoEscape).
     */
    public void notificarCapturasCompletas(EventoModoEscape evento) {
        if (evento == null || !EventoModoEscape.TIPO_MODO_ESCAPE_ACTIVADO.equals(evento.getTipoEvento())) {
            throw new IllegalArgumentException("Evento de escape inválido");
        }
        if (modoActual == ModoOperativo.ESCAPE) return;
        if (evento.getCantidadCapturas() < UMBRAL_CAPTURAS) {
            mantenerModoBusqueda();
            return;
        }
        modoActual = cambiarModo(ModoOperativo.ESCAPE);
        navegacion.establecerModoOperativo(modoActual);
        System.out.println("[ModoEscape] ¡MODO ESCAPE ACTIVADO! " + evento);
    }

    public ModoOperativo getModoActual() {
        return modoActual;
    }

    private ModoOperativo cambiarModo(ModoOperativo nuevoModo) {
        System.out.println("[ModoEscape] Cambiando modo operativo: " + modoActual + " -> " + nuevoModo);
        return nuevoModo;
    }

    private void mantenerModoBusqueda() {
        System.out.println("[ModoEscape] Todavía no se alcanzaron las 3 capturas; se mantiene Modo Búsqueda.");
    }
}
