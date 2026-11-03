package orquitas.herramientas;

import static org.junit.Assert.*;
import static orquitas.servidor.apoyo.Pruebas.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import orquitas.servidor.apoyo.SalidaRegistrada;
import orquitas.servidor.navegacion.RedNeuronal;
import orquitas.simulacion.EntrenamientoDemostracion;

/** Herramientas de datos: recolección de barridos y entrenamiento con el CSV resultante. */
public class HerramientasTest {

    @Test(timeout = 10000) public void recolectorGuardaTelemetriasConElMenosUnoCrudoYRespondeDetener() throws Exception {
        Path csv = Files.createTempDirectory("orquita-datos-").resolve("datos/barridos.csv");
        int[] t = todo(2000);
        t[0] = -1;
        String robot = trama(t) + "\r\nCAPTURA|1\nBASURA\n" + trama(todo(1500)) + "\n";
        SalidaRegistrada salida = new SalidaRegistrada();
        RecolectorBarridos.recolectar(new ByteArrayInputStream(robot.getBytes(StandardCharsets.US_ASCII)), salida,
                "OBJETO", 2, csv, 5000);
        List<String> filas = Files.readAllLines(csv);
        assertEquals(2, filas.size());
        assertTrue(filas.get(0).startsWith("OBJETO,-1,2000,"));
        assertEquals(40, filas.get(0).split(",").length);
        assertEquals(List.of("CONFIRMACION", "ORDEN|DETENER", "CONFIRMACION", "RECHAZO", "CONFIRMACION", "ORDEN|DETENER"),
                salida.respuestas());
        assertTrue(salida.contar("LATIDO") >= 1);
        assertEquals(Map.of("OBJETO", 2), RecolectorBarridos.resumir(csv));
    }

    @Test public void soloAceptaTelemetriasValidas() {
        assertNull(RecolectorBarridos.valoresDeTelemetria("TELEMETRIA|1;2"));
        assertNull(RecolectorBarridos.valoresDeTelemetria(trama(todo(4001))));
        assertNull(RecolectorBarridos.valoresDeTelemetria("CAPTURA|1"));
        assertNotNull(RecolectorBarridos.valoresDeTelemetria(trama(todo(4000))));
    }

    @Test(timeout = 60000) public void entrenamientoRealGuardaModeloCompatibleYReporte() throws Exception {
        Path carpeta = Files.createTempDirectory("orquita-entrenamiento-");
        Path csv = carpeta.resolve("barridos.csv");
        StringBuilder filas = new StringBuilder("fila,mal,formada\n");
        for (var m : EntrenamientoDemostracion.generar(400, 7L)) {
            filas.append(m.etiqueta());
            for (int v : m.valores()) filas.append(',').append(v);
            filas.append('\n');
        }
        Files.writeString(csv, filas);
        Path modelo = carpeta.resolve("entorno.nnet"), reporte = carpeta.resolve("evidencias/entrenamiento.md");
        double exactitud = EntrenamientoReal.entrenar(csv, modelo, 16, reporte);
        assertTrue("exactitud " + exactitud, exactitud >= 80);
        new RedNeuronal(modelo); // 39 entradas y 4 salidas.
        String texto = Files.readString(reporte);
        assertTrue(texto.contains("Matriz de confusión"));
        assertTrue(texto.contains("80 % entrenamiento (320)"));
    }
}
