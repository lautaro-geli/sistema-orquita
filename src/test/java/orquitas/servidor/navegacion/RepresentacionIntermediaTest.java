package orquitas.servidor.navegacion;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.*;
import static orquitas.servidor.navegacion.EtiquetaSector.*;
import static orquitas.servidor.navegacion.RepresentacionIntermedia.*;

import java.util.Arrays;
import org.junit.Test;

/** Representación intermedia: los 14 números que recibe la red de movimiento, con percepciones armadas a mano. */
public class RepresentacionIntermediaTest {
    /** Ejemplo real del simulador: robot recién salido con un Lobito de frente. */
    private static final int[] TELEMETRIA_REFERENCIA = {
        -1, 685, 650, 975, 929, -1, 561, 529, 527, 421, 406, 465, 972, 880, 718, 979, 1031, 1023, 571, 662, 713,
        665, 672, 590, 412, 388, 383, 280, 299, 281, -1, 234, 238, 201, 207, 191, 190, 192, 177};

    /** Percepción falsa todo o nada: probabilidad 1 en la etiqueta de cada sector y 0 en el resto. */
    private static double[] percepcion(EtiquetaSector... etiquetas) {
        assertEquals(13, etiquetas.length);
        return RedPercepcion.salidaDeseada(etiquetas);
    }

    private static EtiquetaSector[] todos(EtiquetaSector etiqueta) {
        EtiquetaSector[] e = new EtiquetaSector[13];
        Arrays.fill(e, etiqueta);
        return e;
    }

    /** Cambia la probabilidad de una etiqueta en un sector. */
    private static void poner(double[] p, int sector, EtiquetaSector etiqueta, double valor) {
        p[sector * RedPercepcion.ETIQUETAS + etiqueta.ordinal()] = valor;
    }

    private static double cerc(int mm) {
        return (1300 - mm) / 1300.0;
    }

    @Test public void referenciaDelEncargoDaLosValoresExactos() {
        double[] p = percepcion(OBSTACULO, PARED, OBSTACULO, OBSTACULO, PARED, SALIDA, LOBITO, PARED, PARED, PARED, PARED, PARED, PARED);
        double[] r = RepresentacionIntermedia.calcular(TELEMETRIA_REFERENCIA, p, ModoOperativo.BUSQUEDA);
        double[] esperado = {1, 0.5, 0.560769, 1, 0.076923, 0.546154, 0.82, 0.687692, 0.863846, 0.5, 0.411923, 1, 0, 0};
        assertEquals(RepresentacionIntermedia.ENTRADAS, r.length);
        assertArrayEquals(Arrays.toString(r), esperado, r, 1e-4);
        // De dónde sale cada valor (ver el encargo).
        assertEquals(cerc(571), r[DISTANCIA_META], 1e-12);            // 4x4 del sector 6.
        assertEquals(cerc(590), r[OBSTACULO_FRENTE], 1e-12);          // Sector 7; el 6 se excluye por ser la meta.
        assertEquals(cerc(234), r[OBSTACULO_DIAGONAL_IZQ], 1e-12);
        assertEquals(cerc(406), r[OBSTACULO_DIAGONAL_DER], 1e-12);
        assertEquals(cerc(177), r[OBSTACULO_COSTADO_IZQ], 1e-12);
        assertEquals(cerc(650), r[OBSTACULO_COSTADO_DER], 1e-12);
        assertEquals(((1 - cerc(177)) - (1 - cerc(406)) + 1) / 2, r[LADO_MAS_ABIERTO], 1e-12);
    }

    @Test public void todasLasEntradasQuedanEntreCeroYUno() {
        double[] p = percepcion(OBSTACULO, PARED, OBSTACULO, OBSTACULO, PARED, SALIDA, LOBITO, PARED, PARED, PARED, PARED, PARED, PARED);
        for (ModoOperativo modo : ModoOperativo.values())
            for (int[] t : new int[][] {TELEMETRIA_REFERENCIA, todo(-1), todo(0), todo(4000)})
                for (double v : RepresentacionIntermedia.calcular(t, p, modo)) assertTrue(v >= 0 && v <= 1);
    }

    @Test public void sinMetaElAnguloQuedaAlMedioYLaDistanciaYElAnchoEnCero() {
        double[] r = RepresentacionIntermedia.calcular(todo(500), percepcion(todos(PARED)), ModoOperativo.BUSQUEDA);
        assertEquals(0, r[HAY_META], 0);
        assertEquals(0.5, r[ANGULO_META], 0);
        assertEquals(0, r[DISTANCIA_META], 0);
        assertEquals(0, r[CONFIANZA_META], 0);
        assertEquals(0, r[ANCHO_META], 0);
        // Todo a 500 mm: las cinco zonas valen lo mismo y los dos lados están igual de abiertos.
        for (int i = OBSTACULO_FRENTE; i <= OBSTACULO_COSTADO_DER; i++) assertEquals(cerc(500), r[i], 1e-12);
        assertEquals(0.5, r[LADO_MAS_ABIERTO], 1e-12);
    }

    @Test public void laConfianzaSeInformaAunqueNoAlcanceParaHaberMeta() {
        double[] p = percepcion(todos(PARED));
        poner(p, 4, LOBITO, 0.4);
        double[] r = RepresentacionIntermedia.calcular(todo(500), p, ModoOperativo.BUSQUEDA);
        assertEquals(0, r[HAY_META], 0);
        assertEquals(0.4, r[CONFIANZA_META], 1e-12);
        assertEquals(0.5, r[ANGULO_META], 0);
        assertEquals(0, r[ANCHO_META], 0);
    }

    @Test public void conDosLobitosTomaElMasProbableYNoPromediaLosAngulos() {
        double[] p = percepcion(todos(PARED));
        poner(p, 2, LOBITO, 0.9);
        poner(p, 2, PARED, 0.1);
        poner(p, 10, LOBITO, 0.6);
        poner(p, 10, PARED, 0.4);
        int[] t = con(con(todo(900), 2, 300), 10, 150);
        double[] r = RepresentacionIntermedia.calcular(t, p, ModoOperativo.BUSQUEDA);
        assertEquals(1, r[HAY_META], 0);
        assertEquals(0.9, r[CONFIANZA_META], 1e-12);
        assertEquals((-60 + 90) / 180.0, r[ANGULO_META], 1e-12); // Sector 2 = 60° a la derecha; el 10 no entra.
        assertEquals(cerc(300), r[DISTANCIA_META], 1e-12);       // La del sector 2, no la del 10 (más cerca).
        assertEquals(1 / 13.0, r[ANCHO_META], 1e-12);
        // Los dos Lobitos son meta: no cuentan como obstáculo en sus zonas.
        assertEquals(cerc(900), r[OBSTACULO_DIAGONAL_DER], 1e-12);
        assertEquals(cerc(900), r[OBSTACULO_DIAGONAL_IZQ], 1e-12);
    }

    @Test public void elAnguloSeAfinaConLosVecinosDelSectorMeta() {
        double[] p = percepcion(todos(PARED));
        poner(p, 8, LOBITO, 0.8); // +30°
        poner(p, 9, LOBITO, 0.4); // +45°: no llega al umbral, pero pondera el ángulo.
        double[] r = RepresentacionIntermedia.calcular(todo(600), p, ModoOperativo.BUSQUEDA);
        double grados = (0.8 * 30 + 0.4 * 45) / 1.2;
        assertEquals((grados + 90) / 180, r[ANGULO_META], 1e-12);
        assertEquals(grados, RepresentacionIntermedia.anguloMetaGrados(r), 1e-9);
        assertEquals(1 / 13.0, r[ANCHO_META], 1e-12);
        // En el borde del abanico solo se promedia con el vecino que existe.
        double[] borde = percepcion(todos(PARED));
        poner(borde, 12, LOBITO, 0.9);
        poner(borde, 11, LOBITO, 0.3);
        double a = (0.9 * 90 + 0.3 * 75) / 1.2;
        assertEquals((a + 90) / 180, RepresentacionIntermedia.calcular(todo(600), borde, ModoOperativo.BUSQUEDA)[ANGULO_META], 1e-12);
    }

    @Test public void conProbabilidadesIgualesGanaElLobitoMasCercano() {
        double[] p = percepcion(todos(PARED));
        poner(p, 3, LOBITO, 0.8);
        poner(p, 9, LOBITO, 0.8);
        int[] t = con(con(todo(900), 3, 400), 9, 200);
        double[] r = RepresentacionIntermedia.calcular(t, p, ModoOperativo.BUSQUEDA);
        assertEquals((45 + 90) / 180.0, r[ANGULO_META], 1e-12); // Sector 9 = 45° a la izquierda.
        assertEquals(cerc(200), r[DISTANCIA_META], 1e-12);
    }

    @Test public void enEscapeLaMetaEsLaSalidaYElAnchoCuentaLosSectoresSeguidos() {
        EtiquetaSector[] e = todos(PARED);
        e[5] = SALIDA; e[6] = SALIDA; e[7] = SALIDA;
        e[1] = LOBITO; // En escape el Lobito no es meta.
        int[] t = todo(700);
        for (int s = 5; s <= 7; s++) con(t, s, -1); // Por el hueco no hay eco.
        con(t, 1, 250);
        double[] r = RepresentacionIntermedia.calcular(t, percepcion(e), ModoOperativo.ESCAPE);
        assertEquals(1, r[HAY_META], 0);
        assertEquals(0.5, r[ANGULO_META], 1e-12);       // Centrada: gana el sector del medio (6).
        assertEquals(3 / 13.0, r[ANCHO_META], 1e-12);
        assertEquals(0, r[DISTANCIA_META], 0);          // Sin eco: distancia desconocida.
        assertEquals(0, r[OBSTACULO_FRENTE], 0);        // Los tres sectores del frente son la meta.
        assertEquals(cerc(250), r[OBSTACULO_COSTADO_DER], 1e-12); // El Lobito cuenta como obstáculo.
        assertEquals(0, r[MODO_BUSQUEDA], 0);
        assertEquals(1, r[MODO_ESCAPE], 0);
        assertEquals(3 / 13.0, r[FRACCION_DESCONOCIDOS], 1e-12);
        // La misma percepción en BUSQUEDA: la meta es el Lobito del sector 1 y la salida cuenta como obstáculo.
        double[] b = RepresentacionIntermedia.calcular(t, percepcion(e), ModoOperativo.BUSQUEDA);
        assertEquals((-75 + 90) / 180.0, b[ANGULO_META], 1e-12);
        assertEquals(1 / 13.0, b[ANCHO_META], 1e-12);
        assertEquals(1, b[MODO_BUSQUEDA], 0);
        assertEquals(0, b[MODO_ESCAPE], 0);
    }

    @Test public void elSectorConLosTresRoiSinEcoCuentaComoDesconocido() {
        int[] t = todo(800);
        con(t, 4, -1);                 // Los tres sin eco: desconocido.
        t[9 * 3] = -1;                 // Solo el 4x4 sin eco: toma el 8x8, no es desconocido.
        t[10 * 3 + 1] = -1;            // Solo el 8x8 sin eco: los otros conservan su valor.
        double[] r = RepresentacionIntermedia.calcular(t, percepcion(todos(PARED)), ModoOperativo.BUSQUEDA);
        assertEquals(1 / 13.0, r[FRACCION_DESCONOCIDOS], 1e-12);
        assertEquals(1, RepresentacionIntermedia.calcular(todo(-1), percepcion(todos(LIBRE)), ModoOperativo.BUSQUEDA)[FRACCION_DESCONOCIDOS], 0);
        assertEquals(0, RepresentacionIntermedia.calcular(todo(800), percepcion(todos(PARED)), ModoOperativo.BUSQUEDA)[FRACCION_DESCONOCIDOS], 0);
    }

    @Test public void enLiberadaLaMetaEstaAusente() {
        EtiquetaSector[] e = todos(PARED);
        e[6] = LOBITO;
        e[8] = SALIDA;
        double[] r = RepresentacionIntermedia.calcular(todo(400), percepcion(e), ModoOperativo.LIBERADA);
        assertEquals(0, r[HAY_META], 0);
        assertEquals(0.5, r[ANGULO_META], 0);
        assertEquals(0, r[DISTANCIA_META], 0);
        assertEquals(0, r[CONFIANZA_META], 0);
        assertEquals(0, r[ANCHO_META], 0);
        assertEquals(0, r[MODO_BUSQUEDA], 0);
        assertEquals(0, r[MODO_ESCAPE], 0);
        assertEquals(cerc(400), r[OBSTACULO_FRENTE], 1e-12); // Sin meta, ningún sector se excluye.
    }

    @Test public void ladoMasAbiertoApuntaAlLadoConMenosObstaculos() {
        int[] t = todo(1000);
        for (int s = 0; s <= 4; s++) con(t, s, 150); // Derecha cerrada.
        double[] r = RepresentacionIntermedia.calcular(t, percepcion(todos(PARED)), ModoOperativo.BUSQUEDA);
        assertTrue(r[LADO_MAS_ABIERTO] > 0.5); // Más abierto a la izquierda.
        assertEquals(((1 - cerc(1000)) - (1 - cerc(150)) + 1) / 2, r[LADO_MAS_ABIERTO], 1e-12);
    }

    @Test public void describirDaUnTextoLegibleParaLaConsola() {
        double[] p = percepcion(OBSTACULO, PARED, OBSTACULO, OBSTACULO, PARED, SALIDA, LOBITO, PARED, PARED, PARED, PARED, PARED, PARED);
        double[] r = RepresentacionIntermedia.calcular(TELEMETRIA_REFERENCIA, p, ModoOperativo.BUSQUEDA);
        assertEquals("Lobito a +0°, 57 cm (1,00); frente libre; izq. cerrado", RepresentacionIntermedia.describir(r, ModoOperativo.BUSQUEDA));
        double[] sinMeta = RepresentacionIntermedia.calcular(con(todo(900), 6, 180), percepcion(todos(PARED)), ModoOperativo.ESCAPE);
        assertEquals("sin Salida (0,00); frente a 18 cm; lados parejos", RepresentacionIntermedia.describir(sinMeta, ModoOperativo.ESCAPE));
        double[] izquierda = percepcion(todos(PARED));
        poner(izquierda, 8, LOBITO, 0.99);
        poner(izquierda, 8, PARED, 0.01);
        int[] t = con(todo(1000), 8, 250);
        con(t, 4, -1);
        assertEquals("Lobito a +30°, 25 cm (0,99); frente libre; lados parejos; 1 sector sin dato",
                RepresentacionIntermedia.describir(RepresentacionIntermedia.calcular(t, izquierda, ModoOperativo.BUSQUEDA), ModoOperativo.BUSQUEDA));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rechazaUnaPercepcionDeOtroLargo() {
        RepresentacionIntermedia.calcular(todo(500), new double[13], ModoOperativo.BUSQUEDA);
    }
}
