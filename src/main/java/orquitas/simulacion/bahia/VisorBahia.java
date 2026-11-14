// Archivo de simulacion
package orquitas.simulacion.bahia;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.OrdenMovimiento;

/**
 * Dibuja la bahía simulada: paredes, estrecho, obstáculos, canicas, robot, recorrido y el último
 * barrido (un rayo por sector con el color de su etiqueta). Sirve para la ventana animada y para PNG.
 *
 * Uso: {@code VisorBahia [semilla] [--maestro | --percepcion=ruta --movimiento=ruta] [--png=ruta]}.
 * Sin --png abre una ventana y anima la prueba (un ciclo cada 400 ms).
 */
public final class VisorBahia {
    private static final double ESCALA = 0.55; // px por mm.
    private static final int MARGEN_PX = 45;
    /** Lugar para la bahía (1520 x 1140 mm), unos 300 mm del otro lado de la salida, el texto y la leyenda. */
    private static final int ANCHO = (int) Math.round(Bahia.BASE_MM * ESCALA) + 2 * MARGEN_PX;
    private static final int ALTO = (int) Math.round((Bahia.ALTO_MM + 300) * ESCALA) + 90 + MARGEN_PX;

    /** Lo que se ve en cada cuadro. */
    public record Cuadro(SimuladorBahia sim, SensorSimulado.Barrido barrido, EtiquetaSector[] etiquetasRed,
                         OrdenMovimiento orden) { }

    public static Color color(EtiquetaSector e) {
        switch (e) {
            case PARED: return new Color(90, 90, 90);
            case OBSTACULO: return new Color(220, 120, 30);
            case LOBITO: return new Color(30, 140, 230);
            case SALIDA: return new Color(40, 170, 80);
            default: return new Color(200, 200, 200);
        }
    }

    private static double px(double x) { return ANCHO / 2.0 + x * ESCALA; }
    private static double py(double y) { return ALTO - 90 - y * ESCALA; }

    public static void dibujar(Graphics2D g, Cuadro c) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, ANCHO, ALTO);
        SimuladorBahia sim = c.sim();
        Bahia b = sim.bahia();
        g.setStroke(new BasicStroke(4));
        g.setColor(new Color(40, 40, 40));
        for (Bahia.Segmento s : b.paredes()) g.draw(new Line2D.Double(px(s.x1()), py(s.y1()), px(s.x2()), py(s.y2())));
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[] {6, 6}, 0));
        g.setColor(color(EtiquetaSector.SALIDA));
        Bahia.Segmento sal = b.salida();
        g.draw(new Line2D.Double(px(sal.x1()), py(sal.y1()), px(sal.x2()), py(sal.y2())));
        g.setStroke(new BasicStroke(2));
        g.setColor(color(EtiquetaSector.OBSTACULO));
        List<Bahia.Segmento> obs = b.obstaculos();
        for (int i = 0; i + 3 < obs.size(); i += 4) {
            Path2D.Double p = new Path2D.Double();
            p.moveTo(px(obs.get(i).x1()), py(obs.get(i).y1()));
            for (int k = 0; k < 4; k++) p.lineTo(px(obs.get(i + k).x2()), py(obs.get(i + k).y2()));
            p.closePath();
            g.fill(p);
        }
        double rc = Bahia.CANICA_MM / 2 * ESCALA + 2;
        for (Bahia.Canica k : b.canicas()) {
            if (k.capturada) continue;
            g.setColor(color(EtiquetaSector.LOBITO));
            g.fill(new Ellipse2D.Double(px(k.x) - rc, py(k.y) - rc, 2 * rc, 2 * rc));
        }
        // Recorrido.
        g.setColor(new Color(150, 80, 200, 140));
        List<double[]> rec = sim.recorrido();
        for (int i = 1; i < rec.size(); i++)
            g.draw(new Line2D.Double(px(rec.get(i - 1)[0]), py(rec.get(i - 1)[1]), px(rec.get(i)[0]), py(rec.get(i)[1])));
        // Barrido: rayo central de cada sector hasta la distancia del 8x8 (o corto si es -1).
        RobotSimulado r = sim.robot();
        if (c.barrido() != null) {
            for (int s = 0; s < SensorSimulado.SECTORES; s++) {
                int d = c.barrido().telemetria()[s * 3 + 1];
                double ang = SensorSimulado.anguloSector(r.rumbo(), s);
                double largo = d < 0 ? 60 : d;
                EtiquetaSector e = c.etiquetasRed() != null ? c.etiquetasRed()[s] : c.barrido().etiquetas()[s];
                g.setColor(color(e));
                g.setStroke(d < 0 ? new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[] {3, 3}, 0)
                                  : new BasicStroke(1.5f));
                g.draw(new Line2D.Double(px(r.sensorX()), py(r.sensorY()),
                        px(r.sensorX() + largo * Math.cos(ang)), py(r.sensorY() + largo * Math.sin(ang))));
            }
        }
        // Robot.
        double[][] e = r.esquinas(r.x(), r.y(), r.rumbo());
        Path2D.Double cuerpo = new Path2D.Double();
        cuerpo.moveTo(px(e[0][0]), py(e[0][1]));
        for (int i = 1; i < 4; i++) cuerpo.lineTo(px(e[i][0]), py(e[i][1]));
        cuerpo.closePath();
        g.setColor(new Color(60, 60, 140, 90));
        g.fill(cuerpo);
        g.setColor(new Color(40, 40, 120));
        g.setStroke(new BasicStroke(2));
        g.draw(cuerpo);
        g.setColor(Color.RED);
        g.draw(new Line2D.Double(px(e[0][0]), py(e[0][1]), px(e[1][0]), py(e[1][1]))); // Frente.
        // Texto.
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        String orden = c.orden() == null ? "-" : c.orden().movimiento() + " " + c.orden().milisegundos() + " ms";
        g.drawString(String.format(Locale.ROOT, "Modo %s   Capturas %d/%d   Choques %d   Ciclo %d   %.0f s   Puntaje %d",
                sim.modo(), sim.capturas(), sim.umbralCapturas(), sim.choques(), sim.ciclos(), sim.segundos(), sim.puntaje()), 15, 22);
        g.drawString("Orden: " + orden, 15, 42);
        int x = 15;
        for (EtiquetaSector et : EtiquetaSector.values()) {
            g.setColor(color(et));
            g.fillRect(x, ALTO - 30, 14, 14);
            g.setColor(Color.BLACK);
            g.drawString(et.name(), x + 18, ALTO - 18);
            x += 120;
        }
    }

    /** Guarda el cuadro como PNG. */
    public static void guardarPng(Cuadro c, Path ruta) throws Exception {
        BufferedImage img = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        dibujar(g, c);
        g.dispose();
        ImageIO.write(img, "png", ruta.toFile());
    }

    /** Ventana que anima una prueba con el piloto dado. */
    public static void animar(SimuladorBahia sim, Piloto piloto) {
        SwingUtilities.invokeLater(() -> {
            Cuadro[] actual = {new Cuadro(sim, null, null, null)};
            JPanel panel = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    dibujar((Graphics2D) g, actual[0]);
                }
            };
            panel.setPreferredSize(new Dimension(ANCHO, ALTO));
            JFrame ventana = new JFrame("Bahía de Caza simulada");
            ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            ventana.add(panel);
            ventana.pack();
            ventana.setVisible(true);
            Timer reloj = new Timer(400, null);
            reloj.addActionListener(ev -> {
                if (sim.terminada()) { reloj.stop(); return; }
                SensorSimulado.Barrido barrido = sim.medir();
                Piloto.Decision d = piloto.decidir(sim, barrido);
                actual[0] = new Cuadro(sim, barrido, d.etiquetas(), d.orden());
                sim.ejecutar(d.orden());
                panel.repaint();
            });
            reloj.start();
        });
    }

    public static void main(String[] args) throws Exception {
        long semilla = 1;
        String png = null, percepcion = null, movimiento = null;
        boolean maestro = false;
        for (String a : args) {
            if (a.startsWith("--png=")) png = a.substring(6);
            else if (a.startsWith("--percepcion=")) percepcion = a.substring(13);
            else if (a.startsWith("--movimiento=")) movimiento = a.substring(13);
            else if (a.equals("--maestro")) maestro = true;
            else semilla = Long.parseLong(a);
        }
        if (percepcion == null) percepcion = "modelos/percepcion.nnet";
        if (movimiento == null) movimiento = "modelos/movimiento.nnet";
        SimuladorBahia sim = SimuladorBahia.alAzar(semilla, 3, 3, RobotSimulado.Calibracion.ESTIMADA, SensorSimulado.Ruido.NORMAL);
        if (!maestro && !(java.nio.file.Files.isRegularFile(Path.of(percepcion)) && java.nio.file.Files.isRegularFile(Path.of(movimiento)))) {
            System.out.println("No están " + percepcion + " y " + movimiento + " (entrenarlas con ./ejecutar.ps1 simular-entrenar)."
                    + " Se muestra el maestro.");
            maestro = true;
        }
        Piloto piloto = maestro ? Piloto.maestro() : Piloto.redes(Path.of(percepcion), Path.of(movimiento));
        if (png == null) {
            animar(sim, piloto);
            return;
        }
        SensorSimulado.Barrido barrido;
        Piloto.Decision d;
        while (!sim.terminada()) {
            barrido = sim.medir();
            d = piloto.decidir(sim, barrido);
            sim.ejecutar(d.orden());
        }
        barrido = sim.medir(); // El dibujo muestra el barrido de la pose final.
        d = piloto.decidir(sim, barrido);
        guardarPng(new Cuadro(sim, barrido, d.etiquetas(), d.orden()), Path.of(png));
        System.out.printf(Locale.ROOT, "Modo %s, capturas %d, choques %d, %.0f s, puntaje %d. Imagen: %s%n",
                sim.modo(), sim.capturas(), sim.choques(), sim.segundos(), sim.puntaje(), png);
    }
}
