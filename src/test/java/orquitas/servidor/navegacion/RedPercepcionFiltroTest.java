package orquitas.servidor.navegacion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;
import orquitas.servidor.navegacion.RedPercepcion.FiltroLobito;

/** Filtro para LOBITO después de la red, con valores tomados de la toma guiada real del 24/11/2026. */
public class RedPercepcionFiltroTest {

    /** Telemetría corregida con todo a 600 mm y el sector s con los tres ROI dados. */
    private static int[] con(int s, int v4, int v8, int v16) {
        int[] t = new int[PreprocesadorTelemetria.VALORES];
        Arrays.fill(t, 600);
        t[s * 3] = v4; t[s * 3 + 1] = v8; t[s * 3 + 2] = v16;
        return t;
    }

    @Test
    public void conEcoSoloSacaLosSectoresCiegos() {
        assertFalse(RedPercepcion.pareceLobito(con(6, -1, -1, -1), 6, FiltroLobito.ECO));
        assertFalse(RedPercepcion.pareceLobito(con(6, -1, 239, 239), 6, FiltroLobito.ECO));
        assertTrue(RedPercepcion.pareceLobito(con(6, 450, 440, 348), 6, FiltroLobito.ECO));
        assertTrue(RedPercepcion.pareceLobito(con(6, -1, -1, -1), 6, FiltroLobito.NO));
    }

    @Test
    public void formaDejaLaCanicaCercaYSacaLaCaraDeUnObstaculo() {
        // Canica a 10 cm al frente (escena 2): chica (el 16x16 mide más) y aislada.
        assertTrue(RedPercepcion.pareceLobito(con(6, 122, 132, 148), 6, FiltroLobito.FORMA));
        // Cara de un obstáculo a 10 cm: los tres ROI casi iguales.
        assertFalse(RedPercepcion.pareceLobito(con(6, 109, 111, 105), 6, FiltroLobito.FORMA));
        // Lejos (4x4 a 30 cm o más): no.
        assertFalse(RedPercepcion.pareceLobito(con(6, 300, 320, 400), 6, FiltroLobito.FORMA));
        // Un vecino a la misma distancia: es parte de algo más ancho.
        int[] ancho = con(6, 122, 132, 148);
        ancho[5 * 3] = 140;
        assertFalse(RedPercepcion.pareceLobito(ancho, 6, FiltroLobito.FORMA));
    }

    @Test
    public void filtrarPoneEnCeroSoloLaCertezaDeLobito() {
        double[] p = new double[RedPercepcion.SALIDAS];
        int lob = EtiquetaSector.LOBITO.ordinal(), sal = EtiquetaSector.SALIDA.ordinal();
        p[6 * RedPercepcion.ETIQUETAS + lob] = 0.9;
        p[6 * RedPercepcion.ETIQUETAS + sal] = 0.3;
        RedPercepcion.filtrarLobitos(p, con(6, -1, -1, -1), FiltroLobito.ECO);
        assertEquals(0, p[6 * RedPercepcion.ETIQUETAS + lob], 0);
        assertEquals(0.3, p[6 * RedPercepcion.ETIQUETAS + sal], 0);
        assertEquals(EtiquetaSector.SALIDA, RedPercepcion.etiquetas(p)[6]);
    }
}
