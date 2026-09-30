package orquitas.servidor.comunicacion;

import java.io.*;
import orquitas.servidor.captura.*;
import orquitas.servidor.navegacion.Movimiento;

/** Una instancia por conexion: una orden nunca se entrega a otro cliente. */
public class ComunicadorOrquita {
    private final Recopilador recopilador;
    private final Writer salida;
    public ComunicadorOrquita(Recopilador recopilador, Writer salida) {
        this.recopilador=recopilador; this.salida=salida;
    }
    public void enviarRecopilacion(String mensaje) throws IOException {
        PaqueteRecopilacion paquete;
        try { paquete=PaqueteRecopilacion.decodificar(mensaje); }
        catch (IllegalArgumentException | java.time.DateTimeException e) {
            System.err.println("[ComunicadorOrquita] Trama invalida: descartada"); return;
        }
        Movimiento movimiento=recopilador.procesarRecopilacion(paquete);
        if(movimiento!=null) enviarOrdenNavegacion(movimiento);
    }
    public void enviarOrdenNavegacion(Movimiento movimiento) throws IOException {
        salida.write(codificarOrden(movimiento)); salida.flush();
    }
    private String codificarOrden(Movimiento movimiento) { return "ORDEN|"+movimiento.name()+"\n"; }
}
