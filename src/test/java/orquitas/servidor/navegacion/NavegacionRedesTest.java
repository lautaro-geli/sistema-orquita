package orquitas.servidor.navegacion;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.util.TransferFunctionType;
import orquitas.servidor.apoyo.RedFija;

/**
 * Navegación con las redes de percepción y movimiento (protocolo v3: ORDEN|movimiento|ms) y la
 * corrección del -1 en la navegación por reglas.
 */
public class NavegacionRedesTest {

    /** Percepción de prueba: todos los sectores PARED. */
    static class PercepcionFija extends RedPercepcion {
        @Override public synchronized double[] probabilidades(int[] telemetria) {
            EtiquetaSector[] e = new EtiquetaSector[13];
            java.util.Arrays.fill(e, EtiquetaSector.PARED);
            return RedPercepcion.salidaDeseada(e);
        }
    }

    /** Movimiento de prueba: devuelve siempre la misma orden y guarda el modo que recibió. */
    static class MovimientoFijo extends RedMovimiento {
        final OrdenMovimiento orden;
        ModoOperativo modoRecibido;
        MovimientoFijo(OrdenMovimiento orden) { this.orden = orden; }
        @Override public synchronized OrdenMovimiento decidir(int[] t, double[] p, ModoOperativo modo) {
            modoRecibido = modo;
            return orden;
        }
    }

    @Test public void conRedesLaOrdenLlevaLosMilisegundosDeLaRed() {
        MovimientoFijo mov = new MovimientoFijo(new OrdenMovimiento(Movimiento.GIRAR_IZQ, 640));
        Navegacion n = new Navegacion(new PercepcionFija(), mov);
        n.determinarMovimiento(todo(500));
        assertEquals(new OrdenMovimiento(Movimiento.GIRAR_IZQ, 640), n.obtenerOrdenDecidida());
        assertEquals("ORDEN|GIRAR_IZQ|640", n.obtenerOrdenDecidida().comoTrama());
        assertEquals(ModoOperativo.BUSQUEDA, mov.modoRecibido);
        assertEquals(EtiquetaSector.PARED, n.obtenerUltimasEtiquetas()[6]);
    }

    @Test public void conRedesElModoLlegaALaRedDeMovimiento() {
        MovimientoFijo mov = new MovimientoFijo(new OrdenMovimiento(Movimiento.AVANZAR, 900));
        Navegacion n = new Navegacion(new PercepcionFija(), mov);
        n.activarModoEscape();
        n.determinarMovimiento(todo(500));
        assertEquals(ModoOperativo.ESCAPE, mov.modoRecibido);
    }

    @Test public void conRedesEnEscapeYTodoSinEcoQuedaLiberada() {
        Navegacion n = new Navegacion(new PercepcionFija(), new MovimientoFijo(new OrdenMovimiento(Movimiento.AVANZAR, 900)));
        n.activarModoEscape();
        n.determinarMovimiento(todo(-1));
        assertEquals(new OrdenMovimiento(Movimiento.DETENER, 0), n.obtenerOrdenDecidida());
        assertEquals("ORDEN|DETENER|0", n.obtenerOrdenDecidida().comoTrama());
    }

    @Test public void reglasSinEcoNoEsElSectorMasCercano() {
        // Lobito en el sector 9 (izquierda); el sector 1 no tiene eco. Antes el -1 "ganaba" como más cercano.
        int[] t = con(todo(1400), 9, 300);
        t = con(t, 1, -1);
        Navegacion n = new Navegacion(new RedFija(EntornoClasificado.LOBITO));
        n.determinarMovimiento(t);
        assertEquals(Movimiento.GIRAR_IZQ, n.obtenerMovimientoDecidido());
        assertEquals(OrdenMovimiento.PASO_GIRO_MS, n.obtenerOrdenDecidida().milisegundos());
    }

    @Test public void reglasCampoLibreConSectoresSinEco() {
        int[] t = todo(2000);
        t = con(t, 3, -1);
        t = con(t, 10, -1);
        Navegacion n = new Navegacion(new RedFija(EntornoClasificado.LIBRE));
        n.activarModoEscape();
        n.determinarMovimiento(t);
        assertEquals(Movimiento.DETENER, n.obtenerMovimientoDecidido()); // Pasó a LIBERADA.
    }

    @Test public void redesRechazanModelosDeOtraDimension() throws Exception {
        Path dir = Files.createTempDirectory("orquita-redes-");
        Path p = dir.resolve("p.nnet");
        new MultiLayerPerceptron(TransferFunctionType.SIGMOID, 39, 4, 4).save(p.toString());
        try { new RedPercepcion(p); fail("aceptó 39 entradas"); } catch (IllegalArgumentException esperado) { }
        try { new RedMovimiento(p); fail("aceptó 39 entradas"); } catch (IllegalArgumentException esperado) { }
        Path ok = dir.resolve("ok.nnet");
        new MultiLayerPerceptron(TransferFunctionType.SIGMOID, RedPercepcion.ENTRADAS, 4, RedPercepcion.SALIDAS_RED).save(ok.toString());
        assertEquals(65, new RedPercepcion(ok).probabilidades(todo(-1)).length);
    }

    @Test public void reflejoAcortaElAvanceSiElFrenteEstaCerca() {
        int[] t = con(todo(1200), 6, 200); // Pared a 200 mm del sensor: 170 mm del frente.
        OrdenMovimiento o = ReflejoSeguridad.ajustar(new OrdenMovimiento(Movimiento.AVANZAR, 1500), t, null);
        assertEquals(Movimiento.AVANZAR, o.movimiento());
        assertEquals(CalibracionRobot.msParaAvanzar(170 - ReflejoSeguridad.MARGEN_MM), o.milisegundos());
    }

    @Test public void reflejoNoFrenaAnteUnLobito() {
        int[] t = con(todo(1200), 6, 90);
        EtiquetaSector[] e = new EtiquetaSector[13];
        java.util.Arrays.fill(e, EtiquetaSector.PARED);
        e[6] = EtiquetaSector.LOBITO;
        OrdenMovimiento avanzar = new OrdenMovimiento(Movimiento.AVANZAR, 600);
        assertEquals(avanzar, ReflejoSeguridad.ajustar(avanzar, t, e));
    }

    @Test public void reflejoDejaGirarConLugarYSeAlejaSiLasEsquinasTocarian() {
        OrdenMovimiento giro = new OrdenMovimiento(Movimiento.GIRAR_DER, 600);
        assertEquals(giro, ReflejoSeguridad.ajustar(giro, todo(1200), null));
        // Pared al costado y frente libre: se aleja avanzando un poco (atrás el sensor no ve).
        assertEquals(Movimiento.AVANZAR, ReflejoSeguridad.ajustar(giro, con(todo(1200), 0, 100), null).movimiento());
        assertEquals(Movimiento.RETROCEDER, ReflejoSeguridad.ajustar(giro, con(todo(1200), 6, 50), null).movimiento());
    }

    @Test public void reflejoTomaComoPegadoUnFrenteSinEcoConVecinosCerca() {
        int[] t = con(todo(1200), 6, -1); // Pegado a la pared: el sensor da -1 por debajo de 40 mm.
        t = con(t, 5, 120);
        t = con(t, 7, 120);
        OrdenMovimiento o = ReflejoSeguridad.ajustar(new OrdenMovimiento(Movimiento.AVANZAR, 600), t, null);
        assertEquals(Movimiento.RETROCEDER, o.movimiento());
    }

    @Test public void reflejoRetrocedeSiYaEstaEncima() {
        OrdenMovimiento o = ReflejoSeguridad.ajustar(new OrdenMovimiento(Movimiento.AVANZAR, 600), con(todo(1200), 5, 60), null);
        assertEquals(Movimiento.RETROCEDER, o.movimiento());
    }

    @Test public void reflejoIgnoraLosDesconocidos() {
        OrdenMovimiento avanzar = new OrdenMovimiento(Movimiento.AVANZAR, 1500);
        assertEquals(avanzar, ReflejoSeguridad.ajustar(avanzar, todo(-1), null));
    }

    @Test public void conRedesElReflejoActuaSobreLaOrdenDeLaRed() {
        Navegacion n = new Navegacion(new PercepcionFija(), new MovimientoFijo(new OrdenMovimiento(Movimiento.AVANZAR, 1500)));
        n.determinarMovimiento(con(todo(1200), 7, 200));
        assertTrue(n.obtenerOrdenDecidida().milisegundos() < 1500);
    }

    @Test public void atascoCambiaLaOrdenSiElBarridoNoCambio() {
        DetectorAtasco d = new DetectorAtasco();
        OrdenMovimiento giro = new OrdenMovimiento(Movimiento.GIRAR_DER, 500);
        assertEquals(giro, d.revisar(todo(400), giro));
        // El barrido llega igual (con ruido chico): el giro no tuvo efecto, se prueba el contrario.
        OrdenMovimiento segunda = d.revisar(con(todo(400), 3, 405), giro);
        assertEquals(Movimiento.GIRAR_IZQ, segunda.movimiento());
        assertEquals(1, d.atascosSeguidos());
        // Si sigue igual, recorre las opciones: retroceder, girar a la derecha, a la izquierda, avanzar un poco.
        java.util.List<Movimiento> vistas = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) vistas.add(d.revisar(todo(400), giro).movimiento());
        assertEquals(java.util.List.of(Movimiento.RETROCEDER, Movimiento.GIRAR_DER, Movimiento.GIRAR_IZQ, Movimiento.AVANZAR), vistas);
        // Si el barrido cambió, vuelve a la orden de la red.
        OrdenMovimiento avanzar = new OrdenMovimiento(Movimiento.AVANZAR, 600);
        assertEquals(avanzar, d.revisar(todo(900), avanzar));
        assertEquals(0, d.atascosSeguidos());
    }

    @Test public void atascoNoSeActivaDespuesDeDetenerNiConPocosDatos() {
        DetectorAtasco d = new DetectorAtasco();
        OrdenMovimiento detener = new OrdenMovimiento(Movimiento.DETENER, 0);
        d.revisar(todo(400), detener);
        OrdenMovimiento avanzar = new OrdenMovimiento(Movimiento.AVANZAR, 600);
        assertEquals(avanzar, d.revisar(todo(400), avanzar));
        DetectorAtasco sinDatos = new DetectorAtasco();
        sinDatos.revisar(todo(-1), avanzar);
        assertEquals(avanzar, sinDatos.revisar(todo(-1), avanzar));
    }

    @Test public void ordenAcotaLaDuracionYDetenerVaEnCero() {
        assertEquals(OrdenMovimiento.MAXIMO_MS, new OrdenMovimiento(Movimiento.AVANZAR, 99999).milisegundos());
        assertEquals(OrdenMovimiento.MINIMO_MS, new OrdenMovimiento(Movimiento.AVANZAR, 1).milisegundos());
        assertEquals(0, new OrdenMovimiento(Movimiento.DETENER, 700).milisegundos());
        OrdenMovimiento o = new OrdenMovimiento(Movimiento.RETROCEDER, 777);
        assertEquals(777, OrdenMovimiento.desnormalizar(o.duracionNormalizada()));
    }

    @Test public void interpretarEligeLaDireccionMayorYDesnormalizaLaDuracion() {
        double[] salida = {0.1, 0.2, 0.9, 0.3, 0.0, 0.5};
        OrdenMovimiento o = RedMovimiento.interpretar(salida);
        assertEquals(Movimiento.GIRAR_IZQ, o.movimiento());
        assertEquals(OrdenMovimiento.desnormalizar(0.5), o.milisegundos());
    }
}
