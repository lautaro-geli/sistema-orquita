package orquitas.servidor.escape;

import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Navegador;

/**
 * Corresponde a CU-11 (Activar Modo Escape). Recibe el EventoModoEscape
 * emitido por LectorEstomago y, si corresponde, cambia el modo operativo
 * del sistema de BUSQUEDA a ESCAPE, avisando a Navegador.
 */
public class ActivadorModoEscape {

    private static final int UMBRAL_CAPTURAS = 3;

    private final Navegador navegador;
    private volatile ModoOperativo modoActual;

    public ActivadorModoEscape(Navegador navegador) {
        this.navegador = navegador;
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
        navegador.establecerModoOperativo(modoActual);
        System.out.println("[ActivadorModoEscape] ¡MODO ESCAPE ACTIVADO! " + evento);
    }

    public ModoOperativo getModoActual() {
        return modoActual;
    }

    private ModoOperativo cambiarModo(ModoOperativo nuevoModo) {
        System.out.println("[ActivadorModoEscape] Cambiando modo operativo: " + modoActual + " -> " + nuevoModo);
        return nuevoModo;
    }

    private void mantenerModoBusqueda() {
        System.out.println("[ActivadorModoEscape] Todavía no se alcanzaron las 3 capturas; se mantiene Modo Búsqueda.");
    }
}
