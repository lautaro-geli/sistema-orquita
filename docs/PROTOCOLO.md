# Protocolo de la demostración Hito 2

Canal: TCP bidireccional (simula el enlace Bluetooth), texto UTF-8. Un mensaje por línea.

## Identificación y separadores

| Separador | Uso |
|---|---|
| `\n` (LF) | Fin de mensaje. Se acepta `\r\n` (CRLF): el `\r` final se descarta. |
| `\|` | Separa los campos de un mensaje. El **primer campo identifica el tipo** de mensaje. |
| `;` | Separa las 12 distancias dentro del campo de telemetría. |

TCP es un flujo: puede haber varias tramas en una lectura y una trama repartida entre lecturas; el LF es lo único que delimita un mensaje.

## Mensajes

| Dirección | Formato | Respuesta |
|---|---|---|
| Orquita → servidor | `RECOPILACION\|fechaISO\|d0;d1;...;d11\|idOpcional` | `ORDEN\|...` si toda la trama es válida; `ERROR\|TRAMA_INVALIDA` si no |
| Servidor → Orquita | `ORDEN\|AVANZAR`, `ORDEN\|GIRAR_IZQ`, `ORDEN\|GIRAR_DER`, `ORDEN\|DETENER` | Siempre una de estas 4 (ver nota) |
| Servidor → Orquita | `ERROR\|TRAMA_INVALIDA` | — |
| Servidor → Orquita | `ESTADO\|ESCAPE`, `ESTADO\|BUSQUEDA` | Cambio de estado (modo operativo) de la Orquita; ver abajo |
| Servidor → Orquita | `ESTADO\|RECONECTAR` | La Orquita debe restablecer el enlace y volver a enviar RECOPILACION |

Nota: la clasificación del entorno (LIBRE, OBJETO, LOBITO) es interna del servidor y **no viaja** en el protocolo. A la Orquita solo le llega la orden resultante: por ejemplo, perseguir un lobito a la derecha llega como `ORDEN|GIRAR_DER`.

Tipos de mensaje: **datos** (`RECOPILACION`, telemetría periódica), **órdenes** (`ORDEN`), **errores** (`ERROR`), **cambios de estado** (`ESTADO`) y **eventos**. El evento de captura de un Lobito no tiene un mensaje propio: viaja como `idOpcional` de la RECOPILACION en la que ocurrió.

El mensaje legado `CAPTURA|id|fecha` **fue eliminado**: un aviso de captura viaja siempre como `idOpcional` de una RECOPILACION. Una línea que empiece con `CAPTURA` es un tipo desconocido.

## Campos y valores válidos de RECOPILACION

Exactamente 4 campos.

1. `RECOPILACION` (literal).
2. `fechaISO`: se interpreta con `LocalDateTime.parse`, sin zona (p. ej. `2026-09-30T12:00`).
3. Telemetría: exactamente 12 distancias en mm separadas por `;`. Gramática decimal estricta `[0-9]+(?:\.[0-9]+)?`, rango 0–4000. Se admiten `0`, `2500` y `2500.5`; no se admiten exponentes (`1e3`), hexadecimales (`0x1p3`), signos (`+2500`), espacios, comas decimales, NaN ni infinito. Sectores de 15°: derecha 0..5, izquierda 6..11, frente 4..7. 0 significa proximidad en este simulador, no error de sensor.
4. `idOpcional`: vacío (sin captura) o `[A-Za-z0-9_-]{1,64}` (aviso de captura). El separador `|` previo debe existir aunque el campo esté vacío.

Toda RECOPILACION válida recibe una ORDEN (decisión C.1: la orden de navegación es la respuesta a cada recopilación; no hay mensaje de pedido aparte). La orden no confirma que la captura ya esté escrita: el Escritor de esa captura corre en su propio hilo. Un id repetido no agrega registros (decisión C.4) aunque sí produce otra orden.

## Cambio de estado: ESTADO|ESCAPE y ESTADO|BUSQUEDA

- Dirección: servidor → Orquita. Valores válidos: `ESCAPE` y `BUSQUEDA` (los nombres del enum `ModoOperativo`).
- Cuándo: el modo cambia en el servidor (el Auditor detecta 3 capturas en `estomago.txt` y ModoEscape pasa Navegacion a ESCAPE). El aviso viaja por la conexión existente como respuesta a la **siguiente** RECOPILACION válida: primero `ESTADO|<modo>` y después la `ORDEN|…`, que ya fue calculada con el modo nuevo.
- Se envía una sola vez por cambio y por conexión. Al conectarse no se envía nada: ambos extremos arrancan en BUSQUEDA. Una conexión nueva abierta cuando el servidor ya está en ESCAPE recibe `ESTADO|ESCAPE` con su primera RECOPILACION válida.
- Qué hace el cliente: registra el cambio (`[Cliente] Cambio de estado: ESCAPE`) y sigue leyendo hasta la `ORDEN|…` de esa recopilación. Un ESTADO nunca reemplaza a la orden.

## Mensajes inválidos, incompletos y desconocidos

- **Tipo desconocido** (primer campo distinto de `RECOPILACION`), cantidad de campos distinta, fecha inválida, distancias fuera de gramática o rango, id inválido: se descarta **toda** la trama, sin efectos parciales (ni captura ni orden), y el servidor responde `ERROR|TRAMA_INVALIDA` (decisión C.2). La conexión sigue abierta.
- **Línea excesiva**: más de 256 caracteres antes del LF. Se descarta entera y al llegar el LF se responde `ERROR|TRAMA_INVALIDA`.
- **Incompleto**: bytes sin LF final al cerrarse la conexión (EOF). Se descartan sin respuesta: nunca se acepta como captura.
- El error no incluye el contenido recibido. El cliente debe consumir cada respuesta antes de enviar otra solicitud o cerrar.

## Pérdida de comunicación (CU-12) y reintento (CU-08)

- `TIMEOUT_MAXIMO` = 5 s por defecto (configurable con `-Dorquitas.timeoutMaximoMs=...`, siempre menor a 10 s). Es claramente menor a los 10 s sin comunicación que eliminan al robot según el reglamento.
- El servidor lee con un plazo corto (TIMEOUT_MAXIMO/5, máximo 1 s) y en cada vencimiento calcula el tiempo sin recepción. Si alcanza TIMEOUT_MAXIMO registra la falla y envía `ESTADO|RECONECTAR` en cada vencimiento hasta recibir una trama completa (que restablece la conexión) o hasta el cierre duro a las 6 × TIMEOUT_MAXIMO (30 s por defecto). Los errores al enviar la señal se ignoran.
- Si falla el envío de una respuesta, se reintenta hasta 3 veces con 100 ms de espera. Limitación de TCP: el servidor no puede volver a abrir la conexión hacia el robot, solo reintentar sobre el mismo socket; si los reintentos fallan, la conexión se cierra y el cliente debe abrir otra. La reconexión real Bluetooth queda fuera de este hito.

## Alcance

La conexión pertenece a una única Orquita: aceptar varias conexiones sirve para la simulación y para reconexiones, no convierte al servidor en multirrobot. Cada conexión tiene su propio `EnlaceOrquita`, de modo que cada respuesta vuelve por el socket que la pidió. La decisión de pasar a Modo Escape es interna (Auditor → ModoEscape → Navegacion); a la Orquita solo le llega el resultado como `ESTADO|ESCAPE`.
