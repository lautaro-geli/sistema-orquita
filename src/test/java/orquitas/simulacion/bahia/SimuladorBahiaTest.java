// Archivo de simulacion
package orquitas.simulacion.bahia;

import static org.junit.Assert.*;

import java.util.Random;
import org.junit.Test;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.MemoriaNavegacion;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.OrdenMovimiento;
import orquitas.servidor.navegacion.RedMovimiento;
import orquitas.servidor.navegacion.RedPercepcion;
import orquitas.servidor.navegacion.RepresentacionIntermedia;

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
        // Desde el centro hacia arriba se pasa por el hueco de la salida; hacia abajo se toca la base.
        Bahia.Impacto arriba = b.lanzarRayo(0, 100, Math.PI / 2, 4000, false);
        assertTrue(arriba.cruzaSalida());
        assertEquals(EtiquetaSector.LIBRE, arriba.tipo()); // Afuera no hay nada.
        assertEquals(Bahia.ALTO_MM - 100, arriba.distanciaSalida(), 1e-6);
        Bahia.Impacto abajo = b.lanzarRayo(0, 100, -Math.PI / 2, 4000, false);
        assertEquals(100, abajo.distancia(), 1e-6);
        assertEquals(EtiquetaSector.PARED, abajo.tipo());
        // Ancho: 1520 mm (760 a cada lado). A la altura de las esquinas la pared ya es el arco: a y = 1
        // queda mucho antes de 760 y a y = 40 está donde lo pone la circunferencia de 150 mm de radio.
        assertEquals(760, b.lanzarRayo(0, 500, 0, 4000, false).distancia(), 1e-6);
        assertEquals(760, b.lanzarRayo(0, 500, Math.PI, 4000, false).distancia(), 1e-6);
        assertTrue(b.lanzarRayo(0, 1, 0, 4000, false).distancia() < 640);
        assertEquals(610 + Math.sqrt(150 * 150 - 110 * 110), b.lanzarRayo(0, 40, 0, 4000, false).distancia(), 2);
        // Alto: 1140 mm; fuera del hueco, arriba hay pared.
        Bahia.Impacto techo = b.lanzarRayo(400, 100, Math.PI / 2, 4000, false);
        assertEquals(Bahia.ALTO_MM - 100, techo.distancia(), 1e-6);
        assertEquals(EtiquetaSector.PARED, techo.tipo());
        assertFalse(techo.cruzaSalida());
        // Hueco de 300 mm centrado en el lado de arriba.
        assertEquals(Bahia.SALIDA_MM, b.salida().x2() - b.salida().x1(), 1e-9);
        assertEquals(-150, b.salida().x1(), 1e-9);
        assertEquals(Bahia.ALTO_MM, b.salida().y1(), 1e-9);
        assertTrue(b.lanzarRayo(140, 100, Math.PI / 2, 4000, false).cruzaSalida());
        assertFalse(b.lanzarRayo(160, 100, Math.PI / 2, 4000, false).cruzaSalida());
        // Un rayo a 45° hacia arriba a la izquierda toca el arco de la esquina: no se escapa por la unión de dos tramos.
        Bahia.Impacto esquina = b.lanzarRayo(-700, 1080, Math.toRadians(135), 4000, false);
        assertEquals(EtiquetaSector.PARED, esquina.tipo());
        assertEquals(150 - Math.hypot(90, 90), esquina.distancia(), 1);
        assertTrue(b.adentro(0, 600));
        assertFalse(b.adentro(0, 1300));
        assertFalse(b.afuera(0, 600));
        assertTrue(b.afuera(0, Bahia.ALTO_MM + 100));
    }

    @Test public void lasCuatroEsquinasSonCurvas() {
        Bahia b = Bahia.vacia();
        // El rectángulo llega hasta (±760, 0) y (±760, 1140), pero el redondeo de 150 mm deja esas puntas afuera.
        for (double x : new double[] {-755, 755})
            for (double y : new double[] {5, Bahia.ALTO_MM - 5})
                assertFalse("(" + x + ", " + y + ")", b.adentro(x, y));
        // Los centros de los arcos y los puntos sobre los lados rectos están adentro.
        for (double x : new double[] {-600, 600})
            for (double y : new double[] {150, Bahia.ALTO_MM - 150})
                assertTrue("(" + x + ", " + y + ")", b.adentro(x, y));
        assertTrue(b.adentro(-755, 500));
        assertTrue(b.adentro(0, 5));
        // Sobre la diagonal de la esquina, el borde está a 150 mm del centro del arco.
        double d = Math.cos(Math.PI / 4);
        assertTrue(b.adentro(-610 - 149 * d, 150 - 149 * d));
        assertFalse(b.adentro(-610 - 151 * d, 150 - 151 * d));
        // Con margen, la bahía se achica: a 100 mm de la pared el radio de la esquina queda en 50 mm.
        assertTrue(b.adentroConMargen(-610 - 49 * d, 150 - 49 * d, 100));
        assertFalse(b.adentroConMargen(-610 - 51 * d, 150 - 51 * d, 100));
        assertFalse(b.adentroConMargen(-700, 500, 100));
        // Ninguna pared se sale del rectángulo de 1520 x 1140 y todas forman un borde cerrado salvo el hueco.
        for (Bahia.Segmento s : b.paredes()) {
            for (double[] p : new double[][] {{s.x1(), s.y1()}, {s.x2(), s.y2()}}) {
                assertTrue(Math.abs(p[0]) <= Bahia.BASE_MM / 2 + 1e-9);
                assertTrue(p[1] >= -1e-9 && p[1] <= Bahia.ALTO_MM + 1e-9);
            }
        }
        for (int grados = 0; grados < 360; grados++) {
            Bahia.Impacto imp = b.lanzarRayo(0, 570, Math.toRadians(grados + 0.5), 4000, false);
            assertTrue("a " + grados + "° el rayo se escapa", imp.tipo() == EtiquetaSector.PARED || imp.cruzaSalida());
        }
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

    @Test public void lasBahiasAlAzarSonReproducibles() {
        for (long semilla = 0; semilla < 30; semilla++) {
            SimuladorBahia a = SimuladorBahia.alAzar(semilla, 3, 3, CAL, SensorSimulado.Ruido.NORMAL);
            SimuladorBahia c = SimuladorBahia.alAzar(semilla, 3, 3, CAL, SensorSimulado.Ruido.NORMAL);
            assertEquals(a.robot().x(), c.robot().x(), 0);
            assertEquals(a.robot().y(), c.robot().y(), 0);
            assertEquals(a.robot().rumbo(), c.robot().rumbo(), 0);
            assertEquals(a.bahia().obstaculos(), c.bahia().obstaculos());
            for (int i = 0; i < 3; i++) {
                assertEquals(a.bahia().canicas().get(i).x, c.bahia().canicas().get(i).x, 0);
                assertEquals(a.bahia().canicas().get(i).y, c.bahia().canicas().get(i).y, 0);
            }
            assertArrayEquals(a.medir().telemetria(), c.medir().telemetria());
        }
    }

    @Test public void lasBahiasAlAzarTienenDosObstaculosTresCanicasYLaPartidaEnSuRango() {
        for (long semilla = 0; semilla < 30; semilla++) {
            SimuladorBahia sim = SimuladorBahia.alAzar(semilla, 3, 3, CAL, SensorSimulado.Ruido.NORMAL);
            Bahia b = sim.bahia();
            RobotSimulado r = sim.robot();
            assertEquals(Bahia.OBSTACULOS * 4, b.obstaculos().size()); // 2 obstáculos de 4 lados.
            assertEquals(3, b.canicas().size());
            // Partida: costado izquierdo a 150-200 mm de la pared izquierda, cola a 150-200 mm de la base, rumbo 90° ± 3°.
            double costado = r.x() - CAL.anchoMm() / 2 + Bahia.BASE_MM / 2, cola = r.y() - CAL.largoMm() / 2;
            assertTrue("costado a " + costado, costado >= 150 && costado <= 200);
            assertTrue("cola a " + cola, cola >= 150 && cola <= 200);
            assertEquals(Bahia.PARTIDA_X, r.x(), Bahia.PARTIDA_RANGO_MM);
            assertEquals(Bahia.PARTIDA_Y, r.y(), Bahia.PARTIDA_RANGO_MM);
            assertEquals(90, Math.toDegrees(r.rumbo()), Bahia.PARTIDA_RANGO_GRADOS);
            assertFalse(r.choca(b, r.x(), r.y(), r.rumbo()));
            // Obstáculos de 90 mm enteros dentro de la bahía, lejos de la partida y con lugar entre ellos.
            double[][] centros = new double[Bahia.OBSTACULOS][];
            for (int o = 0; o < Bahia.OBSTACULOS; o++) {
                double cx = 0, cy = 0;
                for (int k = 0; k < 4; k++) {
                    Bahia.Segmento lado = b.obstaculos().get(o * 4 + k);
                    assertEquals(Bahia.OBSTACULO_MM, Math.hypot(lado.x2() - lado.x1(), lado.y2() - lado.y1()), 1e-6);
                    assertTrue(b.adentroConMargen(lado.x1(), lado.y1(), 60));
                    cx += lado.x1() / 4; cy += lado.y1() / 4;
                }
                centros[o] = new double[] {cx, cy};
                assertTrue(Math.hypot(cx - Bahia.PARTIDA_X, cy - Bahia.PARTIDA_Y) >= Bahia.OBSTACULO_A_PARTIDA_MM - 1e-6);
                assertTrue(Math.hypot(cx, cy - (Bahia.ALTO_MM - 150)) >= Bahia.OBSTACULO_A_ESTRECHO_MM - 1e-6);
            }
            assertTrue(Math.hypot(centros[0][0] - centros[1][0], centros[0][1] - centros[1][1]) >= Bahia.SEPARACION_OBSTACULOS_MM - 1e-6);
            // Canicas dentro de la bahía, separadas de las paredes, de los obstáculos y de la partida.
            for (Bahia.Canica k : b.canicas()) {
                assertTrue(b.adentroConMargen(k.x, k.y, Bahia.MARGEN_CANICA_MM));
                assertTrue(Math.hypot(k.x - Bahia.PARTIDA_X, k.y - Bahia.PARTIDA_Y) >= Bahia.CANICA_A_PARTIDA_MM);
                for (double[] c : centros) assertTrue(Math.hypot(k.x - c[0], k.y - c[1]) >= Bahia.CANICA_A_OBSTACULO_MM - 1e-6);
            }
        }
    }

    /** Percepción de prueba: todos los sectores PARED. */
    static class PercepcionFija extends RedPercepcion {
        @Override public synchronized double[] probabilidades(int[] telemetria) {
            EtiquetaSector[] e = new EtiquetaSector[13];
            java.util.Arrays.fill(e, EtiquetaSector.PARED);
            return RedPercepcion.salidaDeseada(e);
        }
    }

    /** Movimiento de prueba: devuelve siempre la misma orden y guarda las entradas que recibió. */
    static class MovimientoFijo extends RedMovimiento {
        final OrdenMovimiento orden;
        double[] entradasRecibidas;
        MovimientoFijo(OrdenMovimiento orden) { this.orden = orden; }
        @Override public synchronized OrdenMovimiento decidir(double[] entradas) {
            entradasRecibidas = entradas.clone();
            return orden;
        }
        double[] memoriaRecibida() {
            return java.util.Arrays.copyOfRange(entradasRecibidas, RepresentacionIntermedia.ENTRADAS, RedMovimiento.ENTRADAS);
        }
    }

    @Test public void elPilotoDeRedesTieneSuMemoriaYRegistraLaOrdenQueSeEjecuto() {
        SimuladorBahia sim = new SimuladorBahia(Bahia.vacia(), robot(0, 500, 90), new SensorSimulado(SensorSimulado.Ruido.NINGUNO), new Random(1), 3);
        MovimientoFijo mov = new MovimientoFijo(new OrdenMovimiento(Movimiento.GIRAR_IZQ, 250));
        Piloto piloto = Piloto.redes(new PercepcionFija(), mov);
        Piloto.Decision d = piloto.decidir(sim, sim.medir());
        assertEquals(new OrdenMovimiento(Movimiento.GIRAR_IZQ, 250), d.orden());
        assertEquals(24, mov.entradasRecibidas.length);
        assertArrayEquals(new MemoriaNavegacion().entradas(), mov.memoriaRecibida(), 0); // Primer ciclo: memoria vacía.
        // Como en la recolección DAgger: se ejecuta otra orden (la del maestro) y se le avisa al piloto.
        OrdenMovimiento ejecutada = new OrdenMovimiento(Movimiento.GIRAR_DER, 750);
        sim.ejecutar(ejecutada);
        piloto.registrarOrden(ejecutada);
        piloto.decidir(sim, sim.medir());
        double[] memoria = mov.memoriaRecibida();
        assertEquals(1, memoria[MemoriaNavegacion.ORDEN_GIRAR_DER], 0);
        assertEquals(0, memoria[MemoriaNavegacion.ORDEN_GIRAR_IZQ], 0);
        assertEquals(0.5, memoria[MemoriaNavegacion.CUANTO_ANTERIOR], 1e-12);
        assertEquals(0, memoria[MemoriaNavegacion.RUMBO_SENO], 1e-12); // ψ = -90°.
        assertEquals(0.5, memoria[MemoriaNavegacion.RUMBO_COSENO], 1e-12);
        // Cada piloto tiene su propia memoria: uno nuevo empieza de cero.
        Piloto.redes(new PercepcionFija(), mov).decidir(sim, sim.medir());
        assertArrayEquals(new MemoriaNavegacion().entradas(), mov.memoriaRecibida(), 0);
        // El maestro no tiene memoria: avisarle una orden no hace nada.
        Piloto.maestro().registrarOrden(ejecutada);
    }

    @Test public void laPartidaEstaAbajoALaIzquierdaMirandoALaSalida() {
        // Con el robot de 150 x 200 mm: centro en (-760 + 75 + 175, 100 + 175).
        assertEquals(-510, Bahia.PARTIDA_X, 1e-9);
        assertEquals(275, Bahia.PARTIDA_Y, 1e-9);
        assertEquals(25, Bahia.PARTIDA_RANGO_MM, 1e-9);
    }
}
