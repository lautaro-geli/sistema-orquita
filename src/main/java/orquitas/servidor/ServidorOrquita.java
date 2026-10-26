// Archivo de simulacion
package orquitas.servidor;

import orquitas.servidor.captura.Recopilador;
import orquitas.servidor.captura.ReceptorCapturas;
import orquitas.servidor.comunicacion.ReceptorIngestaTCP;
import orquitas.servidor.escape.ModoEscape;
import orquitas.servidor.navegacion.Navegacion;
import orquitas.servidor.persistencia.Escritor;
import orquitas.servidor.persistencia.Estomago;
import orquitas.servidor.persistencia.Auditor;

/** Arranque del Hito 2 simulado. No borra capturas al reiniciar. */
public class ServidorOrquita {
    public static void main(String[] args) throws java.io.IOException {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        String ruta = args.length > 1 ? args[1] : "estomago.txt";
        Estomago archivo = new Estomago(ruta);
        Escritor actualizador = new Escritor(archivo);
        java.nio.file.Path modelo = java.nio.file.Path.of(args.length > 2 ? args[2] : "modelos/entorno-demo.nnet");
        orquitas.simulacion.EntrenadorDemostracion.entrenarSiFalta(modelo);
        Navegacion navegacion = new Navegacion(new orquitas.servidor.navegacion.RedNeuronal(modelo));
        ModoEscape activador = new ModoEscape(navegacion);
        Auditor lector = new Auditor(archivo, activador, 500);
        ReceptorCapturas receptor = new ReceptorCapturas(actualizador);
        Recopilador recopilador = new Recopilador(receptor, navegacion);
        ReceptorIngestaTCP entrada = new ReceptorIngestaTCP(puerto, recopilador);

        actualizador.start(); // JVM invoca run() en el hilo escritor.
        lector.start();       // JVM invoca run() en el hilo auditor independiente.
        Runnable apagar = () -> {
            entrada.detener(); // Primero termina toda recepción en curso.
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
