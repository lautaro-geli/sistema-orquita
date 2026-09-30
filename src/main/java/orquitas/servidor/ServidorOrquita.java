// Archivo de simulacion
package orquitas.servidor;

import orquitas.servidor.captura.Recopilador;
import orquitas.servidor.captura.ReceptorCapturas;
import orquitas.servidor.comunicacion.ReceptorIngestaTCP;
import orquitas.servidor.escape.ActivadorModoEscape;
import orquitas.servidor.navegacion.Navegador;
import orquitas.servidor.persistencia.ActualizadorEstomago;
import orquitas.servidor.persistencia.ArchivoEstomago;
import orquitas.servidor.persistencia.LectorEstomago;

/** Arranque del Hito 2 simulado. No borra capturas al reiniciar. */
public class ServidorOrquita {
    public static void main(String[] args) throws java.io.IOException {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        String ruta = args.length > 1 ? args[1] : "estomago.txt";
        ArchivoEstomago archivo = new ArchivoEstomago(ruta);
        ActualizadorEstomago actualizador = new ActualizadorEstomago(archivo);
        java.nio.file.Path modelo = java.nio.file.Path.of(args.length > 2 ? args[2] : "modelos/entorno-demo.nnet");
        orquitas.simulacion.EntrenadorDemostracion.entrenarSiFalta(modelo);
        Navegador navegador = new Navegador(new orquitas.servidor.navegacion.ClasificadorEntorno(modelo));
        ActivadorModoEscape activador = new ActivadorModoEscape(navegador);
        LectorEstomago lector = new LectorEstomago(archivo, activador, 500);
        ReceptorCapturas receptor = new ReceptorCapturas(actualizador);
        Recopilador recopilador = new Recopilador(receptor, navegador);
        ReceptorIngestaTCP entrada = new ReceptorIngestaTCP(puerto, recopilador);

        actualizador.start(); // JVM invoca run() en el hilo escritor.
        lector.start();       // JVM invoca run() en el hilo auditor independiente.
        Runnable apagar = () -> {
            entrada.detener(); // Primero termina toda recepciÃ³n en curso.
            actualizador.detener();
            try { actualizador.join(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            lector.detener();
        };
        Runtime.getRuntime().addShutdownHook(new Thread(apagar, "CierreLinareada"));
        try {
            entrada.iniciar();
        } finally {
            apagar.run();
        }
    }
}
