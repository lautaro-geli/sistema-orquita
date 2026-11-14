// Archivo de simulacion
package orquitas.herramientas;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.Test;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import orquitas.herramientas.EntrenamientoSimulado.Ejemplo;
import orquitas.herramientas.EntrenamientoSimulado.Redes;
import orquitas.herramientas.EntrenamientoSimulado.Resumen;
import orquitas.servidor.navegacion.EtiquetaSector;
import orquitas.servidor.navegacion.MemoriaNavegacion;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.Navegacion;
import orquitas.servidor.navegacion.OrdenMovimiento;
import orquitas.servidor.navegacion.RedMovimiento;
import orquitas.servidor.navegacion.RedPercepcion;
import orquitas.servidor.navegacion.RepresentacionIntermedia;
import orquitas.simulacion.bahia.RobotSimulado;

/**
 * Entrenamiento en la bahía simulada, sin entrenar redes: los datos se arman igual que en el Servidor y
 * la simulación repartida entre hilos da exactamente lo mismo que en un solo hilo.
 */
public class EntrenamientoSimuladoTest {
    private static final RobotSimulado.Calibracion CAL = RobotSimulado.Calibracion.ESTIMADA;

    /** Percepción de prueba: todo PARED, salvo un Lobito en el sector 8 cuando el barrido está a 600 mm. */
    static class PercepcionFija extends RedPercepcion {
        @Override public synchronized double[] probabilidades(int[] telemetria) {
            EtiquetaSector[] e = new EtiquetaSector[13];
            Arrays.fill(e, EtiquetaSector.PARED);
            if (telemetria[0] == 600) e[8] = EtiquetaSector.LOBITO;
            return RedPercepcion.salidaDeseada(e);
        }
    }

    /** Movimiento de prueba: devuelve las órdenes de una lista, en orden, y guarda las entradas que recibió. */
    static class MovimientoGuionado extends RedMovimiento {
        final List<OrdenMovimiento> guion;
        final List<double[]> entradasRecibidas = new ArrayList<>();
        MovimientoGuionado(List<OrdenMovimiento> guion) { this.guion = guion; }
        @Override public synchronized OrdenMovimiento decidir(double[] entradas) {
            entradasRecibidas.add(entradas.clone());
            return guion.get(entradasRecibidas.size() - 1);
        }
    }

    private static final EtiquetaSector[] SIN_ETIQUETAS = new EtiquetaSector[13];
    private static final OrdenMovimiento AVANZAR_400 = new OrdenMovimiento(Movimiento.AVANZAR, 400);
    private static final OrdenMovimiento AVANZAR_800 = new OrdenMovimiento(Movimiento.AVANZAR, 800);
    private static final OrdenMovimiento IZQUIERDA_90 = new OrdenMovimiento(Movimiento.GIRAR_IZQ, 750);
    private static final OrdenMovimiento DERECHA_30 = new OrdenMovimiento(Movimiento.GIRAR_DER, 250);
    private static final OrdenMovimiento ATRAS_300 = new OrdenMovimiento(Movimiento.RETROCEDER, 300);

    /** Dos episodios armados a mano: en el primer barrido se ejecutó una orden distinta de la del maestro. */
    private static List<List<Ejemplo>> episodiosDePrueba() {
        List<Ejemplo> uno = List.of(
                new Ejemplo(todo(800), SIN_ETIQUETAS, ModoOperativo.BUSQUEDA, AVANZAR_400, IZQUIERDA_90),
                new Ejemplo(todo(600), SIN_ETIQUETAS, ModoOperativo.BUSQUEDA, DERECHA_30, DERECHA_30),
                new Ejemplo(todo(700), SIN_ETIQUETAS, ModoOperativo.ESCAPE, AVANZAR_800, AVANZAR_800));
        List<Ejemplo> dos = List.of(new Ejemplo(todo(500), SIN_ETIQUETAS, ModoOperativo.BUSQUEDA, ATRAS_300, ATRAS_300));
        return List.of(uno, dos);
    }

    private static double[] memoria(DataSetRow fila) {
        return Arrays.copyOfRange(fila.getInput(), RepresentacionIntermedia.ENTRADAS, RedMovimiento.ENTRADAS);
    }

    @Test public void lasFilasDeMovimientoReproducenCadaEpisodioConLaOrdenEjecutada() {
        List<DataSetRow> filas = EntrenamientoSimulado.filasMovimiento(episodiosDePrueba(), new PercepcionFija());
        // Balanceo: "frente" aparece 2 veces y las otras clases 1, así que esas filas se repiten 2 veces.
        assertEquals(6, filas.size());
        DataSetRow primero = filas.get(0), segundo = filas.get(1), tercero = filas.get(3), otroEpisodio = filas.get(4);
        assertSame(segundo, filas.get(2));
        assertSame(otroEpisodio, filas.get(5));
        for (DataSetRow f : filas) {
            assertEquals(24, f.getInput().length);
            assertEquals(RedMovimiento.SALIDAS, f.getDesiredOutput().length);
        }
        // Primer barrido: memoria vacía. Se enseña la orden del maestro (avanzar 400 ms)...
        assertArrayEquals(new MemoriaNavegacion().entradas(), memoria(primero), 0);
        assertArrayEquals(RedMovimiento.salidaDeseada(AVANZAR_400), primero.getDesiredOutput(), 0);
        // ...pero la memoria del segundo barrido tiene la orden que se ejecutó (girar 90° a la izquierda).
        assertEquals(1, memoria(segundo)[MemoriaNavegacion.ORDEN_GIRAR_IZQ], 0);
        assertEquals(0, memoria(segundo)[MemoriaNavegacion.ORDEN_AVANZAR], 0);
        assertEquals(0.5, memoria(segundo)[MemoriaNavegacion.CUANTO_ANTERIOR], 1e-12);
        assertEquals(1, memoria(segundo)[MemoriaNavegacion.RUMBO_SENO], 1e-12);
        assertEquals(0, memoria(segundo)[MemoriaNavegacion.CICLO_DESDE_META], 0); // La percepción ve el Lobito a +30°.
        assertEquals(1, segundo.getInput()[RepresentacionIntermedia.HAY_META], 0);
        // Tercer barrido, ya en ESCAPE: giró 30° a la derecha (rumbo 60°) y la meta anterior se olvidó.
        assertEquals(1, memoria(tercero)[MemoriaNavegacion.ORDEN_GIRAR_DER], 0);
        assertEquals((Math.sin(Math.toRadians(60)) + 1) / 2, memoria(tercero)[MemoriaNavegacion.RUMBO_SENO], 1e-9);
        assertEquals(1, memoria(tercero)[MemoriaNavegacion.CICLO_DESDE_META], 0);
        assertEquals(1, tercero.getInput()[RepresentacionIntermedia.MODO_ESCAPE], 0);
        // El segundo episodio empieza con la memoria vacía: no arrastra nada del primero.
        assertArrayEquals(new MemoriaNavegacion().entradas(), memoria(otroEpisodio), 0);
        assertArrayEquals(RedMovimiento.salidaDeseada(ATRAS_300), otroEpisodio.getDesiredOutput(), 0);
        // datosMovimiento son esas mismas filas, mezcladas.
        DataSet datos = EntrenamientoSimulado.datosMovimiento(episodiosDePrueba(), new PercepcionFija());
        assertEquals(6, datos.size());
        assertEquals(24, datos.getInputSize());
    }

    @Test public void elEntrenamientoArmaLasMismasEntradasQueElServidor() {
        // El Servidor recibe los mismos barridos y su red de movimiento "decide" las órdenes que se ejecutaron.
        List<Ejemplo> episodio = episodiosDePrueba().get(0);
        MovimientoGuionado guion = new MovimientoGuionado(List.of(IZQUIERDA_90, DERECHA_30, AVANZAR_800));
        Navegacion navegacion = new Navegacion(new PercepcionFija(), guion);
        for (Ejemplo e : episodio) {
            if (e.modo() == ModoOperativo.ESCAPE) navegacion.activarModoEscape();
            navegacion.determinarMovimiento(e.telemetria());
            assertEquals(e.ejecutada(), navegacion.obtenerOrdenDecidida()); // Ni el reflejo ni el atasco la cambiaron.
        }
        List<DataSetRow> filas = EntrenamientoSimulado.filasMovimiento(List.of(episodio), new PercepcionFija());
        assertEquals(3, guion.entradasRecibidas.size());
        assertArrayEquals(guion.entradasRecibidas.get(0), filas.get(0).getInput(), 0);
        assertArrayEquals(guion.entradasRecibidas.get(1), filas.get(1).getInput(), 0);
        assertArrayEquals(guion.entradasRecibidas.get(2), filas.get(filas.size() - 1).getInput(), 0);
    }

    @Test(timeout = 60000) public void elMaestroDejaEpisodiosEnOrdenConLaOrdenEjecutada() {
        List<List<Ejemplo>> episodios = EntrenamientoSimulado.recolectarConMaestro(2, CAL, 1);
        assertEquals(2, episodios.size());
        for (List<Ejemplo> episodio : episodios) {
            assertFalse(episodio.isEmpty());
            assertEquals(ModoOperativo.BUSQUEDA, episodio.get(0).modo()); // Cada episodio empieza una prueba nueva.
            for (Ejemplo e : episodio) {
                assertEquals(39, e.telemetria().length);
                assertEquals(13, e.etiquetas().length);
                assertNotNull(e.orden());
                assertNotNull(e.ejecutada());
                assertNotEquals(ModoOperativo.LIBERADA, e.modo());
            }
        }
        assertEquals(episodios.get(0).size() + episodios.get(1).size(), EntrenamientoSimulado.contar(episodios));
        // Con esos episodios se arman las filas de las dos redes (red de percepción sin entrenar: solo el formato).
        RedPercepcion percepcion = new RedPercepcion(EntrenamientoSimulado.nuevaRed(RedPercepcion.ENTRADAS, 4, RedPercepcion.SALIDAS_RED));
        List<DataSetRow> filas = EntrenamientoSimulado.filasMovimiento(episodios, percepcion);
        assertTrue(filas.size() >= EntrenamientoSimulado.contar(episodios));
        for (DataSetRow f : filas) {
            assertEquals(RedMovimiento.ENTRADAS, f.getInput().length);
            for (double v : f.getInput()) assertTrue(v >= 0 && v <= 1);
        }
        assertEquals(RedPercepcion.ENTRADAS, EntrenamientoSimulado.datosPercepcion(episodios).getInputSize());
    }

    @Test(timeout = 60000) public void enDaggerLaOrdenEjecutadaEsLaDelMaestroOLaDeLasRedes() {
        // Redes sin entrenar (pesos al azar con semilla fija): solo importa cómo se guardan los ejemplos.
        List<List<Ejemplo>> episodios = EntrenamientoSimulado.recolectarConRedes(1, CAL, redesSinEntrenar(), 777L, 1);
        assertEquals(1, episodios.size());
        int delMaestro = 0, deLasRedes = 0;
        for (Ejemplo e : episodios.get(0)) {
            if (e.ejecutada().equals(e.orden())) delMaestro++;
            else deLasRedes++;
        }
        assertTrue("maestro " + delMaestro + ", redes " + deLasRedes, delMaestro > 0 && deLasRedes > 0);
    }

    // ---------------------------------------------------------------- Reparto de la simulación entre hilos

    /** Copias por hilo de dos redes sin entrenar (pesos al azar con semilla fija). */
    private static Supplier<Redes> redesSinEntrenar() {
        MultiLayerPerceptron percepcion = EntrenamientoSimulado.nuevaRed(RedPercepcion.ENTRADAS, 4, RedPercepcion.SALIDAS_RED);
        MultiLayerPerceptron movimiento = EntrenamientoSimulado.nuevaRed(RedMovimiento.ENTRADAS, 4, RedMovimiento.SALIDAS);
        return EntrenamientoSimulado.copiasDe(percepcion, movimiento);
    }

    /** Los mismos episodios: misma cantidad y, en el mismo orden, misma telemetría, etiquetas, modo y órdenes. */
    private static void assertMismosEpisodios(List<List<Ejemplo>> esperados, List<List<Ejemplo>> obtenidos) {
        assertEquals(esperados.size(), obtenidos.size());
        for (int i = 0; i < esperados.size(); i++) {
            List<Ejemplo> a = esperados.get(i), b = obtenidos.get(i);
            assertEquals("ejemplos del episodio " + i, a.size(), b.size());
            for (int k = 0; k < a.size(); k++) {
                String donde = "episodio " + i + ", ejemplo " + k;
                assertArrayEquals(donde, a.get(k).telemetria(), b.get(k).telemetria());
                assertArrayEquals(donde, a.get(k).etiquetas(), b.get(k).etiquetas());
                assertEquals(donde, a.get(k).modo(), b.get(k).modo());
                assertEquals(donde, a.get(k).orden(), b.get(k).orden());
                assertEquals(donde, a.get(k).ejecutada(), b.get(k).ejecutada());
            }
        }
    }

    @Test(timeout = 60000) public void conTresEpisodiosDelMaestroUnHiloYTresHilosDanLosMismosEjemplos() {
        List<List<Ejemplo>> conUno = EntrenamientoSimulado.recolectarConMaestro(3, CAL, 1);
        List<List<Ejemplo>> conTres = EntrenamientoSimulado.recolectarConMaestro(3, CAL, 3);
        assertEquals(3, conUno.size());
        assertTrue(EntrenamientoSimulado.contar(conUno) > 0);
        assertEquals(EntrenamientoSimulado.contar(conUno), EntrenamientoSimulado.contar(conTres));
        assertMismosEpisodios(conUno, conTres);
        // Los tres episodios son distintos entre sí (cada uno tiene su semilla)...
        assertFalse(Arrays.equals(conUno.get(0).get(0).telemetria(), conUno.get(1).get(0).telemetria()));
        assertFalse(Arrays.equals(conUno.get(1).get(0).telemetria(), conUno.get(2).get(0).telemetria()));
        // ...y cada uno depende solo de su índice: pedir menos episodios o más hilos que episodios no los cambia.
        assertMismosEpisodios(conUno.subList(0, 2), EntrenamientoSimulado.recolectarConMaestro(2, CAL, 8));
    }

    @Test(timeout = 60000) public void conRedesUnHiloYTresHilosDanLoMismo() {
        Supplier<Redes> redes = redesSinEntrenar();
        // Episodios de una ronda DAgger: cada hilo con sus copias de las redes, cada episodio con su piloto y su memoria.
        List<List<Ejemplo>> conUno = EntrenamientoSimulado.recolectarConRedes(3, CAL, redes, 4242L, 1);
        List<List<Ejemplo>> conTres = EntrenamientoSimulado.recolectarConRedes(3, CAL, redes, 4242L, 3);
        assertEquals(3, conUno.size());
        assertMismosEpisodios(conUno, conTres);
        // Evaluación, matriz de confusión y acuerdo de dirección.
        Resumen maestro = EntrenamientoSimulado.evaluarMaestro(3, CAL, 1);
        assertEquals(maestro, EntrenamientoSimulado.evaluarMaestro(3, CAL, 3));
        assertEquals(3, maestro.pruebas());
        assertEquals(EntrenamientoSimulado.evaluarRedes(redes, 3, CAL, 1), EntrenamientoSimulado.evaluarRedes(redes, 3, CAL, 3));
        int[][] confusion = EntrenamientoSimulado.confusionPercepcion(redes, 3, CAL, 1);
        assertTrue(Arrays.deepEquals(confusion, EntrenamientoSimulado.confusionPercepcion(redes, 3, CAL, 3)));
        int sectores = 0;
        for (int[] fila : confusion) for (int v : fila) sectores += v;
        assertTrue(sectores > 0 && sectores % 13 == 0); // 13 sectores por barrido.
        assertEquals(EntrenamientoSimulado.acuerdoDireccion(redes, 3, CAL, 1), EntrenamientoSimulado.acuerdoDireccion(redes, 3, CAL, 3), 0);
    }

    @Test public void lasCopiasDeLasRedesSonObjetosDistintosConLasMismasSalidas() {
        MultiLayerPerceptron percepcion = EntrenamientoSimulado.nuevaRed(RedPercepcion.ENTRADAS, 4, RedPercepcion.SALIDAS_RED);
        MultiLayerPerceptron movimiento = EntrenamientoSimulado.nuevaRed(RedMovimiento.ENTRADAS, 4, RedMovimiento.SALIDAS);
        Supplier<Redes> copias = EntrenamientoSimulado.copiasDe(percepcion, movimiento);
        Redes a = copias.get(), b = copias.get();
        assertNotSame(a.percepcion(), b.percepcion());
        assertNotSame(a.movimiento(), b.movimiento());
        int[] t = con(con(todo(700), 3, 250), 9, -1);
        double[] p = new RedPercepcion(percepcion).probabilidades(t);
        assertArrayEquals(p, a.percepcion().probabilidades(t), 0);
        assertArrayEquals(p, b.percepcion().probabilidades(t), 0);
        double[] entradas = RedMovimiento.entradasDelCiclo(t, p, ModoOperativo.BUSQUEDA, new MemoriaNavegacion());
        OrdenMovimiento orden = new RedMovimiento(movimiento).decidir(entradas);
        assertEquals(orden, a.movimiento().decidir(entradas));
        assertEquals(orden, b.movimiento().decidir(entradas));
        // También después de entrenar (acá, 2 épocas con 6 filas): es lo que pasa antes de cada ronda DAgger.
        EntrenamientoSimulado.entrenar(movimiento, EntrenamientoSimulado.datosMovimiento(episodiosDePrueba(), new PercepcionFija()), 2, "movimiento de prueba");
        Redes entrenadas = EntrenamientoSimulado.copiasDe(percepcion, movimiento).get();
        double[] salida = salidaDe(movimiento, entradas);
        assertFalse(Arrays.equals(salida, salidaDe(EntrenamientoSimulado.deserializar(EntrenamientoSimulado.serializar(
                EntrenamientoSimulado.nuevaRed(RedMovimiento.ENTRADAS, 4, RedMovimiento.SALIDAS))), entradas))); // Los pesos cambiaron...
        assertArrayEquals(salida, salidaDe(EntrenamientoSimulado.deserializar(EntrenamientoSimulado.serializar(movimiento)), entradas), 0); // ...y la copia los tiene.
        assertEquals(new RedMovimiento(movimiento).decidir(entradas), entrenadas.movimiento().decidir(entradas));
    }

    private static double[] salidaDe(org.neuroph.core.NeuralNetwork<?> red, double[] entradas) {
        red.setInput(entradas);
        red.calculate();
        return red.getOutput().clone();
    }

    @Test(timeout = 20000) public void repartirDevuelveLosResultadosEnElOrdenDelIndiceYUnContextoPorHilo() {
        // Las primeras tareas tardan más: terminan al revés, pero el resultado sale en el orden del índice.
        AtomicInteger contextos = new AtomicInteger();
        Set<String> hilosUsados = ConcurrentHashMap.newKeySet();
        List<String> resultados = EntrenamientoSimulado.repartir(8, 4, contextos::incrementAndGet, (contexto, i) -> {
            try { Thread.sleep(5L * (8 - i)); } catch (InterruptedException e) { throw new IllegalStateException(e); }
            hilosUsados.add(Thread.currentThread().getName() + "/" + contexto);
            return "tarea " + i;
        });
        List<String> esperados = new ArrayList<>();
        for (int i = 0; i < 8; i++) esperados.add("tarea " + i);
        assertEquals(esperados, resultados);
        assertEquals(4, contextos.get());            // Un contexto por hilo...
        assertTrue(hilosUsados.size() <= 4);         // ...y cada contexto lo usa un solo hilo.
        // Nunca se crean más hilos (ni contextos) que tareas; con 1 hilo corre en el hilo que llama.
        contextos.set(0);
        assertEquals(List.of(0, 10), EntrenamientoSimulado.repartir(2, 16, contextos::incrementAndGet, (contexto, i) -> 10 * i));
        assertEquals(2, contextos.get());
        String este = Thread.currentThread().getName();
        assertEquals(List.of(este, este, este), EntrenamientoSimulado.repartir(3, 1, i -> Thread.currentThread().getName()));
        assertTrue(EntrenamientoSimulado.repartir(0, 3, i -> i).isEmpty());
        // Si una tarea falla, el error llega a quien llamó.
        try {
            EntrenamientoSimulado.repartir(6, 3, i -> {
                if (i == 4) throw new IllegalArgumentException("tarea rota");
                return i;
            });
            fail("no avisó la falla");
        } catch (IllegalStateException esperado) {
            assertTrue(esperado.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test public void laCantidadDeHilosSaleDelArgumentoOpcional() {
        int porDefecto = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        assertEquals(porDefecto, EntrenamientoSimulado.hilosPorDefecto());
        assertEquals(porDefecto, EntrenamientoSimulado.hilos(new String[] {}));
        assertEquals(porDefecto, EntrenamientoSimulado.hilos(new String[] {"300", "80", "modelos", "2"}));
        assertEquals(4, EntrenamientoSimulado.hilos(new String[] {"300", "80", "modelos", "2", "--hilos=4"}));
        assertEquals(1, EntrenamientoSimulado.hilos(new String[] {"--hilos=1", "300"}));
        // --hilos no ocupa el lugar de los otros argumentos, esté donde esté.
        assertEquals(List.of("300", "80", "modelos", "2"), EntrenamientoSimulado.posicionales(new String[] {"300", "80", "--hilos=4", "modelos", "2"}));
        assertEquals(List.of(), EntrenamientoSimulado.posicionales(new String[] {"--hilos=2"}));
        for (String malo : new String[] {"--hilos=0", "--hilos=-2", "--hilos=", "--hilos=muchos"}) {
            try { EntrenamientoSimulado.hilos(new String[] {malo}); fail("aceptó " + malo); } catch (IllegalArgumentException esperado) { }
        }
        try { EntrenamientoSimulado.posicionales(new String[] {"300", "--nucleos=4"}); fail("aceptó --nucleos"); } catch (IllegalArgumentException esperado) { }
    }
}
