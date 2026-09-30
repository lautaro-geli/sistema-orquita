package orquitas.servidor.captura;

import java.time.LocalDateTime;
import orquitas.servidor.navegacion.Telemetria;

public final class PaqueteRecopilacion {
    private final Telemetria telemetria;
    private final AvisoCaptura avisoCaptura;
    private PaqueteRecopilacion(Telemetria telemetria, AvisoCaptura aviso) {
        this.telemetria=telemetria; this.avisoCaptura=aviso;
    }
    public static PaqueteRecopilacion decodificar(String mensaje) {
        if (mensaje==null || mensaje.length()>256) throw new IllegalArgumentException("Trama invalida");
        String[] c=mensaje.split("\\|", -1);
        if(c.length==3 && c[0].equals("CAPTURA"))
            return new PaqueteRecopilacion(null, new AvisoCaptura(c[1], LocalDateTime.parse(c[2])));
        if(c.length!=4 || !c[0].equals("RECOPILACION")) throw new IllegalArgumentException("Tipo/campos invalidos");
        LocalDateTime fecha=LocalDateTime.parse(c[1]);
        String[] valores=c[2].split(";", -1);
        if(valores.length!=12) throw new IllegalArgumentException("Faltan distancias");
        double[] distancias=new double[12];
        for(int i=0;i<12;i++) distancias[i]=Double.parseDouble(valores[i]);
        Telemetria t=new Telemetria(distancias);
        AvisoCaptura a=c[3].isEmpty()?null:new AvisoCaptura(c[3], fecha);
        return new PaqueteRecopilacion(t,a);
    }
    public boolean esValido() { return telemetria!=null || avisoCaptura!=null; }
    public Telemetria getTelemetria() { return telemetria; }
    public AvisoCaptura getAvisoCaptura() { return avisoCaptura; }
}
