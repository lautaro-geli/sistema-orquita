package orquitas.herramientas;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.PreprocesadorTelemetria;
import orquitas.servidor.navegacion.RedPercepcion;

/**
 * Análisis de la prueba de detección con notas por sector (herramienta, no forma parte de los diagramas).
 * Uso: {@code AnalisisDeteccion <percepcion.nnet> <deteccion.csv> [otro.csv ...] [--salida=carpeta]}.
 * <p>
 * Vuelve a pasar cada barrido por la red con el código actual (incluida la corrección del piso) y lo
 * compara con lo anotado en la nota (ver NotaEscena). Escribe en la carpeta de salida:
 * <ul>
 *   <li>{@code analisis.md}: qué ve la red en cada tipo de sector, lobitos y obstáculos por distancia, y
 *       la prueba del filtro de forma (pendiente 3 del traspaso) con distintos umbrales.</li>
 *   <li>{@code analisis_sectores.csv}: una fila por sector anotado, con lo medido, lo corregido, lo que ve
 *       la red y los rasgos del filtro.</li>
 *   <li>{@code analisis_filtro.csv}: todas las combinaciones de umbrales probadas.</li>
 * </ul>
 * Filtro de forma: un sector que la red marca LOBITO queda como LOBITO solo si (a) el 4x4 corregido tiene
 * dato a menos de A mm, (b) el 16x16 mide al menos B mm más que el 4x4 (objeto chico) y (c) ningún ROI de
 * los sectores vecinos mide a menos de C mm de lo que mide el 4x4 (aislado).
 */
public final class AnalisisDeteccion {
    static final int[] UMBRAL_4X4_MM = {250, 300, 400};
    static final int[] FORMA_MM = {0, 10, 20, 30, 50, 80};
    static final int[] AISLADO_MM = {0, 10, 20, 30, 50, 80};
    /** Valor de los rasgos cuando falta el dato (16x16 sin eco o vecinos sin eco): cuenta como "pasa". */
    static final int SIN_DATO = 9999;

    /** Categorías de sector, en el orden del informe. */
    static final String LOBITO = "lobito", JUNTO_LOBITO = "junto al lobito", OBSTACULO = "obstaculo",
            BORDE = "junto al obstaculo", SALIDA = "salida", RESTO = "resto de la escena";
    static final List<String> CATEGORIAS = List.of(LOBITO, JUNTO_LOBITO, OBSTACULO, BORDE, SALIDA, RESTO);

    /** Un sector de un barrido anotado. */
    record Caso(String archivo, int barrido, String nota, int sector, String categoria, int distanciaCm,
                int[] crudo, int[] corregido, EtiquetaSector red, double pLobito, int forma, int aislado) { }

    private AnalisisDeteccion() { }

    public static void main(String[] args) throws Exception {
        List<Path> csvs = new ArrayList<>();
        Path salida = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--salida=")) salida = Path.of(args[i].substring(9));
            else csvs.add(Path.of(args[i]));
        }
        if (args.length < 2 || csvs.isEmpty()) {
            System.err.println("Uso: AnalisisDeteccion <percepcion.nnet> <deteccion.csv> [otro.csv ...] [--salida=carpeta]");
            System.exit(2);
        }
        if (salida == null) salida = csvs.get(0).toAbsolutePath().getParent();
        RedPercepcion red = new RedPercepcion(Path.of(args[0]));
        List<String> avisos = new ArrayList<>();
        int[] barridos = new int[2]; // total, anotados
        List<Caso> casos = leer(csvs, red, avisos, barridos);
        String informe = informe(casos, avisos, barridos, csvs, args[0]);
        Files.createDirectories(salida);
        Files.writeString(salida.resolve("analisis.md"), informe, StandardCharsets.UTF_8);
        Files.writeString(salida.resolve("analisis_sectores.csv"), csvSectores(casos), StandardCharsets.UTF_8);
        Files.writeString(salida.resolve("analisis_filtro.csv"), csvFiltro(casos), StandardCharsets.UTF_8);
        System.out.println(informe);
        System.out.println("Archivos en " + salida.toAbsolutePath() + ": analisis.md, analisis_sectores.csv, analisis_filtro.csv");
    }

    // ---------------------------------------------------------------- Lectura

    static List<Caso> leer(List<Path> csvs, RedPercepcion red, List<String> avisos, int[] barridos) throws IOException {
        List<Caso> casos = new ArrayList<>();
        Map<String, NotaEscena> notas = new LinkedHashMap<>();
        for (Path csv : csvs) {
            List<String> lineas = Files.readAllLines(csv, StandardCharsets.UTF_8);
            for (int n = 1; n < lineas.size(); n++) {
                String[] c = lineas.get(n).split(",", -1);
                if (c.length < 3 + PreprocesadorTelemetria.VALORES) continue;
                int[] t = new int[PreprocesadorTelemetria.VALORES];
                try {
                    for (int i = 0; i < t.length; i++) t[i] = Integer.parseInt(c[3 + i].trim());
                } catch (NumberFormatException e) {
                    avisos.add(csv.getFileName() + " línea " + (n + 1) + ": telemetría ilegible, la salteo");
                    continue;
                }
                barridos[0]++;
                int barrido = Integer.parseInt(c[1].trim());
                NotaEscena nota = notas.computeIfAbsent(c[2], NotaEscena::leer);
                if (!nota.valida() && notas.size() > 0 && !avisos.contains(aviso(nota)))
                    avisos.add(aviso(nota));
                if (!nota.anotada()) continue;
                barridos[1]++;
                casos.addAll(casosDelBarrido(csv.getFileName().toString(), barrido, nota, t, red));
            }
        }
        return casos;
    }

    private static String aviso(NotaEscena nota) {
        return "nota \"" + nota.texto() + "\": " + String.join("; ", nota.problemas())
                + (nota.anotada() ? " (uso lo que se entiende: " + nota.resumen() + ")" : " (barridos sin anotar)");
    }

    static List<Caso> casosDelBarrido(String archivo, int barrido, NotaEscena nota, int[] t, RedPercepcion red) {
        double[] p = red.probabilidades(t);
        EtiquetaSector[] et = RedPercepcion.etiquetas(p);
        int[] cor = PreprocesadorTelemetria.corregir(t);
        NotaEscena.Tipo[] verdad = nota.verdad(), vecinos = nota.vecinos();
        List<Caso> casos = new ArrayList<>();
        for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
            String cat = verdad[s] == NotaEscena.Tipo.LOBITO ? LOBITO : verdad[s] == NotaEscena.Tipo.OBSTACULO ? OBSTACULO
                    : verdad[s] == NotaEscena.Tipo.SALIDA ? SALIDA : vecinos[s] == NotaEscena.Tipo.LOBITO ? JUNTO_LOBITO
                    : vecinos[s] == NotaEscena.Tipo.OBSTACULO ? BORDE : RESTO;
            casos.add(new Caso(archivo, barrido, nota.texto(), s, cat, nota.distanciaCm(s), sector(t, s), sector(cor, s), et[s],
                    p[s * RedPercepcion.ETIQUETAS + EtiquetaSector.LOBITO.ordinal()], forma(cor, s), aislado(cor, s)));
        }
        return casos;
    }

    private static int[] sector(int[] v, int s) {
        return new int[] {v[s * 3], v[s * 3 + 1], v[s * 3 + 2]};
    }

    // ---------------------------------------------------------------- Rasgos del filtro de forma

    /** 16x16 menos 4x4 (corregidos), en mm. Sin 4x4: -SIN_DATO (no pasa). Sin 16x16: SIN_DATO (objeto chico). */
    static int forma(int[] cor, int s) {
        int d4 = cor[s * 3], d16 = cor[s * 3 + 2];
        if (d4 < 0) return -SIN_DATO;
        return d16 < 0 ? SIN_DATO : d16 - d4;
    }

    /** Menor diferencia, en mm, entre el 4x4 del sector y cualquier ROI de los sectores vecinos (±1). */
    static int aislado(int[] cor, int s) {
        int d4 = cor[s * 3];
        if (d4 < 0) return -SIN_DATO;
        int menor = SIN_DATO;
        for (int v = s - 1; v <= s + 1; v += 2) {
            if (v < 0 || v >= PreprocesadorTelemetria.SECTORES) continue;
            for (int r = 0; r < 3; r++) {
                int d = cor[v * 3 + r];
                if (d >= 0) menor = Math.min(menor, Math.abs(d - d4));
            }
        }
        return menor;
    }

    /** True si el filtro de forma deja pasar el LOBITO de este caso. */
    static boolean pasa(Caso c, int umbral4x4, int formaMm, int aisladoMm) {
        int d4 = c.corregido()[0];
        return d4 >= 0 && d4 < umbral4x4 && c.forma() >= formaMm && c.aislado() >= aisladoMm;
    }

    static boolean esFalso(String categoria) {
        return categoria.equals(OBSTACULO) || categoria.equals(BORDE) || categoria.equals(SALIDA) || categoria.equals(RESTO);
    }

    /** Resultado de una combinación de umbrales sobre los casos que la red marca LOBITO. */
    record Filtro(int umbral4x4, int forma, int aislado, int lobitos, int lobitosQuedan, int falsos, int falsosQuedan,
                  int obstaculos, int obstaculosQuedan) {
        double pctLobitos() { return lobitos == 0 ? 0 : 100.0 * lobitosQuedan / lobitos; }
        double pctFalsosSacados() { return falsos == 0 ? 0 : 100.0 * (falsos - falsosQuedan) / falsos; }
        double puntaje() { return (pctLobitos() + pctFalsosSacados()) / 2; }
    }

    static Filtro probar(List<Caso> casos, int umbral4x4, int forma, int aislado) {
        int lob = 0, lobQ = 0, fal = 0, falQ = 0, obs = 0, obsQ = 0;
        for (Caso c : casos) {
            if (c.red() != EtiquetaSector.LOBITO) continue;
            boolean queda = pasa(c, umbral4x4, forma, aislado);
            if (c.categoria().equals(LOBITO)) { lob++; if (queda) lobQ++; }
            else if (esFalso(c.categoria())) {
                fal++; if (queda) falQ++;
                if (c.categoria().equals(OBSTACULO) || c.categoria().equals(BORDE)) { obs++; if (queda) obsQ++; }
            }
        }
        return new Filtro(umbral4x4, forma, aislado, lob, lobQ, fal, falQ, obs, obsQ);
    }

    static List<Filtro> barrer(List<Caso> casos) {
        List<Filtro> r = new ArrayList<>();
        for (int a : UMBRAL_4X4_MM) for (int f : FORMA_MM) for (int c : AISLADO_MM) r.add(probar(casos, a, f, c));
        r.sort((x, y) -> Double.compare(y.puntaje(), x.puntaje()));
        return r;
    }

    // ---------------------------------------------------------------- Informe

    static String informe(List<Caso> casos, List<String> avisos, int[] barridos, List<Path> csvs, String red) {
        StringBuilder sb = new StringBuilder("# Análisis de la prueba de detección\n\n");
        sb.append("Red: `").append(red).append("`. Archivos: ");
        for (Path p : csvs) sb.append('`').append(p).append("` ");
        sb.append(String.format(Locale.ROOT, "%nBarridos: %d, anotados: %d. Corrección del piso: %s.%n%n", barridos[0], barridos[1],
                PreprocesadorTelemetria.CORRECCION_PISO ? "prendida" : "apagada"));
        if (!avisos.isEmpty()) {
            sb.append("## Notas con problemas\n\n");
            for (String a : avisos) sb.append("- ").append(a).append('\n');
            sb.append('\n');
        }
        if (casos.isEmpty()) {
            return sb.append("**No hay barridos con notas que se entiendan.** Formato: `lobito -15 20`, `obstaculo +30 15`, ")
                    .append("`salida -30 +15` (ángulo positivo a la izquierda, distancia en cm).\n").toString();
        }
        sb.append("## Qué ve la red en cada tipo de sector\n\n")
          .append("Cada fila es un tipo de sector según la nota; las columnas, lo que dice la red (sectores × barridos).\n\n")
          .append("| Según la nota | Casos | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | P(LOBITO) media |\n|---|---|---|---|---|---|---|---|\n");
        for (String cat : CATEGORIAS) {
            int[] n = new int[EtiquetaSector.values().length];
            int total = 0;
            double p = 0;
            for (Caso c : casos) if (c.categoria().equals(cat)) { n[c.red().ordinal()]++; total++; p += c.pLobito(); }
            if (total == 0) continue;
            sb.append("| ").append(cat).append(" | ").append(total);
            for (int v : n) sb.append(" | ").append(v).append(String.format(Locale.ROOT, " (%.0f %%)", 100.0 * v / total));
            sb.append(String.format(Locale.ROOT, " | %.2f |%n", p / total));
        }
        sb.append("\n\"Junto al\" = sectores vecinos (±1) de lo anotado: el objeto puede asomar. No cuentan como error en el filtro ")
          .append("los de junto al lobito; los de junto al obstáculo sí (es donde el borde se confunde).\n\n");

        sb.append("## Por distancia anotada\n\n| Tipo | Distancia | Casos | La red dice LOBITO | P(LOBITO) media |\n|---|---|---|---|---|\n");
        for (String cat : List.of(LOBITO, OBSTACULO))
            for (int[] r : new int[][] {{0, 10}, {11, 20}, {21, 30}, {31, 999}}) {
                int total = 0, lob = 0;
                double p = 0;
                for (Caso c : casos)
                    if (c.categoria().equals(cat) && c.distanciaCm() >= r[0] && c.distanciaCm() <= r[1]) {
                        total++; p += c.pLobito(); if (c.red() == EtiquetaSector.LOBITO) lob++;
                    }
                if (total == 0) continue;
                sb.append(String.format(Locale.ROOT, "| %s | %s cm | %d | %d (%.0f %%) | %.2f |%n", cat,
                        r[1] == 999 ? "más de 30" : r[0] + "–" + r[1], total, lob, 100.0 * lob / total, p / total));
            }

        sb.append("\n## Filtro de forma (pendiente 3)\n\n")
          .append("Solo sobre los sectores que la red marca LOBITO. \"Lobitos que quedan\": lobitos anotados que el filtro deja pasar. ")
          .append("\"Falsos sacados\": LOBITO en obstáculos, junto a obstáculos, salida o resto que el filtro saca. ")
          .append("A = 4x4 a menos de A mm; B = 16x16 al menos B mm más lejos que el 4x4; C = vecinos a más de C mm.\n\n");
        Filtro sin = probar(casos, Integer.MAX_VALUE, -SIN_DATO, -SIN_DATO);
        sb.append(String.format(Locale.ROOT, "Sin filtro: la red marca LOBITO en %d lobitos anotados y en %d sectores que no lo son (%d en obstáculos o junto a ellos).%n%n",
                sin.lobitos(), sin.falsos(), sin.obstaculos()));
        int perdidos = 0, lobitosAnotados = 0;
        for (Caso c : casos) if (c.categoria().equals(LOBITO)) { lobitosAnotados++; if (c.red() != EtiquetaSector.LOBITO) perdidos++; }
        if (lobitosAnotados > 0)
            sb.append(String.format(Locale.ROOT, "Lobitos anotados que la red **no** marca LOBITO: %d de %d. El filtro no los puede recuperar (eso es el ajuste fino, pendiente 4).%n%n",
                    perdidos, lobitosAnotados));
        sb.append("Mejores combinaciones (promedio de % de lobitos que quedan y % de falsos sacados):\n\n")
          .append("| A | B | C | Lobitos que quedan | Falsos sacados | Obstáculos sacados |\n|---|---|---|---|---|---|\n");
        List<Filtro> filtros = barrer(casos);
        for (Filtro f : filtros.subList(0, Math.min(10, filtros.size())))
            sb.append(String.format(Locale.ROOT, "| %d | %d | %d | %d/%d (%.0f %%) | %d/%d (%.0f %%) | %d/%d |%n", f.umbral4x4(), f.forma(), f.aislado(),
                    f.lobitosQuedan(), f.lobitos(), f.pctLobitos(), f.falsos() - f.falsosQuedan(), f.falsos(), f.pctFalsosSacados(),
                    f.obstaculos() - f.obstaculosQuedan(), f.obstaculos()));
        sb.append("\nTodas las combinaciones están en `analisis_filtro.csv`; cada sector, con sus rasgos, en `analisis_sectores.csv`.\n");
        return sb.toString();
    }

    static String csvSectores(List<Caso> casos) {
        StringBuilder sb = new StringBuilder("archivo,barrido,nota,sector,angulo,categoria,distancia_cm,v4,v8,v16,c4,c8,c16,red,p_lobito,forma_mm,aislado_mm\n");
        for (Caso c : casos)
            sb.append(String.format(Locale.ROOT, "%s,%d,%s,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%s,%.3f,%d,%d%n", c.archivo(), c.barrido(),
                    c.nota().replace(',', ' '), c.sector(), NotaEscena.angulo(c.sector()), c.categoria(), c.distanciaCm(),
                    c.crudo()[0], c.crudo()[1], c.crudo()[2], c.corregido()[0], c.corregido()[1], c.corregido()[2],
                    c.red(), c.pLobito(), c.forma(), c.aislado()));
        return sb.toString();
    }

    static String csvFiltro(List<Caso> casos) {
        StringBuilder sb = new StringBuilder("umbral_4x4_mm,forma_mm,aislado_mm,lobitos,lobitos_quedan,falsos,falsos_quedan,obstaculos,obstaculos_quedan,puntaje\n");
        for (Filtro f : barrer(casos))
            sb.append(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d,%d,%.1f%n", f.umbral4x4(), f.forma(), f.aislado(), f.lobitos(),
                    f.lobitosQuedan(), f.falsos(), f.falsosQuedan(), f.obstaculos(), f.obstaculosQuedan(), f.puntaje()));
        return sb.toString();
    }
}
