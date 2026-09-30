package orquitas.servidor.captura;

import orquitas.servidor.persistencia.ActualizadorEstomago;

/** CU-09. Convierte el aviso validado y despierta al hilo escritor. */
public class ReceptorCapturas {
    private final ActualizadorEstomago actualizadorEstomago;

    public ReceptorCapturas(ActualizadorEstomago actualizadorEstomago) {
        this.actualizadorEstomago = actualizadorEstomago;
    }

    public void notificarCaptura(AvisoCaptura avisoCaptura) {
        Captura captura = new Captura(avisoCaptura.getIdCaptura(), avisoCaptura.getTimestamp());
        actualizadorEstomago.encolarCaptura(captura);
    }
}
