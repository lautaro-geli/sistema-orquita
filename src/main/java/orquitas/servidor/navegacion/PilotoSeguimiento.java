package orquitas.servidor.navegacion;

/**
 * Piloto de reglas sobre el {@link Seguimiento}: decide el movimiento solo con lo que ve el robot (telemetría
 * y etiquetas de la red de percepción de Neuroph) y lo que recuerda. Sigue las tácticas del Maestro v2:
 * apunta al Lobito confirmado más cercano con la tolerancia que permite la boca, explora avanzando si hay
 * lugar o girando hacia lo más abierto, y en ESCAPE va hacia la salida vista o recordada. Con giroYAvance,
 * después de girar avanza en el mismo barrido. Todas las órdenes pasan por el reflejo y el candado.
 */
public final class PilotoSeguimiento {
    /** Una orden y, con "girar y avanzar", el avance que sigue al giro (o null). */
    public record Plan(OrdenMovimiento orden, OrdenMovimiento avanceDespues) { }

    static final double TOLERANCIA_GRADOS = 7;
    static final double MARGEN_BOCA_MM = 25;
    static final double PASO_EXPLORACION_MM = 450;
    static final double FRENTE_PARA_EXPLORAR_MM = 220;
    static final double SIN_DATO_MM = 1300;
    /** En ESCAPE, con el frente abierto al menos esto, se avanza derecho. */
    static final double SALIR_DERECHO_MM = 400;

    private final boolean giroYAvance;
    private final Seguimiento seguimiento = new Seguimiento();
    private final DetectorAtasco atasco = new DetectorAtasco();

    public PilotoSeguimiento(boolean giroYAvance) { this.giroYAvance = giroYAvance; }

    public Seguimiento seguimiento() { return seguimiento; }

    /** Avisa una orden ejecutada (también el avance que va después de un giro). */
    public void registrarOrden(OrdenMovimiento o) { seguimiento.registrarOrden(o); }

    public Plan decidir(int[] telemetria, EtiquetaSector[] etiquetas, ModoOperativo modo, int capturas) {
        if (modo == ModoOperativo.LIBERADA) return new Plan(new OrdenMovimiento(Movimiento.DETENER, 0), null);
        seguimiento.registrarCapturas(capturas);
        seguimiento.registrarBarrido(telemetria, etiquetas);
        etiquetas = seguimiento.etiquetasFiltradas();
        Plan p = decidirSinReflejo(telemetria, etiquetas, modo);
        OrdenMovimiento segura = ReflejoSeguridad.ajustar(p.orden(), telemetria, etiquetas, modo);
        OrdenMovimiento elegida = ReflejoSeguridad.candadoSalida(atasco.revisar(telemetria, segura), telemetria, etiquetas, modo);
        OrdenMovimiento avance = elegida.equals(p.orden()) ? p.avanceDespues() : null;
        // El reflejo solo mira los 3 sectores del frente: un obstáculo de costado, cerca, queda en el ancho
        // del robot. Se acota el avance con todo lo que cae en el corredor (salvo los Lobitos aceptados).
        if (elegida.movimiento() == Movimiento.AVANZAR) {
            double libre = corredor(PreprocesadorTelemetria.corregir(telemetria), etiquetas) - ReflejoSeguridad.MARGEN_MM;
            if (libre < CalibracionRobot.mmEn(elegida.milisegundos())) {
                if (libre >= 30) elegida = avanzar(libre);
                else { elegida = alternativa(new OrdenMovimiento(Movimiento.GIRAR_IZQ, 100)); avance = null; }
            }
        }
        // Lo que ya no se ve (la cola, los costados de atrás) se controla con los puntos recordados.
        if (seguimiento.ordenTocaria(elegida, MARGEN_MEMORIA_MM)) {
            elegida = ReflejoSeguridad.ajustar(alternativa(elegida), telemetria, etiquetas, modo);
            avance = null;
        }
        if (avance != null) {
            double giro = Math.toRadians(elegida.milisegundos() / 1000.0 * CalibracionRobot.GRADOS_POR_SEGUNDO)
                    * (elegida.movimiento() == Movimiento.GIRAR_IZQ ? 1 : -1);
            if (seguimiento.avanceTocaria(seguimiento.rumbo() + giro, CalibracionRobot.mmEn(avance.milisegundos()), MARGEN_MEMORIA_MM)) avance = null;
        }
        if (avance != null && modo == ModoOperativo.BUSQUEDA && ReflejoSeguridad.enElEstrecho(etiquetas, PreprocesadorTelemetria.corregir(telemetria))) avance = null;
        return new Plan(elegida, avance);
    }

    static final double MARGEN_MEMORIA_MM = 10;

    /** Lo libre adelante dentro del ancho del robot (+20 mm), sin contar los sectores LOBITO. */
    static double corredor(int[] corregida, EtiquetaSector[] etiquetas) {
        double libre = Double.MAX_VALUE;
        for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
            if (etiquetas[s] == EtiquetaSector.LOBITO) continue;
            double d = ReflejoSeguridad.distancia(corregida, s);
            if (d == Double.MAX_VALUE) continue;
            double t = Math.toRadians((s - MetaMovimiento.FRENTE) * MetaMovimiento.GRADOS_POR_SECTOR);
            double lateral = d * Math.sin(t), adelante = d * Math.cos(t) - CalibracionRobot.SENSOR_AL_FRENTE_MM;
            if (Math.abs(lateral) <= CalibracionRobot.ANCHO_MM / 2 + 20 && adelante > -CalibracionRobot.SENSOR_AL_FRENTE_MM)
                libre = Math.min(libre, adelante);
        }
        return libre;
    }

    /** Primera orden que no toca nada recordado: giros chicos a los dos lados, retroceso corto, avance corto. */
    private OrdenMovimiento alternativa(OrdenMovimiento original) {
        int lado = original.movimiento() == Movimiento.GIRAR_DER ? -1 : 1;
        java.util.List<OrdenMovimiento> opciones = new java.util.ArrayList<>();
        for (double g : new double[] {-30 * lado, 30 * lado, -60 * lado, 60 * lado, -15 * lado, 15 * lado, -90 * lado, 90 * lado})
            opciones.add(new OrdenMovimiento(g > 0 ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER,
                    (int) Math.round(Math.abs(g) / CalibracionRobot.GRADOS_POR_SEGUNDO * 1000)));
        opciones.add(new OrdenMovimiento(Movimiento.RETROCEDER, CalibracionRobot.msParaAvanzar(80)));
        opciones.add(new OrdenMovimiento(Movimiento.AVANZAR, CalibracionRobot.msParaAvanzar(60)));
        opciones.add(new OrdenMovimiento(Movimiento.RETROCEDER, CalibracionRobot.msParaAvanzar(30)));
        for (OrdenMovimiento o : opciones) {
            if (seguimiento.ordenTocaria(o, MARGEN_MEMORIA_MM)) continue;
            return o;
        }
        return original;
    }

    private Plan decidirSinReflejo(int[] telemetria, EtiquetaSector[] etiquetas, ModoOperativo modo) {
        int[] corregida = PreprocesadorTelemetria.corregir(telemetria);
        double frente = ReflejoSeguridad.frenteLibre(corregida, etiquetas);
        if (frente == Double.MAX_VALUE) frente = SIN_DATO_MM;
        boolean escape = modo == ModoOperativo.ESCAPE;

        double[] meta = null; // ángulo (grados) y distancia (mm desde el eje)
        double tolerancia = TOLERANCIA_GRADOS;
        double extra = 0;
        if (!escape) {
            Seguimiento.Lobito l = seguimiento.lobitoMasCercano();
            if (l != null) {
                meta = seguimiento.haciaPunto(l.x, l.y);
                tolerancia = Math.max(TOLERANCIA_GRADOS, Math.toDegrees(Math.atan2(CalibracionRobot.BOCA_MM / 2 - MARGEN_BOCA_MM, Math.max(1, meta[1]))));
                extra = 15; // Que el eje pase por encima: la canica llega a la puerta.
            }
        } else {
            // Si adelante está el hueco (nada que lo frene y el frente es salida o libre), seguir derecho:
            // girar adentro del estrecho choca contra sus paredes.
            if (frente >= SALIR_DERECHO_MM && frenteAbierto(etiquetas))
                return new Plan(avanzar(Math.min(PASO_EXPLORACION_MM, frente - ReflejoSeguridad.MARGEN_MM)), null);
            double[] vista = salidaVista(corregida, etiquetas);
            if (vista != null) meta = vista;
            else if (seguimiento.salida() != null) meta = seguimiento.haciaPunto(seguimiento.salida()[0], seguimiento.salida()[1]);
            extra = 400; // Pasar de largo el hueco.
        }

        if (meta != null) {
            double angulo = meta[0], distancia = meta[1] + extra;
            if (Math.abs(angulo) > tolerancia) {
                double despues = Math.abs(angulo) <= 90 ? Math.min(distancia, PASO_EXPLORACION_MM) : Double.NaN;
                return girar(angulo, despues, telemetria, etiquetas, modo);
            }
            double avance = Math.min(distancia, frente - ReflejoSeguridad.MARGEN_MM);
            if (avance >= 20) return new Plan(avanzar(avance), null);
        }

        // Explorar.
        if (frente > FRENTE_PARA_EXPLORAR_MM) return new Plan(avanzar(Math.min(PASO_EXPLORACION_MM, frente - 150)), null);
        int mejor = -1;
        double mejorDistancia = 0;
        for (int s = 0; s < PreprocesadorTelemetria.SECTORES; s++) {
            if (s >= ReflejoSeguridad.PRIMER_FRENTE && s <= ReflejoSeguridad.ULTIMO_FRENTE) continue;
            if (!escape && etiquetas[s] == EtiquetaSector.SALIDA) continue;
            double d = ReflejoSeguridad.distancia(corregida, s);
            if (d == Double.MAX_VALUE) d = SIN_DATO_MM;
            if (d > mejorDistancia) { mejorDistancia = d; mejor = s; }
        }
        if (mejor < 0 || mejorDistancia < FRENTE_PARA_EXPLORAR_MM) {
            // Todo cerrado adelante: media vuelta hacia el lado más abierto.
            double izq = ReflejoSeguridad.distancia(corregida, 12), der = ReflejoSeguridad.distancia(corregida, 0);
            return girar(izq >= der ? 120 : -120, Double.NaN, telemetria, etiquetas, modo);
        }
        double angulo = (mejor - MetaMovimiento.FRENTE) * MetaMovimiento.GRADOS_POR_SECTOR;
        return girar(angulo, Math.min(PASO_EXPLORACION_MM, mejorDistancia - 150), telemetria, etiquetas, modo);
    }

    /** True si los sectores del frente son SALIDA o LIBRE. */
    private static boolean frenteAbierto(EtiquetaSector[] etiquetas) {
        boolean salida = false;
        for (int s = ReflejoSeguridad.PRIMER_FRENTE; s <= ReflejoSeguridad.ULTIMO_FRENTE; s++) {
            if (etiquetas[s] != EtiquetaSector.SALIDA && etiquetas[s] != EtiquetaSector.LIBRE) return false;
            if (etiquetas[s] == EtiquetaSector.SALIDA) salida = true;
        }
        return salida || contar(etiquetas, EtiquetaSector.LIBRE) >= ReflejoSeguridad.ABIERTOS_ESTRECHO;
    }

    private static int contar(EtiquetaSector[] etiquetas, EtiquetaSector e) {
        int n = 0;
        for (EtiquetaSector x : etiquetas) if (x == e) n++;
        return n;
    }

    /** Ángulo y distancia hasta el centro de los sectores SALIDA de este barrido, o null. */
    private static double[] salidaVista(int[] corregida, EtiquetaSector[] etiquetas) {
        int primera = -1, ultima = -1;
        for (int s = 0; s < etiquetas.length; s++)
            if (etiquetas[s] == EtiquetaSector.SALIDA) { if (primera < 0) primera = s; ultima = s; }
        if (primera < 0) return null;
        double pared = Double.MAX_VALUE;
        for (int v : new int[] {primera - 1, ultima + 1})
            if (v >= 0 && v < etiquetas.length) pared = Math.min(pared, ReflejoSeguridad.distancia(corregida, v));
        if (pared == Double.MAX_VALUE || pared > SIN_DATO_MM) pared = 700;
        return new double[] {((primera + ultima) / 2.0 - MetaMovimiento.FRENTE) * MetaMovimiento.GRADOS_POR_SECTOR, pared};
    }

    private Plan girar(double grados, double avanceDespues, int[] telemetria, EtiquetaSector[] etiquetas, ModoOperativo modo) {
        int ms = (int) Math.round(Math.abs(grados) / CalibracionRobot.GRADOS_POR_SEGUNDO * 1000);
        OrdenMovimiento giro = new OrdenMovimiento(grados > 0 ? Movimiento.GIRAR_IZQ : Movimiento.GIRAR_DER, Math.max(OrdenMovimiento.MINIMO_MS, ms));
        OrdenMovimiento avance = null;
        if (giroYAvance && !Double.isNaN(avanceDespues) && Math.abs(grados) <= 90) {
            int sector = (int) Math.round(grados / MetaMovimiento.GRADOS_POR_SECTOR) + MetaMovimiento.FRENTE;
            sector = Math.max(0, Math.min(PreprocesadorTelemetria.SECTORES - 1, sector));
            avance = ReflejoSeguridad.avanceTrasGiro(new MetaMovimiento(sector, avanceDespues), telemetria, etiquetas, modo);
        }
        return new Plan(giro, avance);
    }

    private static OrdenMovimiento avanzar(double mm) {
        return new OrdenMovimiento(Movimiento.AVANZAR, CalibracionRobot.msParaAvanzar(mm));
    }
}
