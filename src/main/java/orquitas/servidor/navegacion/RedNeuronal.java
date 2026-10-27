package orquitas.servidor.navegacion;

import java.nio.file.Path;
import org.neuroph.core.NeuralNetwork;

public class RedNeuronal {
    private final String rutaModeloNeuroph;
    private final NeuralNetwork<?> red;
    public RedNeuronal(Path ruta) {
        rutaModeloNeuroph = ruta.toString();
        red = NeuralNetwork.createFromFile(rutaModeloNeuroph);
        if (red.getInputsCount()!=12 || red.getOutputsCount()!=3)
            throw new IllegalArgumentException("Modelo incompatible: se espera 12 entradas / 3 salidas");
    }
    /** Neuroph reutiliza buffers: proteger setInput/calculate/getOutput como una sola operacion. */
    public synchronized EntornoClasificado clasificarEntorno(Telemetria telemetria) {
        if (telemetria == null) return null;
        red.setInput(telemetria.normalizar()); red.calculate();
        double[] salida = red.getOutput();
        int mayor = 0; // argmax; el orden de las salidas es el del enum.
        for (int i = 0; i < salida.length; i++) {
            if (!Double.isFinite(salida[i])) return null;
            if (salida[i] > salida[mayor]) mayor = i;
        }
        return EntornoClasificado.values()[mayor];
    }
}
