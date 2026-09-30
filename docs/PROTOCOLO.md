# Protocolo de la demostración Hito 2

TCP bidireccional, UTF-8, una trama por LF. Se acepta CRLF. Máximo 256 caracteres antes de LF. EOF sin LF se descarta. Una línea excesiva se descarta entera y recibe ERROR al llegar LF. Puede haber varias tramas por lectura TCP y una trama repartida entre lecturas. TCP conserva el orden y evita tener que reconstruir datagramas UDP; el delimitador sigue siendo necesario porque TCP es un flujo.

| Dirección | Formato | Resultado |
|---|---|---|
| Orquita → servidor | `RECOPILACION\|fechaISO\|d0;d1;...;d11\|idOpcional` | Una ORDEN si toda la trama es válida |
| Orquita → servidor | `CAPTURA\|id\|fechaISO` | Solo encola; sin respuesta |
| Servidor → Orquita | `ORDEN\|AVANZAR`, `ORDEN\|GIRAR_IZQ`, `ORDEN\|GIRAR_DER`, `ORDEN\|DETENER` | Simulador imprime la orden |

fechaISO se interpreta con LocalDateTime.parse (sin zona). Las distancias son números finitos de 0 a 4000 mm, exactamente 12; 0 significa proximidad en este simulador, no error de sensor. Gramática decimal estricta: `[0-9]+(?:\.[0-9]+)?`. Se admite 0, 2500 y 2500.5, pero no exponentes (1e3), hexadecimales (0x1p3), signos (+2500), espacios ni comas decimales. id: `[A-Za-z0-9_-]{1,64}`; en RECOPILACION puede estar vacío, pero el separador final debe existir. Campos extra, fecha inválida, NaN, infinito y tipo desconocido invalidan TODA la trama, incluida su captura: no hay efectos parciales de persistencia o navegación. El servidor responde `ERROR\|TRAMA_INVALIDA` y conserva la conexión si el envío es posible. Esto también se aplica a CAPTURA mal formada, tipo desconocido o línea excesiva; CAPTURA válida sigue sin ACK. El error no incluye el contenido recibido. El cliente debe consumir la respuesta antes de enviar otra solicitud o cerrar.

Una orden responde a una lectura, no confirma que la captura ya esté escrita. Los duplicados por id no agregan registros, aunque una lectura repetida sí produce otra orden. No hay ACK de captura ni reintento automático de disco. El simulador debe conocer esta limitación.

El manejador cierra conexiones tras 10 segundos sin recibir caracteres o ante IOException, y el servidor continúa aceptando clientes nuevos. No reenvía órdenes antiguas ni intenta que un servidor TCP reconecte a un cliente: el cliente abre otro socket. No hay reconexión automática en el simulador actual. Repetir una captura con el mismo id es seguro contra duplicados.

No se implementa ESTADO. EventoModoEscape es una notificación entre objetos Java, no una trama. El modo y la decisión anterior pertenecen a una única Orquita; aceptar varias conexiones sirve para la simulación/retransmisiones, no convierte al servidor en un sistema multirrobot. Cada conexión conserva su Writer para recibir su propia respuesta.
