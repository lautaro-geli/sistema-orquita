package orquitas.servidor.comunicacion;

import java.io.*;
import orquitas.servidor.captura.Recopilacion;
import orquitas.servidor.navegacion.ModoOperativo;
import orquitas.servidor.navegacion.Movimiento;
import orquitas.servidor.navegacion.Navegacion;
import orquitas.servidor.persistencia.Estomago;

/** Una instancia por conexion: una orden nunca se entrega a otro cliente. */
public class EnlaceOrquita {
    /**
     * CU-12. Milisegundos sin recepcion antes de declarar la falla. Debe quedar claramente
     * por debajo de los 10 s sin comunicacion que eliminan al robot segun el reglamento.
     * Configurable con -Dorquitas.timeoutMaximoMs=...
     */
    public static final long TIMEOUT_MAXIMO = Long.getLong("orquitas.timeoutMaximoMs", 5000L);
    private static final int INTENTOS_RECONEXION = 3;
    private static final long ESPERA_RECONEXION_MS = 100;
    private final Estomago estomago;
    private final Navegacion navegacion;
    private final Writer salida;
    private volatile long tiempoUltimaRecepcion = System.nanoTime();
    private volatile boolean conexionActiva = true;
    private String tramaPendiente;
    /** Ultimo modo informado a la Orquita; ambos arrancan en BUSQUEDA, asi que al inicio no se envia ESTADO. */
    private ModoOperativo modoNotificado = ModoOperativo.BUSQUEDA;
    public EnlaceOrquita(Estomago estomago, Navegacion navegacion, Writer salida) {
        this.estomago=estomago; this.navegacion=navegacion; this.salida=salida;
    }
    public void enviarRecopilacion(String linea) throws IOException {
        tiempoUltimaRecepcion=System.nanoTime();
        if(!conexionActiva) {
            conexionActiva=true;
            System.out.println("[EnlaceOrquita] Trafico recibido: conexion restablecida");
        }
        Recopilacion recopilacion=Recopilacion.decodificar(linea);
        if(!recopilacion.esValida()) {
            System.err.println("[EnlaceOrquita] Trama invalida: descartada");
            enviarErrorTrama(); return;
        }
        // C.1: la orden de navegacion es la respuesta a cada RECOPILACION valida.
        Movimiento movimiento=recopilacion.procesar(estomago, navegacion);
        // El cambio de estado viaja por la conexion existente, antes de la orden que ya lo tiene en cuenta.
        ModoOperativo modoActual=navegacion.getModoOperativo();
        if(modoActual!=modoNotificado) {
            enviarCambioEstado(modoActual);
            modoNotificado=modoActual;
        }
        enviarOrdenNavegacion(movimiento);
    }
    /** Informa a la Orquita su modo operativo: ESTADO|ESCAPE o ESTADO|BUSQUEDA. */
    public void enviarCambioEstado(ModoOperativo modo) throws IOException {
        transmitir("ESTADO|"+modo.name()+"\n");
    }
    /** Respuesta acotada: no refleja contenido recibido ni confirma una captura. */
    public void enviarErrorTrama() throws IOException {
        transmitir("ERROR|TRAMA_INVALIDA\n");
    }
    public void enviarOrdenNavegacion(Movimiento movimiento) throws IOException {
        transmitir(codificarOrden(movimiento));
    }
    private String codificarOrden(Movimiento movimiento) { return "ORDEN|"+movimiento.name()+"\n"; }

    /** CU-12. Milisegundos transcurridos desde la ultima trama recibida. */
    public long calcularTiempoSinRecepcion() {
        return (System.nanoTime()-tiempoUltimaRecepcion)/1_000_000L;
    }
    /** CU-12. Idempotente: la falla se informa una vez hasta que vuelva a haber trafico. */
    public void registrarFalla() {
        if(!conexionActiva) return;
        conexionActiva=false;
        System.err.println("[EnlaceOrquita] Falla de comunicacion: "+calcularTiempoSinRecepcion()+" ms sin recepcion");
    }
    /** CU-12. Pide al robot que restablezca el enlace; los errores de socket se ignoran. */
    public void enviarSenalReconexion() {
        try { salida.write("ESTADO|RECONECTAR\n"); salida.flush(); }
        catch (IOException e) { /* El enlace esta caido: la senal se repite en el proximo ciclo. */ }
    }
    /** CU-12. Dentro del plazo: no hay nada que corregir. */
    public void continuarOperacionNormal() {
        // La conexion sigue dentro de TIMEOUT_MAXIMO; la recepcion continua sin cambios.
    }
    public boolean isConexionActiva() { return conexionActiva; }

    /**
     * CU-08. Reintento acotado de la ultima trama que no se pudo enviar.
     * Limitacion TCP: el servidor no puede volver a abrir la conexion hacia el robot; solo
     * puede reintentar sobre el mismo socket, y un socket roto casi nunca se recupera. Si
     * los reintentos fallan, la conexion se cierra y el cliente debe abrir otra. Con
     * Bluetooth el reintento real (reemparejar/reabrir el canal) queda fuera de este hito.
     */
    public boolean reintentarConexion() {
        String trama=tramaPendiente;
        if(trama==null) return conexionActiva;
        for(int intento=1;intento<=INTENTOS_RECONEXION;intento++) {
            try { Thread.sleep(ESPERA_RECONEXION_MS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            try {
                escribir(trama);
                tramaPendiente=null; conexionActiva=true;
                System.out.println("[EnlaceOrquita] Reenvio exitoso en el intento "+intento);
                return true;
            } catch (IOException e) {
                System.err.println("[EnlaceOrquita] Reintento "+intento+"/"+INTENTOS_RECONEXION+" fallido: "+e.getMessage());
            }
        }
        tramaPendiente=null;
        return false;
    }
    private void transmitir(String trama) throws IOException {
        try { escribir(trama); }
        catch (IOException e) {
            tramaPendiente=trama;
            registrarFalla();
            if(!reintentarConexion()) throw e;
        }
    }
    private void escribir(String trama) throws IOException {
        salida.write(trama); salida.flush();
    }
}
