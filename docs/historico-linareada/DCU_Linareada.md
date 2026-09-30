# Casos de uso de La Linareada

Alcance: CU-06 sólo capturas, CU-09, CU-10 y CU-11. El Hito 2 general aún requiere Neuroph y protocolo completo.

## CU-06 Recibir recopilación de capturas

Actor: Orquita, mediante el simulador TCP.

Precondición: Servidor iniciado; receptor TCP disponible; escritor y auditor iniciados.

Postcondición: Aviso válido entregado a la cola del escritor. La aceptación no confirma todavía la escritura.

### Curso normal

1. **Orquita:** Envía CAPTURA|id|fecha, terminada con LF, por el socket TCP.
2. **ReceptorIngestaTCP:** Reconstruye una trama completa y llama a procesarRecopilacion(mensaje) de Recopilador.
3. **Recopilador:** Valida el tipo, los tres campos, el identificador y la fecha. Construye AvisoCaptura.
4. **Recopilador:** Invoca notificarCaptura(avisoCaptura) de ReceptorCapturas. Continúa CU-09.

### Alternativas

- Tipo desconocido, campo inválido o fecha incorrecta: descartarPaquete() devuelve false; no se encola una captura.
- Trama mayor de 256 caracteres o conexión cerrada sin LF: el receptor descarta esa trama. Una nueva conexión puede continuar operando.


## CU-09 Actualizar estómago

Actor: Orquita, origen de la captura. La escritura la ejecuta el hilo del servidor.

Precondición: Aviso validado en CU-06; ActualizadorEstomago.start() ya ejecutado por el arranque.

Postcondición: Una captura nueva queda persistida y cerrada correctamente. Se libera el acceso incluso ante un error.

### Curso normal

1. **Orquita:** Origina el aviso de captura recibido y validado en CU-06.
2. **ReceptorCapturas:** Construye Captura y llama a ActualizadorEstomago.encolarCaptura(captura).
3. **ActualizadorEstomago:** Anuncia la escritura pendiente, encola el registro y hace notifyAll() dentro del bloque synchronized de la cola.
4. **ActualizadorEstomago.run:** Despierta, extrae un registro de la cola y llama a ArchivoEstomago.escribirRegistro(captura).
5. **ArchivoEstomago:** CRITICAL: toma mutex.acquire(). Si el lector ya estaba leyendo, espera a que termine. Lee el archivo para detectar el mismo id.
6. **ArchivoEstomago:** Agrega el registro nuevo en UTF-8 y cierra el archivo. En finally libera mutex.release() antes de devolver true.
7. **ActualizadorEstomago.run:** En finally llama a finalizarEscritura(). Sin pendientes, el lector puede continuar. El escritor espera otra captura o procesa la siguiente.

### Alternativas

- Identificador ya persistido: no agrega otra línea y devuelve false. Es una retransmisión, no otro Lobito.
- IOException: registra el error y no confirma la captura; libera semáforo y condición pendiente. El cliente puede reenviar el mismo id. No hay reintento automático de errores de disco.
- Al detener el servidor, el escritor termina lo aceptado en la cola. Interrumpir su espera del semáforo reencola el aviso antes del cierre.


## CU-10 Leer estómago

Actor: Orquita, asociada por las capturas que origina. El disparador periódico es interno.

Precondición: LectorEstomago.start() ejecutado; ambos hilos comparten ArchivoEstomago. Un archivo ausente representa cero capturas.

Postcondición: Cantidad reconstruida desde registros válidos y únicos. Al alcanzar el umbral se notifica CU-11 una sola vez por ejecución del auditor.

### Curso normal

1. **Orquita:** Puede originar capturas mediante CU-06 y CU-09 mientras funciona el auditor; no necesita enviar una nueva para cada lectura.
2. **LectorEstomago.run:** LOOP: mientras sistemaActivo(), llama a ArchivoEstomago.leerRegistros().
3. **ArchivoEstomago:** Si hay escrituras pendientes, espera con wait(). Después toma mutex.acquire() y entra en CRITICAL.
4. **ArchivoEstomago:** Lee el archivo completo, descarta líneas inválidas e ids repetidos. En finally libera mutex.release() y devuelve los registros.
5. **LectorEstomago.run:** Calcula cantidadCapturas con contarRegistrosValidos(registros). No consulta la cola para contar Lobitos.
6. **LectorEstomago.run:** Si cantidad >= 3 y !modoEscapeNotificado, crea EventoModoEscape e invoca notificarCapturasCompletas(evento). Después marca la notificación como realizada.
7. **LectorEstomago.run:** Espera esperarProximoCiclo(intervaloAuditoria) y vuelve al paso 2. Al detenerse sale del LOOP.

### Alternativas

- Menos de tres registros o escape ya notificado: omite CU-11 y espera el próximo ciclo.
- IOException: no usa una lista parcial, no cuenta y no notifica; registra el error y vuelve a intentar en el ciclo siguiente.
- Interrupción de cierre: termina el hilo. Si el archivo ya tiene tres registros al reiniciar, los reconoce sin mensajes nuevos.


## CU-11 Activar modo escape

Actor: Orquita, beneficiaria del cambio de modo originado por sus capturas persistidas.

Precondición: CU-10 detectó al menos tres capturas válidas y únicas en estomago.txt y construyó EventoModoEscape.

Postcondición: Navegador conserva ModoOperativo.ESCAPE. En este hito no se emiten órdenes de motores ni se calcula una ruta física.

### Curso normal

1. **Orquita:** Participa por el contexto de capturas recibido, persistido y auditado en CU-06, CU-09 y CU-10.
2. **LectorEstomago:** Invoca ActivadorModoEscape.notificarCapturasCompletas(evento).
3. **ActivadorModoEscape:** Valida el tipo del evento. Si todavía estaba en BUSQUEDA y la cantidad es >= 3, llama a cambiarModo(ESCAPE).
4. **ActivadorModoEscape:** Llama a Navegador.establecerModoOperativo(modoActual) y registra la activación.

### Alternativas

- Cantidad < 3: mantenerModoBusqueda(); no cambia el modo.
- Modo ya ESCAPE: retorna sin repetir ni revertir el cambio.
- Evento nulo o tipo desconocido: IllegalArgumentException. El auditor genera únicamente el tipo acordado.

