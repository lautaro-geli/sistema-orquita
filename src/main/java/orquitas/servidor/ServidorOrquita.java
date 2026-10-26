// Archivo de simulacion
package orquitas.servidor;

import orquitas.servidor.comunicacion.ReceptorIngestaTCP;
import orquitas.servidor.escape.ModoEscape;
import orquitas.servidor.navegacion.Navegacion;
import orquitas.servidor.navegacion.RedNeuronal;
import orquitas.servidor.persistencia.Auditor;
import orquitas.servidor.persistencia.Estomago;

/** Arranque del Hito 2 simulado. No borra capturas al reiniciar. */
public class ServidorOrquita {
    public static void main(String[] args) throws java.io.IOException {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        String ruta = args.length > 1 ? args[1] : "estomago.txt";
        Estomago estomago = new Estomago(ruta);
        java.nio.file.Path modelo = java.nio.file.Path.of(args.length > 2 ? args[2] : "modelos/entorno-demo.nnet");
        orquitas.simulacion.EntrenadorDemostracion.entrenarSiFalta(modelo);
        Navegacion navegacion = new Navegacion(new RedNeuronal(modelo));
        ModoEscape modoEscape = new ModoEscape(navegacion);
        Auditor auditor = new Auditor(estomago, modoEscape, 500);
        ReceptorIngestaTCP entrada = new ReceptorIngestaTCP(puerto, estomago, navegacion);

        auditor.start(); // JVM invoca run() en el hilo auditor independiente.
        Runnable apagar = () -> {
            entrada.detener(); // Primero termina toda recepción en curso.
            try { estomago.esperarEscrituras(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            auditor.detener();
        };
        Runtime.getRuntime().addShutdownHook(new Thread(apagar, "CierreServidor"));
        try {
            entrada.iniciar();
        } finally {
            apagar.run();
        }
    }
}
