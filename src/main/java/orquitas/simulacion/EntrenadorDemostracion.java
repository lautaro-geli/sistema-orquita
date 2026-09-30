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

/** Datos ficticios reproducibles. La regla de etiquetado se usa SOLO para entrenar/evaluar. */
public class EntrenadorDemostracion {
    public record Muestra(Telemetria telemetria, EntornoClasificado etiqueta) { }
    public static List<Muestra> generar(int cantidad, long semilla) {
        Random azar=new Random(semilla);
        List<Muestra> muestras=new ArrayList<>();
        // Alternar garantiza igual cantidad de clases. Rechazar ruido que cambie la clase prevista.
        while(muestras.size()<cantidad) {
            boolean objeto=muestras.size()%2==1;
            double[] d=new double[12];
            int escenario=azar.nextInt(6);
            for(int i=0;i<12;i++) d[i]=600+azar.nextDouble()*3000;
            if(escenario==0) Arrays.fill(d, objeto?80+azar.nextDouble()*200:1000+azar.nextDouble()*2500);
            if(escenario==1) { // Pared lateral con frente despejado.
                for(int i=0;i<4;i++) d[i]=80+azar.nextDouble()*200;
            }
            if(escenario==2) for(int i=8;i<12;i++) d[i]=80+azar.nextDouble()*200;
            if(escenario==3) for(int i=4;i<8;i++) d[i]=430+azar.nextDouble()*500;
            if(objeto) { // Objeto estrecho que solo ocupa uno o dos sectores frontales.
                int sector=4+azar.nextInt(4);
                d[sector]=40+azar.nextDouble()*340;
                if(azar.nextBoolean()) d[4+(sector-4+1)%4]=40+azar.nextDouble()*340;
            }
            if (escenario>=4) { // Ejemplos cercanos al umbral; el ruido también puede cruzarlo.
                for(int i=4;i<8;i++) d[i]=1500+azar.nextDouble()*2000;
                d[4+azar.nextInt(4)]=330+azar.nextDouble()*170;
            }
            for(int i=0;i<12;i++) d[i]=Math.min(4000,Math.max(0,d[i]*(0.95+azar.nextDouble()*0.1)));
            boolean etiquetaObjeto=false;
            for(int i=4;i<8;i++) if(d[i]<400) etiquetaObjeto=true;
            if(etiquetaObjeto==objeto) muestras.add(new Muestra(new Telemetria(d),objeto?EntornoClasificado.OBJETO:EntornoClasificado.LIBRE));
        }
        Collections.shuffle(muestras,azar);
        return muestras;
    }
    public static void entrenarSiFalta(Path ruta) throws IOException {
        if(!Files.exists(ruta)) entrenar(ruta);
    }
    public static void entrenar(Path ruta) throws IOException {
        MultiLayerPerceptron red=new MultiLayerPerceptron(TransferFunctionType.SIGMOID,12,6,2);
        red.randomizeWeights(new Random(20260930L));
        MomentumBackpropagation regla=new MomentumBackpropagation();
        regla.setLearningRate(0.1); regla.setMomentum(0.7);
        regla.setMaxIterations(4000); regla.setMaxError(0.003);
        red.setLearningRule(regla);
        DataSet datos=new DataSet(12,2);
        for(Muestra m:generar(2400,101L)) datos.add(new DataSetRow(m.telemetria().normalizar(),
            m.etiqueta()==EntornoClasificado.LIBRE?new double[]{1,0}:new double[]{0,1}));
        System.out.println("[Entrenamiento] 2400 ejemplos SINTETICOS, MLP 12-6-2, semilla 101");
        red.learn(datos);
        Files.createDirectories(ruta.toAbsolutePath().getParent());
        red.save(ruta.toString());
        System.out.println("[Entrenamiento] Iteraciones="+regla.getCurrentIteration()+" error="+regla.getTotalNetworkError());
    }
    public static double evaluar(Path ruta, Path informe) throws IOException {
        ClasificadorEntorno clasificador=new ClasificadorEntorno(ruta);
        int[][] matriz=new int[2][2];
        for(Muestra m:generar(1000,202L)) {
            EntornoClasificado pred=clasificador.clasificarEntorno(m.telemetria());
            matriz[m.etiqueta().ordinal()][pred.ordinal()]++;
        }
        double precision=(matriz[0][0]+matriz[1][1])/10.0;
        String texto=String.format(Locale.ROOT,
            "DATOS FICTICIOS - NO SON MEDICIONES DE CAMPO%nMLP 12-6-2 sigmoid / MomentumBackpropagation%n"+
            "Entrenamiento: 2400 ejemplos, semilla 101; pesos: 20260930%n"+
            "Validacion independiente: 1000 ejemplos, semilla 202%n"+
            "Filas=real, columnas=prediccion [LIBRE, OBJETO]%nLIBRE: %d %d%nOBJETO: %d %d%nExactitud sintetica: %.2f%%%n",
            matriz[0][0],matriz[0][1],matriz[1][0],matriz[1][1],precision);
        Files.createDirectories(informe.toAbsolutePath().getParent()); Files.writeString(informe,texto);
        System.out.print(texto);
        return precision;
    }
    public static void main(String[] args) throws Exception {
        Path ruta=Path.of(args.length>0?args[0]:"modelos/entorno-demo.nnet");
        entrenarSiFalta(ruta);
        double exactitud=evaluar(ruta,Path.of("evidencias/validacion-sintetica.txt"));
        for(Muestra m:generar(6,303L)) {
            System.out.println(Arrays.toString(m.telemetria().getDistancias())+" esperado="+m.etiqueta()+
                " red="+new ClasificadorEntorno(ruta).clasificarEntorno(m.telemetria()));
        }
        if(exactitud<95) throw new IllegalStateException("No se alcanzo el objetivo del 95% sintetico");
    }
}
