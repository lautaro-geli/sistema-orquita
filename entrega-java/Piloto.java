// Archivo de simulacion
package orquitas.simulacion.bahia;

import java.nio.file.Path;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.OrdenMovimiento;
import orquitas.servidor.navegacion.RedMovimiento;
import orquitas.servidor.navegacion.RedPercepcion;

/** Quién maneja el robot en la simulación: el maestro (reglas con la geometría real) o las redes. */
public interface Piloto {
    /** Orden decidida y, si las hay, las etiquetas por sector que vio el piloto. */
    record Decision(OrdenMovimiento orden, EtiquetaSector[] etiquetas) { }

    Decision decidir(SimuladorBahia sim, SensorSimulado.Barrido barrido);

    static Piloto maestro() {
        return (sim, b) -> new Decision(Maestro.decidir(sim, b), b.etiquetas());
    }

    /** Las dos redes, igual que en el Servidor: solo ven la telemetría y el modo. */
    static Piloto redes(RedPercepcion percepcion, RedMovimiento movimiento) {
        return (sim, b) -> {
            if (sim.modo() == ModoOperativo.LIBERADA) return new Decision(new OrdenMovimiento(orquitas.servidor.navegacion.Movimiento.DETENER, 0), null);
            double[] probabilidades = percepcion.probabilidades(b.telemetria());
            OrdenMovimiento orden = movimiento.decidir(b.telemetria(), probabilidades, sim.modo());
            return new Decision(orden, RedPercepcion.etiquetas(probabilidades));
        };
    }

    static Piloto redes(Path percepcion, Path movimiento) {
        return redes(new RedPercepcion(percepcion), new RedMovimiento(movimiento));
    }
}
