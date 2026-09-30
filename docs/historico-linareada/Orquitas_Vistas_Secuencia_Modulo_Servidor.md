# Vistas de secuencia del módulo servidor

## Alcance y actor

La Linareada implementa CU-06 sólo para capturas, CU-09, CU-10 y CU-11. El actor es Orquita y aparece en todas las vistas. Los fragmentos `ref` muestran cómo sus capturas originan las operaciones internas; no inventan llamadas del robot a `run()`.

Las imágenes y los editables draw.io están en `entrega-linareada-corregida/diagramas`. No se documenta el interior del simulador cliente. CU-07, CU-08 y CU-12 pertenecen al diseño completo y no se presentan como implementados en este recorte.

## CU-06 Recibir recopilación de capturas

```plantuml
@startuml
skinparam backgroundColor white
actor Orquita
participant ReceptorIngestaTCP
participant Recopilador
participant ReceptorCapturas
Orquita -> ReceptorIngestaTCP : CAPTURA|id|fecha + LF
ReceptorIngestaTCP -> Recopilador : procesarRecopilacion(mensaje)
alt mensaje completo, campos y fecha válidos
  note right of Recopilador : Construir AvisoCaptura(id, timestamp)
  Recopilador -> ReceptorCapturas : notificarCaptura(avisoCaptura)
  ref over ReceptorCapturas : CU-09 Actualizar estómago
else inválido o tipo desconocido
  Recopilador -> Recopilador : descartarPaquete()
end
note over ReceptorIngestaTCP : Trama excesiva o EOF sin LF: descartar antes de llamar al Recopilador.
@enduml
```

## CU-09 Actualizar estómago

```plantuml
@startuml
skinparam backgroundColor white
actor Orquita
participant Recopilador
participant ReceptorCapturas
participant ActualizadorEstomago
participant ArchivoEstomago
ref over Orquita, Recopilador : CU-06 Captura validada
Recopilador -> ReceptorCapturas : notificarCaptura(avisoCaptura)
ReceptorCapturas -> ActualizadorEstomago : encolarCaptura(captura)
ActualizadorEstomago -> ArchivoEstomago : anunciarEscritura()
note over ActualizadorEstomago : synchronized(pendientes): addLast y notifyAll\nEl escritor ya fue iniciado con start(); la JVM ejecuta run().
loop run(): esperar trabajo o procesar siguiente captura
  critical permiso exclusivo sobre estomago.txt
    ActualizadorEstomago -> ArchivoEstomago : escribirRegistro(captura)
    note over ArchivoEstomago : mutex.acquire(); leerDelArchivo()
    alt id nuevo
      note over ArchivoEstomago : Escribir UTF-8 y cerrar correctamente
    else id ya persistido
      note over ArchivoEstomago : No duplicar la línea
    else IOException
      note over ArchivoEstomago : Error pendiente de propagación
    end
    note over ArchivoEstomago : finally: mutex.release()
    ArchivoEstomago --> ActualizadorEstomago : true / false / excepción
  end
  ActualizadorEstomago -> ArchivoEstomago : finally: finalizarEscritura()
end
@enduml
```

La cola organiza solicitudes. El número de pendientes nunca decide el modo. Al cerrar se drena la cola; un error de disco se registra sin confirmar y no se reintenta automáticamente. Si se interrumpe la espera del permiso, el aviso se reencola para cerrar ordenadamente.

## CU-10 Leer estómago

```plantuml
@startuml
skinparam backgroundColor white
actor Orquita
participant ActualizadorEstomago
participant ArchivoEstomago
participant LectorEstomago
participant ActivadorModoEscape
par recepción y escritura
  ref over Orquita, ActualizadorEstomago : CU-06 y CU-09: capturas encoladas y persistidas
else auditor independiente
  loop sistemaActivo() == true
    critical lectura del archivo
      LectorEstomago -> ArchivoEstomago : leerRegistros()
      note over ArchivoEstomago : wait() mientras escriturasPendientes > 0\nmutex.acquire()
      ArchivoEstomago -> ArchivoEstomago : leerDelArchivo()
      note over ArchivoEstomago : finally: mutex.release()
      ArchivoEstomago --> LectorEstomago : registros válidos y únicos
    end
    alt lectura correcta
      LectorEstomago -> LectorEstomago : contarRegistrosValidos(registros)
      opt cantidadCapturas >= 3 y !modoEscapeNotificado
        note over LectorEstomago : Crear EventoModoEscape
        LectorEstomago -> ActivadorModoEscape : notificarCapturasCompletas(evento)
        ref over ActivadorModoEscape : CU-11
        note over LectorEstomago : modoEscapeNotificado = true
      end
    else IOException
      note over LectorEstomago : Registrar error; no contar ni notificar
    end
    LectorEstomago -> LectorEstomago : esperarProximoCiclo(intervaloAuditoria)
  end
end
@enduml
```

El LOOP se inicia con el servidor y sigue sin mensajes nuevos. Una lectura en curso puede terminar antes de una captura recién anunciada; las siguientes lecturas esperan las escrituras pendientes. El cierre interrumpe al lector y termina su ciclo.

## CU-11 Activar modo escape

```plantuml
@startuml
skinparam backgroundColor white
actor Orquita
participant LectorEstomago
participant ActivadorModoEscape
participant Navegador
ref over Orquita, LectorEstomago : CU-06 / CU-09 / CU-10: capturas persistidas y auditadas
LectorEstomago -> ActivadorModoEscape : notificarCapturasCompletas(evento)
alt evento nulo o tipo incorrecto
  ActivadorModoEscape --> LectorEstomago : IllegalArgumentException
else modoActual == ESCAPE
  note over ActivadorModoEscape : Retornar sin repetir ni revertir
else cantidadCapturas < 3
  ActivadorModoEscape -> ActivadorModoEscape : mantenerModoBusqueda()
else cantidadCapturas >= 3
  ActivadorModoEscape -> ActivadorModoEscape : modoActual = cambiarModo(ESCAPE)
  ActivadorModoEscape -> Navegador : establecerModoOperativo(modoActual)
end
@enduml
```

El evento contiene tipoEvento, cantidadCapturas y timestamp. No existe una llamada sin argumentos a notificarCapturasCompletas. Este hito cambia el modo interno y no simula que ya navega hacia una salida física.
