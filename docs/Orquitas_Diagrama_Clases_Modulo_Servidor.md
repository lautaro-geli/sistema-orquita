# Diagrama de clases del módulo servidor

## Alcance implementado

La Linareada: CU-06 limitado a capturas, CU-09, CU-10 y CU-11. Bluetooth, Neuroph, decisiones de movimiento y Safe Mode físico pertenecen al sistema completo y no están implementados en esta carpeta.

Los PNG de fondo blanco y los originales editables están en `entrega-linareada-corregida/diagramas`.

## Clases y firmas verificadas

### AvisoCaptura

Paquete: `orquitas.servidor.captura`.

```text
- idCaptura : String
- timestamp : LocalDateTime
+ AvisoCaptura(String, LocalDateTime)
+ getIdCaptura() : String
+ getTimestamp() : LocalDateTime
+ toString() : String
```

### Captura

Paquete: `orquitas.servidor.captura`.

```text
- idCaptura : String
- timestamp : LocalDateTime
+ Captura(String, LocalDateTime)
+ getIdCaptura() : String
+ getTimestamp() : LocalDateTime
+ aLinea() : String
+ desdeLinea(String) : Captura {static}
+ toString() : String
```

### ReceptorCapturas

Paquete: `orquitas.servidor.captura`.

```text
- actualizadorEstomago : ActualizadorEstomago
+ ReceptorCapturas(ActualizadorEstomago)
+ notificarCaptura(AvisoCaptura) : void
```

### Recopilador

Paquete: `orquitas.servidor.captura`.

```text
- receptorCapturas : ReceptorCapturas
+ Recopilador(ReceptorCapturas)
+ procesarRecopilacion(String) : boolean
- descartarPaquete() : boolean
```

### ReceptorIngestaTCP

Paquete: `orquitas.servidor.comunicacion`.

```text
- puerto : int
- recopilador : Recopilador
- clientes : Set<Socket>
- manejadores : Set<Thread>
- activo : boolean
- servidor : ServerSocket
+ ReceptorIngestaTCP(int, Recopilador)
+ iniciar() : void
+ obtenerPuerto() : int
+ detener() : void
- manejarCliente(Socket) : void
```

### ActivadorModoEscape

Paquete: `orquitas.servidor.escape`.

```text
- UMBRAL_CAPTURAS : int {static}
- navegador : Navegador
- modoActual : ModoOperativo
+ ActivadorModoEscape(Navegador)
+ notificarCapturasCompletas(EventoModoEscape) : void
+ getModoActual() : ModoOperativo
- cambiarModo(ModoOperativo) : ModoOperativo
- mantenerModoBusqueda() : void
```

### EventoModoEscape

Paquete: `orquitas.servidor.escape`.

```text
+ TIPO_MODO_ESCAPE_ACTIVADO : String {static}
- tipoEvento : String
- cantidadCapturas : int
- timestamp : LocalDateTime
+ EventoModoEscape(String, int, LocalDateTime)
+ getTipoEvento() : String
+ getCantidadCapturas() : int
+ getTimestamp() : LocalDateTime
+ toString() : String
```

### ModoOperativo

Paquete: `orquitas.servidor.navegacion`.

```text
BUSQUEDA
ESCAPE
```

### Navegador

Paquete: `orquitas.servidor.navegacion`.

```text
- modoOperativo : ModoOperativo
+ Navegador()
+ establecerModoOperativo(ModoOperativo) : void
+ getModoOperativo() : ModoOperativo
```

### ActualizadorEstomago

Paquete: `orquitas.servidor.persistencia`.

```text
- archivoEstomago : ArchivoEstomago
- pendientes : ArrayDeque<Captura>
- activo : boolean
+ ActualizadorEstomago(ArchivoEstomago)
+ encolarCaptura(Captura) : void
+ run() : void
+ detener() : void
```

### ArchivoEstomago

Paquete: `orquitas.servidor.persistencia`.

```text
- rutaArchivo : String
- mutex : Semaphore
- controlEscrituras : Object
- escriturasPendientes : int
+ ArchivoEstomago(String)
+ anunciarEscritura() : void
+ finalizarEscritura() : void
+ escribirRegistro(Captura) : boolean
+ leerRegistros() : List<Captura>
- leerDelArchivo() : List<Captura>
```

### LectorEstomago

Paquete: `orquitas.servidor.persistencia`.

```text
- UMBRAL_CAPTURAS : int {static}
- archivoEstomago : ArchivoEstomago
- activadorModoEscape : ActivadorModoEscape
- intervaloAuditoria : long
- activo : boolean
- modoEscapeNotificado : boolean
+ LectorEstomago(ArchivoEstomago, ActivadorModoEscape, long)
+ run() : void
+ sistemaActivo() : boolean
+ detener() : void
- contarRegistrosValidos(List<Captura>) : int
- esperarProximoCiclo(long) : void
```

### ServidorOrquita

Paquete: `orquitas.servidor`.

```text
+ ServidorOrquita()
+ main(String[]) : void {static}
```

## Relaciones y concurrencia

Recopilador → ReceptorCapturas → ActualizadorEstomago → ArchivoEstomago ← LectorEstomago → ActivadorModoEscape → Navegador.

ActualizadorEstomago y LectorEstomago heredan de Thread; `ServidorOrquita.main()` ejecuta `start()` en ambos. Hay un solo ArchivoEstomago compartido. El contador escriturasPendientes coordina operaciones: no determina la cantidad de capturas. La cantidad se reconstruye siempre desde el archivo.

`escribirRegistro`, `leerRegistros` y `leerDelArchivo` propagan IOException; las dos primeras también InterruptedException. En las secciones críticas, el permiso se libera antes del retorno o propagación.

Los datos usan atributos privados y accesores públicos, tal como el código. `aLinea()` y `desdeLinea()` reemplazan los nombres ingleses de serialización.
