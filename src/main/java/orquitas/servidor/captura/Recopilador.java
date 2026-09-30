package orquitas.servidor.captura;

import java.time.LocalDateTime;

/** CU-06, recorte del hito: valida mensajes de captura, sin telemetría ni IA. */
public class Recopilador {
    private final ReceptorCapturas receptorCapturas;

    public Recopilador(ReceptorCapturas receptorCapturas) {
        this.receptorCapturas = receptorCapturas;
    }

    public boolean procesarRecopilacion(String mensaje) {
        if (mensaje == null || mensaje.length() > 256) return descartarPaquete();
        String[] campos = mensaje.split("\\|", -1);
        if (campos.length != 3 || !"CAPTURA".equals(campos[0])) return descartarPaquete();
        AvisoCaptura aviso;
        try {
            aviso = new AvisoCaptura(campos[1], LocalDateTime.parse(campos[2]));
        } catch (RuntimeException e) {
            return descartarPaquete();
        }
        receptorCapturas.notificarCaptura(aviso);
        return true; // Aceptado en cola, NO significa persistido todavía.
    }

    private boolean descartarPaquete() {
        System.err.println("[Recopilador] Mensaje inválido o desconocido: descartado");
        return false;
    }
}
