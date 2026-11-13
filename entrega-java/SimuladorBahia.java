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
     * Prueba al azar: bahía con 2 a 4 obstáculos y las canicas pedidas, robot en la partida (abajo, al
     * centro) mirando hacia arriba con un desvío de hasta ±40°.
     */
    public static SimuladorBahia alAzar(long semilla, int canicas, int umbral, RobotSimulado.Calibracion cal,
                                        SensorSimulado.Ruido ruido) {
        Random azar = new Random(semilla);
        while (true) {
            Bahia bahia = Bahia.generar(azar, 2 + azar.nextInt(3), canicas);
            double rumbo = Math.PI / 2 + Math.toRadians((azar.nextDouble() * 2 - 1) * 40);
            RobotSimulado robot = new RobotSimulado(cal, (azar.nextDouble() * 2 - 1) * 80, 150, rumbo);
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
