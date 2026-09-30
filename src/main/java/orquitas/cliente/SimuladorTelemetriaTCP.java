// Archivo de simulacion
package orquitas.cliente;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;

/** Cada lectura es ficticia. Recibe una orden por lectura por el mismo socket. */
public class SimuladorTelemetriaTCP {
    public static void main(String[] args) throws Exception {
        String host=args.length>0?args[0]:"localhost";
        int puerto=args.length>1?Integer.parseInt(args[1]):5000;
        String prefijo=args.length>2?args[2]:"DEMO";
        double[][] escenas={
            {2500,2500,2500,2500,2500,2500,2500,2500,2500,2500,2500,2500},
            {100,120,150,180,2000,2000,2000,2000,2500,2500,2500,2500},
            {400,400,400,400,120,150,2200,2200,2500,2500,2500,2500},
            {2500,2500,2500,2500,2200,2200,150,120,400,400,400,400},
            {80,80,80,80,80,80,80,80,80,80,80,80},
            {3000,3000,3000,3000,3000,3000,3000,3000,3000,3000,3000,3000}};
        try(Socket socket=new Socket(host,puerto);
            BufferedReader entrada=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
            BufferedWriter salida=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8))) {
            socket.setSoTimeout(5000);
            for(int i=0;i<escenas.length;i++) {
                String distancias=String.join(";",Arrays.stream(escenas[i]).mapToObj(d->Integer.toString((int)d)).toList());
                String id=i>=2 && i<=4?prefijo+(i-1):"";
                String trama="RECOPILACION|"+LocalDateTime.now()+"|"+distancias+"|"+id;
                salida.write(trama+"\n"); salida.flush();
                String respuesta=entrada.readLine();
                if(respuesta==null || !respuesta.startsWith("ORDEN|")) throw new IOException("No llego una orden valida");
                System.out.println("SIMULADO "+trama+" -> "+respuesta);
                Thread.sleep(650);
            }
        }
    }
}
