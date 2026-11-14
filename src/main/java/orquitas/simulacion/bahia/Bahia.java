// Archivo de simulacion
package orquitas.simulacion.bahia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import orquitas.servidor.navegacion.CalibracionRobot;
import orquitas.servidor.navegacion.EtiquetaSector;

/**
 * Modelo 2D de la Bahía de Caza (medidas en mm). Origen en el centro de la base, eje y hacia la salida.
 * Forma: rectángulo de BASE_MM x ALTO_MM (x de -760 a 760, y de 0 a 1140) con las 4 esquinas
 * redondeadas (radio RADIO_ESQUINA_MM) y un hueco de SALIDA_MM centrado en el lado de arriba (el
 * Estrecho de Salida, sin pasillo). Afuera de la bahía no hay nada: un rayo que sale por el hueco
 * no toca nada. Adentro: obstáculos cuadrados de OBSTACULO_MM y canicas (Lobitos) de CANICA_MM.
 */
public final class Bahia {
    public static final double BASE_MM = 1520;
    public static final double ALTO_MM = 1140;
    public static final double RADIO_ESQUINA_MM = 150;
    public static final double SALIDA_MM = 300;
    public static final double OBSTACULO_MM = 90;
    public static final double CANICA_MM = 16;
    /** Segmentos rectos con los que se aproxima cada esquina redondeada. */
    private static final int TRAMOS_POR_ESQUINA = 8;
    /** En la pista siempre hay 2 obstáculos. */
    public static final int OBSTACULOS = 2;

    /**
     * Partida: abajo a la izquierda, mirando hacia la salida (rumbo 90°). [A CONFIRMAR] El equipo dijo
     * "a 15-20 cm de la pared izquierda y de la base"; acá se toma como la separación entre la pared y
     * el costado izquierdo del robot, y entre la base y la cola del robot. Si en realidad se mide al
     * centro del robot, poner PARTIDA_BORDE_X_MM y PARTIDA_BORDE_Y_MM en 0.
     */
    public static final double PARTIDA_SEPARACION_MINIMA_MM = 150, PARTIDA_SEPARACION_MAXIMA_MM = 200;
    /** Del punto que se mide (costado izquierdo y cola del robot) al centro del robot. */
    public static final double PARTIDA_BORDE_X_MM = CalibracionRobot.ANCHO_MM / 2, PARTIDA_BORDE_Y_MM = CalibracionRobot.LARGO_MM / 2;
    /** Centro del rango de la partida (centro del robot, mm): (-510, 275) con las medidas de hoy. */
    public static final double PARTIDA_X = -BASE_MM / 2 + PARTIDA_BORDE_X_MM
            + (PARTIDA_SEPARACION_MINIMA_MM + PARTIDA_SEPARACION_MAXIMA_MM) / 2;
    public static final double PARTIDA_Y = PARTIDA_BORDE_Y_MM + (PARTIDA_SEPARACION_MINIMA_MM + PARTIDA_SEPARACION_MAXIMA_MM) / 2;
    /** Lo que se sortea la partida alrededor de ese centro: ± mm en x y en y, ± grados de rumbo. */
    public static final double PARTIDA_RANGO_MM = (PARTIDA_SEPARACION_MAXIMA_MM - PARTIDA_SEPARACION_MINIMA_MM) / 2;
    public static final double PARTIDA_RANGO_GRADOS = 3;

    /** Segmento de pared u obstáculo. */
    public record Segmento(double x1, double y1, double x2, double y2, EtiquetaSector tipo) { }

    /** Canica: posición y si ya fue capturada. */
    public static final class Canica {
        public double x, y;
        public boolean capturada;
        Canica(double x, double y) { this.x = x; this.y = y; }
    }

    /** Resultado de lanzar un rayo: distancia, tipo de lo que tocó (LIBRE si nada), coseno de incidencia. */
    public record Impacto(double distancia, EtiquetaSector tipo, double coseno, boolean cruzaSalida, double distanciaSalida) { }

    private final List<Segmento> paredes = new ArrayList<>();
    private final List<Segmento> obstaculos = new ArrayList<>();
    private final List<double[]> centrosObstaculos = new ArrayList<>();
    private final List<Canica> canicas = new ArrayList<>();
    private final Segmento salida;

    private Bahia() {
        double a = BASE_MM / 2, r = RADIO_ESQUINA_MM, cx = a - r, cyAbajo = r, cyArriba = ALTO_MM - r, s = SALIDA_MM / 2;
        // Se recorre el borde en sentido antihorario desde la base; cada arco es un cuarto de circunferencia.
        pared(-cx, 0, cx, 0);                                   // Base.
        agregarEsquina(cx, cyAbajo, -90);                       // Abajo a la derecha.
        pared(a, cyAbajo, a, cyArriba);                         // Lado derecho.
        agregarEsquina(cx, cyArriba, 0);                        // Arriba a la derecha.
        pared(cx, ALTO_MM, s, ALTO_MM);                         // Arriba, a la derecha del hueco.
        pared(-s, ALTO_MM, -cx, ALTO_MM);                       // Arriba, a la izquierda del hueco.
        agregarEsquina(-cx, cyArriba, 90);                      // Arriba a la izquierda.
        pared(-a, cyArriba, -a, cyAbajo);                       // Lado izquierdo.
        agregarEsquina(-cx, cyAbajo, 180);                      // Abajo a la izquierda.
        salida = new Segmento(-s, ALTO_MM, s, ALTO_MM, EtiquetaSector.SALIDA);
    }

    private void pared(double x1, double y1, double x2, double y2) {
        paredes.add(new Segmento(x1, y1, x2, y2, EtiquetaSector.PARED));
    }

    /** Cuarto de circunferencia de radio RADIO_ESQUINA_MM con centro (cx, cy), desde el ángulo dado (grados). */
    private void agregarEsquina(double cx, double cy, double desdeGrados) {
        double r = RADIO_ESQUINA_MM, desde = Math.toRadians(desdeGrados);
        double px = cx + r * Math.cos(desde), py = cy + r * Math.sin(desde);
        for (int i = 1; i <= TRAMOS_POR_ESQUINA; i++) {
            double t = desde + (Math.PI / 2) * i / TRAMOS_POR_ESQUINA;
            double x = cx + r * Math.cos(t), y = cy + r * Math.sin(t);
            pared(px, py, x, y);
            px = x; py = y;
        }
    }

    /** Bahía vacía (sin obstáculos ni canicas), para pruebas. */
    public static Bahia vacia() {
        return new Bahia();
    }

    /** Distancia mínima del centro de un obstáculo a las paredes. */
    static final double MARGEN_OBSTACULO_MM = 130;
    /** Distancia mínima entre centros de obstáculos: deja pasar al robot entre los dos. */
    static final double SEPARACION_OBSTACULOS_MM = 320;
    /** Distancia mínima del centro de un obstáculo y de una canica a la partida. */
    static final double OBSTACULO_A_PARTIDA_MM = 340, CANICA_A_PARTIDA_MM = 280;
    /** Zona libre delante del estrecho: sin obstáculos ni canicas cerca de estos puntos. */
    static final double OBSTACULO_A_ESTRECHO_MM = 300, CANICA_A_ESTRECHO_MM = 200;
    /** Distancia mínima de una canica a las paredes, al centro de un obstáculo y a otra canica. */
    static final double MARGEN_CANICA_MM = 70, CANICA_A_OBSTACULO_MM = 130, SEPARACION_CANICAS_MM = 150;

    /**
     * Bahía al azar: obstáculos girados al azar, separados entre sí, de la partida y del estrecho para
     * que el robot pueda pasar; canicas lejos de paredes, obstáculos, del estrecho y de la partida.
     */
    public static Bahia generar(Random azar, int cantidadObstaculos, int cantidadCanicas) {
        Bahia bahia = new Bahia();
        int intentos = 0;
        while (bahia.centrosObstaculos.size() < cantidadObstaculos && intentos++ < 2000) {
            double x = (azar.nextDouble() * 2 - 1) * (BASE_MM / 2 - MARGEN_OBSTACULO_MM);
            double y = MARGEN_OBSTACULO_MM + azar.nextDouble() * (ALTO_MM - 2 * MARGEN_OBSTACULO_MM);
            if (!bahia.adentroConMargen(x, y, MARGEN_OBSTACULO_MM)) continue;
            if (Math.hypot(x, y - (ALTO_MM - 150)) < OBSTACULO_A_ESTRECHO_MM) continue; // No tapar el estrecho.
            if (Math.hypot(x - PARTIDA_X, y - PARTIDA_Y) < OBSTACULO_A_PARTIDA_MM) continue; // Lejos de la partida.
            boolean lejos = true;
            for (double[] c : bahia.centrosObstaculos) if (Math.hypot(c[0] - x, c[1] - y) < SEPARACION_OBSTACULOS_MM) lejos = false;
            if (lejos) bahia.agregarObstaculo(x, y, azar.nextDouble() * Math.PI / 2);
        }
        intentos = 0;
        while (bahia.canicas.size() < cantidadCanicas && intentos++ < 5000) {
            double x = (azar.nextDouble() * 2 - 1) * (BASE_MM / 2 - MARGEN_CANICA_MM);
            double y = MARGEN_CANICA_MM + azar.nextDouble() * (ALTO_MM - 2 * MARGEN_CANICA_MM);
            if (!bahia.adentroConMargen(x, y, MARGEN_CANICA_MM)) continue;
            if (Math.hypot(x - PARTIDA_X, y - PARTIDA_Y) < CANICA_A_PARTIDA_MM) continue; // Lejos de la partida.
            if (Math.hypot(x, y - (ALTO_MM - 100)) < CANICA_A_ESTRECHO_MM) continue; // No delante del estrecho.
            boolean libre = true;
            for (double[] c : bahia.centrosObstaculos) if (Math.hypot(c[0] - x, c[1] - y) < CANICA_A_OBSTACULO_MM) libre = false;
            for (Canica c : bahia.canicas) if (Math.hypot(c.x - x, c.y - y) < SEPARACION_CANICAS_MM) libre = false;
            if (libre) bahia.canicas.add(new Canica(x, y));
        }
        return bahia;
    }

    /** Agrega un obstáculo cuadrado centrado en (x, y) girado ang radianes. */
    public void agregarObstaculo(double x, double y, double ang) {
        double h = OBSTACULO_MM / 2;
        double[][] esquinas = new double[4][];
        double[][] locales = {{-h, -h}, {h, -h}, {h, h}, {-h, h}};
        for (int i = 0; i < 4; i++) {
            double lx = locales[i][0], ly = locales[i][1];
            esquinas[i] = new double[] {x + lx * Math.cos(ang) - ly * Math.sin(ang), y + lx * Math.sin(ang) + ly * Math.cos(ang)};
        }
        for (int i = 0; i < 4; i++) {
            double[] p = esquinas[i], q = esquinas[(i + 1) % 4];
            obstaculos.add(new Segmento(p[0], p[1], q[0], q[1], EtiquetaSector.OBSTACULO));
        }
        centrosObstaculos.add(new double[] {x, y, ang});
    }

    /** Agrega una canica en (x, y). */
    public void agregarCanica(double x, double y) {
        canicas.add(new Canica(x, y));
    }

    /** True si (x, y) está dentro de la bahía (el rectángulo con las esquinas redondeadas). */
    public boolean adentro(double x, double y) {
        return adentroConMargen(x, y, 0);
    }

    /**
     * Dentro de la bahía achicada en margen mm (a esa distancia o más de todas las paredes). Con un
     * margen mayor que el radio de las esquinas el redondeo desaparece y queda un rectángulo.
     */
    public boolean adentroConMargen(double x, double y, double margen) {
        double medioAncho = BASE_MM / 2 - margen, abajo = margen, arriba = ALTO_MM - margen;
        if (medioAncho <= 0 || arriba <= abajo) return false;
        if (Math.abs(x) > medioAncho || y < abajo || y > arriba) return false;
        double radio = RADIO_ESQUINA_MM - margen;
        if (radio <= 0) return true;
        // Distancia al centro del arco más cercano; fuera de los cuadrados de las esquinas no hay redondeo.
        double dx = Math.abs(x) - (BASE_MM / 2 - RADIO_ESQUINA_MM);
        double dy = Math.max(RADIO_ESQUINA_MM - y, y - (ALTO_MM - RADIO_ESQUINA_MM));
        if (dx <= 0 || dy <= 0) return true;
        return dx * dx + dy * dy <= radio * radio;
    }

    /** True si el punto ya pasó el hueco de la salida (está del otro lado de la pared de arriba). */
    public boolean afuera(double x, double y) {
        return y > ALTO_MM + 60 && Math.abs(x) <= BASE_MM / 2;
    }

    /**
     * Lanza un rayo desde (x, y) en el ángulo ang (radianes, 0 = eje x) hasta maximo mm.
     * Tiene en cuenta paredes, obstáculos y, si conCanicas, las canicas no capturadas.
     */
    public Impacto lanzarRayo(double x, double y, double ang, double maximo, boolean conCanicas) {
        double dx = Math.cos(ang), dy = Math.sin(ang);
        double mejor = maximo;
        EtiquetaSector tipo = EtiquetaSector.LIBRE;
        double coseno = 1;
        for (List<Segmento> lista : List.of(paredes, obstaculos)) {
            for (Segmento s : lista) {
                double t = interseccion(x, y, dx, dy, s);
                if (t >= 0 && t < mejor) {
                    mejor = t;
                    tipo = s.tipo();
                    double sx = s.x2() - s.x1(), sy = s.y2() - s.y1(), largo = Math.hypot(sx, sy);
                    coseno = Math.abs((dx * -sy + dy * sx) / largo); // |d · normal|
                }
            }
        }
        if (conCanicas) {
            double r = CANICA_MM / 2;
            for (Canica c : canicas) {
                if (c.capturada) continue;
                double ox = x - c.x, oy = y - c.y;
                double bq = ox * dx + oy * dy, cq = ox * ox + oy * oy - r * r;
                double disc = bq * bq - cq;
                if (disc < 0) continue;
                double t = -bq - Math.sqrt(disc);
                if (t >= 0 && t < mejor) {
                    mejor = t;
                    tipo = EtiquetaSector.LOBITO;
                    double hx = x + dx * t - c.x, hy = y + dy * t - c.y;
                    coseno = Math.abs((hx * dx + hy * dy) / r);
                }
            }
        }
        double tSalida = interseccion(x, y, dx, dy, salida);
        boolean cruza = tSalida >= 0 && tSalida < mejor;
        return new Impacto(mejor, tipo, coseno, cruza, cruza ? tSalida : -1);
    }

    /** Tolerancia en los extremos de un segmento: un rayo que pasa justo por la unión de dos tramos no se escapa. */
    private static final double TOLERANCIA_EXTREMO = 1e-9;

    /** Parámetro t >= 0 del rayo donde corta al segmento, o -1. */
    static double interseccion(double x, double y, double dx, double dy, Segmento s) {
        double ex = s.x2() - s.x1(), ey = s.y2() - s.y1();
        double den = dx * ey - dy * ex;
        if (Math.abs(den) < 1e-12) return -1;
        double qx = s.x1() - x, qy = s.y1() - y;
        double t = (qx * ey - qy * ex) / den;
        double u = (qx * dy - qy * dx) / den;
        return (t >= 0 && u >= -TOLERANCIA_EXTREMO && u <= 1 + TOLERANCIA_EXTREMO) ? t : -1;
    }

    public List<Segmento> paredes() { return Collections.unmodifiableList(paredes); }
    public List<Segmento> obstaculos() { return Collections.unmodifiableList(obstaculos); }
    public List<Canica> canicas() { return Collections.unmodifiableList(canicas); }
    public Segmento salida() { return salida; }

    /** Canicas que todavía no se capturaron. */
    public int canicasLibres() {
        int n = 0;
        for (Canica c : canicas) if (!c.capturada) n++;
        return n;
    }
}
