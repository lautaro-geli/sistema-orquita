package orquitas.servidor.apoyo;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** OutputStream de prueba que guarda las líneas enviadas por el enlace; puede simular un puerto caído. */
public class SalidaRegistrada extends OutputStream {
    private final StringBuilder actual = new StringBuilder();
    private final List<String> lineas = new ArrayList<>();
    private volatile boolean caida;

    public void caer() { caida = true; }

    @Override
    public synchronized void write(int b) throws IOException {
        if (caida) throw new IOException("puerto caído (simulado)");
        if (b == '\n') { lineas.add(actual.toString()); actual.setLength(0); }
        else actual.append((char) b);
    }

    @Override
    public void write(byte[] b, int desde, int largo) throws IOException {
        if (caida) throw new IOException("puerto caído (simulado)");
        for (int i = desde; i < desde + largo; i++) write(b[i]);
    }

    /** Copia de todas las líneas recibidas. */
    public synchronized List<String> lineas() { return new ArrayList<>(lineas); }

    /** Líneas sin los LATIDO, para comparar respuestas. */
    public synchronized List<String> respuestas() {
        List<String> r = new ArrayList<>();
        for (String l : lineas) if (!l.equals("LATIDO")) r.add(l);
        return r;
    }

    public synchronized long contar(String linea) { return lineas.stream().filter(linea::equals).count(); }
}
