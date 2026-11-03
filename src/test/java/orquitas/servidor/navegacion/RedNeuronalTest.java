package orquitas.servidor.navegacion;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Test;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.util.TransferFunctionType;

/** CU-07: carga del modelo, dimensiones, umbral de confianza y normalización (sin entrenar). */
public class RedNeuronalTest {

    /** Perceptrón 39-4 sin capa oculta: con todos los pesos iguales la salida queda fija. */
    private static Path modelo(double pesoGanadora, double pesoResto, int ganadora) throws Exception {
        MultiLayerPerceptron red = new MultiLayerPerceptron(TransferFunctionType.SIGMOID, 39, 4);
        int porSalida = red.getWeights().length / 4; // 39 entradas + bias por cada salida.
        double[] pesos = new double[porSalida * 4];
        for (int salida = 0; salida < 4; salida++)
            Arrays.fill(pesos, salida * porSalida, (salida + 1) * porSalida, salida == ganadora ? pesoGanadora : pesoResto);
        red.setWeights(pesos);
        Path ruta = Files.createTempDirectory("orquita-red-").resolve("red.nnet");
        red.save(ruta.toString());
        return ruta;
    }

    @Test public void cargaYClasificaLaSalidaGanadora() throws Exception {
        // Con cercanía 1 en todo (pegado al sensor) la salida 2 (LOBITO) suma mucho y supera el 50 %.
        RedNeuronal red = new RedNeuronal(modelo(1, -1, EntornoClasificado.LOBITO.ordinal()));
        assertEquals(EntornoClasificado.LOBITO, red.clasificarEntorno(todo(0)));
    }

    @Test public void debajoDelUmbralDeConfianzaNoReconoce() throws Exception {
        // Todas las salidas en sigmoid(negativo) < 0,5: ninguna clase convence.
        RedNeuronal red = new RedNeuronal(modelo(-0.1, -1, EntornoClasificado.LIBRE.ordinal()));
        assertNull(red.clasificarEntorno(todo(0)));
        assertEquals(0.5, RedNeuronal.UMBRAL_CONFIANZA, 0);
    }

    @Test public void rechazaModelosDeOtraDimension() throws Exception {
        Path ruta = Files.createTempDirectory("orquita-red-").resolve("viejo.nnet");
        new MultiLayerPerceptron(12, 6, 3).save(ruta.toString()); // Modelo del Hito 2.
        try { new RedNeuronal(ruta); fail("aceptó 12 entradas"); } catch (IllegalArgumentException esperado) { }
        new MultiLayerPerceptron(39, 6, 3).save(ruta.toString());
        try { new RedNeuronal(ruta); fail("aceptó 3 salidas"); } catch (IllegalArgumentException esperado) { }
    }

    @Test public void normalizarConvierteDistanciaEnCercania() {
        int[] t = todo(4000);
        t[0] = 0; t[1] = 750; t[2] = 1500; t[3] = -1; // -1 crudo: sin eco cuenta como lejos.
        double[] c = RedNeuronal.normalizar(t);
        assertEquals(1, c[0], 1e-9);
        assertEquals(0.5, c[1], 1e-9);
        assertEquals(0, c[2], 1e-9);
        assertEquals(0, c[3], 1e-9);
        assertEquals(0, c[38], 1e-9);
        try { RedNeuronal.normalizar(new int[12]); fail(); } catch (IllegalArgumentException esperado) { }
    }
}
