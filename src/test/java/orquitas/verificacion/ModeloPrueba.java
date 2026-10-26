// Archivo de simulacion
package orquitas.verificacion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import orquitas.servidor.navegacion.RedNeuronal;
import orquitas.simulacion.EntrenadorDemostracion;

/** Modelo temporal compartido: se entrena una sola vez por JVM de pruebas. */
final class ModeloPrueba {
    private static Path ruta;

    private ModeloPrueba() { }

    static synchronized Path ruta() throws IOException {
        if (ruta == null) {
            Path nueva = Files.createTempDirectory("orquita-red-").resolve("red.nnet");
            EntrenadorDemostracion.entrenarSiFalta(nueva);
            ruta = nueva;
        }
        return ruta;
    }

    static RedNeuronal red() throws IOException {
        return new RedNeuronal(ruta());
    }
}
