// Archivo de simulacion
package orquitas.verificacion;

import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import orquitas.servidor.captura.*;
import orquitas.servidor.comunicacion.*;
import orquitas.servidor.navegacion.*;
import orquitas.servidor.persistencia.*;
import orquitas.simulacion.EntrenadorDemostracion;

/** Pruebas adicionales del Hito 2: umbral, frontera, parser estricto, respuesta ERROR y concurrencia. */
public class RobustezHitoDosTest {
    private static Path modelo;
    private static final String DISTANCIAS_OK = String.join(";", Collections.nCopies(12, "2500"));
    @BeforeClass public static void prepararModelo() throws Exception {
        modelo = ModeloPrueba.ruta();
    }
    private static double[] todas(double v) { double[] d = new double[12]; Arrays.fill(d, v); return d; }
    private static String trama(String distancias, String id) { return "RECOPILACION|2026-09-30T12:00|" + distancias + "|" + id; }

    private static void esperarArranque(ReceptorIngestaTCP rx) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while(rx.obtenerPuerto()<0 && System.nanoTime()<limite) Thread.sleep(5);
        assertTrue("Servidor no arranco",rx.obtenerPuerto()>0);
    }
    @Test(timeout=30000) public void cierreDeClientesSilenciososRepetidoSinEsperaFija() throws Exception {
        for(int i=0;i<30;i++) new LinareadaTest().detenerCierraClientesQueNoEnvianNada();
    }
    @Test public void umbralDeCadaSectorFrontalEstaCercaDe400() throws Exception {
        RedNeuronal c = new RedNeuronal(modelo);
        for (int sector = 4; sector <= 7; sector++) {
            int umbral = -1;
            for (int d = 1000; d >= 40; d -= 5) {
                double[] x = todas(2500); x[sector] = d;
                if (c.clasificarEntorno(new Telemetria(x)) == EntornoClasificado.OBJETO) { umbral = d; break; }
            }
            assertTrue("sector " + sector + " umbral " + umbral, umbral >= 330 && umbral <= 470);
        }
    }
    @Test public void sectoresLateralesAisladosConFrenteLibre() throws Exception {
        RedNeuronal c = new RedNeuronal(modelo);
        for (int sector : new int[]{0, 1, 2, 3, 8, 9, 10, 11}) {
            double[] x = todas(2500); x[sector] = 50;
            assertEquals("sector " + sector, EntornoClasificado.LIBRE, c.clasificarEntorno(new Telemetria(x)));
        }
    }
    @Test public void exactitudEnFronteraSuperaElNivelMinimo() throws Exception {
        RedNeuronal c = new RedNeuronal(modelo);
        Random r = new Random(7); int aciertos = 0, n = 2000;
        for (int i = 0; i < n; i++) {
            double[] x = new double[12]; for (int j = 0; j < 12; j++) x[j] = 1500 + r.nextDouble() * 2000;
            int s = 4 + r.nextInt(4); x[s] = 300 + r.nextDouble() * 200;
            boolean objeto = x[s] < 400;
            if ((c.clasificarEntorno(new Telemetria(x)) == EntornoClasificado.OBJETO) == objeto) aciertos++;
        }
        assertTrue("franja 300-500 mm: " + aciertos + "/" + n, aciertos >= 0.85 * n);
    }
    @Test public void exactitudConOtrasSemillasSupera95() throws Exception {
        RedNeuronal c = new RedNeuronal(modelo);
        for (long semilla = 1000; semilla < 1004; semilla++) {
            int ok = 0; var muestras = EntrenadorDemostracion.generar(1000, semilla);
            for (var m : muestras) if (c.clasificarEntorno(m.telemetria()) == m.etiqueta()) ok++;
            assertTrue("semilla " + semilla + ": " + ok, ok >= 950);
        }
    }
    @Test public void parserRechazaNotacionNoDecimal() {
        for (String malo : new String[]{"1e3", "0x1p3", "+2500", " 2500", "2500 ", "-1", "NaN", "Infinity", "2,5", "", "12345", "4001"}) {
            String d = malo + ";" + String.join(";", Collections.nCopies(11, "2500"));
            assertFalse("Debia rechazar '" + malo + "'", Recopilacion.decodificar(trama(d, "")).esValida());
        }
        assertTrue(Recopilacion.decodificar(trama("2500.5;" + String.join(";", Collections.nCopies(11, "0")), "")).esValida());
    }
    @Test(timeout = 15000) public void tramaInvalidaDeRecopilacionRecibeErrorYLaConexionSigue() throws Exception {
        Path estomago = Files.createTempDirectory("orquita-err-").resolve("estomago.txt");
        Estomago archivo = new Estomago(estomago.toString());
        ReceptorIngestaTCP rx = new ReceptorIngestaTCP(0, archivo, new Navegacion(new RedNeuronal(modelo)));
        Thread servidor = new Thread(rx::iniciar); servidor.start();
        try {
            esperarArranque(rx);
            try (Socket s = new Socket("127.0.0.1", rx.obtenerPuerto())) {
                s.setSoTimeout(2000);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
                for (String mala : List.of(trama("2500;2500", "A"), trama(DISTANCIAS_OK.replaceFirst("2500", "1e3"), "B"),
                        trama(DISTANCIAS_OK, "id malo;"), "RECOPILACION|fecha|" + DISTANCIAS_OK + "|C")) {
                    out.write(mala + "\n"); out.flush();
                    assertEquals("ERROR|TRAMA_INVALIDA", in.readLine());
                }
                out.write(trama(DISTANCIAS_OK, "") + "\n"); out.flush();
                assertEquals("ORDEN|AVANZAR", in.readLine());
            }
        } finally { rx.detener(); servidor.join(1000); archivo.esperarEscrituras(); }
        assertTrue(archivo.leerRegistros().isEmpty());
    }
    @Test(timeout = 30000) public void variosClientesRecibenSuPropiaOrdenYLasCapturasSonUnicas() throws Exception {
        Path estomago = Files.createTempDirectory("orquita-conc-").resolve("estomago.txt");
        Estomago archivo = new Estomago(estomago.toString());
        ReceptorIngestaTCP rx = new ReceptorIngestaTCP(0, archivo, new Navegacion(new RedNeuronal(modelo)));
        Thread servidor = new Thread(rx::iniciar); servidor.start();
        AtomicInteger errores = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            esperarArranque(rx);
            final int puerto = rx.obtenerPuerto();
            List<Future<?>> fs = new ArrayList<>();
            for (int k = 0; k < 6; k++) { final int cliente = k;
                fs.add(pool.submit(() -> {
                    try (Socket s = new Socket("127.0.0.1", puerto)) {
                        s.setSoTimeout(5000);
                        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                        Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
                        for (int i = 0; i < 40; i++) {
                            boolean libre = (i + cliente) % 2 == 0;
                            String d = String.join(";", Collections.nCopies(12, libre ? "3000" : "80"));
                            out.write(trama(d, i % 10 == 0 ? "C" + cliente + "_" + i : "") + "\n"); out.flush();
                            if (!in.readLine().equals(libre ? "ORDEN|AVANZAR" : "ORDEN|DETENER")) errores.incrementAndGet();
                        }
                    } catch (Exception e) { errores.incrementAndGet(); }
                }));
            }
            for (Future<?> f : fs) f.get(25, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); rx.detener(); servidor.join(1000); archivo.esperarEscrituras(); pool.awaitTermination(2, TimeUnit.SECONDS); }
        assertEquals(0, errores.get());
        assertEquals(24, archivo.leerRegistros().size());
    }
}
