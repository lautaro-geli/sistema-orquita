// Archivo de simulacion
package orquitas.simulacion.bahia;

import java.nio.file.Path;
import orquitas.servidor.navegacion.DetectorAtasco;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.MemoriaNavegacion;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.OrdenMovimiento;
import orquitas.servidor.navegacion.RedMovimiento;
import orquitas.servidor.navegacion.RedPercepcion;
import orquitas.servidor.navegacion.ReflejoSeguridad;

/** Quién maneja el robot en la simulación: el maestro (reglas con la geometría real) o las redes. */
public interface Piloto {
    /** Orden decidida y, si las hay, las etiquetas por sector que vio el piloto. */
    record Decision(OrdenMovimiento orden, EtiquetaSector[] etiquetas) { }

    Decision decidir(SimuladorBahia sim, SensorSimulado.Barrido barrido);

    /**
     * Avisa al piloto la orden que realmente se ejecutó en este ciclo (paso 6 del ciclo de la memoria).
     * Casi siempre es la que él decidió, pero no en la recolección de datos, donde a veces maneja el
     * maestro. Hay que llamarlo después de cada decidir. El maestro no tiene memoria: no hace nada.
     */
    default void registrarOrden(OrdenMovimiento ejecutada) { }

    static Piloto maestro() {
        return (sim, b) -> new Decision(Maestro.decidir(sim, b), b.etiquetas());
    }

    /**
     * Las dos redes, la memoria, el reflejo de seguridad y el detector de atasco, con el mismo ciclo que
     * Navegacion en el Servidor. Un piloto por prueba: tiene su propia MemoriaNavegacion y su propio DetectorAtasco.
     */
    static Piloto redes(RedPercepcion percepcion, RedMovimiento movimiento) {
        return new Piloto() {
            private final DetectorAtasco atasco = new DetectorAtasco();
            private final MemoriaNavegacion memoria = new MemoriaNavegacion();

            @Override public Decision decidir(SimuladorBahia sim, SensorSimulado.Barrido b) {
                if (sim.modo() == ModoOperativo.LIBERADA) return new Decision(new OrdenMovimiento(Movimiento.DETENER, 0), null);
                double[] probabilidades = percepcion.probabilidades(b.telemetria());
                EtiquetaSector[] etiquetas = RedPercepcion.etiquetas(probabilidades);
                double[] entradas = RedMovimiento.entradasDelCiclo(b.telemetria(), probabilidades, sim.modo(), memoria);
                OrdenMovimiento orden = movimiento.decidir(entradas);
                OrdenMovimiento segura = ReflejoSeguridad.ajustar(orden, b.telemetria(), etiquetas);
                return new Decision(atasco.revisar(b.telemetria(), segura), etiquetas);
            }

            @Override public void registrarOrden(OrdenMovimiento ejecutada) {
                memoria.registrarOrden(ejecutada);
            }
        };
    }

    static Piloto redes(Path percepcion, Path movimiento) {
        return redes(new RedPercepcion(percepcion), new RedMovimiento(movimiento));
    }
}
