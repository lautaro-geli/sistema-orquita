// Archivo de simulacion
package orquitas.simulacion;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import org.neuroph.core.data.*;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.nnet.learning.MomentumBackpropagation;
import org.neuroph.util.TransferFunctionType;
import orquitas.servidor.navegacion.*;

/**
 * Datos ficticios reproducibles de un VL53L1X (ToF, solo distancia, haz angosto) en 12 sectores de 15 grados.
 * La clase sale del ANCHO de la banda cercana: un lobito de 17 mm subtiende menos de 15 grados (1 sector);
 * un obstaculo de ~90 mm ocupa varios sectores contiguos y un muro, muchos. La regla de etiquetado se usa
 * SOLO para generar y evaluar; la inferencia usa exclusivamente la red.
 * Limite: la distincion por ancho es confiable a rango corto/medio (donde importa para cazar); muy lejos
 * todo tiende a LIBRE. No se usa la senal de intensidad del sensor.
 */
public class EntrenamientoDemostracion {
    public record Muestra(Telemetria telemetria, EntornoClasificado etiqueta) { }
    static final double CERCA_MIN=50, CERCA_MAX=900, LEJOS_MIN=1200, LEJOS_MAX=4000, RUIDO=10;
    public static List<Muestra> generar(int cantidad, long semilla) {
        Random azar=new Random(semilla);
        EntornoClasificado[] clases=EntornoClasificado.values();
        List<Muestra> muestras=new ArrayList<>();
        while(muestras.size()<cantidad) {
            EntornoClasificado clase=clases[muestras.size()%clases.length]; // Clases balanceadas.
            double[] d=new double[12];
            for(int i=0;i<12;i++) d[i]=LEJOS_MIN+azar.nextDouble()*(LEJOS_MAX-LEJOS_MIN);
            if(clase==EntornoClasificado.LOBITO) {
                d[azar.nextInt(12)]=cerca(azar);
            } else if(clase==EntornoClasificado.OBJETO) {
                // Mitad obstaculos (2..6 sectores contiguos) y mitad muros (7..12).
                int ancho=azar.nextBoolean()?2+azar.nextInt(5):7+azar.nextInt(6);
                int inicio=azar.nextInt(12-ancho+1);
                for(int i=inicio;i<inicio+ancho;i++) d[i]=cerca(azar);
            }
            for(int i=0;i<12;i++) d[i]=Math.min(4000,Math.max(0,d[i]+(azar.nextDouble()*2-1)*RUIDO));
            muestras.add(new Muestra(new Telemetria(d),clase));
        }
        Collections.shuffle(muestras,azar);
        return muestras;
    }
    private static double cerca(Random azar) { return CERCA_MIN+azar.nextDouble()*(CERCA_MAX-CERCA_MIN); }
    public static void entrenarSiFalta(Path ruta) throws IOException {
        if(!Files.exists(ruta)) entrenar(ruta);
    }
    /** Codificacion one-hot en el orden del enum: LIBRE, OBJETO, LOBITO. */
    static double[] salidaEsperada(EntornoClasificado clase) {
        double[] salida=new double[EntornoClasificado.values().length];
        salida[clase.ordinal()]=1;
        return salida;
    }
    public static void entrenar(Path ruta) throws IOException {
        MultiLayerPerceptron red=new MultiLayerPerceptron(TransferFunctionType.SIGMOID,12,6,3);
        red.randomizeWeights(new Random(20260930L));
        MomentumBackpropagation regla=new MomentumBackpropagation();
        regla.setLearningRate(0.1); regla.setMomentum(0.7);
        regla.setMaxIterations(4000); regla.setMaxError(0.003);
        red.setLearningRule(regla);
        DataSet datos=new DataSet(12,3);
        for(Muestra m:generar(2400,101L)) datos.add(new DataSetRow(m.telemetria().normalizar(),salidaEsperada(m.etiqueta())));
        System.out.println("[Entrenamiento] 2400 ejemplos SINTETICOS, MLP 12-6-3, semilla 101");
        red.learn(datos);
        Files.createDirectories(ruta.toAbsolutePath().getParent());
        red.save(ruta.toString());
        System.out.println("[Entrenamiento] Iteraciones="+regla.getCurrentIteration()+" error="+regla.getTotalNetworkError());
    }
    public static double evaluar(Path ruta, Path informe) throws IOException {
        RedNeuronal redNeuronal=new RedNeuronal(ruta);
        int n=EntornoClasificado.values().length, total=1000, aciertos=0;
        int[][] matriz=new int[n][n+1]; // Ultima columna: entorno no reconocido (salida bajo el umbral).
        for(Muestra m:generar(total,202L)) {
            EntornoClasificado pred=redNeuronal.clasificarEntorno(m.telemetria());
            matriz[m.etiqueta().ordinal()][pred==null?n:pred.ordinal()]++;
            if(pred==m.etiqueta()) aciertos++;
        }
        double precision=100.0*aciertos/total;
        StringBuilder filas=new StringBuilder();
        for(EntornoClasificado real:EntornoClasificado.values())
            filas.append(String.format(Locale.ROOT,"%s: %d %d %d %d%n",real,
                matriz[real.ordinal()][0],matriz[real.ordinal()][1],matriz[real.ordinal()][2],matriz[real.ordinal()][3]));
        String texto=String.format(Locale.ROOT,
            "DATOS FICTICIOS - NO SON MEDICIONES DE CAMPO%nMLP 12-6-3 sigmoid / MomentumBackpropagation%n"+
            "Entrenamiento: 2400 ejemplos, semilla 101; pesos: 20260930%n"+
            "Validacion independiente: 1000 ejemplos, semilla 202%n"+
            "Filas=real, columnas=prediccion [LIBRE, OBJETO, LOBITO, NO_RECONOCIDO]%n%sExactitud sintetica: %.2f%%%n",
            filas,precision);
        Files.createDirectories(informe.toAbsolutePath().getParent()); Files.writeString(informe,texto);
        System.out.print(texto);
        return precision;
    }
    /** Guarda los ejemplos de entrenamiento (semilla 101): numero;clase;d0..d11 en mm. */
    public static void exportarEntrenamiento(Path csv) throws IOException {
        StringBuilder texto=new StringBuilder("ejemplo;clase;d0;d1;d2;d3;d4;d5;d6;d7;d8;d9;d10;d11\n");
        int numero=1;
        for(Muestra m:generar(2400,101L)) {
            texto.append(numero++).append(';').append(m.etiqueta());
            for(double d:m.telemetria().getDistancias()) texto.append(String.format(Locale.ROOT,";%.1f",d));
            texto.append('\n');
        }
        Files.createDirectories(csv.toAbsolutePath().getParent()); Files.writeString(csv,texto.toString());
    }
    public static void main(String[] args) throws Exception {
        Path ruta=Path.of(args.length>0?args[0]:"modelos/entorno-demo.nnet");
        entrenarSiFalta(ruta);
        exportarEntrenamiento(Path.of("evidencias/datos-entrenamiento.csv"));
        double exactitud=evaluar(ruta,Path.of("evidencias/validacion-sintetica.txt"));
        for(Muestra m:generar(6,303L)) {
            System.out.println(Arrays.toString(m.telemetria().getDistancias())+" esperado="+m.etiqueta()+
                " red="+new RedNeuronal(ruta).clasificarEntorno(m.telemetria()));
        }
        if(exactitud<95) throw new IllegalStateException("No se alcanzo el objetivo del 95% sintetico");
    }
}
