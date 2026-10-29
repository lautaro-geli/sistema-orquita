package orquitas.servidor.navegacion;

import java.nio.file.Path;
import org.neuroph.core.NeuralNetwork;

/** CU-07. Modelo Neuroph que clasifica una Telemetria en LIBRE, OBJETO o LOBITO. */
public class RedNeuronal {
    /** Salida mínima de la clase ganadora para dar el entorno por reconocido (50 %). */
    public static final double UMBRAL_CONFIANZA = 0.5;
    private final String rutaModeloNeuroph;
    private final NeuralNetwork<?> red;
    public RedNeuronal(Path ruta) {
        rutaModeloNeuroph = ruta.toString();
        red = NeuralNetwork.createFromFile(rutaModeloNeuroph);
        if (red.getInputsCount()!=Telemetria.SECTORES || red.getOutputsCount()!=EntornoClasificado.values().length)
            throw new IllegalArgumentException("Modelo incompatible: se espera " + Telemetria.SECTORES
                    + " entradas / " + EntornoClasificado.values().length + " salidas");
    }
    /**
     * Devuelve la clase de mayor salida, o null (entorno no reconocido) si la telemetría es nula, alguna
     * salida no es un número finito o la salida ganadora no alcanza UMBRAL_CONFIANZA.
     * Neuroph reutiliza buffers: setInput/calculate/getOutput se protegen como una sola operación.
     */
    public synchronized EntornoClasificado clasificarEntorno(Telemetria telemetria) {
        if (telemetria == null) return null;
        red.setInput(telemetria.normalizar()); red.calculate();
        double[] salida = red.getOutput();
        int mayor = 0; // El orden de las salidas es el del enum.
        for (int i = 0; i < salida.length; i++) {
            if (!Double.isFinite(salida[i])) return null;
            if (salida[i] > salida[mayor]) mayor = i;
        }
        if (salida[mayor] < UMBRAL_CONFIANZA) return null;
        return EntornoClasificado.values()[mayor];
    }
}
