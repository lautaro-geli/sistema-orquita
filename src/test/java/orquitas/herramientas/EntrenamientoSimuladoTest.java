// Archivo de simulacion
package orquitas.herramientas;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import orquitas.herramientas.EntrenamientoSimulado.Ejemplo;
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

/** Entrenamiento en la bahía simulada: los datos se arman igual que en el Servidor (sin entrenar redes). */
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
        List<List<Ejemplo>> episodios = EntrenamientoSimulado.recolectarConMaestro(2, CAL);
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
        RedPercepcion rp = new RedPercepcion(EntrenamientoSimulado.nuevaRed(RedPercepcion.ENTRADAS, 4, RedPercepcion.SALIDAS_RED));
        RedMovimiento rm = new RedMovimiento(EntrenamientoSimulado.nuevaRed(RedMovimiento.ENTRADAS, 4, RedMovimiento.SALIDAS));
        List<List<Ejemplo>> episodios = EntrenamientoSimulado.recolectarConRedes(1, CAL, rp, rm, 777L);
        assertEquals(1, episodios.size());
        int delMaestro = 0, deLasRedes = 0;
        for (Ejemplo e : episodios.get(0)) {
            if (e.ejecutada().equals(e.orden())) delMaestro++;
            else deLasRedes++;
        }
        assertTrue("maestro " + delMaestro + ", redes " + deLasRedes, delMaestro > 0 && deLasRedes > 0);
    }
}
