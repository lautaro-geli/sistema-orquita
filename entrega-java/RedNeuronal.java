package orquitas.servidor.navegacion;

import java.nio.file.Path;
import org.neuroph.core.NeuralNetwork;

/**
 * CU-07. Red neuronal (MLP de Neuroph) que clasifica el entorno a partir de las 39 distancias
 * de un barrido: 13 sectores x 3 ROI (4x4, 8x8, 16x16), en orden de sector.
 */
public class RedNeuronal {
    /** Certeza mínima de la salida ganadora; por debajo, el entorno no se reconoce (null). */
    public static final double UMBRAL_CONFIANZA = 0.5;
    /** Distancia desde la que un valor cuenta como vacío (cercanía 0). [A CONFIRMAR] con datos reales. */
    private static final double ALCANCE_CERCANIA_MM = 1500;
    private static final int ENTRADAS = 39;
    private final NeuralNetwork<?> red;

    /** Carga el modelo .nnet y verifica que tenga 39 entradas y 4 salidas (LIBRE, OBJETO, LOBITO, SALIDA). */
    public RedNeuronal(Path ruta) {
        red = NeuralNetwork.createFromFile(ruta.toString());
        int salidas = EntornoClasificado.values().length;
        if (red.getInputsCount() != ENTRADAS || red.getOutputsCount() != salidas)
            throw new IllegalArgumentException("Modelo incompatible " + ruta + ": se esperan " + ENTRADAS
                    + " entradas y " + salidas + " salidas, tiene " + red.getInputsCount() + " y " + red.getOutputsCount());
    }

    /** Solo para subclases de prueba que reemplazan clasificarEntorno: no carga ningún modelo. */
    protected RedNeuronal() {
        red = null;
    }

    /**
     * Devuelve la clase ganadora (argmax), o null si la certeza no llega a UMBRAL_CONFIANZA o alguna
     * salida no es finita (entorno no reconocido). Neuroph reutiliza sus buffers: setInput, calculate
     * y getOutput forman una sola operación.
     */
    public synchronized EntornoClasificado clasificarEntorno(int[] telemetria) {
        red.setInput(normalizar(telemetria));
        red.calculate();
        double[] salida = red.getOutput();
        int ganadora = 0;
        for (int i = 0; i < salida.length; i++) {
            if (!Double.isFinite(salida[i])) return null;
            if (salida[i] > salida[ganadora]) ganadora = i;
        }
        if (salida[ganadora] < UMBRAL_CONFIANZA) return null;
        return EntornoClasificado.values()[ganadora];
    }

    /**
     * Cercanía de cada valor: clamp((ALCANCE_CERCANIA_MM - d) / ALCANCE_CERCANIA_MM, 0, 1). Lejos vale 0
     * y pegado al sensor vale 1. Un valor negativo (sin eco, -1 crudo) vale 0. Es estática y pública para
     * que el entrenamiento normalice exactamente igual que la inferencia.
     */
    public static double[] normalizar(int[] telemetria) {
        if (telemetria == null || telemetria.length != ENTRADAS)
            throw new IllegalArgumentException("Se esperan " + ENTRADAS + " valores");
        double[] cercanias = new double[ENTRADAS];
        for (int i = 0; i < ENTRADAS; i++) {
            double d = telemetria[i];
            cercanias[i] = d < 0 ? 0 : Math.min(1, Math.max(0, (ALCANCE_CERCANIA_MM - d) / ALCANCE_CERCANIA_MM));
        }
        return cercanias;
    }
}
