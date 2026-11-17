package orquitas.servidor.navegacion;

import java.util.ArrayList;
import java.util.List;

/**
 * Seguimiento de Lobitos y de la salida con odometría: el Servidor guarda dónde los vio, en un marco propio
 * que arranca en la partida (x, y del eje en mm; rumbo en radianes), y los mueve con cada orden que manda
 * (cuánto giró y avanzó, según {@link CalibracionRobot}). No ve la geometría real: solo la telemetría y lo
 * que dice la red de percepción en cada sector.
 * <ul>
 *   <li>Un Lobito se confirma recién cuando aparece en {@link #VISTAS_PARA_CONFIRMAR} barridos en el mismo
 *       lugar (±{@link #ASOCIAR_MM}): filtra los Lobitos falsos de la percepción.</li>
 *   <li>Se recuerda aunque deje de verse, hasta que se captura, hasta que no aparece
 *       {@link #FALTAS_PARA_OLVIDAR} veces donde debería verse, o hasta {@link #CICLOS_PARA_OLVIDAR} ciclos.</li>
 *   <li>La salida se guarda como el punto del hueco: en la dirección de los sectores SALIDA, a la distancia
 *       de la pared de al lado.</li>
 * </ul>
 */
public final class Seguimiento {
    public static final int VISTAS_PARA_CONFIRMAR = 2;
    public static final double ASOCIAR_MM = 130;
    public static final int FALTAS_PARA_OLVIDAR = 2;
    public static final int CICLOS_PARA_OLVIDAR = 12;
    /** Más lejos que esto no se cree un Lobito (las canicas se distinguen de cerca). */
    public static final double LOBITO_MAXIMO_MM = 700;
    /** Distancia a la que se debería ver un Lobito recordado para contar una falta si no aparece. */
    public static final double DEBERIA_VERSE_MM = 450;
    /** Diferencia de distancia para considerar que dos sectores ven el mismo objeto. */
    public static final double MISMA_DISTANCIA_MM = 50;
    /** Más sectores que esto viendo lo mismo = objeto ancho (obstáculo), no canica. */
    public static final int SECTORES_CANICA = 2;

    /** Un Lobito recordado, en el marco propio. */
    public static final class Lobito {
        public double x, y;
        public int vistas;
        public int faltas;
        public int ultimoCiclo;
        boolean vistoAhora;

        Lobito(double x, double y, int ciclo) { this.x = x; this.y = y; this.vistas = 1; this.ultimoCiclo = ciclo; }

        public boolean confirmado() { return vistas >= VISTAS_PARA_CONFIRMAR; }
    }

    private double x, y, rumbo;
    private int ciclo;
    private int capturas;
    private final List<Lobito> lobitos = new ArrayList<>();
    private double[] salida;
    /** Puntos de paredes y obstáculos vistos (marco propio), para no chocar con lo que ya no se ve. */
    private final java.util.ArrayDeque<double[]> puntos = new java.util.ArrayDeque<>();
    /** Se recuerdan los puntos de los últimos barridos: con más, el error de la odometría los corre. */
    public static final int PUNTOS_MAXIMOS = 13 * 6;
    /** Medidas del robot desde el eje (mm). */
    public static final double COLA_MM = 120, MEDIO_ANCHO_MM = 75;

    public double x() { return x; }
    public double y() { return y; }
    public double rumbo() { return rumbo; }
    public List<Lobito> lobitos() { return lobitos; }
    /** Punto estimado del hueco de salida (marco propio) o null si nunca se vio. */
    public double[] salida() { return salida; }

    /** Mueve la pose estimada con una orden ejecutada. */
    public void registrarOrden(OrdenMovimiento o) {
        if (o == null) return;
        double s = o.milisegundos() / 1000.0;
        switch (o.movimiento()) {
            case AVANZAR -> { x += s * CalibracionRobot.MM_POR_SEGUNDO * Math.cos(rumbo); y += s * CalibracionRobot.MM_POR_SEGUNDO * Math.sin(rumbo); }
            case RETROCEDER -> { x -= s * CalibracionRobot.MM_POR_SEGUNDO * Math.cos(rumbo); y -= s * CalibracionRobot.MM_POR_SEGUNDO * Math.sin(rumbo); }
            case GIRAR_IZQ -> rumbo += Math.toRadians(s * CalibracionRobot.GRADOS_POR_SEGUNDO);
            case GIRAR_DER -> rumbo -= Math.toRadians(s * CalibracionRobot.GRADOS_POR_SEGUNDO);
            default -> { }
        }
    }

    /** Avisa cuántas capturas lleva el robot: si subió, se borra el Lobito más cercano a la boca. */
    public void registrarCapturas(int total) {
        while (capturas < total) {
            capturas++;
            Lobito mas = null;
            double mejor = Double.MAX_VALUE;
            for (Lobito l : lobitos) {
                double d = Math.hypot(l.x - x, l.y - y);
                if (d < mejor) { mejor = d; mas = l; }
            }
            if (mas != null && mejor < 400) lobitos.remove(mas);
        }
    }

    private EtiquetaSector[] filtradas;

    /**
     * Etiquetas del último barrido con los LOBITO que no parecen canicas (anchos o lejanos) cambiados por
     * OBSTACULO: el reflejo deja pasar por los sectores LOBITO, así que un falso Lobito sobre un obstáculo
     * hacía chocar.
     */
    public EtiquetaSector[] etiquetasFiltradas() { return filtradas; }

    /** Incorpora un barrido: telemetría cruda y etiquetas por sector de la red de percepción. */
    public void registrarBarrido(int[] telemetria, EtiquetaSector[] etiquetas) {
        filtradas = etiquetas.clone();
        ciclo++;
        int[] corregida = PreprocesadorTelemetria.corregir(telemetria);
        double sx = x + (CalibracionRobot.EJE_AL_FRENTE_MM - CalibracionRobot.SENSOR_AL_FRENTE_MM) * Math.cos(rumbo);
        double sy = y + (CalibracionRobot.EJE_AL_FRENTE_MM - CalibracionRobot.SENSOR_AL_FRENTE_MM) * Math.sin(rumbo);
        List<double[]> detecciones = new ArrayList<>();
        // Sectores LOBITO seguidos = una sola canica: el sector más cercano del grupo.
        for (int s = 0; s < etiquetas.length; s++) {
            if (etiquetas[s] != EtiquetaSector.LOBITO) continue;
            int fin = s;
            while (fin + 1 < etiquetas.length && etiquetas[fin + 1] == EtiquetaSector.LOBITO) fin++;
            double mejor = Double.MAX_VALUE, angulo = 0;
            for (int k = s; k <= fin; k++) {
                double d = ReflejoSeguridad.distancia(corregida, k);
                if (d < mejor) { mejor = d; angulo = (k - MetaMovimiento.FRENTE) * MetaMovimiento.GRADOS_POR_SECTOR; }
            }
            // Ancho: sectores (del grupo y vecinos) que ven algo a la misma distancia. Una canica de 16 mm
            // ocupa uno o dos; un obstáculo de 90 mm (que la percepción a veces marca LOBITO), más.
            int parecidos = 0;
            for (int k = Math.max(0, s - 1); k <= Math.min(etiquetas.length - 1, fin + 1); k++) {
                double d = ReflejoSeguridad.distancia(corregida, k);
                if (d != Double.MAX_VALUE && Math.abs(d - mejor) < MISMA_DISTANCIA_MM) parecidos++;
            }
            boolean ancho = parecidos > SECTORES_CANICA && mejor > 150;
            if (ancho || mejor == Double.MAX_VALUE || mejor > LOBITO_MAXIMO_MM)
                for (int k = s; k <= fin; k++) filtradas[k] = EtiquetaSector.OBSTACULO;
            if (mejor != Double.MAX_VALUE && mejor <= LOBITO_MAXIMO_MM && !ancho) {
                // La distancia es a la cara de la canica; el centro está un poco más lejos.
                double a = rumbo + Math.toRadians(angulo), d = mejor + 8;
                detecciones.add(new double[] {sx + d * Math.cos(a), sy + d * Math.sin(a)});
            }
            s = fin;
        }
        for (Lobito l : lobitos) l.vistoAhora = false;
        for (double[] p : detecciones) {
            int mejor = -1;
            double mejorD = ASOCIAR_MM;
            for (int i = 0; i < lobitos.size(); i++) {
                double d = Math.hypot(lobitos.get(i).x - p[0], lobitos.get(i).y - p[1]);
                if (d < mejorD) { mejorD = d; mejor = i; }
            }
            if (mejor >= 0) {
                Lobito l = lobitos.get(mejor);
                l.x = (l.x * l.vistas + p[0]) / (l.vistas + 1);
                l.y = (l.y * l.vistas + p[1]) / (l.vistas + 1);
                l.vistas++;
                l.faltas = 0;
                l.ultimoCiclo = ciclo;
                l.vistoAhora = true;
            } else {
                Lobito nuevo = new Lobito(p[0], p[1], ciclo);
                nuevo.vistoAhora = true;
                lobitos.add(nuevo);
            }
        }
        // Faltas: un Lobito recordado que debería verse (adelante y cerca, sin nada en el medio) y no aparece.
        for (int i = lobitos.size() - 1; i >= 0; i--) {
            Lobito l = lobitos.get(i);
            if (l.vistoAhora) continue;
            double d = Math.hypot(l.x - sx, l.y - sy);
            double rel = Math.toDegrees(normalizar(Math.atan2(l.y - sy, l.x - sx) - rumbo));
            if (d < DEBERIA_VERSE_MM && d > 60 && Math.abs(rel) < 80) {
                int sector = (int) Math.round(rel / MetaMovimiento.GRADOS_POR_SECTOR) + MetaMovimiento.FRENTE;
                double medido = ReflejoSeguridad.distancia(corregida, Math.max(0, Math.min(12, sector)));
                if (medido > d - 80) l.faltas++; // Si algo lo tapa, no cuenta.
            }
            if (l.faltas >= FALTAS_PARA_OLVIDAR || ciclo - l.ultimoCiclo > CICLOS_PARA_OLVIDAR
                    || (!l.confirmado() && ciclo - l.ultimoCiclo > 2)) lobitos.remove(i);
        }
        // Puntos de paredes y obstáculos (no Lobitos ni sectores sin dato).
        for (int s = 0; s < etiquetas.length; s++) {
            if (etiquetas[s] == EtiquetaSector.LOBITO) continue;
            double d = ReflejoSeguridad.distancia(corregida, s);
            if (d == Double.MAX_VALUE || d <= 0 || d > 900) continue;
            double a = rumbo + Math.toRadians((s - MetaMovimiento.FRENTE) * MetaMovimiento.GRADOS_POR_SECTOR);
            puntos.addLast(new double[] {sx + d * Math.cos(a), sy + d * Math.sin(a)});
        }
        while (puntos.size() > PUNTOS_MAXIMOS) puntos.removeFirst();
        // Salida: centro de los sectores SALIDA, a la distancia de la pared de al lado.
        int primera = -1, ultima = -1;
        for (int s = 0; s < etiquetas.length; s++)
            if (etiquetas[s] == EtiquetaSector.SALIDA) { if (primera < 0) primera = s; ultima = s; }
        if (primera >= 0) {
            double pared = Double.MAX_VALUE;
            for (int v : new int[] {primera - 1, ultima + 1})
                if (v >= 0 && v < etiquetas.length) pared = Math.min(pared, ReflejoSeguridad.distancia(corregida, v));
            if (pared == Double.MAX_VALUE || pared > 1300) pared = 700;
            double a = rumbo + Math.toRadians(((primera + ultima) / 2.0 - MetaMovimiento.FRENTE) * MetaMovimiento.GRADOS_POR_SECTOR);
            double[] nueva = {sx + pared * Math.cos(a), sy + pared * Math.sin(a)};
            salida = salida == null ? nueva : new double[] {(salida[0] + nueva[0]) / 2, (salida[1] + nueva[1]) / 2};
        }
    }

    /** True si el robot, con el eje en (px, py) y rumbo r, tocaría algún punto recordado (más margen). */
    public boolean tocaria(double px, double py, double r, double margen) {
        double c = Math.cos(r), sn = Math.sin(r);
        for (double[] q : puntos) {
            if (yaToca(q, margen)) continue; // Un punto que ya toca en la pose actual es un error de la odometría.
            double dx = q[0] - px, dy = q[1] - py;
            double adelante = dx * c + dy * sn, lado = -dx * sn + dy * c;
            if (adelante < CalibracionRobot.EJE_AL_FRENTE_MM + margen && adelante > -COLA_MM - margen
                    && Math.abs(lado) < MEDIO_ANCHO_MM + margen) return true;
        }
        return false;
    }

    private boolean yaToca(double[] q, double margen) {
        double dx = q[0] - x, dy = q[1] - y, c = Math.cos(rumbo), sn = Math.sin(rumbo);
        double adelante = dx * c + dy * sn, lado = -dx * sn + dy * c;
        return adelante < CalibracionRobot.EJE_AL_FRENTE_MM + margen && adelante > -COLA_MM - margen
                && Math.abs(lado) < MEDIO_ANCHO_MM + margen;
    }

    /** True si la orden (desde la pose estimada) tocaría algo recordado en el camino. */
    public boolean ordenTocaria(OrdenMovimiento o, double margen) {
        double s = o.milisegundos() / 1000.0;
        switch (o.movimiento()) {
            case GIRAR_IZQ, GIRAR_DER -> {
                double total = Math.toRadians(s * CalibracionRobot.GRADOS_POR_SEGUNDO * 1.15) * (o.movimiento() == Movimiento.GIRAR_IZQ ? 1 : -1);
                int pasos = Math.max(1, (int) Math.ceil(Math.abs(Math.toDegrees(total)) / 3));
                for (int i = 1; i <= pasos; i++) if (tocaria(x, y, rumbo + total * i / pasos, margen)) return true;
                return false;
            }
            case AVANZAR -> { return false; } // Adelante lo controla el reflejo con el barrido actual.
            case RETROCEDER -> {
                double mm = s * CalibracionRobot.MM_POR_SEGUNDO * 1.1 * (o.movimiento() == Movimiento.AVANZAR ? 1 : -1);
                int pasos = Math.max(1, (int) Math.ceil(Math.abs(mm) / 10));
                for (int i = 1; i <= pasos; i++)
                    if (tocaria(x + mm * i / pasos * Math.cos(rumbo), y + mm * i / pasos * Math.sin(rumbo), rumbo, margen)) return true;
                return false;
            }
            default -> { return false; }
        }
    }

    /** Como ordenTocaria, pero desde una pose dada (para el avance que sigue a un giro). */
    public boolean avanceTocaria(double rumboDespues, double mm, double margen) {
        int pasos = Math.max(1, (int) Math.ceil(mm * 1.1 / 10));
        for (int i = 1; i <= pasos; i++)
            if (tocaria(x + mm * 1.1 * i / pasos * Math.cos(rumboDespues), y + mm * 1.1 * i / pasos * Math.sin(rumboDespues), rumboDespues, margen)) return true;
        return false;
    }

    /** El Lobito confirmado más cercano, o null. */
    public Lobito lobitoMasCercano() {
        Lobito mas = null;
        double mejor = Double.MAX_VALUE;
        for (Lobito l : lobitos) {
            if (!l.confirmado()) continue;
            double d = Math.hypot(l.x - x, l.y - y);
            if (d < mejor) { mejor = d; mas = l; }
        }
        return mas;
    }

    /** Ángulo (grados, + izquierda) y distancia (mm, desde el eje) hasta un punto del marco propio. */
    public double[] haciaPunto(double px, double py) {
        return new double[] {Math.toDegrees(normalizar(Math.atan2(py - y, px - x) - rumbo)), Math.hypot(px - x, py - y)};
    }

    static double normalizar(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}
