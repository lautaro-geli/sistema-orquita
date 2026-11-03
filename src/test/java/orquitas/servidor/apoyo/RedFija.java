package orquitas.servidor.apoyo;

import java.util.function.Function;
import orquitas.servidor.navegacion.EntornoClasificado;
import orquitas.servidor.navegacion.RedNeuronal;

/** Red de prueba: devuelve el entorno que decide la función, sin cargar ningún modelo. */
public class RedFija extends RedNeuronal {
    private volatile Function<int[], EntornoClasificado> regla;

    public RedFija(EntornoClasificado entorno) { this(t -> entorno); }

    public RedFija(Function<int[], EntornoClasificado> regla) { this.regla = regla; }

    public void cambiar(EntornoClasificado entorno) { regla = t -> entorno; }

    @Override
    public EntornoClasificado clasificarEntorno(int[] telemetria) { return regla.apply(telemetria); }
}
