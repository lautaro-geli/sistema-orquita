package orquitas.servidor.captura;

import orquitas.servidor.persistencia.Escritor;

/** CU-09. Convierte el aviso validado y despierta al hilo escritor. */
public class ReceptorCapturas {
    private final Escritor escritor;

    public ReceptorCapturas(Escritor escritor) {
        this.escritor = escritor;
    }

    public void notificarCaptura(AvisoCaptura avisoCaptura) {
        Captura captura = new Captura(avisoCaptura.getIdCaptura(), avisoCaptura.getTimestamp());
        escritor.encolarCaptura(captura);
    }
}
