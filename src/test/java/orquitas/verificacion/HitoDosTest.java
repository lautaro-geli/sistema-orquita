// Archivo de simulacion
package orquitas.verificacion;

import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import orquitas.servidor.captura.*;
import orquitas.servidor.navegacion.*;
import orquitas.servidor.comunicacion.*;
import orquitas.servidor.persistencia.*;
import orquitas.simulacion.EntrenadorDemostracion;

public class HitoDosTest {
    private static Path modelo;
    @BeforeClass public static void prepararModelo() throws Exception {
        modelo=Files.createTempDirectory("orquita-red-").resolve("red.nnet");
        EntrenadorDemostracion.entrenarSiFalta(modelo);
    }
    private static Telemetria uniforme(double valor) { double[] d=new double[12]; Arrays.fill(d,valor); return new Telemetria(d); }
    private static String trama(String id, double valor) {
        return "RECOPILACION|2026-09-30T12:00|"+String.join(";",Collections.nCopies(12,Double.toString(valor)))+"|"+id;
    }
    @Test public void validacionIndependienteSupera95PorCiento() throws Exception {
        assertTrue(EntrenadorDemostracion.evaluar(modelo,modelo.resolveSibling("evaluacion.txt"))>=95);
    }
    @Test public void reinicioCargaModeloSinVolverAEntrenar() throws Exception {
        byte[] antes=Files.readAllBytes(modelo);
        EntrenadorDemostracion.entrenarSiFalta(modelo);
        assertArrayEquals(antes,Files.readAllBytes(modelo));
        ClasificadorEntorno a=new ClasificadorEntorno(modelo), b=new ClasificadorEntorno(modelo);
        for(var m:EntrenadorDemostracion.generar(40,909))
            assertEquals(a.clasificarEntorno(m.telemetria()),b.clasificarEntorno(m.telemetria()));
    }
    @Test public void telemetriaRechazaNoFinitosDimensionesYRangosYEsInmutable() {
        for(double v:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,4001}) {
            try { uniforme(v); fail("Acepto distancia invalida"); } catch(IllegalArgumentException esperado) { }
        }
        try { new Telemetria(1,2); fail("Acepto dimension invalida"); } catch(IllegalArgumentException esperado) { }
        double[] datos=new double[12]; Arrays.fill(datos,1234);
        Telemetria t=new Telemetria(datos); datos[0]=0; t.getDistancias()[0]=0;
        assertEquals(1234,t.getDistancias()[0],0);
    }
    @Test public void frenteLibreConParedLateralNoSeConfundeConObjetoFrontal() {
        ClasificadorEntorno c=new ClasificadorEntorno(modelo);
        double[] d=uniforme(2500).getDistancias(); Arrays.fill(d,0,4,100);
        assertEquals(EntornoClasificado.LIBRE,c.clasificarEntorno(new Telemetria(d)));
        d[5]=100;
        assertEquals(EntornoClasificado.OBJETO,c.clasificarEntorno(new Telemetria(d)));
    }
    @Test public void decideAvanceAmbosGirosYDetencionConModeloReal() {
        Navegador n=new Navegador(new ClasificadorEntorno(modelo));
        assertEquals(Movimiento.AVANZAR,n.determinarMovimiento(uniforme(2500)));
        assertEquals(Movimiento.GIRAR_IZQ,n.determinarMovimiento(new Telemetria(400,400,400,400,120,150,2200,2200,2500,2500,2500,2500)));
        assertEquals(Movimiento.GIRAR_DER,n.determinarMovimiento(new Telemetria(2500,2500,2500,2500,2200,2200,150,120,400,400,400,400)));
        assertEquals(Movimiento.DETENER,n.determinarMovimiento(uniforme(80)));
    }
    @Test public void faltaDeClasificacionConservaUltimaDecisionYModoEscapeSeMantiene() {
        Navegador n=new Navegador(new ClasificadorEntorno(modelo));
        assertEquals(Movimiento.DETENER,n.determinarMovimiento(null));
        n.determinarMovimiento(uniforme(2500)); n.establecerModoOperativo(ModoOperativo.ESCAPE);
        assertEquals(Movimiento.AVANZAR,n.determinarMovimiento(null));
        assertEquals(ModoOperativo.ESCAPE,n.getModoOperativo());
        assertEquals(Movimiento.DETENER,n.determinarMovimiento(uniforme(80)));
    }
    @Test public void tramaInvalidaConCapturaNoProduceEfectosParciales() throws Exception {
        ArchivoEstomago archivo=new ArchivoEstomago(Files.createTempDirectory("orquita-parser-").resolve("estomago.txt").toString());
        ActualizadorEstomago escritor=new ActualizadorEstomago(archivo); escritor.start();
        try {
            Recopilador r=new Recopilador(new ReceptorCapturas(escritor),new Navegador(new ClasificadorEntorno(modelo)));
            for(String t:List.of(trama("A",Double.NaN),trama("B",-1),trama("C",5000),trama("D",100).replace("2026-09-30T12:00","mal"),trama("E;X",100),"RECOPILACION|2026-09-30T12:00|1;2|F"))
                assertFalse(r.procesarRecopilacion(t));
        } finally { escritor.detener(); escritor.join(1000); }
        assertTrue(archivo.leerRegistros().isEmpty());
    }
    @Test(timeout=10000) public void tcpRespondePorSocketCorrectoFragmentaAgrupaYReconecta() throws Exception {
        ArchivoEstomago archivo=new ArchivoEstomago(Files.createTempDirectory("orquita-e2e-").resolve("estomago.txt").toString());
        ActualizadorEstomago escritor=new ActualizadorEstomago(archivo); escritor.start();
        Navegador n=new Navegador(new ClasificadorEntorno(modelo));
        ReceptorIngestaTCP receptor=new ReceptorIngestaTCP(0,new Recopilador(new ReceptorCapturas(escritor),n));
        Thread servidor=new Thread(receptor::iniciar); servidor.start();
        try {
            long limite=System.nanoTime()+2_000_000_000L;
            while(receptor.obtenerPuerto()<0 && System.nanoTime()<limite) Thread.sleep(5);
            assertTrue(receptor.obtenerPuerto()>0);
            try(Socket a=new Socket("localhost",receptor.obtenerPuerto()); Socket b=new Socket("localhost",receptor.obtenerPuerto())) {
                a.setSoTimeout(1500); b.setSoTimeout(1500);
                BufferedReader ra=new BufferedReader(new InputStreamReader(a.getInputStream(),StandardCharsets.UTF_8));
                BufferedReader rb=new BufferedReader(new InputStreamReader(b.getInputStream(),StandardCharsets.UTF_8));
                String mensaje=trama("A",2500)+"\r\n";
                a.getOutputStream().write(mensaje.substring(0,8).getBytes(StandardCharsets.UTF_8));
                a.getOutputStream().flush();
                b.getOutputStream().write((trama("B",80)+"\n").getBytes(StandardCharsets.UTF_8));
                assertEquals("ORDEN|DETENER",rb.readLine());
                a.getOutputStream().write(mensaje.substring(8).getBytes(StandardCharsets.UTF_8));
                assertEquals("ORDEN|AVANZAR",ra.readLine());
                a.getOutputStream().write(("X".repeat(300)+"\n"+trama("INVALIDA",Double.NaN)+"\n"+trama("C",80)+"\n"+trama("A",2500)+"\n").getBytes(StandardCharsets.UTF_8));
                assertEquals("ERROR|TRAMA_INVALIDA",ra.readLine()); // Trama excesiva
                assertEquals("ERROR|TRAMA_INVALIDA",ra.readLine()); // NaN
                assertEquals("ORDEN|DETENER",ra.readLine()); assertEquals("ORDEN|AVANZAR",ra.readLine());
            }
            try(Socket c=new Socket("localhost",receptor.obtenerPuerto())) {
                c.setSoTimeout(1500);
                c.getOutputStream().write((trama("",2500)+"\n").getBytes(StandardCharsets.UTF_8));
                assertEquals("ORDEN|AVANZAR",new BufferedReader(new InputStreamReader(c.getInputStream())).readLine());
            }
        } finally { receptor.detener(); servidor.join(1000); escritor.detener(); escritor.join(1000); }
        assertEquals(3,archivo.leerRegistros().size());
    }
    @Test public void modeloIncompatibleSeRechaza() throws Exception {
        Path ruta=modelo.resolveSibling("incompatible.nnet");
        new org.neuroph.nnet.MultiLayerPerceptron(3,2).save(ruta.toString());
        try { new ClasificadorEntorno(ruta); fail("Acepto otra dimension"); } catch(IllegalArgumentException esperado) { }
    }
}
