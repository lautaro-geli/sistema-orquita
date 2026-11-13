package orquitas.simulacion.bahia;

import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.OrdenMovimiento;

/**
 * Piloto "maestro" del simulador: conoce la geometría real y decide la orden ideal (dirección y ms).
 * Solo persigue lo que el barrido realmente ve (sectores LOBITO o SALIDA de ese barrido) y no tiene
 * memoria, así su decisión depende de lo mismo que ve la red: la red de movimiento aprende a imitarlo.
 *
 * Reglas: (1) si está por chocar, retrocede; (2) BUSQUEDA: si ve un Lobito, gira hasta tenerlo de frente
 * y avanza hasta tragarlo (esquiva si hay un obstáculo en el medio); (3) ESCAPE: si ve la salida, va a
 * un punto frente al estrecho y después lo cruza; (4) si no ve nada útil, explora: avanza si hay lugar
 * y si no gira hacia el lado más abierto.
 */
public final class Maestro {
    private static final double TOLERANCIA_GRADOS = 7;
    private static final double DESPEJE_MINIMO_MM = 45;
    private static final double PASO_EXPLORACION_MM = 450;
    private static final double VISTA_LOBITO_MM = 600;

    private Maestro() { }

    public static OrdenMovimiento decidir(SimuladorBahia sim, SensorSimulado.Barrido barrido) {
        Bahia bahia = sim.bahia();
        RobotSimulado r = sim.robot();
        RobotSimulado.Calibracion cal = r.calibracion();
        if (sim.modo() == ModoOperativo.LIBERADA) return new OrdenMovimiento(Movimiento.DETENER, 0);

        double despeje = despejeAdelante(bahia, r, sim.modo() != ModoOperativo.ESCAPE);
        if (despeje < DESPEJE_MINIMO_MM) return salirDelPaso(sim, barrido);

        double[] meta = null;
        if (sim.modo() == ModoOperativo.BUSQUEDA && contiene(barrido, EtiquetaSector.LOBITO)) meta = lobitoVisible(bahia, r);
        if (sim.modo() == ModoOperativo.ESCAPE && (contiene(barrido, EtiquetaSector.SALIDA) || cercaDelEstrecho(r)))
            meta = puntoDeEscape(r);

        if (meta != null) {
            boolean cerrada = sim.modo() != ModoOperativo.ESCAPE;
            double dx = meta[0] - r.x(), dy = meta[1] - r.y();
            double rumboMeta = Math.atan2(dy, dx);
            double distancia = Math.hypot(dx, dy) - cal.largoMm() / 2;
            double direccion = rumboMeta;
            double avanceMaximo = distancia + (sim.modo() == ModoOperativo.ESCAPE ? 60 : 40);
            double despejeMeta = despejeEnDireccion(bahia, r, rumboMeta, cerrada);
            if (Math.abs(Math.toDegrees(normalizar(rumboMeta - r.rumbo()))) <= TOLERANCIA_GRADOS)
                despejeMeta = Math.min(despejeMeta, despeje);
            if (despejeMeta + 10 < distancia && despejeMeta < 450) {
                // Algo en el medio: desvío medido desde la dirección de la meta (estable entre ciclos).
                direccion = Double.NaN;
                int lado = ladoMasAbierto(barrido);
                for (double desvio : new double[] {40 * lado, -40 * lado, 70 * lado, -70 * lado}) {
                    double ang = rumboMeta + Math.toRadians(desvio);
                    double d = despejeEnDireccion(bahia, r, ang, cerrada);
                    if (d > 300) {
                        direccion = ang;
                        avanceMaximo = Math.min(250, d - 60);
                        break;
                    }
                }
                if (Double.isNaN(direccion)) return girar(sim, anguloMasAbierto(barrido, cerrada), barrido);
            }
            double error = normalizar(direccion - r.rumbo());
            if (Math.abs(Math.toDegrees(error)) > TOLERANCIA_GRADOS) return girar(sim, Math.toDegrees(error), barrido);
            double avance = Math.min(avanceMaximo, (despeje - 40) / 1.3); // Margen por el ruido de velocidad.
            if (avance < 20) return girar(sim, ladoMasAbierto(barrido) * 45, barrido);
            return new OrdenMovimiento(Movimiento.AVANZAR, msRecto(cal, avance));
        }

        // Explorar.
        if (despeje > 220) {
            return new OrdenMovimiento(Movimiento.AVANZAR, msRecto(cal, Math.min(PASO_EXPLORACION_MM, despeje - 150)));
        }
        return girar(sim, anguloMasAbierto(barrido, sim.modo() != ModoOperativo.ESCAPE), barrido);
    }

    /**
     * Ángulo (grados, positivo = izquierda) hacia el sector más abierto que no sea el frente. Si nada
     * está más abierto que 400 mm, da media vuelta hacia el lado más abierto (para ver lo de atrás).
     */
    static double anguloMasAbierto(SensorSimulado.Barrido b, boolean salidaCerrada) {
        int mejor = -1;
        double mejorDistancia = 400;
        for (int s = 0; s < 13; s++) {
            if (s >= 5 && s <= 7) continue;
            double d = Math.min(b.distanciasReales()[s], 1300);
            if (salidaCerrada && b.etiquetas()[s] == EtiquetaSector.SALIDA) d = 300;
            if (d > mejorDistancia) { mejor = s; mejorDistancia = d; }
        }
        if (mejor < 0) return ladoMasAbierto(b) * 120;
        return 15.0 * mejor - 90;
    }

    /** Gira grados (positivo = izquierda). Si el giro chocaría, busca otra forma de salir. */
    private static OrdenMovimiento girar(SimuladorBahia sim, double grados, SensorSimulado.Barrido barrido) {
        RobotSimulado.Calibracion cal = sim.robot().calibracion();
        if (!puedeGirar(sim, grados)) {
            if (puedeGirar(sim, -grados)) grados = -grados;
            else return salirDelPaso(sim, barrido);
        }
        int ms = (int) Math.round(Math.abs(grados) / cal.gradosPorSegundo() * 1000);
        return new OrdenMovimiento(grados > 0 ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER, ms);
    }

    /** Pegado a algo: retrocede si atrás hay lugar; si no, gira poco hacia donde se pueda. */
    private static OrdenMovimiento salirDelPaso(SimuladorBahia sim, SensorSimulado.Barrido barrido) {
        RobotSimulado.Calibracion cal = sim.robot().calibracion();
        if (puedeRetroceder(sim, 120)) return new OrdenMovimiento(Movimiento.RETROCEDER, msRecto(cal, 120));
        if (puedeRetroceder(sim, 50)) return new OrdenMovimiento(Movimiento.RETROCEDER, msRecto(cal, 50));
        int lado = ladoMasAbierto(barrido);
        for (double g : new double[] {30 * lado, -30 * lado, 15 * lado, -15 * lado, 6 * lado, -6 * lado})
            if (puedeGirar(sim, g))
                return new OrdenMovimiento(g > 0 ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER,
                        (int) Math.round(Math.abs(g) / cal.gradosPorSegundo() * 1000));
        if (puedeRetroceder(sim, 15)) return new OrdenMovimiento(Movimiento.RETROCEDER, msRecto(cal, 15));
        if (despejeAdelante(sim.bahia(), sim.robot(), false) > 20) return new OrdenMovimiento(Movimiento.AVANZAR, msRecto(cal, 12));
        return new OrdenMovimiento(Movimiento.GIRAR_IZQ, 50);
    }

    private static boolean puedeGirar(SimuladorBahia sim, double grados) {
        RobotSimulado r = sim.robot();
        double total = grados * 1.35; // Margen por el ruido de velocidad del giro.
        int pasos = (int) Math.ceil(Math.abs(total)); // Cada 1°, como el movimiento.
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

    /** +1 si la izquierda (sectores 8 a 12) está más abierta que la derecha (0 a 4), si no -1. */
    static int ladoMasAbierto(SensorSimulado.Barrido b) {
        double der = 0, izq = 0;
        for (int s = 0; s < 5; s++) der += Math.min(b.distanciasReales()[s], 1300);
        for (int s = 8; s < 13; s++) izq += Math.min(b.distanciasReales()[s], 1300);
        return izq >= der ? 1 : -1;
    }

    /**
     * Distancia libre en línea recta delante del frente del robot (corredor del ancho del robot).
     * Con salidaCerrada el estrecho cuenta como pared (en búsqueda no hay que salir de la bahía).
     */
    static double despejeAdelante(Bahia bahia, RobotSimulado r, boolean salidaCerrada) {
        return despejeEnDireccion(bahia, r, r.rumbo(), salidaCerrada);
    }

    /** Como despejeAdelante, pero para un corredor que sale del centro del robot en la dirección ang. */
    static double despejeEnDireccion(Bahia bahia, RobotSimulado r, double ang, boolean salidaCerrada) {
        RobotSimulado.Calibracion cal = r.calibracion();
        double c = Math.cos(ang), s = Math.sin(ang);
        double minimo = Double.MAX_VALUE;
        for (int k = 0; k <= 6; k++) {
            double lateral = (k / 6.0 - 0.5) * (cal.anchoMm() + 40);
            double ox = r.x() - lateral * s, oy = r.y() + lateral * c;
            Bahia.Impacto imp = bahia.lanzarRayo(ox, oy, ang, 3000, false);
            minimo = Math.min(minimo, salidaCerrada && imp.cruzaSalida() ? imp.distanciaSalida() : imp.distancia());
        }
        return minimo - cal.largoMm() / 2;
    }

    private static boolean contiene(SensorSimulado.Barrido b, EtiquetaSector e) {
        for (EtiquetaSector x : b.etiquetas()) if (x == e) return true;
        return false;
    }

    /** Canica libre más cercana dentro del abanico del sensor, sin nada en el medio. */
    private static double[] lobitoVisible(Bahia bahia, RobotSimulado r) {
        double[] mejor = null;
        double mejorDist = VISTA_LOBITO_MM;
        for (Bahia.Canica k : bahia.canicas()) {
            if (k.capturada) continue;
            double dx = k.x - r.sensorX(), dy = k.y - r.sensorY(), d = Math.hypot(dx, dy);
            double rumboRelativo = normalizar(Math.atan2(dy, dx) - r.rumbo());
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

    /** Primero un punto frente al estrecho; ya alineado, un punto del otro lado. */
    private static double[] puntoDeEscape(RobotSimulado r) {
        double frente = Bahia.ALTO_MM - 260;
        boolean llego = Math.hypot(r.x(), r.y() - frente) < 100 || (r.y() > frente - 60 && Math.abs(r.x()) < 60);
        if (llego) return new double[] {0, Bahia.ALTO_MM + 400};
        return new double[] {0, frente};
    }

    static double normalizar(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}
