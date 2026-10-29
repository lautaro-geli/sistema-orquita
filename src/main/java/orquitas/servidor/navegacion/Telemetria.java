package orquitas.servidor.navegacion;

/** 12 sectores de 15 grados: derecha 0..5, izquierda 6..11, frente 4..7. */
public final class Telemetria {
    public static final int SECTORES = 12;
    private final double[] distancias;
    public Telemetria(double... distancias) {
        if (distancias == null || distancias.length != SECTORES) throw new IllegalArgumentException("Se requieren " + SECTORES + " distancias");
        this.distancias = distancias.clone();
        for (double d : this.distancias) if (!Double.isFinite(d) || d < 0 || d > 4000)
            throw new IllegalArgumentException("Distancia fuera de 0..4000 mm");
    }
    public double[] getDistancias() { return distancias.clone(); }
    /**
     * Alcance de la "cercania" que ve la red: a esta distancia o mas, un sector cuenta como vacio (0).
     * Si cambia el alcance util del sensor hay que reentrenar la red: el modelo aprendio con este valor.
     */
    public static final double ALCANCE_CERCANIA_MM = 1500;
    /**
     * Entrada de la red: cercania de cada sector, clamp((ALCANCE - d) / ALCANCE, 0, 1). Lejos vale 0 y
     * pegado al sensor vale 1, asi la red ve el ancho de la banda cercana (1 sector = lobito, varios =
     * objeto) sin que la variacion de los sectores lejanos la oculte. Solo la usa la red: la navegacion
     * decide con las distancias crudas en mm.
     */
    public double[] normalizar() {
        double[] datos = new double[SECTORES];
        for (int i=0; i<SECTORES; i++)
            datos[i] = Math.min(1, Math.max(0, (ALCANCE_CERCANIA_MM - distancias[i]) / ALCANCE_CERCANIA_MM));
        return datos;
    }
    public double sumarDerecha() { double s=0; for(int i=0;i<6;i++) s+=distancias[i]; return s; }
    public double sumarIzquierda() { double s=0; for(int i=6;i<12;i++) s+=distancias[i]; return s; }
    /** Indice (0..11) de la menor distancia en mm; ante empate, el menor indice. */
    public int sectorMasCercano() {
        int cercano = 0;
        for (int i = 1; i < SECTORES; i++) if (distancias[i] < distancias[cercano]) cercano = i;
        return cercano;
    }
}
