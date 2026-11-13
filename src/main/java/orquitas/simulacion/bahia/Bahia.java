package orquitas.simulacion.bahia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import orquitas.servidor.navegacion.EtiquetaSector;

/**
 * Modelo 2D de la Bahía de Caza (medidas en mm). Origen en el centro de la base recta, eje y hacia
 * la salida. Forma: base recta de BASE_MM y una media elipse de alto ALTO_MM con una abertura de
 * SALIDA_MM arriba en el centro (el Estrecho de Salida). Afuera de la bahía no hay nada.
 * Adentro: obstáculos cuadrados de OBSTACULO_MM y canicas (Lobitos) de CANICA_MM de diámetro.
 */
public final class Bahia {
    public static final double BASE_MM = 1150;
    public static final double ALTO_MM = 1200;
    public static final double SALIDA_MM = 300;
    public static final double OBSTACULO_MM = 90;
    public static final double CANICA_MM = 16;
    private static final int TRAMOS_POR_LADO = 30;

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
        double a = BASE_MM / 2, b = ALTO_MM;
        double tCorte = Math.acos((SALIDA_MM / 2) / a); // Ángulo de la elipse donde empieza la abertura.
        paredes.add(new Segmento(-a, 0, a, 0, EtiquetaSector.PARED));
        agregarArco(a, b, 0, tCorte);
        agregarArco(a, b, Math.PI - tCorte, Math.PI);
        double ySalida = b * Math.sin(tCorte);
        salida = new Segmento(-SALIDA_MM / 2, ySalida, SALIDA_MM / 2, ySalida, EtiquetaSector.SALIDA);
    }

    private void agregarArco(double a, double b, double desde, double hasta) {
        double px = a * Math.cos(desde), py = b * Math.sin(desde);
        for (int i = 1; i <= TRAMOS_POR_LADO; i++) {
            double t = desde + (hasta - desde) * i / TRAMOS_POR_LADO;
            double x = a * Math.cos(t), y = b * Math.sin(t);
            paredes.add(new Segmento(px, py, x, y, EtiquetaSector.PARED));
            px = x; py = y;
        }
    }

    /** Bahía vacía (sin obstáculos ni canicas), para pruebas. */
    public static Bahia vacia() {
        return new Bahia();
    }

    /**
     * Bahía al azar: obstáculos girados al azar, separados entre sí y de la salida para que el robot
     * pueda pasar; canicas lejos de paredes, obstáculos y del punto de partida.
     */
    public static Bahia generar(Random azar, int cantidadObstaculos, int cantidadCanicas) {
        Bahia bahia = new Bahia();
        int intentos = 0;
        while (bahia.centrosObstaculos.size() < cantidadObstaculos && intentos++ < 2000) {
            double x = (azar.nextDouble() * 2 - 1) * (BASE_MM / 2 - 120);
            double y = 300 + azar.nextDouble() * (ALTO_MM - 550);
            if (!bahia.adentroConMargen(x, y, 130)) continue;
            if (Math.hypot(x, y - (ALTO_MM - 150)) < 300) continue; // No tapar el estrecho.
            if (Math.hypot(x, y - 150) < 340) continue; // Lejos de la partida.
            boolean lejos = true;
            for (double[] c : bahia.centrosObstaculos) if (Math.hypot(c[0] - x, c[1] - y) < 320) lejos = false;
            if (lejos) bahia.agregarObstaculo(x, y, azar.nextDouble() * Math.PI / 2);
        }
        intentos = 0;
        while (bahia.canicas.size() < cantidadCanicas && intentos++ < 5000) {
            double x = (azar.nextDouble() * 2 - 1) * (BASE_MM / 2 - 60);
            double y = 80 + azar.nextDouble() * (ALTO_MM - 250);
            if (!bahia.adentroConMargen(x, y, 70)) continue;
            if (Math.hypot(x, y - 150) < 280) continue; // Lejos de la partida.
            if (Math.hypot(x, y - (ALTO_MM - 100)) < 200) continue;
            boolean libre = true;
            for (double[] c : bahia.centrosObstaculos) if (Math.hypot(c[0] - x, c[1] - y) < 130) libre = false;
            for (Canica c : bahia.canicas) if (Math.hypot(c.x - x, c.y - y) < 150) libre = false;
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

    /** True si (x, y) está dentro de la media elipse (sin contar la abertura). */
    public boolean adentro(double x, double y) {
        return adentroConMargen(x, y, 0);
    }

    /** Dentro de la elipse achicada en margen mm. */
    public boolean adentroConMargen(double x, double y, double margen) {
        double a = BASE_MM / 2 - margen, b = ALTO_MM - margen;
        if (y < margen || a <= 0 || b <= 0) return false;
        return (x * x) / (a * a) + (y * y) / (b * b) <= 1;
    }

    /** True si el punto ya pasó el estrecho (está arriba de la abertura, fuera de la elipse). */
    public boolean afuera(double x, double y) {
        return y > salida.y1() + 60 && !adentro(x, y);
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

    /** Parámetro t >= 0 del rayo donde corta al segmento, o -1. */
    static double interseccion(double x, double y, double dx, double dy, Segmento s) {
        double ex = s.x2() - s.x1(), ey = s.y2() - s.y1();
        double den = dx * ey - dy * ex;
        if (Math.abs(den) < 1e-12) return -1;
        double qx = s.x1() - x, qy = s.y1() - y;
        double t = (qx * ey - qy * ex) / den;
        double u = (qx * dy - qy * dx) / den;
        return (t >= 0 && u >= 0 && u <= 1) ? t : -1;
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
