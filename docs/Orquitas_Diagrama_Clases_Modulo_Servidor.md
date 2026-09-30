# Clases vigentes — Hito 2 preliminar

Firmas extraídas del bytecode Java compilado. Referencia: `diagramas/Orquitas_Hito2.drawio`. Las firmas del documento recibido fueron actualizadas con las decisiones explicadas en README.

## AvisoCaptura

Fuente: `src/main/java/orquitas/servidor/captura/AvisoCaptura.java`.

```text
- idCaptura : String
- timestamp : LocalDateTime
+ AvisoCaptura(String, LocalDateTime)
+ getIdCaptura() : String
+ getTimestamp() : LocalDateTime
+ toString() : String
```

## Captura

Fuente: `src/main/java/orquitas/servidor/captura/Captura.java`.

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

## PaqueteRecopilacion

Fuente: `src/main/java/orquitas/servidor/captura/PaqueteRecopilacion.java`.

```text
- telemetria : Telemetria
- avisoCaptura : AvisoCaptura
- PaqueteRecopilacion(Telemetria, AvisoCaptura)
+ decodificar(String) : PaqueteRecopilacion {static}
+ esValido() : boolean
+ getTelemetria() : Telemetria
+ getAvisoCaptura() : AvisoCaptura
```

## ReceptorCapturas

Fuente: `src/main/java/orquitas/servidor/captura/ReceptorCapturas.java`.

```text
- actualizadorEstomago : ActualizadorEstomago
+ ReceptorCapturas(ActualizadorEstomago)
+ notificarCaptura(AvisoCaptura) : void
```

## Recopilador

Fuente: `src/main/java/orquitas/servidor/captura/Recopilador.java`.

```text
- receptorCapturas : ReceptorCapturas
- navegador : Navegador
+ Recopilador(ReceptorCapturas)
+ Recopilador(ReceptorCapturas, Navegador)
+ procesarRecopilacion(String) : boolean
+ procesarRecopilacion(PaqueteRecopilacion) : Movimiento
- extraerTelemetria(PaqueteRecopilacion) : Telemetria
- extraerAvisoCaptura(PaqueteRecopilacion) : AvisoCaptura
- descartarPaquete(PaqueteRecopilacion) : void
```

## ComunicadorOrquita

Fuente: `src/main/java/orquitas/servidor/comunicacion/ComunicadorOrquita.java`.

```text
- recopilador : Recopilador
- salida : Writer
+ ComunicadorOrquita(Recopilador, Writer)
+ enviarRecopilacion(String) : void
+ enviarOrdenNavegacion(Movimiento) : void
- codificarOrden(Movimiento) : String
```

## ReceptorIngestaTCP

Fuente: `src/main/java/orquitas/servidor/comunicacion/ReceptorIngestaTCP.java`.

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

## ActivadorModoEscape

Fuente: `src/main/java/orquitas/servidor/escape/ActivadorModoEscape.java`.

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

## EventoModoEscape

Fuente: `src/main/java/orquitas/servidor/escape/EventoModoEscape.java`.

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

## ClasificadorEntorno

Fuente: `src/main/java/orquitas/servidor/navegacion/ClasificadorEntorno.java`.

```text
- rutaModeloNeuroph : String
- red : NeuralNetwork<?>
+ ClasificadorEntorno(Path)
+ clasificarEntorno(Telemetria) : EntornoClasificado
```

## EntornoClasificado

Fuente: `src/main/java/orquitas/servidor/navegacion/EntornoClasificado.java`.

```text
+ LIBRE : EntornoClasificado {static}
+ OBJETO : EntornoClasificado {static}
+ values() : EntornoClasificado[] {static}
+ valueOf(String) : EntornoClasificado {static}
- EntornoClasificado()
```

## ModoOperativo

Fuente: `src/main/java/orquitas/servidor/navegacion/ModoOperativo.java`.

```text
+ BUSQUEDA : ModoOperativo {static}
+ ESCAPE : ModoOperativo {static}
+ values() : ModoOperativo[] {static}
+ valueOf(String) : ModoOperativo {static}
- ModoOperativo()
```

## Movimiento

Fuente: `src/main/java/orquitas/servidor/navegacion/Movimiento.java`.

```text
+ AVANZAR : Movimiento {static}
+ GIRAR_IZQ : Movimiento {static}
+ GIRAR_DER : Movimiento {static}
+ DETENER : Movimiento {static}
+ values() : Movimiento[] {static}
+ valueOf(String) : Movimiento {static}
- Movimiento()
```

## Navegador

Fuente: `src/main/java/orquitas/servidor/navegacion/Navegador.java`.

```text
- modoOperativo : ModoOperativo
- ultimaDecision : Movimiento
- clasificador : ClasificadorEntorno
+ Navegador()
+ Navegador(ClasificadorEntorno)
+ determinarMovimiento(Telemetria) : Movimiento
- calcularMovimiento(EntornoClasificado, ModoOperativo, Telemetria) : Movimiento
- mantenerUltimaDecision() : Movimiento
- obtenerModoOperativo() : ModoOperativo
+ establecerModoOperativo(ModoOperativo) : void
+ getModoOperativo() : ModoOperativo
```

## Telemetria

Fuente: `src/main/java/orquitas/servidor/navegacion/Telemetria.java`.

```text
- distancias : double[]
+ Telemetria(double...)
+ getDistancias() : double[]
+ normalizar() : double[]
+ sumarDerecha() : double
+ sumarIzquierda() : double
```

## ActualizadorEstomago

Fuente: `src/main/java/orquitas/servidor/persistencia/ActualizadorEstomago.java`.

```text
- archivoEstomago : ArchivoEstomago
- pendientes : ArrayDeque<Captura>
- activo : boolean
+ ActualizadorEstomago(ArchivoEstomago)
+ encolarCaptura(Captura) : void
+ run() : void
+ detener() : void
```

## ArchivoEstomago

Fuente: `src/main/java/orquitas/servidor/persistencia/ArchivoEstomago.java`.

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

## LectorEstomago

Fuente: `src/main/java/orquitas/servidor/persistencia/LectorEstomago.java`.

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

## ServidorOrquita

Fuente: `src/main/java/orquitas/servidor/ServidorOrquita.java`.

```text
+ ServidorOrquita()
+ main(String[]) : void {static}
```

## Relaciones

- Navegador → ClasificadorEntorno
- ClasificadorEntorno → Telemetria
- Navegador → ModoOperativo
- Navegador → Movimiento
- ClasificadorEntorno → EntornoClasificado
- ReceptorIngestaTCP → Recopilador
- ReceptorIngestaTCP → ComunicadorOrquita
- ComunicadorOrquita → Recopilador
- Recopilador → PaqueteRecopilacion
- ReceptorCapturas → ActualizadorEstomago
- ActualizadorEstomago → ArchivoEstomago
- LectorEstomago → ArchivoEstomago
- LectorEstomago → ActivadorModoEscape
- ActivadorModoEscape → EventoModoEscape
- Recopilador → Navegador / ReceptorCapturas
- PaqueteRecopilacion → Telemetria / AvisoCaptura
- ActivadorModoEscape → Navegador
- ActualizadorEstomago y LectorEstomago heredan Thread y comparten ArchivoEstomago.
- ServidorOrquita construye el grafo y ejecuta start() en ambos hilos.
- ClasificadorEntorno posee NeuralNetwork; carga el modelo y sincroniza la inferencia.
- El cliente y el entrenador son simulación; no integran el diseño POO del servidor.