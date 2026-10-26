package orquitas.servidor.comunicacion;

import java.io.*;
import orquitas.servidor.captura.Recopilacion;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.Navegacion;
import orquitas.servidor.persistencia.Estomago;

/** Una instancia por conexion: una orden nunca se entrega a otro cliente. */
public class EnlaceOrquita {
    private final Estomago estomago;
    private final Navegacion navegacion;
    private final Writer salida;
    public EnlaceOrquita(Estomago estomago, Navegacion navegacion, Writer salida) {
        this.estomago=estomago; this.navegacion=navegacion; this.salida=salida;
    }
    public void enviarRecopilacion(String linea) throws IOException {
        Recopilacion recopilacion=Recopilacion.decodificar(linea);
        if(!recopilacion.esValida()) {
            System.err.println("[EnlaceOrquita] Trama invalida: descartada");
            enviarErrorTrama(); return;
        }
        Movimiento movimiento=recopilacion.procesar(estomago, navegacion);
        if(movimiento!=null) enviarOrdenNavegacion(movimiento);
    }
    /** Respuesta acotada: no refleja contenido recibido ni confirma una captura. */
    public void enviarErrorTrama() throws IOException {
        salida.write("ERROR|TRAMA_INVALIDA\n"); salida.flush();
    }
    public void enviarOrdenNavegacion(Movimiento movimiento) throws IOException {
        salida.write(codificarOrden(movimiento)); salida.flush();
    }
    private String codificarOrden(Movimiento movimiento) { return "ORDEN|"+movimiento.name()+"\n"; }
}
