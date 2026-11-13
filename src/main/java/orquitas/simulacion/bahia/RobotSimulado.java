// Archivo de simulacion
package orquitas.simulacion.bahia;

import java.util.Random;
import orquitas.servidor.navegacion.CalibracionRobot;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.OrdenMovimiento;

/**
 * Robot de la simulación: rectángulo de largo x ancho con el sensor adelante y la "boca" (entrada de
 * la canica al sensor FC-51) en el centro del frente. Pose (x, y, rumbo): rumbo en radianes, 0 = eje x,
 * positivo = giro a la izquierda (antihorario).
 */
public final class RobotSimulado {

    /**
     * Calibración del robot real. [A CONFIRMAR] con mediciones: cuánto avanza y gira por segundo.
     * Si cambia, hay que volver a entrenar la red de movimiento, porque los ms dependen de esto.
     */
    public record Calibracion(double mmPorSegundo, double gradosPorSegundo, double largoMm, double anchoMm,
                              double bocaMm, double ruidoVelocidad) {
        /** La misma calibración que usa el Servidor (CalibracionRobot), boca de 60 mm y 10 % de ruido. */
        public static final Calibracion ESTIMADA = new Calibracion(CalibracionRobot.MM_POR_SEGUNDO,
                CalibracionRobot.GRADOS_POR_SEGUNDO, CalibracionRobot.LARGO_MM, CalibracionRobot.ANCHO_MM, 60, 0.10);
    }

    private static final double PASO_MM = 5;
    private static final double PASO_GRADOS = 1;

    private final Calibracion cal;
    private double x, y, rumbo;

    public RobotSimulado(Calibracion cal, double x, double y, double rumbo) {
        this.cal = cal;
        this.x = x; this.y = y; this.rumbo = rumbo;
    }

    /** Resultado de un movimiento: si chocó y cuántas canicas capturó. */
    public record Resultado(boolean choco, int capturas) { }

    /**
     * Ejecuta la orden con ruido de velocidad y una leve deriva al avanzar. Se mueve en pasos chicos:
     * si un paso choca contra pared u obstáculo, vuelve a la pose anterior y se detiene (choque).
     */
    public Resultado mover(OrdenMovimiento orden, Bahia bahia, Random azar) {
        double segundos = orden.milisegundos() / 1000.0;
        double factor = 1 + (azar.nextGaussian() * cal.ruidoVelocidad());
        factor = Math.max(0.7, Math.min(1.3, factor));
        Movimiento m = orden.movimiento();
        int capturas = 0;
        if (m == Movimiento.DETENER || segundos <= 0) return new Resultado(false, 0);
        if (m == Movimiento.AVANZAR || m == Movimiento.RETROCEDER) {
            double total = cal.mmPorSegundo() * segundos * factor;
            double signo = m == Movimiento.AVANZAR ? 1 : -1;
            double deriva = Math.toRadians(azar.nextGaussian() * 1.5) / Math.max(1, total / PASO_MM);
            for (double hecho = 0; hecho < total; hecho += PASO_MM) {
                double paso = Math.min(PASO_MM, total - hecho);
                double nx = x + signo * paso * Math.cos(rumbo), ny = y + signo * paso * Math.sin(rumbo);
                double nr = rumbo + deriva;
                if (choca(bahia, nx, ny, nr)) return new Resultado(true, capturas);
                x = nx; y = ny; rumbo = nr;
                capturas += interactuarConCanicas(bahia);
            }
        } else {
            double total = cal.gradosPorSegundo() * segundos * factor;
            double signo = m == Movimiento.GIRAR_IZQ ? 1 : -1;
            for (double hecho = 0; hecho < total; hecho += PASO_GRADOS) {
                double paso = Math.min(PASO_GRADOS, total - hecho);
                double nr = rumbo + signo * Math.toRadians(paso);
                if (choca(bahia, x, y, nr)) return new Resultado(true, capturas);
                rumbo = nr;
                capturas += interactuarConCanicas(bahia);
            }
        }
        return new Resultado(false, capturas);
    }

    /** True si el rectángulo del robot en esa pose toca una pared o un obstáculo. */
    public boolean choca(Bahia bahia, double px, double py, double pr) {
        double[][] esq = esquinas(px, py, pr);
        for (var lista : java.util.List.of(bahia.paredes(), bahia.obstaculos())) {
            for (Bahia.Segmento s : lista) {
                for (int i = 0; i < 4; i++) {
                    double[] a = esq[i], b = esq[(i + 1) % 4];
                    if (cortan(a[0], a[1], b[0], b[1], s.x1(), s.y1(), s.x2(), s.y2())) return true;
                }
                if (dentroDelRobot(s.x1(), s.y1(), px, py, pr, 0)) return true;
            }
        }
        return false;
    }

    /**
     * Canicas que tocan el robot: si entran por la boca (frente, centro) se capturan; si tocan otra parte
     * del cuerpo se empujan hacia afuera.
     */
    private int interactuarConCanicas(Bahia bahia) {
        int capturas = 0;
        double r = Bahia.CANICA_MM / 2;
        double c = Math.cos(rumbo), s = Math.sin(rumbo);
        for (Bahia.Canica k : bahia.canicas()) {
            if (k.capturada) continue;
            double rx = k.x - x, ry = k.y - y;
            double adelante = rx * c + ry * s, izquierda = -rx * s + ry * c;
            double medioLargo = cal.largoMm() / 2, medioAncho = cal.anchoMm() / 2;
            if (Math.abs(adelante) > medioLargo + r || Math.abs(izquierda) > medioAncho + r) continue;
            if (adelante > medioLargo - 25 && Math.abs(izquierda) < cal.bocaMm() / 2) {
                k.capturada = true;
                capturas++;
                continue;
            }
            // Empuje: sacarla por el lado más cercano del rectángulo.
            double fAdelante = medioLargo + r - Math.abs(adelante), fCostado = medioAncho + r - Math.abs(izquierda);
            double na = adelante, ni = izquierda;
            if (fAdelante < fCostado) na = Math.signum(adelante == 0 ? 1 : adelante) * (medioLargo + r + 1);
            else ni = Math.signum(izquierda == 0 ? 1 : izquierda) * (medioAncho + r + 1);
            double nx = x + na * c - ni * s, ny = y + na * s + ni * c;
            if (bahia.adentroConMargen(nx, ny, r)) { k.x = nx; k.y = ny; }
        }
        return capturas;
    }

    private boolean dentroDelRobot(double qx, double qy, double px, double py, double pr, double margen) {
        double rx = qx - px, ry = qy - py;
        double adelante = rx * Math.cos(pr) + ry * Math.sin(pr), izquierda = -rx * Math.sin(pr) + ry * Math.cos(pr);
        return Math.abs(adelante) <= cal.largoMm() / 2 + margen && Math.abs(izquierda) <= cal.anchoMm() / 2 + margen;
    }

    /** Esquinas del rectángulo: delantera derecha, delantera izquierda, trasera izquierda, trasera derecha. */
    public double[][] esquinas(double px, double py, double pr) {
        double l = cal.largoMm() / 2, w = cal.anchoMm() / 2;
        double[][] loc = {{l, -w}, {l, w}, {-l, w}, {-l, -w}};
        double[][] r = new double[4][];
        for (int i = 0; i < 4; i++)
            r[i] = new double[] {px + loc[i][0] * Math.cos(pr) - loc[i][1] * Math.sin(pr),
                                 py + loc[i][0] * Math.sin(pr) + loc[i][1] * Math.cos(pr)};
        return r;
    }

    private static boolean cortan(double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy) {
        double d1 = orient(cx, cy, dx, dy, ax, ay), d2 = orient(cx, cy, dx, dy, bx, by);
        double d3 = orient(ax, ay, bx, by, cx, cy), d4 = orient(ax, ay, bx, by, dx, dy);
        return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0));
    }

    private static double orient(double ax, double ay, double bx, double by, double cx, double cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    /** Posición del sensor (CalibracionRobot.SENSOR_AL_FRENTE_MM detrás del frente). */
    public double sensorX() { return x + (cal.largoMm() / 2 - CalibracionRobot.SENSOR_AL_FRENTE_MM) * Math.cos(rumbo); }
    public double sensorY() { return y + (cal.largoMm() / 2 - CalibracionRobot.SENSOR_AL_FRENTE_MM) * Math.sin(rumbo); }

    public double x() { return x; }
    public double y() { return y; }
    public double rumbo() { return rumbo; }
    public Calibracion calibracion() { return cal; }
}
