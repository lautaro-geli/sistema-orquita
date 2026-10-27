package orquitas.servidor.navegacion;

/** 12 sectores de 15 grados: derecha 0..5, izquierda 6..11, frente 4..7. */
public final class Telemetria {
    private final double[] distancias;
    public Telemetria(double... distancias) {
        if (distancias == null || distancias.length != 12) throw new IllegalArgumentException("Se requieren 12 distancias");
        this.distancias = distancias.clone();
        for (double d : this.distancias) if (!Double.isFinite(d) || d < 0 || d > 4000)
            throw new IllegalArgumentException("Distancia fuera de 0..4000 mm");
    }
    public double[] getDistancias() { return distancias.clone(); }
    public double[] normalizar() {
        double[] datos = new double[12];
        for (int i=0; i<12; i++) datos[i] = distancias[i] / 4000.0;
        return datos;
    }
    public double sumarDerecha() { double s=0; for(int i=0;i<6;i++) s+=distancias[i]; return s; }
    public double sumarIzquierda() { double s=0; for(int i=6;i<12;i++) s+=distancias[i]; return s; }
}
