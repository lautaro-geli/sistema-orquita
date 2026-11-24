package orquitas.herramientas;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Nota de la escena de la prueba de detección (herramienta, no forma parte de los diagramas): qué hay en
 * cada sector. Formato, con el ángulo positivo a la izquierda y 0 al frente:
 * <ul>
 *   <li>{@code lobito -15 20}: canica a 15° a la derecha, a 20 cm (también "canica").</li>
 *   <li>{@code obstaculo +30 15}: obstáculo a 30° a la izquierda, a 15 cm.</li>
 *   <li>{@code salida -30 +15}: la salida ocupa de 30° a la derecha a 15° a la izquierda.</li>
 * </ul>
 * Se pueden poner varias cosas en la misma nota: {@code obstaculo 0 15 y lobito -30 10} (también con ";").
 * {@code vacio}: escena anotada sin lobitos ni obstáculos (solo paredes); en el análisis, todo LOBITO es falso.
 * Una nota vacía es "sin anotar". El ángulo se lleva al sector más cercano (cada 15°).
 */
public final class NotaEscena {
    public enum Tipo { LOBITO, OBSTACULO, SALIDA }

    /** Una cosa de la escena. Para SALIDA, distanciaCm es -1 y la salida va de angulo a anguloHasta. */
    public record Objeto(Tipo tipo, int angulo, int distanciaCm, int anguloHasta) { }

    public static final int SECTORES = 13;
    private static final List<String> RELLENO = List.of("y", "a", "cm", "grados", "en", "al", "de", "+");

    private final String texto;
    private final List<Objeto> objetos;
    private final List<String> problemas;
    private final boolean vacio;

    private NotaEscena(String texto, List<Objeto> objetos, List<String> problemas, boolean vacio) {
        this.texto = texto;
        this.vacio = vacio;
        this.objetos = Collections.unmodifiableList(objetos);
        this.problemas = Collections.unmodifiableList(problemas);
    }

    public static NotaEscena leer(String nota) {
        String texto = nota == null ? "" : nota.trim();
        String limpio = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace(',', ' ').replace(';', ' ').replaceAll("(\\d)\\s*cm\\b", "$1");
        List<Objeto> objetos = new ArrayList<>();
        List<String> problemas = new ArrayList<>();
        boolean vacio = false;
        String[] t = limpio.isEmpty() ? new String[0] : limpio.split("\\s+");
        for (int i = 0; i < t.length; i++) {
            if (t[i].equals("vacio") || t[i].equals("vacia")) { vacio = true; continue; }
            Tipo tipo = tipo(t[i]);
            if (tipo == null) {
                if (!RELLENO.contains(t[i])) problemas.add("no entiendo \"" + t[i] + "\"");
                continue;
            }
            Integer a = i + 1 < t.length ? numero(t[i + 1]) : null, b = i + 2 < t.length ? numero(t[i + 2]) : null;
            if (a == null || b == null) {
                problemas.add(tipo == Tipo.SALIDA ? "a \"salida\" le faltan los dos angulos (ej.: salida -30 +15)"
                        : "a \"" + t[i] + "\" le faltan el angulo y la distancia (ej.: " + t[i] + " -15 20)");
                continue;
            }
            i += 2;
            if (Math.abs(a) > 90 || (tipo == Tipo.SALIDA && Math.abs(b) > 90)) {
                problemas.add("angulo fuera de -90..+90 en " + t[i - 2] + " " + a + " " + b);
                continue;
            }
            if (tipo == Tipo.SALIDA) {
                objetos.add(new Objeto(tipo, Math.min(a, b), -1, Math.max(a, b)));
            } else {
                if (b <= 0 || b > 150) {
                    problemas.add("distancia rara (" + b + " cm) en " + t[i - 2] + " " + a + " " + b + ": va en cm");
                    continue;
                }
                objetos.add(new Objeto(tipo, a, b, a));
            }
        }
        if (vacio && !objetos.isEmpty()) problemas.add("\"vacio\" no se combina con lobitos, obstaculos ni salida");
        return new NotaEscena(texto, objetos, problemas, vacio && objetos.isEmpty());
    }

    private static Tipo tipo(String palabra) {
        if (palabra.startsWith("lobito") || palabra.startsWith("canica")) return Tipo.LOBITO;
        if (palabra.startsWith("obstaculo") || palabra.equals("obst")) return Tipo.OBSTACULO;
        if (palabra.equals("salida")) return Tipo.SALIDA;
        return null;
    }

    private static Integer numero(String s) {
        try {
            return Integer.parseInt(s.startsWith("+") ? s.substring(1) : s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Sector más cercano a un ángulo relativo (positivo a la izquierda): 0 = derecha, 6 = frente, 12 = izquierda. */
    public static int sector(int angulo) {
        return Math.max(0, Math.min(SECTORES - 1, (int) Math.round((angulo + 90) / 15.0)));
    }

    /** Ángulo relativo de un sector (positivo a la izquierda). */
    public static int angulo(int sector) {
        return sector * 15 - 90;
    }

    /** Qué hay en cada sector según la nota (null: nada anotado). Los lobitos pisan a lo demás. */
    public Tipo[] verdad() {
        Tipo[] v = new Tipo[SECTORES];
        for (Tipo orden : new Tipo[] {Tipo.SALIDA, Tipo.OBSTACULO, Tipo.LOBITO})
            for (Objeto o : objetos) {
                if (o.tipo() != orden) continue;
                for (int s = sector(o.angulo()); s <= sector(o.anguloHasta()); s++) v[s] = o.tipo();
            }
        return v;
    }

    /** Sectores vecinos (±1) de un lobito u obstáculo que no tienen nada anotado: el objeto puede asomar. */
    public Tipo[] vecinos() {
        Tipo[] verdad = verdad(), v = new Tipo[SECTORES];
        for (Tipo orden : new Tipo[] {Tipo.OBSTACULO, Tipo.LOBITO})
            for (Objeto o : objetos) {
                if (o.tipo() != orden) continue;
                int c = sector(o.angulo());
                for (int s = c - 1; s <= c + 1; s += 2)
                    if (s >= 0 && s < SECTORES && verdad[s] == null) v[s] = o.tipo();
            }
        return v;
    }

    /** Distancia anotada (cm) del lobito u obstáculo de un sector, o -1. */
    public int distanciaCm(int sector) {
        int d = -1;
        for (Objeto o : objetos)
            if (o.tipo() != Tipo.SALIDA && sector(o.angulo()) == sector && (d < 0 || o.tipo() == Tipo.LOBITO)) d = o.distanciaCm();
        return d;
    }

    public String texto() { return texto; }
    public List<Objeto> objetos() { return objetos; }
    public List<String> problemas() { return problemas; }
    public boolean anotada() { return vacio || !objetos.isEmpty(); }
    /** Escena anotada sin lobitos, obstáculos ni salida (solo paredes). */
    public boolean vacio() { return vacio; }
    public boolean valida() { return problemas.isEmpty(); }

    /** Resumen legible, para confirmar la nota al escribirla. */
    public String resumen() {
        if (vacio) return "VACIO (solo paredes, sin lobitos ni obstaculos)";
        if (objetos.isEmpty()) return "sin anotar";
        StringBuilder sb = new StringBuilder();
        for (Objeto o : objetos) {
            if (sb.length() > 0) sb.append("; ");
            if (o.tipo() == Tipo.SALIDA)
                sb.append("SALIDA en los sectores ").append(sector(o.angulo())).append(" a ").append(sector(o.anguloHasta()));
            else
                sb.append(o.tipo()).append(" en el sector ").append(sector(o.angulo())).append(" (").append(lado(o.angulo()))
                        .append("), a ").append(o.distanciaCm()).append(" cm");
        }
        return sb.toString();
    }

    static String lado(int angulo) {
        int a = angulo(sector(angulo));
        return a == 0 ? "frente" : a > 0 ? "izq " + a : "der " + (-a);
    }
}
