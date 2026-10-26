package orquitas.servidor.escape;

import java.util.Objects;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Navegacion;

/**
 * Corresponde a CU-11 (Activar Modo Escape). Auditor le informa la cantidad
 * de capturas leída de estomago.txt y, si se alcanzó el umbral, cambia el
 * modo operativo de Navegacion de BUSQUEDA a ESCAPE.
 */
public class ModoEscape {

    public static final int UMBRAL_CAPTURAS = 3;

    private final Navegacion navegacion;

    public ModoEscape(Navegacion navegacion) {
        this.navegacion = Objects.requireNonNull(navegacion);
    }

    public void notificarCapturasCompletas(int cantidadCapturas) {
        if (cantidadCapturas < 0) throw new IllegalArgumentException("Cantidad de capturas negativa");
        if (cantidadCapturas < UMBRAL_CAPTURAS) {
            mantenerModoBusqueda();
            return;
        }
        activar(cantidadCapturas);
    }

    /** Idempotente: el Auditor vuelve a notificar en cada ciclo y el cambio ocurre una sola vez. */
    private synchronized void activar(int cantidadCapturas) {
        if (navegacion.getModoOperativo() == ModoOperativo.ESCAPE) return;
        navegacion.establecerModoOperativo(ModoOperativo.ESCAPE);
        System.out.println("[ModoEscape] ¡MODO ESCAPE ACTIVADO! capturas=" + cantidadCapturas);
    }

    private void mantenerModoBusqueda() {
        System.out.println("[ModoEscape] Todavía no se alcanzaron las 3 capturas; se mantiene Modo Búsqueda.");
    }
}
