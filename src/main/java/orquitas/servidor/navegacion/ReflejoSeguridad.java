package orquitas.servidor.navegacion;

/**
 * Reflejos de seguridad que se aplican a la orden de la red de movimiento (en el Servidor y en la
 * simulación por igual). Solo miran la telemetría; cada choque resta 5 puntos y es más barato frenar
 * que confiar en una sola salida de la red.
 * <ul>
 *   <li>AVANZAR: si el frente ve algo más cerca que lo que la red quiere avanzar, acorta el avance para
 *       frenar a MARGEN_MM; si ya está encima, retrocede. Los sectores marcados LOBITO no cuentan (hay que
 *       tocar la canica para tragarla).</li>
 *   <li>Giros: si algo que ve el sensor quedaría dentro del robot al girar sobre el eje de las ruedas
 *       (se prueba la geometría real del giro pedido), primero se aleja:
 *       avanza un poco si el frente está libre (atrás el sensor no ve) y si no, retrocede.</li>
 *   <li>Un sector sin dato (desconocido) cuyo vecino ve algo a menos de VECINO_CERCA_MM se toma como pegado:
 *       el sensor da -1 por debajo de 40 mm.</li>
 *   <li>Candado de la salida: en BUSQUEDA (todavía sin los 3 Lobitos) no se avanza hacia un frente que la
 *       percepción marca SALIDA (salir sin completar la misión descalifica): se gira hacia el lado más
 *       abierto que no sea la salida.</li>
 * </ul>
 */
public final class ReflejoSeguridad {
    /** Distancia que se deja libre delante del robot al frenar. */
    static final double MARGEN_MM = 60;
    /** Sectores que cubren el frente del robot (75° a 105°). */
    static final int PRIMER_FRENTE = 5, ULTIMO_FRENTE = 7;
    /** Por debajo de esto (frente a obstáculo) se retrocede. */
    static final double PEGADO_MM = 40;
    static final double RETROCESO_MM = 80;
    /** Avance corto para alejarse cuando un giro no entra y el frente está libre. */
    static final double AVANCE_PARA_GIRAR_MM = 100;
    /** Un desconocido con un vecino más cerca que esto se toma como pegado. */
    static final double VECINO_CERCA_MM = 200;
    /** Margen (mm) alrededor del rectángulo del robot al comprobar si un giro entra. */
    static final double MARGEN_GIRO_MM = 15;
    /** El giro se comprueba un 10 % más largo (ruido de la velocidad de giro). */
    static final double GIRO_DE_MAS = 1.10;

    private ReflejoSeguridad() { }

    /** Como ajustar(orden, telemetria, etiquetas), más el candado de la salida si el modo es BUSQUEDA. */
    public static OrdenMovimiento ajustar(OrdenMovimiento orden, int[] telemetria, EtiquetaSector[] etiquetas,
                                         ModoOperativo modo) {
        OrdenMovimiento conCandado = candadoSalida(orden, telemetria, etiquetas, modo);
        if (conCandado != orden) return conCandado; // El candado ya pasó su orden por el reflejo.
        return ajustar(orden, telemetria, etiquetas);
    }

    /**
     * Candado de la salida (salir sin los 3 Lobitos descalifica). En BUSQUEDA no deja avanzar si el robot
     * ya está en el estrecho mirando afuera (retrocede) ni hacia un frente marcado SALIDA (gira hacia lo
     * más abierto que no sea la salida). Se aplica también después del DetectorAtasco.
     */
    public static OrdenMovimiento candadoSalida(OrdenMovimiento orden, int[] telemetria, EtiquetaSector[] etiquetas,
                                                ModoOperativo modo) {
        if (modo != ModoOperativo.BUSQUEDA || orden.movimiento() != Movimiento.AVANZAR) return orden;
        int[] corregida = PreprocesadorTelemetria.corregir(telemetria);
        if (enElEstrecho(etiquetas, corregida)) return retroceder();
        if (!saleDeFrente(etiquetas, corregida)) return orden;
        // Giro de a lo sumo GIRO_CANDADO_GRADOS: con 90° o marcha atrás chocaba más (atrás no ve).
        double grados = anguloLejosDeLaSalida(corregida, etiquetas);
        grados = Math.signum(grados) * Math.min(GIRO_CANDADO_GRADOS, Math.abs(grados));
        OrdenMovimiento giro = new OrdenMovimiento(grados > 0 ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER,
                (int) Math.round(Math.abs(grados) / CalibracionRobot.GRADOS_POR_SEGUNDO * 1000));
        // El giro también pasa por el reflejo; si no entra, retrocede (avanzar acercaría a la salida).
        OrdenMovimiento seguro = ajustar(giro, telemetria, etiquetas);
        return seguro.movimiento() == Movimiento.AVANZAR ? retroceder() : seguro;
    }

    /** Avance mínimo que vale la pena mandar después de un giro. */
    static final double AVANCE_TRAS_GIRO_MINIMO_MM = 40;

    /**
     * "Girar y avanzar": el avance que sigue al giro hacia el sector de la meta, sin barrido nuevo. Se
     * acorta a lo que el barrido vio en ese sector y sus vecinos (menos MARGEN_MM) y a menos cuanto más
     * grande el giro (el giro real puede quedar corto o largo). En BUSQUEDA no avanza hacia un sector
     * SALIDA. Null si no hay giro, si no hay mm o si no queda lugar.
     */
    public static OrdenMovimiento avanceTrasGiro(MetaMovimiento meta, int[] telemetria, EtiquetaSector[] etiquetas,
                                                 ModoOperativo modo) {
        int s = meta.clase();
        if (s == MetaMovimiento.FRENTE || s == MetaMovimiento.ATRAS || meta.mm() < AVANCE_TRAS_GIRO_MINIMO_MM) return null;
        if (modo == ModoOperativo.BUSQUEDA && etiquetas != null && etiquetas[s] == EtiquetaSector.SALIDA) return null;
        int[] corregida = PreprocesadorTelemetria.corregir(telemetria);
        double libre = Double.MAX_VALUE;
        for (int v = Math.max(0, s - 1); v <= Math.min(PreprocesadorTelemetria.SECTORES - 1, s + 1); v++) {
            if (etiquetas != null && etiquetas[v] == EtiquetaSector.LOBITO && v == s) continue;
            libre = Math.min(libre, distancia(corregida, v));
        }
        double grados = Math.abs(s - MetaMovimiento.FRENTE) * MetaMovimiento.GRADOS_POR_SECTOR;
        double mm = meta.mm() * (1 - grados / 300);
        if (libre != Double.MAX_VALUE) mm = Math.min(mm, libre - CalibracionRobot.SENSOR_AL_FRENTE_MM - MARGEN_MM);
        if (mm < AVANCE_TRAS_GIRO_MINIMO_MM) return null;
        return new OrdenMovimiento(Movimiento.AVANZAR, CalibracionRobot.msParaAvanzar(mm));
    }

    /**
     * True si el frente no ve nada (LIBRE o SALIDA) y al menos ABIERTOS_ESTRECHO sectores tampoco: adentro
     * de la bahía un abanico así siempre ve paredes; pasa cuando el robot está en el hueco mirando afuera.
     */
    static boolean enElEstrecho(EtiquetaSector[] etiquetas, int[] corregida) {
        if (etiquetas == null) return false;
        int abiertos = 0;
        for (int s = 0; s < etiquetas.length; s++) {
            boolean abierto = etiquetas[s] == EtiquetaSector.LIBRE || etiquetas[s] == EtiquetaSector.SALIDA
                    || (etiquetas[s] == EtiquetaSector.LOBITO && !lobitoCerca(corregida, s));
            if (s >= PRIMER_FRENTE && s <= ULTIMO_FRENTE && !abierto) return false;
            if (abierto) abiertos++;
        }
        return abiertos >= ABIERTOS_ESTRECHO;
    }

    /** Sectores sin nada (LIBRE o SALIDA) a partir de los cuales se considera que está en el estrecho. */
    static final int ABIERTOS_ESTRECHO = 7;

    /**
     * Un LOBITO que el sensor no ve cerca (sin dato o a más de LOBITO_CERCA_MM) se toma como falso: mirando
     * por el estrecho la red a veces ve Lobitos donde no hay nada.
     */
    static boolean lobitoCerca(int[] corregida, int s) {
        return distancia(corregida, s) < LOBITO_CERCA_MM;
    }

    static final double LOBITO_CERCA_MM = 600;
    /** Giro máximo del candado de la salida. */
    static final double GIRO_CANDADO_GRADOS = 45;

    /** True si algún sector del frente es SALIDA y ninguno es un LOBITO cercano. */
    static boolean saleDeFrente(EtiquetaSector[] etiquetas, int[] corregida) {
        if (etiquetas == null) return false;
        boolean salida = false;
        for (int s = PRIMER_FRENTE; s <= ULTIMO_FRENTE; s++) {
            if (etiquetas[s] == EtiquetaSector.LOBITO && lobitoCerca(corregida, s)) return false;
            if (etiquetas[s] == EtiquetaSector.SALIDA) salida = true;
        }
        return salida;
    }

    /** Grados (positivo = izquierda) hacia el sector más lejano que no sea frente ni SALIDA; si no hay, 90°. */
    static double anguloLejosDeLaSalida(int[] corregida, EtiquetaSector[] etiquetas) {
        int mejor = -1;
        double mejorDistancia = 0;
        for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
            if (s >= PRIMER_FRENTE - 1 && s <= ULTIMO_FRENTE + 1) continue;
            if (etiquetas[s] == EtiquetaSector.SALIDA) continue;
            double d = distancia(corregida, s);
            if (d == Double.MAX_VALUE) d = 1300;
            if (d > mejorDistancia) { mejor = s; mejorDistancia = d; }
        }
        return mejor < 0 ? 90 : (mejor - 6) * 15.0;
    }

    public static OrdenMovimiento ajustar(OrdenMovimiento orden, int[] telemetria, EtiquetaSector[] etiquetas) {
        int[] corregida = PreprocesadorTelemetria.corregir(telemetria);
        Movimiento m = orden.movimiento();
        if (m == Movimiento.GIRAR_IZQ || m == Movimiento.GIRAR_DER) {
            double grados = orden.milisegundos() * CalibracionRobot.GRADOS_POR_SEGUNDO / 1000.0 * GIRO_DE_MAS;
            double signo = m == Movimiento.GIRAR_IZQ ? 1 : -1;
            for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
                if (etiquetas != null && etiquetas[s] == EtiquetaSector.LOBITO) continue;
                if (elGiroTocaria(distancia(corregida, s), s, signo * grados)) {
                    // No entra el giro: si adelante hay lugar, avanza un poco (lo ve); atrás no ve nada.
                    // Acá cuenta todo lo que está en el camino del ancho del robot, también una canica:
                    // detrás de ella puede haber una pared que el sensor no separa.
                    if (corredorLibre(corregida) - MARGEN_MM > AVANCE_PARA_GIRAR_MM)
                        return new OrdenMovimiento(Movimiento.AVANZAR, CalibracionRobot.msParaAvanzar(AVANCE_PARA_GIRAR_MM));
                    return retroceder();
                }
            }
            return orden;
        }
        if (m != Movimiento.AVANZAR) return orden;
        double libre = frenteLibre(corregida, etiquetas);
        if (libre == Double.MAX_VALUE) return orden;
        if (libre < PEGADO_MM) return retroceder();
        double permitido = libre - MARGEN_MM;
        if (CalibracionRobot.mmEn(orden.milisegundos()) <= permitido) return orden;
        if (permitido < CalibracionRobot.mmEn(OrdenMovimiento.MINIMO_MS)) return retroceder();
        return new OrdenMovimiento(Movimiento.AVANZAR, CalibracionRobot.msParaAvanzar(permitido));
    }

    /** Distancia libre delante del frente del robot (sectores 5 a 7, sin los LOBITO); MAX_VALUE si no se sabe. */
    static double frenteLibre(int[] corregida, EtiquetaSector[] etiquetas) {
        double libre = Double.MAX_VALUE;
        for (int s = PRIMER_FRENTE; s <= ULTIMO_FRENTE; s++) {
            if (etiquetas != null && etiquetas[s] == EtiquetaSector.LOBITO) continue;
            libre = Math.min(libre, distancia(corregida, s) - CalibracionRobot.SENSOR_AL_FRENTE_MM);
        }
        return libre;
    }

    /**
     * True si al girar en el lugar (sobre el eje de las ruedas) el rectángulo del robot tocaría lo que el
     * sector s ve a d mm del sensor. Se prueba cada 2° hasta el ángulo pedido (positivo = izquierda).
     * Detrás del robot el sensor no ve: eso no se puede comprobar.
     */
    static boolean elGiroTocaria(double d, int s, double grados) {
        if (d == Double.MAX_VALUE) return false;
        double theta = Math.toRadians((s - 6) * 15.0);
        double eje = CalibracionRobot.LARGO_MM - CalibracionRobot.EJE_AL_FRENTE_MM;
        double sensor = CalibracionRobot.EJE_AL_FRENTE_MM - CalibracionRobot.SENSOR_AL_FRENTE_MM;
        double px = sensor + d * Math.cos(theta), py = d * Math.sin(theta);   // Desde el eje: x adelante, y izquierda.
        int pasos = Math.max(1, (int) Math.ceil(Math.abs(grados) / 2));
        for (int k = 0; k <= pasos; k++) {
            double fi = Math.toRadians(grados * k / pasos);
            double x = px * Math.cos(fi) + py * Math.sin(fi), y = -px * Math.sin(fi) + py * Math.cos(fi);
            if (x <= CalibracionRobot.EJE_AL_FRENTE_MM + MARGEN_GIRO_MM && x >= -eje - MARGEN_GIRO_MM
                    && Math.abs(y) <= CalibracionRobot.ANCHO_MM / 2 + MARGEN_GIRO_MM) return true;
        }
        return false;
    }

    /**
     * Distancia libre delante del frente en el corredor del ancho del robot (más 20 mm por lado), con
     * todos los sectores que caen en ese corredor y sin mirar las etiquetas; MAX_VALUE si no se sabe.
     */
    static double corredorLibre(int[] corregida) {
        double libre = Double.MAX_VALUE;
        for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
            double d = distancia(corregida, s);
            if (d == Double.MAX_VALUE) continue;
            double theta = Math.toRadians((s - 6) * 15.0);
            double lateral = d * Math.sin(theta), adelante = d * Math.cos(theta) - CalibracionRobot.SENSOR_AL_FRENTE_MM;
            if (Math.abs(lateral) <= CalibracionRobot.ANCHO_MM / 2 + 20 && adelante > -CalibracionRobot.SENSOR_AL_FRENTE_MM)
                libre = Math.min(libre, adelante);
        }
        return libre;
    }

    private static OrdenMovimiento retroceder() {
        return new OrdenMovimiento(Movimiento.RETROCEDER, CalibracionRobot.msParaAvanzar(RETROCESO_MM));
    }

    /**
     * Menor distancia conocida del sector (8x8 o 16x16). Si el sector no tiene dato y un vecino ve algo
     * a menos de VECINO_CERCA_MM, se toma como pegado (0). Si no se sabe nada, MAX_VALUE.
     */
    static double distancia(int[] corregida, int s) {
        double d = Double.MAX_VALUE;
        for (int r = PreprocesadorTelemetria.ROI_8X8; r <= PreprocesadorTelemetria.ROI_16X16; r++) {
            int v = corregida[s * PreprocesadorTelemetria.ROIS + r];
            if (!PreprocesadorTelemetria.esDesconocido(v)) d = Math.min(d, v);
        }
        if (d != Double.MAX_VALUE) return d;
        for (int vecino : new int[] {s - 1, s + 1}) {
            if (vecino < 0 || vecino >= PreprocesadorTelemetria.SECTORES) continue;
            int v = corregida[vecino * PreprocesadorTelemetria.ROIS + PreprocesadorTelemetria.ROI_8X8];
            if (!PreprocesadorTelemetria.esDesconocido(v) && v < VECINO_CERCA_MM) return 0;
        }
        return Double.MAX_VALUE;
    }
}
