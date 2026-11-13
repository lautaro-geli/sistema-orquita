// Archivo de simulacion
package orquitas.simulacion.bahia;

import static org.junit.Assert.*;

import java.util.Random;
import org.junit.Test;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.OrdenMovimiento;

/** Bahía simulada: geometría, sensor, capturas, choques, escenas mixtas y el maestro. */
public class SimuladorBahiaTest {
    private static final RobotSimulado.Calibracion CAL = RobotSimulado.Calibracion.ESTIMADA;
    private static final RobotSimulado.Calibracion SIN_RUIDO =
            new RobotSimulado.Calibracion(CAL.mmPorSegundo(), CAL.gradosPorSegundo(), CAL.largoMm(), CAL.anchoMm(), CAL.bocaMm(), 0);

    private static RobotSimulado robot(double x, double y, double grados) {
        return new RobotSimulado(SIN_RUIDO, x, y, Math.toRadians(grados));
    }

    private static SensorSimulado.Barrido medir(Bahia b, RobotSimulado r) {
        return new SensorSimulado(SensorSimulado.Ruido.NINGUNO).medir(b, r, new Random(1));
    }

    @Test public void geometriaDeLaBahia() {
        Bahia b = Bahia.vacia();
        // Desde el centro hacia arriba se pasa por el estrecho; hacia abajo se toca la base recta.
        Bahia.Impacto arriba = b.lanzarRayo(0, 100, Math.PI / 2, 4000, false);
        assertTrue(arriba.cruzaSalida());
        assertEquals(EtiquetaSector.LIBRE, arriba.tipo());
        Bahia.Impacto abajo = b.lanzarRayo(0, 100, -Math.PI / 2, 4000, false);
        assertEquals(100, abajo.distancia(), 1e-6);
        assertEquals(EtiquetaSector.PARED, abajo.tipo());
        // Ancho de la base: 1150 mm; ancho del estrecho: 300 mm.
        assertEquals(575, b.lanzarRayo(0, 1, 0, 4000, false).distancia(), 1);
        assertEquals(Bahia.SALIDA_MM, b.salida().x2() - b.salida().x1(), 1e-9);
        assertTrue(b.adentro(0, 600));
        assertFalse(b.adentro(0, 1300));
        assertTrue(b.afuera(0, Bahia.ALTO_MM + 100));
    }

    @Test public void avanzarHastaElMuroEsChoqueYNoLoAtraviesa() {
        Bahia b = Bahia.vacia();
        RobotSimulado r = robot(0, 300, -90); // Mirando a la base.
        RobotSimulado.Resultado res = r.mover(new OrdenMovimiento(Movimiento.AVANZAR, 1500), b, new Random(1));
        assertTrue(res.choco());
        assertTrue(r.y() >= CAL.largoMm() / 2 - 1);
        assertFalse(r.choca(b, r.x(), r.y(), r.rumbo()));
    }

    @Test public void girarCalculaElAnguloConLaCalibracion() {
        Bahia b = Bahia.vacia();
        RobotSimulado r = robot(0, 500, 90);
        int ms = (int) Math.round(90 / CAL.gradosPorSegundo() * 1000);
        r.mover(new OrdenMovimiento(Movimiento.GIRAR_DER, ms), b, new Random(1));
        assertEquals(0, Math.toDegrees(r.rumbo()), 1.5);
    }

    @Test public void laCanicaQueEntraPorLaBocaSeCaptura() {
        Bahia b = Bahia.vacia();
        b.agregarCanica(0, 500);
        RobotSimulado r = robot(0, 300, 90);
        RobotSimulado.Resultado res = r.mover(new OrdenMovimiento(Movimiento.AVANZAR, 800), b, new Random(1));
        assertEquals(1, res.capturas());
        assertEquals(0, b.canicasLibres());
    }

    @Test public void laCanicaQueTocaElCostadoSeEmpujaYNoSeCaptura() {
        Bahia b = Bahia.vacia();
        b.agregarCanica(70, 500); // Fuera de la boca (60 mm), dentro del ancho del robot (150 mm).
        RobotSimulado r = robot(0, 300, 90);
        RobotSimulado.Resultado res = r.mover(new OrdenMovimiento(Movimiento.AVANZAR, 800), b, new Random(1));
        assertEquals(0, res.capturas());
        assertEquals(1, b.canicasLibres());
    }

    @Test public void sensorVeLaCanicaCercaMasCercaEnEl4x4QueEnEl16x16() {
        Bahia b = Bahia.vacia();
        RobotSimulado r = robot(0, 300, 90);
        b.agregarCanica(r.sensorX(), r.sensorY() + 70);
        SensorSimulado.Barrido m = medir(b, r);
        int s = 6; // Frente.
        assertEquals(EtiquetaSector.LOBITO, m.etiquetas()[s]);
        assertTrue("4x4 " + m.telemetria()[s * 3] + " < 16x16 " + m.telemetria()[s * 3 + 2],
                m.telemetria()[s * 3] < m.telemetria()[s * 3 + 2]);
    }

    @Test public void escenaMixtaTieneEtiquetasDistintasPorSector() {
        // Pared de fondo, un obstáculo cerca a la derecha y un Lobito a la izquierda en el mismo barrido.
        Bahia b = Bahia.vacia();
        RobotSimulado r = robot(0, 250, 180); // Mirando a la pared izquierda: el estrecho queda fuera del abanico.
        double sx = r.sensorX(), sy = r.sensorY();
        b.agregarObstaculo(sx - 50, sy + 200, 0);               // Derecha del robot (que mira a -x): +y.
        b.agregarCanica(sx - 40 * Math.cos(Math.toRadians(45)), sy - 40 * Math.sin(Math.toRadians(45))); // Izquierda-frente.
        SensorSimulado.Barrido m = medir(b, r);
        java.util.Set<EtiquetaSector> vistas = java.util.EnumSet.noneOf(EtiquetaSector.class);
        for (EtiquetaSector e : m.etiquetas()) vistas.add(e);
        assertTrue(java.util.Arrays.toString(m.etiquetas()), vistas.contains(EtiquetaSector.PARED));
        assertTrue(java.util.Arrays.toString(m.etiquetas()), vistas.contains(EtiquetaSector.OBSTACULO));
        assertTrue(java.util.Arrays.toString(m.etiquetas()), vistas.contains(EtiquetaSector.LOBITO));
    }

    @Test public void sinRuidoNoHaySinEcoDentroDeLaBahia() {
        Bahia b = Bahia.vacia();
        SensorSimulado.Barrido m = medir(b, robot(0, 300, -90 + 180)); // Mirando a la salida.
        int sinEco = 0;
        for (int v : m.telemetria()) if (v == -1) sinEco++;
        // Solo los sectores que miran por el estrecho pueden quedar sin eco.
        int salida = 0;
        for (EtiquetaSector e : m.etiquetas()) if (e == EtiquetaSector.SALIDA) salida++;
        assertTrue(salida > 0);
        assertTrue(sinEco <= salida * 3);
    }

    @Test public void elMaestroVaHaciaElLobitoQueVe() {
        Bahia b = Bahia.vacia();
        RobotSimulado r = robot(0, 300, 90);
        b.agregarCanica(r.sensorX() + 150, r.sensorY() + 150); // 45° a la derecha.
        SimuladorBahia sim = new SimuladorBahia(b, r, new SensorSimulado(SensorSimulado.Ruido.NINGUNO), new Random(1), 3);
        SensorSimulado.Barrido m = sim.medir();
        OrdenMovimiento o = Maestro.decidir(sim, m);
        assertEquals(Movimiento.GIRAR_DER, o.movimiento());
        sim.ejecutar(o);
        for (int i = 0; i < 6 && sim.capturas() == 0; i++) sim.ejecutar(Maestro.decidir(sim, sim.medir()));
        assertEquals(1, sim.capturas());
    }

    @Test public void conElUmbralCumplidoPasaAEscapeYElMaestroSaleDeLaBahia() {
        Bahia b = Bahia.vacia();
        b.agregarCanica(0, 450);
        RobotSimulado r = robot(0, 250, 90);
        SimuladorBahia sim = new SimuladorBahia(b, r, new SensorSimulado(SensorSimulado.Ruido.NINGUNO), new Random(2), 1);
        while (!sim.terminada()) sim.ejecutar(Maestro.decidir(sim, sim.medir()));
        assertEquals(ModoOperativo.LIBERADA, sim.modo());
        assertEquals(0, sim.choques());
        assertTrue(sim.puntaje() >= 5 + 15 + 30);
    }

    @Test public void lasBahiasAlAzarSonReproduciblesYLaPartidaNoChoca() {
        for (long semilla = 0; semilla < 30; semilla++) {
            SimuladorBahia a = SimuladorBahia.alAzar(semilla, 3, 3, CAL, SensorSimulado.Ruido.NORMAL);
            SimuladorBahia c = SimuladorBahia.alAzar(semilla, 3, 3, CAL, SensorSimulado.Ruido.NORMAL);
            assertEquals(a.robot().x(), c.robot().x(), 0);
            assertEquals(a.bahia().obstaculos().size(), c.bahia().obstaculos().size());
            assertEquals(3, a.bahia().canicas().size());
            assertEquals(Bahia.OBSTACULOS * 4, a.bahia().obstaculos().size());
            assertEquals(Bahia.PARTIDA_X, a.robot().x(), 15);
            assertEquals(90, Math.toDegrees(a.robot().rumbo()), 3);
            assertFalse(a.robot().choca(a.bahia(), a.robot().x(), a.robot().y(), a.robot().rumbo()));
        }
    }
}
