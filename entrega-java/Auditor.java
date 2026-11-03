package orquitas.servidor.persistencia;

import java.io.IOException;
import orquitas.servidor.navegacion.Navegacion;

/**
 * CU-10 y CU-11. Hilo independiente que cuenta las capturas leyendo solo el archivo y, al llegar al
 * umbral, activa el Modo Escape (idempotente: puede pedirlo en cada ciclo).
 */
public class Auditor extends Thread {
    private final Estomago estomago;
    private final Navegacion navegacion;
    private final long intervaloAuditoria;
    private final int umbralCapturas;
    private volatile boolean sistemaActivo = true;
    /** Último conteo informado por consola, solo para no repetir el mensaje. */
    private int cantidadInformada;

    public Auditor(Estomago estomago, Navegacion navegacion, long intervaloAuditoria, int umbralCapturas) {
        super("Auditor");
        if (intervaloAuditoria <= 0) throw new IllegalArgumentException("Intervalo positivo requerido");
        if (umbralCapturas <= 0) throw new IllegalArgumentException("Umbral de capturas positivo requerido");
        this.estomago = estomago;
        this.navegacion = navegacion;
        this.intervaloAuditoria = intervaloAuditoria;
        this.umbralCapturas = umbralCapturas;
    }

    @Override
    public void run() {
        while (sistemaActivo) {
            try {
                int cantidadCapturas = estomago.contarRegistrosValidos();
                if (cantidadCapturas != cantidadInformada) {
                    System.out.println("[Auditor] Capturas en el estómago: " + cantidadCapturas + "/" + umbralCapturas);
                    cantidadInformada = cantidadCapturas;
                }
                if (cantidadCapturas >= umbralCapturas) navegacion.activarModoEscape();
            } catch (IOException e) {
                // Nunca decidir con una lectura fallida ni reemplazarla por un contador en memoria.
                System.err.println("[Auditor] Lectura fallida, se reintenta: " + e.getMessage());
            } catch (InterruptedException e) {
                sistemaActivo = false;
                break;
            }
            esperarProximoCiclo();
        }
    }

    /** Corta el bucle e interrumpe la espera en curso. */
    public void detener() {
        sistemaActivo = false;
        interrupt();
    }

    private void esperarProximoCiclo() {
        try {
            Thread.sleep(intervaloAuditoria);
        } catch (InterruptedException e) {
            sistemaActivo = false;
        }
    }
}
