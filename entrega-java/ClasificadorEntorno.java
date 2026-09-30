package orquitas.servidor.navegacion;

import java.nio.file.Path;
import org.neuroph.core.NeuralNetwork;

public class ClasificadorEntorno {
    private final String rutaModeloNeuroph;
    private final NeuralNetwork<?> red;
    public ClasificadorEntorno(Path ruta) {
        rutaModeloNeuroph = ruta.toString();
        red = NeuralNetwork.createFromFile(rutaModeloNeuroph);
        if (red.getInputsCount()!=12 || red.getOutputsCount()!=2)
            throw new IllegalArgumentException("Modelo incompatible: se espera 12 entradas / 2 salidas");
    }
    /** Neuroph reutiliza buffers: proteger setInput/calculate/getOutput como una sola operacion. */
    public synchronized EntornoClasificado clasificarEntorno(Telemetria telemetria) {
        if (telemetria == null) return null;
        red.setInput(telemetria.normalizar()); red.calculate();
        double[] salida = red.getOutput();
        if (!Double.isFinite(salida[0]) || !Double.isFinite(salida[1])) return null;
        return salida[0] >= salida[1] ? EntornoClasificado.LIBRE : EntornoClasificado.OBJETO;
    }
}
