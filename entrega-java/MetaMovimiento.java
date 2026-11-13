package orquitas.servidor.navegacion;

/**
 * Lo que decide la red de movimiento: a qué sector ir (0 a 12, como el barrido) o retroceder, y cuántos
 * mm. El Servidor lo traduce a una orden con milisegundos:
 * <ul>
 *   <li>Sector 6 (frente): AVANZAR los mm pedidos.</li>
 *   <li>Otro sector: girar hacia él (15° por sector; 0 = 90° a la derecha, 12 = 90° a la izquierda).
 *       En el ciclo siguiente la red vuelve a mirar y, si quedó de frente, avanza.</li>
 *   <li>ATRAS: RETROCEDER los mm pedidos.</li>
 * </ul>
 * Es más fácil de aprender que dirección + ms directo, porque el sector meta coincide con el sector
 * donde la percepción ve el Lobito o la salida.
 */
public record MetaMovimiento(int clase, double mm) {
    public static final int SECTORES = PreprocesadorTelemetria.SECTORES;
    public static final int FRENTE = 6;
    /** Clase que significa retroceder. */
    public static final int ATRAS = SECTORES;
    /** Clases: 13 sectores + ATRAS. */
    public static final int CLASES = SECTORES + 1;
    /** Grados por sector. */
    public static final double GRADOS_POR_SECTOR = 15;
    /** Distancia máxima de un paso: lo que se recorre en OrdenMovimiento.MAXIMO_MS. */
    public static final double MAXIMO_MM = CalibracionRobot.mmEn(OrdenMovimiento.MAXIMO_MS);

    public MetaMovimiento {
        if (clase < 0 || clase >= CLASES) throw new IllegalArgumentException("Clase inválida: " + clase);
        mm = Math.max(0, Math.min(MAXIMO_MM, mm));
    }

    /** Convierte una orden (por ejemplo, la del maestro) en meta: los giros se redondean al sector más cercano. */
    public static MetaMovimiento desde(OrdenMovimiento orden) {
        double mm = CalibracionRobot.mmEn(orden.milisegundos());
        switch (orden.movimiento()) {
            case AVANZAR: return new MetaMovimiento(FRENTE, mm);
            case RETROCEDER: return new MetaMovimiento(ATRAS, mm);
            case GIRAR_IZQ: case GIRAR_DER: {
                double grados = CalibracionRobot.GRADOS_POR_SEGUNDO * orden.milisegundos() / 1000.0;
                int sectores = Math.max(1, (int) Math.round(grados / GRADOS_POR_SECTOR));
                int s = orden.movimiento() == Movimiento.GIRAR_IZQ ? FRENTE + sectores : FRENTE - sectores;
                return new MetaMovimiento(Math.max(0, Math.min(SECTORES - 1, s)), 0);
            }
            default: return new MetaMovimiento(FRENTE, 0);
        }
    }

    /** Orden con milisegundos para la Orquita. */
    public OrdenMovimiento comoOrden() {
        if (clase == ATRAS) return new OrdenMovimiento(Movimiento.RETROCEDER, CalibracionRobot.msParaAvanzar(mm));
        if (clase == FRENTE) return new OrdenMovimiento(Movimiento.AVANZAR, CalibracionRobot.msParaAvanzar(mm));
        double grados = Math.abs(clase - FRENTE) * GRADOS_POR_SECTOR;
        int ms = (int) Math.round(grados / CalibracionRobot.GRADOS_POR_SEGUNDO * 1000);
        return new OrdenMovimiento(clase > FRENTE ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER, ms);
    }
}
