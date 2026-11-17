// Archivo de simulacion
package orquitas.simulacion.bahia;

import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.OrdenMovimiento;
import orquitas.servidor.navegacion.ReflejoSeguridad;

/**
 * Maestro con mejoras que se prenden por separado, para medir cuánto gana cada una (uno por prueba:
 * guarda la memoria de lo ya visto). Sin ninguna opción decide igual que {@link Maestro}.
 * <ul>
 *   <li>giroYAvance: cuando gira para después avanzar, manda las dos órdenes con un solo barrido.</li>
 *   <li>memoria: explora hacia donde todavía no miró (grilla de 10 cm de lo que entró en el abanico
 *       del sensor), en lugar de avanzar si hay lugar y si no girar hacia lo más abierto.</li>
 *   <li>toleranciaEmbudo: apunta al Lobito con la tolerancia que permite la boca de 150 mm (más ancha
 *       cuanto más cerca está), y al estrecho con la que permite el hueco de 300 mm.</li>
 * </ul>
 */
public final class MaestroV2 {
    /** Opciones; todas apagadas = Maestro original. */
    public record Opciones(boolean giroYAvance, boolean memoria, boolean toleranciaEmbudo, boolean memoriaSuave,
                           boolean giroSeguro) {
        public static Opciones desde(String texto) {
            return new Opciones(texto.contains("giro"), texto.contains("memoria") || texto.contains("memsuave"),
                    texto.contains("embudo"), texto.contains("memsuave"), texto.contains("seguro"));
        }
    }

    /** Decisión: una orden y, si giroYAvance, el avance que sigue al giro (o null). */
    public record Plan(OrdenMovimiento orden, OrdenMovimiento avanceDespues) { }

    private static final double TOLERANCIA_GRADOS = 7;
    private static final double DESPEJE_MINIMO_MM = 45;
    private static final double PASO_EXPLORACION_MM = 450;
    private static final double FRENTE_CERCA_MM = 70;
    /** Margen lateral para que la canica entre en la boca (mitad de la boca menos esto). */
    private static final double MARGEN_BOCA_MM = 25;
    /** Alcance con el que se considera "mirada" una celda (lo que el Maestro ve como Lobito llega a 600). */
    private static final double ALCANCE_VISTA_MM = 550;
    private static final double CELDA_MM = 100;

    private final Opciones op;
    private final boolean[][] vista;
    private final int columnas, filas;

    public MaestroV2(Opciones op) {
        this.op = op;
        columnas = (int) Math.ceil(Bahia.BASE_MM / CELDA_MM);
        filas = (int) Math.ceil(Bahia.ALTO_MM / CELDA_MM);
        vista = new boolean[columnas][filas];
    }

    public Plan decidir(SimuladorBahia sim, SensorSimulado.Barrido barrido) {
        RobotSimulado r = sim.robot();
        if (sim.modo() == ModoOperativo.LIBERADA) return new Plan(new OrdenMovimiento(Movimiento.DETENER, 0), null);
        if (op.memoria) marcarVisto(sim.bahia(), r);
        double despeje = Maestro.despejeAdelante(sim.bahia(), r, sim.modo() != ModoOperativo.ESCAPE);
        if (despeje < DESPEJE_MINIMO_MM) return solo(salirDelPaso(sim, barrido), barrido);
        Plan p = decidirSinReflejo(sim, barrido, despeje);
        OrdenMovimiento segura = ReflejoSeguridad.ajustar(p.orden(), barrido.telemetria(), barrido.etiquetas());
        if (!segura.equals(p.orden())) return new Plan(segura, null);
        return p;
    }

    private Plan solo(OrdenMovimiento o, SensorSimulado.Barrido b) {
        return new Plan(ReflejoSeguridad.ajustar(o, b.telemetria(), b.etiquetas()), null);
    }

    private Plan decidirSinReflejo(SimuladorBahia sim, SensorSimulado.Barrido barrido, double despeje) {
        Bahia bahia = sim.bahia();
        RobotSimulado r = sim.robot();
        RobotSimulado.Calibracion cal = r.calibracion();
        if (Maestro.frenteVisto(barrido) < FRENTE_CERCA_MM) return new Plan(salirDelPaso(sim, barrido), null);

        boolean escape = sim.modo() == ModoOperativo.ESCAPE;
        double[] meta = null;
        if (!escape && contiene(barrido, EtiquetaSector.LOBITO)) meta = lobitoVisible(bahia, r);
        if (escape && (contiene(barrido, EtiquetaSector.SALIDA) || cercaDelEstrecho(r))) meta = puntoDeEscape(r);

        if (meta != null) {
            boolean cerrada = !escape;
            double dx = meta[0] - r.x(), dy = meta[1] - r.y();
            double rumboMeta = Math.atan2(dy, dx);
            double distancia = Math.hypot(dx, dy) - cal.ejeAlFrenteMm();
            double direccion = rumboMeta;
            double avanceMaximo = escape ? distancia + 60 : Math.hypot(dx, dy) + 15;
            double tolerancia = tolerancia(r, meta, escape);
            double despejeMeta = Maestro.despejeEnDireccion(bahia, r, rumboMeta, cerrada);
            if (Math.abs(Math.toDegrees(Maestro.normalizar(rumboMeta - r.rumbo()))) <= tolerancia)
                despejeMeta = Math.min(despejeMeta, despeje);
            boolean desvio = false;
            if (despejeMeta + 10 < distancia && despejeMeta < 450) {
                direccion = Double.NaN;
                int lado = Maestro.ladoMasAbierto(barrido);
                for (double d0 : new double[] {40 * lado, -40 * lado, 70 * lado, -70 * lado}) {
                    double ang = rumboMeta + Math.toRadians(d0);
                    double d = Maestro.despejeEnDireccion(bahia, r, ang, cerrada);
                    if (d > 300) {
                        direccion = ang;
                        avanceMaximo = Math.min(250, d - 60);
                        desvio = true;
                        break;
                    }
                }
                if (Double.isNaN(direccion)) return girar(sim, Maestro.anguloMasAbierto(barrido, cerrada), barrido, Double.NaN);
            }
            double error = Maestro.normalizar(direccion - r.rumbo());
            if (Math.abs(Math.toDegrees(error)) > (desvio ? TOLERANCIA_GRADOS : tolerancia)) {
                // Después de girar, el frente apunta a la dirección: lo que se avanza sale de esa dirección.
                double despDir = Maestro.despejeEnDireccion(bahia, r, direccion, cerrada);
                double av = Math.min(avanceMaximo, (despDir - 40) / 1.3);
                return girar(sim, Math.toDegrees(error), barrido, av);
            }
            double avance = Math.min(avanceMaximo, (despeje - 40) / 1.3);
            if (avance < 20) return girar(sim, Maestro.ladoMasAbierto(barrido) * 45, barrido, Double.NaN);
            return new Plan(new OrdenMovimiento(Movimiento.AVANZAR, msRecto(cal, avance)), null);
        }

        if (op.memoria && !(op.memoriaSuave && despeje > 220)) {
            Plan p = explorarConMemoria(sim, barrido, despeje, !escape);
            if (p != null) return p;
        }
        if (despeje > 220)
            return new Plan(new OrdenMovimiento(Movimiento.AVANZAR, msRecto(cal, Math.min(PASO_EXPLORACION_MM, despeje - 150))), null);
        double g = Maestro.anguloMasAbierto(barrido, !escape);
        double despG = Maestro.despejeEnDireccion(bahia, r, r.rumbo() + Math.toRadians(g), !escape);
        return girar(sim, g, barrido, despG > 220 ? Math.min(PASO_EXPLORACION_MM, despG - 150) : Double.NaN);
    }

    /** Tolerancia de puntería (grados): fija, o la que permite la boca (Lobito) o el hueco (estrecho). */
    private double tolerancia(RobotSimulado r, double[] meta, boolean escape) {
        if (!op.toleranciaEmbudo) return TOLERANCIA_GRADOS;
        double dist = Math.max(1, Math.hypot(meta[0] - r.x(), meta[1] - r.y()));
        double lateral = escape ? (Bahia.SALIDA_MM - r.calibracion().anchoMm()) / 2 - 30
                : r.calibracion().bocaMm() / 2 - MARGEN_BOCA_MM;
        if (escape && dist > 0 && !(meta[1] > Bahia.ALTO_MM)) lateral = 80; // Punto frente al estrecho: radio 100.
        return Math.max(TOLERANCIA_GRADOS, Math.min(30, Math.toDegrees(Math.atan2(Math.max(lateral, 0), dist))));
    }

    /**
     * Explora hacia donde quedan más celdas sin mirar: prueba los 13 rumbos del barrido y dar media
     * vuelta, avanza lo que deja el despeje y cuenta las celdas nuevas que entrarían en el abanico.
     * Null si ningún rumbo deja avanzar.
     */
    private Plan explorarConMemoria(SimuladorBahia sim, SensorSimulado.Barrido barrido, double despeje, boolean cerrada) {
        RobotSimulado r = sim.robot();
        RobotSimulado.Calibracion cal = r.calibracion();
        double mejorPuntaje = -1, mejorGiro = 0, mejorAvance = 0;
        double[] giros = {-90, -75, -60, -45, -30, -15, 0, 15, 30, 45, 60, 75, 90, 135, -135, 180};
        for (double g : giros) {
            double rumbo = r.rumbo() + Math.toRadians(g);
            double desp = g == 0 ? despeje : Maestro.despejeEnDireccion(sim.bahia(), r, rumbo, cerrada);
            double av = Math.min(PASO_EXPLORACION_MM, desp - 150);
            if (av < 100) continue;
            if (g != 0 && !puedeGirar(sim, g)) continue;
            double ex = r.x() + av * Math.cos(rumbo), ey = r.y() + av * Math.sin(rumbo);
            double puntaje = nuevasEnAbanico(ex, ey, rumbo) - Math.abs(g) / 90.0; // Leve preferencia por girar poco.
            if (puntaje > mejorPuntaje) { mejorPuntaje = puntaje; mejorGiro = g; mejorAvance = av; }
        }
        if (mejorPuntaje < 0) return null;
        if (mejorGiro == 0) return new Plan(new OrdenMovimiento(Movimiento.AVANZAR, msRecto(cal, mejorAvance)), null);
        return girar(sim, mejorGiro, barrido, mejorAvance);
    }

    private int nuevasEnAbanico(double x, double y, double rumbo) {
        int n = 0;
        for (int i = 0; i < columnas; i++)
            for (int j = 0; j < filas; j++) {
                if (vista[i][j]) continue;
                double cx = -Bahia.BASE_MM / 2 + (i + 0.5) * CELDA_MM, cy = (j + 0.5) * CELDA_MM;
                double d = Math.hypot(cx - x, cy - y);
                if (d > ALCANCE_VISTA_MM) continue;
                if (Math.abs(Maestro.normalizar(Math.atan2(cy - y, cx - x) - rumbo)) <= Math.PI / 2) n++;
            }
        return n;
    }

    /** Marca las celdas que el sensor alcanza a ver desde la pose actual (sin nada en el medio). */
    private void marcarVisto(Bahia bahia, RobotSimulado r) {
        double sx = r.sensorX(), sy = r.sensorY();
        for (int i = 0; i < columnas; i++)
            for (int j = 0; j < filas; j++) {
                if (vista[i][j]) continue;
                double cx = -Bahia.BASE_MM / 2 + (i + 0.5) * CELDA_MM, cy = (j + 0.5) * CELDA_MM;
                double d = Math.hypot(cx - sx, cy - sy);
                if (d > ALCANCE_VISTA_MM) continue;
                double ang = Math.atan2(cy - sy, cx - sx);
                if (Math.abs(Maestro.normalizar(ang - r.rumbo())) > Math.toRadians(97)) continue;
                if (bahia.lanzarRayo(sx, sy, ang, 3000, false).distancia() + 30 < d) continue;
                vista[i][j] = true;
            }
    }

    /** Gira; con giroYAvance y un avance posible, el avance va en el mismo ciclo. */
    private Plan girar(SimuladorBahia sim, double grados, SensorSimulado.Barrido barrido, double avanceDespues) {
        RobotSimulado.Calibracion cal = sim.robot().calibracion();
        if (!puedeGirar(sim, grados)) {
            if (puedeGirar(sim, -grados)) { grados = -grados; avanceDespues = Double.NaN; }
            else return new Plan(salirDelPaso(sim, barrido), null);
        }
        int ms = (int) Math.round(Math.abs(grados) / cal.gradosPorSegundo() * 1000);
        OrdenMovimiento giro = new OrdenMovimiento(grados > 0 ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER, ms);
        OrdenMovimiento avance = null;
        if (op.giroSeguro && !Double.isNaN(avanceDespues)) {
            // El giro real puede quedar ±10 % corto o largo: más giro, más desvío; se avanza menos.
            avanceDespues = Math.abs(grados) > 100 ? Double.NaN : avanceDespues * (1 - Math.abs(grados) / 300);
        }
        if (op.giroYAvance && !Double.isNaN(avanceDespues) && avanceDespues >= 20)
            avance = new OrdenMovimiento(Movimiento.AVANZAR, msRecto(cal, avanceDespues));
        return new Plan(giro, avance);
    }

    private static OrdenMovimiento salirDelPaso(SimuladorBahia sim, SensorSimulado.Barrido barrido) {
        RobotSimulado.Calibracion cal = sim.robot().calibracion();
        if (puedeRetroceder(sim, 120)) return new OrdenMovimiento(Movimiento.RETROCEDER, msRecto(cal, 120));
        if (puedeRetroceder(sim, 50)) return new OrdenMovimiento(Movimiento.RETROCEDER, msRecto(cal, 50));
        int lado = Maestro.ladoMasAbierto(barrido);
        for (double g : new double[] {30 * lado, -30 * lado, 15 * lado, -15 * lado, 6 * lado, -6 * lado})
            if (puedeGirar(sim, g))
                return new OrdenMovimiento(g > 0 ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER,
                        (int) Math.round(Math.abs(g) / cal.gradosPorSegundo() * 1000));
        if (puedeRetroceder(sim, 15)) return new OrdenMovimiento(Movimiento.RETROCEDER, msRecto(cal, 15));
        if (Maestro.despejeAdelante(sim.bahia(), sim.robot(), false) > 20) return new OrdenMovimiento(Movimiento.AVANZAR, msRecto(cal, 12));
        return new OrdenMovimiento(Movimiento.GIRAR_IZQ, 50);
    }

    private static boolean puedeGirar(SimuladorBahia sim, double grados) {
        RobotSimulado r = sim.robot();
        double total = grados * 1.35;
        int pasos = (int) Math.ceil(Math.abs(total));
        for (int i = 1; i <= pasos; i++)
            if (r.choca(sim.bahia(), r.x(), r.y(), r.rumbo() + Math.toRadians(total * i / pasos))) return false;
        return true;
    }

    private static boolean puedeRetroceder(SimuladorBahia sim, double mm) {
        RobotSimulado r = sim.robot();
        for (double d = 5; d <= mm * 1.3; d += 5)
            if (r.choca(sim.bahia(), r.x() - d * Math.cos(r.rumbo()), r.y() - d * Math.sin(r.rumbo()), r.rumbo())) return false;
        return true;
    }

    private static int msRecto(RobotSimulado.Calibracion cal, double mm) {
        return (int) Math.round(mm / cal.mmPorSegundo() * 1000);
    }

    private static boolean contiene(SensorSimulado.Barrido b, EtiquetaSector e) {
        for (EtiquetaSector x : b.etiquetas()) if (x == e) return true;
        return false;
    }

    private static double[] lobitoVisible(Bahia bahia, RobotSimulado r) {
        double[] mejor = null;
        double mejorDist = 600;
        for (Bahia.Canica k : bahia.canicas()) {
            if (k.capturada) continue;
            double dx = k.x - r.sensorX(), dy = k.y - r.sensorY(), d = Math.hypot(dx, dy);
            double rumboRelativo = Maestro.normalizar(Math.atan2(dy, dx) - r.rumbo());
            if (Math.abs(rumboRelativo) > Math.toRadians(100) || d >= mejorDist) continue;
            double tapa = bahia.lanzarRayo(r.sensorX(), r.sensorY(), Math.atan2(dy, dx), 3000, false).distancia();
            if (tapa < d) continue;
            mejor = new double[] {k.x, k.y};
            mejorDist = d;
        }
        return mejor;
    }

    private static boolean cercaDelEstrecho(RobotSimulado r) {
        return r.y() > Bahia.ALTO_MM - 350 && Math.abs(r.x()) < 200;
    }

    private static double[] puntoDeEscape(RobotSimulado r) {
        double frente = Bahia.ALTO_MM - 260;
        boolean llego = Math.hypot(r.x(), r.y() - frente) < 100 || (r.y() > frente - 60 && Math.abs(r.x()) < 60);
        if (llego) return new double[] {0, Bahia.ALTO_MM + 400};
        return new double[] {0, frente};
    }
}
