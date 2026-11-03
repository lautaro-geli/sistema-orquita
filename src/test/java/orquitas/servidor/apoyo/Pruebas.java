package orquitas.servidor.apoyo;

import static org.junit.Assert.assertTrue;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** Utilidades de prueba: telemetrías armadas a mano, esperas con límite y lectura de atributos privados. */
public final class Pruebas {
    private Pruebas() { }

    /** 39 valores con todo a la distancia indicada. */
    public static int[] todo(int distancia) {
        int[] t = new int[39];
        Arrays.fill(t, distancia);
        return t;
    }

    /** Pone la distancia en los 3 ROI del sector. */
    public static int[] con(int[] t, int sector, int distancia) {
        for (int r = 0; r < 3; r++) t[sector * 3 + r] = distancia;
        return t;
    }

    /** Línea TELEMETRIA con los 39 valores. */
    public static String trama(int[] t) {
        StringBuilder s = new StringBuilder("TELEMETRIA|");
        for (int i = 0; i < t.length; i++) s.append(i == 0 ? "" : ";").append(t[i]);
        return s.toString();
    }

    /** Espera una condición hasta el límite; falla si no se cumple (sin sleep fijo para sincronizar). */
    public static void esperar(BooleanSupplier condicion, long limiteMs) throws InterruptedException {
        long fin = System.nanoTime() + limiteMs * 1_000_000L;
        while (!condicion.getAsBoolean() && System.nanoTime() < fin) Thread.sleep(5);
        assertTrue("No se cumplió la condición en " + limiteMs + " ms", condicion.getAsBoolean());
    }

    @SuppressWarnings("unchecked")
    public static <T> T leer(Object objeto, String atributo) {
        for (Class<?> clase = objeto.getClass(); clase != null; clase = clase.getSuperclass()) {
            try {
                Field campo = clase.getDeclaredField(atributo);
                campo.setAccessible(true);
                return (T) campo.get(objeto);
            } catch (NoSuchFieldException e) {
                // Sigue por la superclase (p. ej. subclases anónimas de prueba).
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("No existe el atributo " + atributo);
    }
}
