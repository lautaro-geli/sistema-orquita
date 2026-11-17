// Archivo de simulacion
package orquitas.simulacion.bahia;

import java.nio.file.Path;
import orquitas.servidor.navegacion.DetectorAtasco;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.MemoriaNavegacion;
import orquitas.servidor.navegacion.MetaMovimiento;
import orquitas.servidor.navegacion.PilotoSeguimiento;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.OrdenMovimiento;
import orquitas.servidor.navegacion.RedMovimiento;
import orquitas.servidor.navegacion.RedPercepcion;
import orquitas.servidor.navegacion.ReflejoSeguridad;

/** Quién maneja el robot en la simulación: el maestro (reglas con la geometría real) o las redes. */
public interface Piloto {
    /**
     * Orden decidida, las etiquetas por sector que vio el piloto (si las hay) y, con "girar y avanzar",
     * el avance que va después del giro en el mismo barrido (null si no hay).
     */
    record Decision(OrdenMovimiento orden, EtiquetaSector[] etiquetas, OrdenMovimiento avanceDespues) {
        public Decision(OrdenMovimiento orden, EtiquetaSector[] etiquetas) { this(orden, etiquetas, null); }

        /** Ejecuta la decisión en el simulador (con el avance, si lo hay, en el mismo barrido). */
        public RobotSimulado.Resultado ejecutarEn(SimuladorBahia sim) {
            return avanceDespues == null ? sim.ejecutar(orden) : sim.ejecutarGiroYAvance(orden, avanceDespues);
        }
    }

    Decision decidir(SimuladorBahia sim, SensorSimulado.Barrido barrido);

    /**
     * Avisa al piloto la orden que realmente se ejecutó en este ciclo (paso 6 del ciclo de la memoria).
     * Casi siempre es la que él decidió, pero no en la recolección de datos, donde a veces maneja el
     * maestro. Hay que llamarlo después de cada decidir. El maestro no tiene memoria: no hace nada.
     */
    default void registrarOrden(OrdenMovimiento ejecutada) { }

    /** Avisa la decisión ejecutada: la orden y, si hubo, el avance después del giro. */
    default void registrar(Decision ejecutada) {
        registrarOrden(ejecutada.orden());
        if (ejecutada.avanceDespues() != null) registrarOrden(ejecutada.avanceDespues());
    }

    /**
     * Piloto de reglas con seguimiento (ver PilotoSeguimiento): ve solo la telemetría y las etiquetas de la
     * red de percepción (o, si percepcion es null, las etiquetas reales: percepción perfecta). Uno por prueba.
     */
    static Piloto seguimiento(RedPercepcion percepcion, boolean giroYAvance) {
        PilotoSeguimiento p = new PilotoSeguimiento(giroYAvance);
        return new Piloto() {
            @Override public Decision decidir(SimuladorBahia sim, SensorSimulado.Barrido b) {
                EtiquetaSector[] etiquetas = percepcion == null ? b.etiquetas() : RedPercepcion.etiquetas(percepcion.probabilidades(b.telemetria()));
                PilotoSeguimiento.Plan plan = p.decidir(b.telemetria(), etiquetas, sim.modo(), sim.capturas());
                return new Decision(plan.orden(), etiquetas, plan.avanceDespues());
            }

            @Override public void registrarOrden(OrdenMovimiento ejecutada) { p.registrarOrden(ejecutada); }
        };
    }

    /** Maestro v2 (ver MaestroV2): uno por prueba, porque puede tener memoria. */
    static Piloto maestroV2(MaestroV2.Opciones opciones) {
        MaestroV2 m = new MaestroV2(opciones);
        return (sim, b) -> {
            MaestroV2.Plan p = m.decidir(sim, b);
            return new Decision(p.orden(), b.etiquetas(), p.avanceDespues());
        };
    }

    static Piloto maestro() {
        return (sim, b) -> new Decision(Maestro.decidir(sim, b), b.etiquetas());
    }

    /**
     * Las dos redes, la memoria, el reflejo de seguridad y el detector de atasco, con el mismo ciclo que
     * Navegacion en el Servidor. Un piloto por prueba: tiene su propia MemoriaNavegacion y su propio DetectorAtasco.
     */
    static Piloto redes(RedPercepcion percepcion, RedMovimiento movimiento) {
        return redes(percepcion, movimiento, false);
    }

    /**
     * Con giroYAvance, cuando la red pide girar hacia un sector con mm, el avance va después del giro en
     * el mismo barrido (si el reflejo y el candado lo dejan: ver ReflejoSeguridad.avanceTrasGiro).
     */
    static Piloto redes(RedPercepcion percepcion, RedMovimiento movimiento, boolean giroYAvance) {
        return new Piloto() {
            private final DetectorAtasco atasco = new DetectorAtasco();
            private final MemoriaNavegacion memoria = new MemoriaNavegacion();

            @Override public Decision decidir(SimuladorBahia sim, SensorSimulado.Barrido b) {
                if (sim.modo() == ModoOperativo.LIBERADA) return new Decision(new OrdenMovimiento(Movimiento.DETENER, 0), null);
                double[] probabilidades = percepcion.probabilidades(b.telemetria());
                EtiquetaSector[] etiquetas = RedPercepcion.etiquetas(probabilidades);
                double[] entradas = RedMovimiento.entradasDelCicloDe(movimiento.variante(), b.telemetria(), probabilidades, sim.modo(), memoria);
                MetaMovimiento meta = giroYAvance ? movimiento.decidirMeta(entradas) : null;
                OrdenMovimiento orden = giroYAvance ? meta.comoOrden() : movimiento.decidir(entradas);
                OrdenMovimiento segura = ReflejoSeguridad.ajustar(orden, b.telemetria(), etiquetas, sim.modo());
                OrdenMovimiento elegida = ReflejoSeguridad.candadoSalida(atasco.revisar(b.telemetria(), segura), b.telemetria(), etiquetas, sim.modo());
                OrdenMovimiento avance = null;
                if (giroYAvance && elegida.equals(orden))
                    avance = ReflejoSeguridad.avanceTrasGiro(meta, b.telemetria(), etiquetas, sim.modo());
                return new Decision(elegida, etiquetas, avance);
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
