package orquitas.herramientas;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

/** Neuroevolución: lo que no depende de correr partidas. */
public class NeuroevolucionTest {
    @Test
    public void losRangosSoloMiranElOrden() {
        double[] r = Neuroevolucion.rangosCentrados(List.of(10.0, -500.0, 30.0, 20.0));
        assertArrayEquals(new double[] {-1 / 6.0, -0.5, 0.5, 1 / 6.0}, r, 1e-12);
        // Escalar la recompensa no cambia nada.
        assertArrayEquals(r, Neuroevolucion.rangosCentrados(List.of(100.0, -5000.0, 300.0, 200.0)), 1e-12);
    }

    @Test
    public void losPesosSeLeenYSeEscribenIgual() {
        org.neuroph.nnet.MultiLayerPerceptron red = new org.neuroph.nnet.MultiLayerPerceptron(3, 4, 2);
        double[] w = Neuroevolucion.pesos(red);
        for (int i = 0; i < w.length; i++) w[i] = i * 0.01;
        red.setWeights(w);
        assertArrayEquals(w, Neuroevolucion.pesos(red), 0);
    }
}
