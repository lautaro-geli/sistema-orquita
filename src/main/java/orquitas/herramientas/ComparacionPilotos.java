// Archivo de simulacion
package orquitas.herramientas;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import orquitas.servidor.navegacion.RedMovimiento;
import orquitas.servidor.navegacion.RedPercepcion;
import orquitas.simulacion.bahia.Piloto;
import orquitas.simulacion.bahia.RobotSimulado;
import orquitas.simulacion.bahia.SensorSimulado;

/**
 * Compara pilotos en las mismas bahías simuladas (no va en los diagramas). Uso:
 * {@code ComparacionPilotos <carpetaE2> <salida.md> [--bahias=900] [--hilos=N] [nombre=carpeta ...]}.
 * Siempre corre E2 (redes) y el modo barrido con la percepción de E2; cada nombre=carpeta agrega unas redes
 * (percepcion.nnet y movimiento.nnet). La bahía i usa la semilla de evaluación + i en todos los pilotos, así
 * la diferencia contra E2 es pareada (se informa ± 2 errores estándar). Con -Dorquita.simRealista=true usa el
 * sensor parecido al real.
 */
public class ComparacionPilotos {
    record Resultado(boolean liberada, int capturas, int choques, int puntaje, double segundos) { }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Uso: ComparacionPilotos <carpetaE2> <salida.md> [--bahias=900] [--hilos=N] [nombre=carpeta ...]");
            System.exit(2);
        }
        Path e2 = Path.of(args[0]);
        Path salida = Path.of(args[1]);
        int bahias = 900;
        int hilos = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        Map<String, Path> redes = new LinkedHashMap<>();
        redes.put("E2", e2);
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("--bahias=")) bahias = Integer.parseInt(args[i].substring(9));
            else if (args[i].startsWith("--hilos=")) hilos = Integer.parseInt(args[i].substring(8));
            else if (args[i].contains("=")) redes.put(args[i].substring(0, args[i].indexOf('=')), Path.of(args[i].substring(args[i].indexOf('=') + 1)));
        }
        Map<String, List<Resultado>> todos = new LinkedHashMap<>();
        for (Map.Entry<String, Path> r : redes.entrySet()) {
            Path c = r.getValue();
            todos.put(r.getKey(), correr(bahias, hilos, () -> Piloto.redes(new RedPercepcion(c.resolve("percepcion.nnet")), new RedMovimiento(c.resolve("movimiento.nnet")))));
            System.out.println("Listo: " + r.getKey());
            if (r.getKey().equals("E2")) {
                todos.put("Barrido (percepción E2)", correr(bahias, hilos, () -> Piloto.barrido(new RedPercepcion(e2.resolve("percepcion.nnet")))));
                System.out.println("Listo: barrido");
            }
        }
        String tabla = tabla(todos, bahias);
        System.out.println(tabla);
        Files.writeString(salida, tabla, StandardCharsets.UTF_8);
    }

    static List<Resultado> correr(int bahias, int hilos, Supplier<Piloto> piloto) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(hilos);
        List<Future<Resultado>> fs = new ArrayList<>();
        for (int i = 0; i < bahias; i++) {
            final int k = i;
            fs.add(ex.submit(() -> {
                EntrenamientoSimulado.Prueba p = EntrenamientoSimulado.pruebaCompleta(EntrenamientoSimulado.SEMILLA_EVALUACION + k,
                        piloto.get(), RobotSimulado.Calibracion.ESTIMADA);
                return new Resultado(p.liberada(), p.capturas(), p.choques(), p.puntaje(), p.segundos());
            }));
        }
        List<Resultado> r = new ArrayList<>();
        for (Future<Resultado> f : fs) r.add(f.get());
        ex.shutdown();
        return r;
    }

    static String tabla(Map<String, List<Resultado>> todos, int bahias) {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "# Comparación de pilotos (%d bahías, mismas semillas; simulación realista: %s)%n%n"
                + "| Piloto | Misión | Capturas | Choques | Puntaje | Segundos | Misión vs E2 | Choques vs E2 |%n|---|---|---|---|---|---|---|---|%n",
                bahias, SensorSimulado.REALISTA));
        List<Resultado> base = todos.get("E2");
        for (Map.Entry<String, List<Resultado>> e : todos.entrySet()) {
            List<Resultado> r = e.getValue();
            double mision = 0, capt = 0, choq = 0, punt = 0, seg = 0;
            for (Resultado x : r) { mision += x.liberada() ? 1 : 0; capt += x.capturas(); choq += x.choques(); punt += x.puntaje(); seg += x.segundos(); }
            int n = r.size();
            sb.append(String.format(Locale.ROOT, "| %s | %.1f %% | %.2f | %.2f | %.1f | %.0f | %s | %s |%n", e.getKey(), 100 * mision / n, capt / n,
                    choq / n, punt / n, seg / n, e.getKey().equals("E2") ? "—" : diferencia(base, r, true), e.getKey().equals("E2") ? "—" : diferencia(base, r, false)));
        }
        return sb.toString();
    }

    /** Diferencia pareada media ± 2 errores estándar (misión en puntos porcentuales o choques). */
    static String diferencia(List<Resultado> a, List<Resultado> b, boolean mision) {
        int n = Math.min(a.size(), b.size());
        double[] d = new double[n];
        double suma = 0;
        for (int i = 0; i < n; i++) {
            d[i] = mision ? 100.0 * ((b.get(i).liberada() ? 1 : 0) - (a.get(i).liberada() ? 1 : 0)) : b.get(i).choques() - a.get(i).choques();
            suma += d[i];
        }
        double media = suma / n, var = 0;
        for (double x : d) var += (x - media) * (x - media);
        double ee = Math.sqrt(var / Math.max(1, n - 1) / n);
        return String.format(Locale.ROOT, "%+.1f ± %.1f", media, 2 * ee);
    }
}
