// Archivo de simulacion
package orquitas.simulacion;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import orquitas.servidor.navegacion.*;

/** Exporta predicciones reproducibles. No usa la etiqueta de referencia para decidir. */
public class EvaluadorDemostracion {
    private static void registrar(Writer salida, ClasificadorEntorno red, String grupo, int caso,
                                  double[] d, EntornoClasificado esperado) throws IOException {
        Telemetria t=new Telemetria(d);
        EntornoClasificado predicho=red.clasificarEntorno(t);
        Movimiento orden=new Navegador(red).determinarMovimiento(t);
        salida.write(grupo+";"+caso+";"+esperado+";"+predicho+";"+orden);
        for(double v:d) salida.write(String.format(Locale.ROOT,";%.6f",v));
        salida.write("\n");
    }
    public static void main(String[] args) throws Exception {
        Path modelo=Path.of(args.length>0?args[0]:"modelos/entorno-demo.nnet");
        Path salida=Path.of(args.length>1?args[1]:"evidencias/predicciones.csv");
        ClasificadorEntorno red=new ClasificadorEntorno(modelo);
        Files.createDirectories(salida.toAbsolutePath().getParent());
        try(Writer csv=Files.newBufferedWriter(salida)) {
            csv.write("grupo;caso;esperado;predicho;orden;d0;d1;d2;d3;d4;d5;d6;d7;d8;d9;d10;d11\n");
            for(long semilla:new long[]{202,1000,1001,1002,1003,1004,1005,1006,1007,1008,1009,424242}) {
                int i=0;
                for(var m:EntrenadorDemostracion.generar(1000,semilla))
                    registrar(csv,red,"general_"+semilla,i++,m.telemetria().getDistancias(),m.etiqueta());
            }
            // Distribución de frontera independiente del generador de entrenamiento.
            for(long semilla:new long[]{7,987654}) {
                Random azar=new Random(semilla);
                for(int i=0;i<4000;i++) {
                    double[] d=new double[12];
                    for(int j=0;j<12;j++) d[j]=1500+azar.nextDouble()*2000;
                    int sector=4+azar.nextInt(4);d[sector]=300+azar.nextDouble()*200;
                    registrar(csv,red,"frontera_"+semilla,i,d,d[sector]<400?EntornoClasificado.OBJETO:EntornoClasificado.LIBRE);
                }
            }
            double[][] ejemplos={
                {2500,2500,2500,2500,2500,2500,2500,2500,2500,2500,2500,2500},
                {100,120,150,180,2000,2000,2000,2000,2500,2500,2500,2500},
                {400,400,400,400,120,150,2200,2200,2500,2500,2500,2500},
                {2500,2500,2500,2500,2200,2200,150,120,400,400,400,400},
                {80,80,80,80,80,80,80,80,80,80,80,80},
                {3000,3000,3000,3000,3000,3000,3000,3000,3000,3000,3000,3000}};
            for(int i=0;i<ejemplos.length;i++) registrar(csv,red,"ejemplo",i+1,ejemplos[i],
                i==2 || i==3 || i==4?EntornoClasificado.OBJETO:EntornoClasificado.LIBRE);
            for(int sector=4;sector<8;sector++) for(int distancia=300;distancia<=550;distancia+=5) {
                double[] d=new double[12];Arrays.fill(d,2500);d[sector]=distancia;
                registrar(csv,red,"umbral_"+sector,distancia,d,distancia<400?EntornoClasificado.OBJETO:EntornoClasificado.LIBRE);
            }
        }
        System.out.println("Predicciones de simulacion guardadas en "+salida);
    }
}
