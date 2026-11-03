package orquitas.servidor;

import java.nio.file.Files;
import java.nio.file.Path;
import orquitas.servidor.comunicacion.EnlaceOrquita;
import orquitas.servidor.navegacion.Navegacion;
import orquitas.servidor.navegacion.RedNeuronal;
import orquitas.servidor.persistencia.Auditor;
import orquitas.servidor.persistencia.Estomago;

/**
 * Arranque del Servidor del Hito 3. Uso:
 * {@code ServidorOrquita <COMx> [--umbral=N] [--estomago=ruta] [--modelo=ruta]}.
 * Cada arranque empieza una misión nueva: vacía estomago.txt.
 */
public class ServidorOrquita {
    private static final long INTERVALO_AUDITORIA_MS = 500;

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args[0].startsWith("--")) {
            System.err.println("Uso: ServidorOrquita <COMx> [--umbral=N] [--estomago=ruta] [--modelo=ruta]");
            System.exit(2);
        }
        String nombrePuerto = args[0];
        int umbral = 1; // Prueba de campo del Hito 3; el reglamento de la competencia usa 3.
        String rutaEstomago = "estomago.txt";
        Path rutaModelo = Path.of("modelos/entorno.nnet");
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--umbral=")) umbral = Integer.parseInt(args[i].substring(9));
            else if (args[i].startsWith("--estomago=")) rutaEstomago = args[i].substring(11);
            else if (args[i].startsWith("--modelo=")) rutaModelo = Path.of(args[i].substring(9));
            else {
                System.err.println("Argumento desconocido: " + args[i]);
                System.exit(2);
            }
        }
        System.out.println("==============================================");
        System.out.println(" Servidor Orquita - Hito 3 (Bluetooth)");
        System.out.println(" Puerto: " + nombrePuerto + " | Modelo: " + rutaModelo + " | Umbral de capturas: " + umbral);
        System.out.println(" Estómago: " + rutaEstomago + " (se vacía: la misión empieza de cero)");
        System.out.println("==============================================");

        Estomago estomago = new Estomago(rutaEstomago);
        estomago.reiniciar();
        if (!Files.isRegularFile(rutaModelo)) {
            System.err.println("No se encontró el modelo " + rutaModelo.toAbsolutePath()
                    + ". Generarlo con: ./ejecutar.ps1 entrenar (datos reales) o ./ejecutar.ps1 provisional.");
            System.exit(1);
        }
        Navegacion navegacion = new Navegacion(new RedNeuronal(rutaModelo));
        Auditor auditor = new Auditor(estomago, navegacion, INTERVALO_AUDITORIA_MS, umbral);
        EnlaceOrquita enlace;
        try {
            enlace = new EnlaceOrquita(nombrePuerto, estomago, navegacion);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { // Ctrl+C: cierre ordenado.
            enlace.detener();
            auditor.detener();
        }, "CierreServidor"));
        auditor.start();
        enlace.start();
        enlace.join(); // Solo termina por falla del enlace (Ctrl+C cierra desde el hook).
        auditor.detener();
        auditor.join(2000);
        System.err.println("[Servidor] El enlace terminó: se cierra el Servidor.");
        System.exit(1);
    }
}
