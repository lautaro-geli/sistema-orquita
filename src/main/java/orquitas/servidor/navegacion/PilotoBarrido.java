package orquitas.servidor.navegacion;

/**
 * Modo "barrido" (--modo=barrido): recorre la bahía en zigzag, en carriles separados por el ancho de la
 * boca (CARRIL_MM), porque el sensor real ve la canica solo a ~10 cm (toma guiada del 24/11/2026): en vez
 * de buscarla de lejos, se pasa por todos lados con la boca. Usa la percepción de Neuroph (etiquetas por
 * sector) y la telemetría; el rumbo sale de las órdenes ejecutadas ({@link Seguimiento}).
 * <ul>
 *   <li>Carril: avanza de a PASO_MM manteniendo el rumbo del carril, hasta que el frente queda a PARADA_MM.
 *       Al llegar a una pared se mide su inclinación con los sectores del frente y se endereza el rumbo del
 *       carril (perpendicular a esa pared): así el error de girar a tiempo no se acumula.</li>
 *   <li>Fin de carril: gira 90° hacia el lado del cambio, avanza CARRIL_MM, gira otros 90° y vuelve en sentido
 *       contrario. Si no hay lugar para el cambio (llegó a la otra punta), el zigzag da la vuelta hacia el otro lado.</li>
 *   <li>Obstáculo adelante (la percepción marca OBSTACULO o LOBITO en el frente y no es una canica): lo rodea
 *       corriéndose un carril, sin dar la vuelta.</li>
 *   <li>Canica: si un sector es LOBITO, el 4x4 la mide a menos de CANICA_CERCA_MM y tiene forma de canica
 *       (RedPercepcion.pareceLobito con el filtro "forma"), va hacia ella y la pasa por la boca.</li>
 *   <li>Mirando la salida en BUSQUEDA, el carril termina ahí (y además está el candado).</li>
 *   <li>ESCAPE: lo resuelve {@link PilotoSeguimiento} (va hacia la salida vista o recordada).</li>
 * </ul>
 * Todas las órdenes pasan por ReflejoSeguridad, el DetectorAtasco y el candado de la salida.
 */
public final class PilotoBarrido {
    /** Separación entre carriles: el ancho de la boca. */
    public static final double CARRIL_MM = CalibracionRobot.BOCA_MM;
    /**
     * Avance máximo por barrido. Puede ser largo: una canica en el carril entra en la boca aunque el sensor
     * no la vea (la ve recién a ~10 cm); verla sirve para desviarse hacia las que quedan al costado.
     */
    static final double PASO_MM = 450;
    /** Una pared vista de frente se usa para enderezar el rumbo si está a menos de esto de perpendicular. */
    static final double ENDEREZAR_MAXIMO_GRADOS = 30;
    /** Distancia al frente a la que termina un carril. */
    static final double PARADA_MM = 120;
    /** Una canica más lejos que esto (4x4, desde el sensor) no se persigue: el sensor real no la ve bien. */
    static final double CANICA_CERCA_MM = 150;
    /** Avance de más para que la canica entre en la boca. */
    static final double PASAR_CANICA_MM = 40;
    /** Error de rumbo tolerado antes de corregir. */
    static final double TOLERANCIA_RUMBO_GRADOS = 12;
    /** Tolerancia para apuntar a una canica (visto desde el eje): a 10 cm, 16° son ~3 cm de costado. */
    static final double TOLERANCIA_CANICA_GRADOS = 16;
    /** Un sector del frente sin ningún dato se toma a esta distancia (puede ser algo pegado: -1 debajo de 40 mm). */
    static final double FRENTE_CIEGO_MM = 200;
    /** Avance mínimo que vale la pena mandar. */
    static final double AVANCE_MINIMO_MM = 30;

    public enum Fase { CARRIL, GIRO_A_CAMBIO, CAMBIO, GIRO_A_CARRIL }

    private final PilotoSeguimiento escape = new PilotoSeguimiento(false);
    private final DetectorAtasco atasco = new DetectorAtasco();
    private Fase fase = Fase.CARRIL;
    private double rumboCarril = 0;
    /** +1: los carriles se corren hacia la izquierda; -1: hacia la derecha. 0 = todavía no se eligió. */
    private int ladoCambio = 0;
    /** El cambio de carril es para rodear un obstáculo (se sigue en el mismo sentido). */
    private boolean rodeo = false;
    private double restanteCambio = 0;
    /** Barridos seguidos con el frente ciego (los 3 sectores del frente sin ningún dato). */
    private int ciegos = 0;
    /** Lo avanzado en el carril actual y carriles seguidos que terminaron casi sin avanzar. */
    private double avanceCarril = 0;
    private int carrilesCortos = 0;
    /** Carriles cortos seguidos a partir de los que se sale hacia lo más abierto. */
    static final int CARRILES_CORTOS_MAXIMO = 2;
    static final double CARRIL_CORTO_MM = 100;

    public Fase fase() { return fase; }
    public int ladoCambio() { return ladoCambio; }
    public Seguimiento seguimiento() { return escape.seguimiento(); }

    /** Avisa una orden ejecutada. */
    public void registrarOrden(OrdenMovimiento o) {
        escape.registrarOrden(o);
        if (o != null && fase == Fase.CAMBIO && o.movimiento() == Movimiento.AVANZAR)
            restanteCambio -= CalibracionRobot.mmEn(o.milisegundos());
        if (o != null && fase == Fase.CARRIL && o.movimiento() == Movimiento.AVANZAR)
            avanceCarril += CalibracionRobot.mmEn(o.milisegundos());
    }

    public OrdenMovimiento decidir(int[] telemetria, EtiquetaSector[] etiquetas, ModoOperativo modo, int capturas) {
        if (modo == ModoOperativo.LIBERADA) return new OrdenMovimiento(Movimiento.DETENER, 0);
        if (modo == ModoOperativo.ESCAPE) return escape.decidir(telemetria, etiquetas, modo, capturas).orden();
        Seguimiento seg = escape.seguimiento();
        seg.registrarCapturas(capturas);
        seg.registrarBarrido(telemetria, etiquetas);
        int[] corregida = PreprocesadorTelemetria.corregir(telemetria);
        if (ladoCambio == 0) ladoCambio = ReflejoSeguridad.distancia(corregida, 12) >= ReflejoSeguridad.distancia(corregida, 0) ? 1 : -1;
        OrdenMovimiento orden = frenteCiego(corregida) ? esperarCiego() : planear(corregida, etiquetas);
        if (!frenteCiego(corregida)) ciegos = 0;
        OrdenMovimiento segura = ReflejoSeguridad.ajustar(orden, telemetria, etiquetas, modo);
        return ReflejoSeguridad.candadoSalida(atasco.revisar(telemetria, segura), telemetria, etiquetas, modo);
    }

    /**
     * Frente ciego: puede ser algo pegado (menos de 40 mm da -1) o que el sensor perdió media vuelta. La
     * primera vez se espera otro barrido sin moverse; si sigue, se retrocede un poco para volver a ver.
     */
    private OrdenMovimiento esperarCiego() {
        ciegos++;
        if (ciegos % 2 == 1) return new OrdenMovimiento(Movimiento.DETENER, 0);
        return new OrdenMovimiento(Movimiento.RETROCEDER, CalibracionRobot.msParaAvanzar(50));
    }

    static boolean frenteCiego(int[] corregida) {
        for (int s = ReflejoSeguridad.PRIMER_FRENTE; s <= ReflejoSeguridad.ULTIMO_FRENTE; s++)
            if (distanciaConCuatro(corregida, s) != Double.MAX_VALUE) return false;
        return true;
    }

    private OrdenMovimiento planear(int[] corregida, EtiquetaSector[] etiquetas) {
        int canica = canicaCerca(corregida, etiquetas);
        if (canica >= 0) {
            // Ángulo visto desde el eje de las ruedas (donde se gira): el sensor está más adelante.
            double t = Math.toRadians((canica - MetaMovimiento.FRENTE) * MetaMovimiento.GRADOS_POR_SECTOR);
            double d = corregida[canica * PreprocesadorTelemetria.ROIS];
            double adelanteSensor = CalibracionRobot.EJE_AL_FRENTE_MM - CalibracionRobot.SENSOR_AL_FRENTE_MM;
            double angulo = Math.toDegrees(Math.atan2(d * Math.sin(t), adelanteSensor + d * Math.cos(t)));
            if (Math.abs(angulo) > TOLERANCIA_CANICA_GRADOS) return girar(angulo);
            return avanzar(d * Math.cos(t) + PASAR_CANICA_MM);
        }
        double rumbo = Math.toDegrees(escape.seguimiento().rumbo());
        double frente = frente(corregida, etiquetas);
        for (int vueltas = 0; vueltas < 4; vueltas++) {
            switch (fase) {
                case CARRIL -> {
                    double error = normalizar(rumboCarril - rumbo);
                    if (Math.abs(error) > TOLERANCIA_RUMBO_GRADOS) return girar(error);
                    double avance = Math.min(PASO_MM, frente - PARADA_MM);
                    if (avance >= AVANCE_MINIMO_MM) return avanzar(avance);
                    carrilesCortos = avanceCarril < CARRIL_CORTO_MM ? carrilesCortos + 1 : 0;
                    avanceCarril = 0;
                    if (carrilesCortos >= CARRILES_CORTOS_MAXIMO) {
                        // Encerrado (rincón u obstáculos): carril nuevo hacia lo más abierto.
                        carrilesCortos = 0;
                        double hacia = anguloMasAbierto(corregida);
                        rumboCarril = normalizar(rumbo + hacia);
                        ladoCambio = -ladoCambio;
                        return girar(hacia);
                    }
                    rodeo = obstaculoAdelante(corregida, etiquetas);
                    if (!rodeo) {
                        double inclinacion = inclinacionPared(corregida, etiquetas);
                        if (!Double.isNaN(inclinacion)) rumboCarril = normalizar(rumbo + inclinacion);
                    }
                    fase = Fase.GIRO_A_CAMBIO;
                }
                case GIRO_A_CAMBIO -> {
                    double error = normalizar(rumboCarril + 90 * ladoCambio - rumbo);
                    if (Math.abs(error) > TOLERANCIA_RUMBO_GRADOS) return girar(error);
                    fase = Fase.CAMBIO;
                    restanteCambio = CARRIL_MM;
                }
                case CAMBIO -> {
                    if (restanteCambio < AVANCE_MINIMO_MM) { fase = Fase.GIRO_A_CARRIL; continue; }
                    double avance = Math.min(restanteCambio, frente - PARADA_MM);
                    if (avance >= AVANCE_MINIMO_MM) return avanzar(avance);
                    // Llegó a la otra punta: el zigzag vuelve hacia el otro lado.
                    ladoCambio = -ladoCambio;
                    rodeo = false;
                    fase = Fase.GIRO_A_CARRIL;
                }
                case GIRO_A_CARRIL -> {
                    double destino = rodeo ? rumboCarril : rumboCarril + 180;
                    double error = normalizar(destino - rumbo);
                    if (Math.abs(error) > TOLERANCIA_RUMBO_GRADOS) return girar(error);
                    rumboCarril = normalizar(destino);
                    rodeo = false;
                    avanceCarril = 0;
                    fase = Fase.CARRIL;
                }
            }
        }
        return girar(90 * ladoCambio); // No debería pasar: algo cambió de fase sin moverse.
    }

    /**
     * Sector de una canica que vale la pena ir a buscar (o -1): LOBITO para la red, el 4x4 a menos de
     * CANICA_CERCA_MM y con forma de canica (chica y aislada); el más cercano.
     */
    static int canicaCerca(int[] corregida, EtiquetaSector[] etiquetas) {
        int mejor = -1;
        int mejorDistancia = Integer.MAX_VALUE;
        for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
            if (etiquetas[s] != EtiquetaSector.LOBITO) continue;
            int d4 = corregida[s * PreprocesadorTelemetria.ROIS];
            if (PreprocesadorTelemetria.esDesconocido(d4) || d4 > CANICA_CERCA_MM) continue;
            if (!RedPercepcion.pareceLobito(corregida, s, RedPercepcion.FiltroLobito.FORMA)) continue;
            if (d4 < mejorDistancia) { mejorDistancia = d4; mejor = s; }
        }
        return mejor;
    }

    /**
     * Lo libre adelante: el corredor del ancho del robot y los 3 sectores del frente, contando todo (también
     * lo marcado LOBITO: si no es una canica cerca, se lo trata como algo con qué chocar). Mirando la
     * salida (SALIDA en el frente), el carril termina ahí.
     */
    public static double frente(int[] corregida, EtiquetaSector[] etiquetas) {
        double libre = Math.min(ReflejoSeguridad.frenteLibre(corregida, null), ReflejoSeguridad.corredorLibre(corregida));
        for (int s = ReflejoSeguridad.PRIMER_FRENTE; s <= ReflejoSeguridad.ULTIMO_FRENTE; s++) {
            double d = distanciaConCuatro(corregida, s);
            libre = Math.min(libre, d == Double.MAX_VALUE ? FRENTE_CIEGO_MM : d - CalibracionRobot.SENSOR_AL_FRENTE_MM);
            if (etiquetas[s] == EtiquetaSector.SALIDA) libre = Math.min(libre, PARADA_MM);
        }
        return libre == Double.MAX_VALUE ? PilotoSeguimiento.SIN_DATO_MM : libre;
    }

    /** Menor distancia conocida del sector con los 3 ROI (MAX_VALUE si ninguno tiene dato ni se infiere pegado). */
    static double distanciaConCuatro(int[] corregida, int s) {
        double d = ReflejoSeguridad.distancia(corregida, s);
        int d4 = corregida[s * PreprocesadorTelemetria.ROIS];
        return PreprocesadorTelemetria.esDesconocido(d4) ? d : Math.min(d, d4);
    }

    /** Ángulo (grados, + izquierda) del sector más abierto, sin contar el frente. */
    static double anguloMasAbierto(int[] corregida) {
        int mejor = 0;
        double mejorD = -1;
        for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
            if (s >= ReflejoSeguridad.PRIMER_FRENTE && s <= ReflejoSeguridad.ULTIMO_FRENTE) continue;
            double d = distanciaConCuatro(corregida, s);
            if (d == Double.MAX_VALUE) continue;
            if (d > mejorD) { mejorD = d; mejor = s; }
        }
        return (mejor - MetaMovimiento.FRENTE) * MetaMovimiento.GRADOS_POR_SECTOR;
    }

    /**
     * Hacia dónde habría que mirar (grados, + izquierda) para tener de frente, perpendicular, la pared que
     * está adelante; NaN si no se puede medir. Usa el 4x4 (el más angosto) de los sectores de ±15° y ±30°
     * que sean PARED, y ajusta una recta.
     */
    static double inclinacionPared(int[] corregida, EtiquetaSector[] etiquetas) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0, syy = 0;
        int n = 0;
        for (int s = 4; s <= 8; s++) {
            if (etiquetas[s] != EtiquetaSector.PARED) continue;
            int d = corregida[s * PreprocesadorTelemetria.ROIS];
            if (PreprocesadorTelemetria.esDesconocido(d) || d > 600) continue;
            double t = Math.toRadians((s - MetaMovimiento.FRENTE) * MetaMovimiento.GRADOS_POR_SECTOR);
            double x = d * Math.cos(t), y = d * Math.sin(t); // x adelante, y a la izquierda.
            sx += x; sy += y; sxx += x * x; sxy += x * y; syy += y * y;
            n++;
        }
        if (n < 3) return Double.NaN;
        // Recta x = a + b·y (la pared corre "de costado"): b = dx/dy.
        double den = n * syy - sy * sy;
        if (Math.abs(den) < 1e-6) return Double.NaN;
        double b = (n * sxy - sx * sy) / den;
        // Dirección de la pared (dy=1, dx=b); su normal hacia adelante forma con el frente un ángulo de -atan(b).
        double grados = -Math.toDegrees(Math.atan(b));
        return Math.abs(grados) <= ENDEREZAR_MAXIMO_GRADOS ? grados : Double.NaN;
    }

    /** True si lo que corta el carril parece un obstáculo (no la pared): al menos 2 sectores del frente así. */
    static boolean obstaculoAdelante(int[] corregida, EtiquetaSector[] etiquetas) {
        int n = 0;
        for (int s = ReflejoSeguridad.PRIMER_FRENTE; s <= ReflejoSeguridad.ULTIMO_FRENTE; s++)
            if (etiquetas[s] == EtiquetaSector.OBSTACULO || etiquetas[s] == EtiquetaSector.LOBITO) n++;
        return n >= 2;
    }

    /** Ángulo en grados entre -180 y 180 (el rumbo de Seguimiento está en radianes). */
    private static double normalizar(double grados) {
        double g = grados % 360;
        if (g > 180) g -= 360;
        if (g < -180) g += 360;
        return g;
    }

    private static OrdenMovimiento girar(double grados) {
        int ms = (int) Math.round(Math.abs(grados) / CalibracionRobot.GRADOS_POR_SEGUNDO * 1000);
        return new OrdenMovimiento(grados > 0 ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER, ms);
    }

    private static OrdenMovimiento avanzar(double mm) {
        return new OrdenMovimiento(Movimiento.AVANZAR, CalibracionRobot.msParaAvanzar(mm));
    }
}
