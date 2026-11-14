// Archivo de simulacion
package orquitas.simulacion.bahia;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.OrdenMovimiento;

/**
 * Una prueba en la bahía simulada, con el mismo ciclo que el robot real: barrido -> orden -> paso.
 * Lleva capturas, choques, modo (BUSQUEDA, ESCAPE, LIBERADA), tiempo estimado y puntaje del reglamento.
 */
public final class SimuladorBahia {
    /** Tiempo de un barrido real (13 posiciones), en segundos. */
    public static final double SEGUNDOS_BARRIDO = 4.8;
    /** Tope de tiempo de la prueba de clasificación (7 minutos). */
    public static final double SEGUNDOS_MAXIMOS = 7 * 60;

    private final Bahia bahia;
    private final RobotSimulado robot;
    private final SensorSimulado sensor;
    private final Random azar;
    private final int umbralCapturas;
    private ModoOperativo modo = ModoOperativo.BUSQUEDA;
    private int capturas, choques, ciclos;
    private double segundos;
    private final List<double[]> recorrido = new ArrayList<>();

    public SimuladorBahia(Bahia bahia, RobotSimulado robot, SensorSimulado sensor, Random azar, int umbralCapturas) {
        this.bahia = bahia;
        this.robot = robot;
        this.sensor = sensor;
        this.azar = azar;
        this.umbralCapturas = umbralCapturas;
        recorrido.add(new double[] {robot.x(), robot.y()});
    }

    /**
     * Prueba al azar: bahía con 2 obstáculos y las canicas pedidas en lugares al azar. El robot sale
     * siempre abajo a la izquierda mirando hacia la salida: el centro se sortea alrededor de
     * (Bahia.PARTIDA_X, Bahia.PARTIDA_Y) en ± Bahia.PARTIDA_RANGO_MM (el costado izquierdo y la cola quedan
     * a 150-200 mm de la pared izquierda y de la base) y el rumbo en 90° ± Bahia.PARTIDA_RANGO_GRADOS.
     * Todo sale del Random de la prueba: con la misma semilla, la misma bahía y la misma partida.
     */
    public static SimuladorBahia alAzar(long semilla, int canicas, int umbral, RobotSimulado.Calibracion cal,
                                        SensorSimulado.Ruido ruido) {
        Random azar = new Random(semilla);
        while (true) {
            Bahia bahia = Bahia.generar(azar, Bahia.OBSTACULOS, canicas);
            double rumbo = Math.PI / 2 + Math.toRadians((azar.nextDouble() * 2 - 1) * Bahia.PARTIDA_RANGO_GRADOS);
            RobotSimulado robot = new RobotSimulado(cal, Bahia.PARTIDA_X + (azar.nextDouble() * 2 - 1) * Bahia.PARTIDA_RANGO_MM,
                    Bahia.PARTIDA_Y + (azar.nextDouble() * 2 - 1) * Bahia.PARTIDA_RANGO_MM, rumbo);
            if (!robot.choca(bahia, robot.x(), robot.y(), robot.rumbo()))
                return new SimuladorBahia(bahia, robot, new SensorSimulado(ruido), azar, umbral);
        }
    }

    /** Barrido desde la pose actual. */
    public SensorSimulado.Barrido medir() {
        return sensor.medir(bahia, robot, azar);
    }

    /** Ejecuta la orden, cuenta choques y capturas y actualiza el modo. */
    public RobotSimulado.Resultado ejecutar(OrdenMovimiento orden) {
        ciclos++;
        segundos += SEGUNDOS_BARRIDO + orden.milisegundos() / 1000.0;
        RobotSimulado.Resultado r = robot.mover(orden, bahia, azar);
        if (r.choco()) choques++;
        capturas += r.capturas();
        if (modo == ModoOperativo.BUSQUEDA && capturas >= umbralCapturas) modo = ModoOperativo.ESCAPE;
        if (modo == ModoOperativo.ESCAPE && bahia.afuera(robot.x(), robot.y())) modo = ModoOperativo.LIBERADA;
        recorrido.add(new double[] {robot.x(), robot.y()});
        return r;
    }

    public boolean terminada() {
        return modo == ModoOperativo.LIBERADA || segundos >= SEGUNDOS_MAXIMOS;
    }

    /**
     * Puntaje según el reglamento: 5/10/15 por capturas, +15 Modo Escape, +30 abandono, bonificación por
     * tiempo si completa la misión, -5 por choque (muros u obstáculos).
     */
    public int puntaje() {
        int p = 0;
        int[] porCaptura = {5, 10, 15};
        for (int i = 0; i < Math.min(capturas, 3); i++) p += porCaptura[i];
        if (modo != ModoOperativo.BUSQUEDA) p += 15;
        if (modo == ModoOperativo.LIBERADA) {
            p += 30;
            if (segundos <= 60) p += 15;
            else if (segundos <= 240) p += 10;
            else if (segundos <= 600) p += 5;
        }
        return p - 5 * choques;
    }

    public Bahia bahia() { return bahia; }
    public RobotSimulado robot() { return robot; }
    public ModoOperativo modo() { return modo; }
    public int capturas() { return capturas; }
    public int choques() { return choques; }
    public int ciclos() { return ciclos; }
    public double segundos() { return segundos; }
    public int umbralCapturas() { return umbralCapturas; }
    public List<double[]> recorrido() { return recorrido; }
}
