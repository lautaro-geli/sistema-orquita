# Decisiones de diseño del Hito 3 (resumen para la defensa)

> Resumen de los documentos `Fase1_Diseno_Base_Hito3`, `Correcciones_Diagramas_Hito3_v5` y `Pendientes_Fase2_Codigo_Servidor` del Proyecto de Claude (2 al 4/11/2026). El diseño final quedó en `docs/diseno/` (DCU v3.6, clases v6, secuencias v5) y en `docs/PROTOCOLO.md`.

## Protocolo y comunicación

- **Bluetooth SPP con el HC-06.** El emparejamiento y el puerto COM los crea Windows a mano; el Servidor solo abre el COM (jSerialComm).
- **Mensajes separados:** `TELEMETRIA|39 valores` y `CAPTURA|id`. Cada uno recibe `CONFIRMACION` o `RECHAZO`. La captura se repite hasta que llega la confirmación, y la confirmación sale **después** de que el Escritor la anotó.
- **Sin mensaje `ESTADO`:** los cambios de modo (BUSQUEDA → ESCAPE → LIBERADA) se informan en la consola del Servidor (`[Estado] A -> B`) y la Orquita los percibe por las órdenes. Si el profesor marca que las Reglas Técnicas (3.b.iv) piden "cambios de estado" en el protocolo: los cambios se comunican a través de la orden. Si insiste, se agrega `ESTADO|modo` solo cuando cambia.
- **Fail-Safe:** el Servidor manda `LATIDO` cada 1 s; la Orquita frena sola a los 3 s sin recibir nada (Safe Mode). Como se mueve en pasos cortos (parar–medir–enviar–mover), después del corte no avanza más que un paso.
- **Falla del enlace en el Servidor:** registra la falla (`[Estado] ERROR_SEGURO`), se cierra de forma ordenada y se reinicia a mano (reiniciar vacía el estómago).
- La hora de cada captura la pone el Servidor (el Arduino no tiene reloj). El id lo genera el robot (1, 2, 3…).
- Protocolo v3 (posterior): la orden lleva la duración: `ORDEN|movimiento|ms`.

## Clases (Servidor)

- Se eliminaron `Recopilacion`, `Telemetria` y `Captura`. La decodificación quedó en `EnlaceOrquita.decodificar(trama)`, que devuelve el tipo (`CAPTURA`, `TELEMETRIA` o `INVALIDA`) y guarda lo leído.
- `Estomago` es dueña del semáforo, del archivo y del formato de la línea. `registrarCaptura` crea un `Escritor` por captura y espera su resultado. `contarRegistrosValidos()` lee, valida y cuenta dentro de la misma sección crítica.
- `Auditor`: hilo independiente; el umbral de capturas es un parámetro (1 en la prueba de campo, 3 en la competencia). `activarModoEscape` es idempotente.
- **El diagrama de clases no lleva estados:** para eso está el Diagrama de Estados.

## Estados (correspondencia diseño–código)

| Estado del diagrama | En el código |
|---|---|
| Búsqueda | `ModoOperativo.BUSQUEDA` |
| Aproximación | Condición dentro de BUSQUEDA (la red ve un LOBITO); no se informa por consola |
| Captura | Mensaje `CAPTURA` y `registrarCaptura()` (CU-09); `[Captura]` en consola |
| Escape | `ModoOperativo.ESCAPE` |
| Finalización | `ModoOperativo.LIBERADA` |
| Error Seguro | Safe Mode del Arduino y `EstadoEnlace.DESCONECTADO` + `[Estado] ERROR_SEGURO` |

Esta correspondencia tiene que figurar en la carpeta técnica (la consigna pide justificar las diferencias entre diseño e implementación).

## Hardware (decisiones de entonces)

- Captura con **FC-51** montado de costado en el canal, activo en bajo, con antirrebote y bloqueo de ~1,5 s.
- El segundo servo **levanta el sensor** para que la canica pase por debajo.
- El robot **puede retroceder** (`RETROCEDER`).
- **Se detiene una vez afuera** (`LIBERADA`).

## Vistas pendientes al 04/11 (verificar si se entregaron)

- `Orquitas_Diagrama_Estados_v1.drawio` y `Orquitas_Diagrama_Despliegue_v2.drawio` (en draw.io, estilo del docente). Faltaba exportar los PNG (fondo blanco, sin grilla) y confirmar con el docente de Desarrollo de Sistemas las especificaciones de Estados y Despliegue.
- **Ojo, coherencia:** los diagramas v6/v5 describen la navegación con la red de una sola clase (`RedNeuronal`, `entorno.nnet`). La navegación con dos redes (`RedPercepcion`, `RedMovimiento`, `RepresentacionIntermedia`, `ReflejoSeguridad` con el candado, etc.) se agregó después y **no está en los diagramas**. Si se entrega con las redes E2, hay que decidir si se actualizan los diagramas o se justifica la diferencia en la carpeta técnica.
