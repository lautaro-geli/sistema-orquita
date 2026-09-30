package orquitas.servidor.navegacion;

/**
 * Versión mínima de Navegador para esta prueba de integración. El alcance
 * de "La Linareada" es concurrencia y persistencia (CU-09/CU-10/CU-11); no
 * incluye telemetría ni clasificación con Neuroph (CU-07), por lo que aquí
 * solo se deja el punto de enganche que ActivadorModoEscape necesita para
 * avisar el cambio de modo operativo, según ya está definido en el
 * Diagrama de Clases del Módulo Servidor.
 */
public class Navegador {

    private volatile ModoOperativo modoOperativo = ModoOperativo.BUSQUEDA;

    public void establecerModoOperativo(ModoOperativo modo) {
        this.modoOperativo = java.util.Objects.requireNonNull(modo);
        System.out.println("[Navegador] Modo operativo actualizado a: " + modo);
    }

    public ModoOperativo getModoOperativo() {
        return modoOperativo;
    }
}
