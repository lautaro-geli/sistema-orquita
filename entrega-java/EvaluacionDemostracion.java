// Archivo de simulacion
package orquitas.simulacion;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import orquitas.servidor.navegacion.*;

/** Exporta predicciones reproducibles. No usa la etiqueta de referencia para decidir. */
public class EvaluacionDemostracion {
    private static void registrar(Writer salida, RedNeuronal red, String grupo, int caso,
                                  double[] d, EntornoClasificado esperado) throws IOException {
        Telemetria t=new Telemetria(d);
        EntornoClasificado predicho=red.clasificarEntorno(t);
        Movimiento orden=new Navegacion(red).determinarMovimiento(t);
        salida.write(grupo+";"+caso+";"+esperado+";"+(predicho==null?"NO_RECONOCIDO":predicho)+";"+orden);
        for(double v:d) salida.write(String.format(Locale.ROOT,";%.6f",v));
        salida.write("\n");
    }
    public static void main(String[] args) throws Exception {
        Path modelo=Path.of(args.length>0?args[0]:"modelos/entorno-demo.nnet");
        Path salida=Path.of(args.length>1?args[1]:"evidencias/predicciones.csv");
        RedNeuronal red=new RedNeuronal(modelo);
        Files.createDirectories(salida.toAbsolutePath().getParent());
        try(Writer csv=Files.newBufferedWriter(salida)) {
            csv.write("grupo;caso;esperado;predicho;orden;d0;d1;d2;d3;d4;d5;d6;d7;d8;d9;d10;d11\n");
            for(long semilla:new long[]{202,1000,1001,1002,1003,1004,1005,1006,1007,1008,1009,424242}) {
                int i=0;
                for(var m:EntrenamientoDemostracion.generar(1000,semilla))
                    registrar(csv,red,"general_"+semilla,i++,m.telemetria().getDistancias(),m.etiqueta());
            }
            // Frontera de distancia (generador propio): un sector entre 600 y 1500 mm, sin la franja 900-1200
            // que el entrenamiento deja vacia. <900 es LOBITO; >1200, LIBRE.
            for(long semilla:new long[]{7,987654}) {
                Random azar=new Random(semilla);
                for(int i=0;i<4000;i++) {
                    double[] d=new double[12];
                    for(int j=0;j<12;j++) d[j]=1500+azar.nextDouble()*2000;
                    int sector=azar.nextInt(12);
                    do { d[sector]=600+azar.nextDouble()*900; } while(d[sector]>=900 && d[sector]<=1200);
                    registrar(csv,red,"frontera_"+semilla,i,d,d[sector]<900?EntornoClasificado.LOBITO:EntornoClasificado.LIBRE);
                }
            }
            // Frontera de ancho: 1 sector cercano (LOBITO) contra 2 contiguos (OBJETO), la diferencia mas fina.
            for(long semilla:new long[]{8,24680}) {
                Random azar=new Random(semilla);
                for(int i=0;i<4000;i++) {
                    double[] d=new double[12];
                    for(int j=0;j<12;j++) d[j]=1200+azar.nextDouble()*2800;
                    int ancho=1+azar.nextInt(2), inicio=azar.nextInt(13-ancho);
                    for(int j=inicio;j<inicio+ancho;j++) d[j]=50+azar.nextDouble()*850;
                    registrar(csv,red,"ancho_"+semilla,i,d,ancho==1?EntornoClasificado.LOBITO:EntornoClasificado.OBJETO);
                }
            }
            double[][] ejemplos={
                {2500,2500,2500,2500,2500,2500,2500,2500,2500,2500,2500,2500},
                {2500,2500,2500,2500,2500,2500,2500,2500,2500,2500,400,2500},
                {100,120,150,180,2000,2000,2000,2000,2500,2500,2500,2500},
                {400,400,400,400,120,150,2200,2200,2500,2500,2500,2500},
                {2500,2500,2500,2500,2200,2200,150,120,400,400,400,400},
                {80,80,80,80,80,80,80,80,80,80,80,80},
                {3000,3000,3000,3000,3000,3000,3000,3000,3000,3000,3000,3000}};
            EntornoClasificado[] esperados={EntornoClasificado.LIBRE,EntornoClasificado.LOBITO,EntornoClasificado.OBJETO,
                EntornoClasificado.OBJETO,EntornoClasificado.OBJETO,EntornoClasificado.OBJETO,EntornoClasificado.LIBRE};
            for(int i=0;i<ejemplos.length;i++) registrar(csv,red,"ejemplo",i+1,ejemplos[i],esperados[i]);
            // Barrido de un sector aislado: referencia en 1050 mm, el medio de la franja sin entrenar.
            for(int sector=0;sector<12;sector++) for(int distancia=700;distancia<=1400;distancia+=5) {
                double[] d=new double[12];Arrays.fill(d,2500);d[sector]=distancia;
                registrar(csv,red,"umbral_"+sector,distancia,d,distancia<1050?EntornoClasificado.LOBITO:EntornoClasificado.LIBRE);
            }
        }
        System.out.println("Predicciones de simulacion guardadas en "+salida);
    }
}
