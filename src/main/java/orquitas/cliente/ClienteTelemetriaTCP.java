// Archivo de simulacion
package orquitas.cliente;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;

/** Cada lectura es ficticia. Recibe una orden por lectura por el mismo socket. */
public class ClienteTelemetriaTCP {
    public static void main(String[] args) throws Exception {
        String host=args.length>0?args[0]:"localhost";
        int puerto=args.length>1?Integer.parseInt(args[1]):5000;
        String prefijo=args.length>2?args[2]:"DEMO";
        // Clases segun la red: LIBRE (todo lejos), LOBITO (1 sector cerca), OBJETO (bloque contiguo cerca).
        double[][] escenas={
            {2500,2500,2500,2500,2500,2500,2500,2500,2500,2500,2500,2500},  // LIBRE: avanza.
            {100,120,150,180,2000,2000,2000,2000,2500,2500,2500,2500},      // Pared a la derecha: esquiva.
            {2500,2500,400,2500,2500,2500,2500,2500,2500,2500,2500,2500},   // Lobito a la derecha: lo persigue.
            {2500,2500,2500,2500,2500,300,2500,2500,2500,2500,2500,2500},   // Lobito al frente: avanza; captura 1.
            {400,400,400,400,120,150,2200,2200,2500,2500,2500,2500},        // Obstaculo: esquiva; captura 2.
            {80,80,80,80,80,80,80,80,80,80,80,80},                          // Encerrada: se detiene; captura 3.
            {3000,3000,3000,3000,3000,3000,3000,3000,3000,3000,3000,3000},  // Ya en ESCAPE: avanza.
            {2500,2500,2500,2500,2500,2500,2500,2500,2500,2500,400,2500},   // ESCAPE ignora al lobito.
            {400,400,400,400,120,150,2200,2200,2500,2500,2500,2500}};       // ESCAPE esquiva sin frenar.
        try(Socket socket=new Socket(host,puerto);
            BufferedReader entrada=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
            BufferedWriter salida=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8))) {
            socket.setSoTimeout(5000);
            for(int i=0;i<escenas.length;i++) {
                String distancias=String.join(";",Arrays.stream(escenas[i]).mapToObj(d->Integer.toString((int)d)).toList());
                String id=i>=3 && i<=5?prefijo+(i-2):""; // La captura la reporta el robot.
                String trama="RECOPILACION|"+LocalDateTime.now()+"|"+distancias+"|"+id;
                salida.write(trama+"\n"); salida.flush();
                String respuesta=entrada.readLine();
                while(respuesta!=null && respuesta.startsWith("ESTADO|")) { // Estado o senal CU-12: no es una orden.
                    System.out.println("[Cliente] Cambio de estado: "+respuesta.substring("ESTADO|".length()));
                    respuesta=entrada.readLine();
                }
                if(respuesta==null || !respuesta.startsWith("ORDEN|")) throw new IOException("No llego una orden valida");
                System.out.println("SIMULADO "+trama+" -> "+respuesta);
                Thread.sleep(650);
            }
        }
    }
}
