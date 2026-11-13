# Protocolo v3 — Hito 3 (Bluetooth RFCOMM/SPP)

Cambio de v2 a v3: la ORDEN lleva la duración en milisegundos (`ORDEN|movimiento|ms`) y el Servidor ya no reemplaza el `-1` de la telemetría (lo trata la regla del -1, ver abajo). El robot sigue aceptando `ORDEN|movimiento` sin ms (usa el paso fijo de v2).

Canal: Bluetooth clásico SPP entre el HC-06 del robot (esclavo, 9600 baudios) y la PC del Servidor (Windows). Windows crea un **puerto COM** al emparejar el HC-06; el Servidor solo lo abre (jSerialComm), no empareja ni crea el puerto.

Texto ASCII, **un mensaje por línea** terminada en `\n` (se acepta `\r\n`). El primer campo identifica el tipo; `|` separa el tipo de su contenido y `;` separa los valores de la telemetría.

## Mensajes

| Sentido | Mensaje | Detalle |
|---|---|---|
| Orquita → Servidor | `TELEMETRIA\|v0;v1;...;v38` | 39 enteros, sin campo de secuencia. Para el sector 0..12 (0° a 180°, cada 15°), los tres ROI en orden 4x4, 8x8, 16x16: índice = sector × 3 + roi. Cada valor es una distancia en mm (0–4000) o `-1` = sin eco. |
| Orquita → Servidor | `CAPTURA\|id` | `id` de 1 a 16 caracteres `[A-Za-z0-9_-]`. Lo genera el robot (1, 2, 3…). Se repite hasta recibir CONFIRMACION. |
| Servidor → Orquita | `ORDEN\|movimiento\|ms` | `AVANZAR`, `RETROCEDER`, `GIRAR_IZQ`, `GIRAR_DER` o `DETENER`, y la duración del paso en ms (50 a 1500; `DETENER` lleva 0). Es la respuesta a cada TELEMETRIA válida. El robot acota la duración a `PASO_MAXIMO_MS` = 1500 (queda por debajo del Safe Mode de 3000 ms). |
| Servidor → Orquita | `CONFIRMACION` | Mensaje válido y procesado. Para CAPTURA sale **después** de que el Escritor terminó de escribir en `estomago.txt`. |
| Servidor → Orquita | `RECHAZO` | Mensaje incompleto, inválido, desconocido, demasiado largo, o captura que no pudo anotarse. |
| Servidor → Orquita | `LATIDO` | Cada 1 s (`PERIODO_LATIDO_MS`), haya o no tráfico. |

**Orden de las respuestas**

- TELEMETRIA válida → `CONFIRMACION` y luego `ORDEN|…|ms`.
- CAPTURA válida → el Escritor escribe → `CONFIRMACION`. Una captura repetida (mismo id) también se confirma, sin duplicar la línea.
- Cualquier otra cosa → `RECHAZO`. La conexión sigue abierta.

**Validación estricta** (`EnlaceOrquita.decodificar`):

- TELEMETRIA lleva exactamente 39 enteros decimales sin signo, de 0 a 4000, o el `-1` exacto. No se aceptan `+`, espacios, decimales, exponentes, letras, campos vacíos ni campos de más.
- CAPTURA lleva exactamente un id válido.
- Tipo desconocido o línea vacía → `RECHAZO`.
- `LARGO_MAXIMO` = 320 caracteres por línea. Una línea más larga se descarta hasta el próximo `\n` y se responde `RECHAZO`.
- El Servidor conserva el `-1` al decodificar. La navegación aplica la **regla del -1** por sector (`PreprocesadorTelemetria`):
  - si el 4x4 o el 16x16 son `-1` y el 8x8 tiene valor, toman el valor del 8x8;
  - si el 8x8 es `-1`, el 8x8 queda **desconocido** (el 4x4 y el 16x16 conservan su valor si lo tienen);
  - si los tres son `-1`, los tres quedan **desconocidos**.

  Las redes reciben, por cada valor, su cercanía (desconocido = 0) y una marca aparte de desconocido. La navegación por reglas toma el desconocido como lejos (no es "el sector más cercano").
- Varias líneas en una misma lectura, o una línea repartida entre lecturas, se reconstruyen por `\n`.

**Hora de las capturas:** la pone el Servidor (el Arduino no tiene reloj). Línea en `estomago.txt`: `id;fechaISO`.

## Cambios de estado y falla del enlace

- **No existe mensaje ESTADO.** Los cambios de estado se informan en la consola del Servidor: `[Estado] ANTERIOR -> NUEVO` en cada cambio de modo (BUSQUEDA, ESCAPE, LIBERADA) y `[Estado] ERROR_SEGURO (causa)` cuando falla el enlace. El robot los percibe por la ORDEN que recibe.
- **Servidor (CU-12):** si no recibe nada durante `TIMEOUT_MAXIMO_MS` = 15000 ms, o falla una escritura al puerto, registra la falla (`ERROR_SEGURO`), deja el enlace `DESCONECTADO`, termina el hilo y el Servidor se cierra con código ≠ 0. No reabre el puerto: el enlace se restablece a mano (vincular de nuevo el HC-06 y reiniciar el Servidor, lo que vacía el estómago).
- **Robot (Safe Mode):** si no recibe nada del Servidor (ni LATIDO) durante 3000 ms, frena los motores de inmediato y espera; sale del Safe Mode al volver a recibir algo.

## Trazabilidad Hito 2 (TCP) → Hito 3 (Bluetooth)

| Hito 2 (simulación TCP) | Hito 3 (Bluetooth) |
|---|---|
| `RECOPILACION\|fecha\|12 distancias\|idOpcional` | Se parte en `TELEMETRIA\|39 valores` y `CAPTURA\|id`. La fecha la pone el Servidor. |
| 12 sectores × 1 distancia (`double`) | 13 sectores × 3 ROI = 39 enteros; `-1` = sin eco. |
| `ERROR\|TRAMA_INVALIDA` | `RECHAZO` |
| — (la orden era la única respuesta) | `CONFIRMACION` antes de cada ORDEN y como acuse de cada CAPTURA. |
| `ESTADO\|ESCAPE`, `ESTADO\|BUSQUEDA`, `ESTADO\|RECONECTAR` | No hay mensaje ESTADO: los estados se informan por consola; `LATIDO` cada 1 s reemplaza la supervisión. |
| Canal TCP (socket, puerto 5000) | Puerto COM del Bluetooth SPP (RFCOMM), 9600 baudios. |
| Reintento acotado de escritura y `ESTADO\|RECONECTAR` | Restablecimiento manual: falla → `DESCONECTADO`, se vuelve a vincular y se reinicia el Servidor. Del lado del robot, Safe Mode. |
| `LARGO_MAXIMO` = 256 | `LARGO_MAXIMO` = 320 (la telemetría ocupa hasta ~205 caracteres). |
| `CanalIngestaTCP`, `ClienteTelemetriaTCP`, `ClienteIngestaTCP`, `Recopilacion`, `Telemetria`, `Captura` | Eliminadas. `EnlaceOrquita` abre el COM y procesa las tramas; la telemetría es `int[39]`; el formato de línea del estómago pasó a `Estomago`. |
| Órdenes `AVANZAR`, `GIRAR_IZQ`, `GIRAR_DER`, `DETENER` | Se suma `RETROCEDER`. En v3 la orden lleva la duración en ms. |
