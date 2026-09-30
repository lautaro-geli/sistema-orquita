# DCU vigente — Hito 2 preliminar simulado

Actor externo: **Orquita**, representada por el simulador TCP. Ejecutor de validación, clasificación, persistencia y auditoría: sistema servidor. Solo se documentan secuencias del servidor. No confundir la asociación del actor con invocar directamente todos los métodos internos.

## CU-06 — Recibir recopilación

Precondición: servidor iniciado y conexión TCP abierta. Disparador: Orquita envía una trama terminada en LF. Postcondición: paquete aceptado, captura opcional encolada y orden enviada si contiene telemetría.

1. Orquita envía RECOPILACION o CAPTURA.
2. ReceptorIngestaTCP reconstruye la línea y llama ComunicadorOrquita.enviarRecopilacion(mensaje).
3. ComunicadorOrquita llama PaqueteRecopilacion.decodificar(mensaje), que valida todos los campos.
4. Recopilador.procesarRecopilacion(paquete) comprueba esValido(), extrae telemetría y aviso.
5. Si existe aviso, ReceptorCapturas.notificarCaptura(aviso) inicia CU-09 mediante encolarCaptura.
6. Si existe telemetría, llama Navegador.determinarMovimiento(telemetria) (CU-07). Su retorno llega al comunicador, que continúa CU-08.

Alternativas: una trama inválida se descarta sin captura ni orden; CAPTURA legado ejecuta solamente CU-09. Error de envío cierra esa conexión; una captura ya encolada no se revierte. La orden no es confirmación de persistencia.

## CU-07 — Determinar movimiento

Actor asociado: Orquita a través de CU-06. Precondición: telemetría validada. Postcondición: un Movimiento retornado.

1. Recopilador llama determinarMovimiento(telemetria).
2. Navegador llama ClasificadorEntorno.clasificarEntorno(telemetria).
3. Clasificador normaliza distancias / 4000, ejecuta la red Neuroph y elige la salida mayor (LIBRE/OBJETO). La inferencia se sincroniza porque Neuroph comparte buffers.
4. Navegador obtiene modoActual y llama calcularMovimiento(entorno, modoActual, telemetria).
5. LIBRE produce AVANZAR. OBJETO compara sumas derecha e izquierda: ambas menores a 1200 producen DETENER; de lo contrario gira a la suma mayor, con empate a izquierda.
6. Guarda ultimaDecision y la devuelve; CU-06 continúa CU-08.

Alternativa: clasificación nula (telemetría nula o salida no finita) devuelve mantenerUltimaDecision(); inicialmente DETENER. Una trama inválida se descarta antes de esta etapa. BUSQUEDA y ESCAPE comparten esta regla preliminar; localizar salida queda pendiente.

## CU-08 — Enviar orden de navegación

Actor receptor: Orquita. Precondición: Movimiento retornado y conexión abierta.

1. ComunicadorOrquita.enviarOrdenNavegacion(movimiento) llama codificarOrden(movimiento).
2. Escribe ORDEN y LF en el Writer de esa misma conexión, y hace flush().
3. Orquita recibe e interpreta la orden; en la demostración la imprime.

Alternativa: IOException cierra la conexión. Orquita puede abrir otra y enviar una lectura nueva. No hay un bucle ficticio de reconexión desde el servidor ni retransmisión de una orden vieja.

## CU-09 — Actualizar estómago

Actor originador: Orquita a través de CU-06. Precondición: aviso válido. Postcondición de escritura exitosa: registro único persistido.

1. ReceptorCapturas crea Captura y llama ActualizadorEstomago.encolarCaptura.
2. Se anuncia escritura pendiente en ArchivoEstomago, se encola y notifyAll() despierta al escritor permanente, iniciado por start() en el arranque.
3. En su run(), el escritor espera trabajo dentro de un LOOP y saca una captura.
4. escribirRegistro(captura) adquiere Semaphore(1,true), entra en CRITICAL, verifica el id y escribe UTF-8 si es nuevo.
5. finally libera el semáforo; el escritor llama finalizarEscritura() incluso si hubo error. Vuelve al LOOP.

Alternativas: id duplicado se omite. IOException se registra sin confirmar; se libera el recurso y se sigue con la próxima captura. Una lectura ya iniciada termina; lecturas nuevas esperan escrituras pendientes. El cierre normal drena la cola. Un cierre abrupto puede perder capturas que aún estén en memoria.

## CU-10 — Leer estómago

Actor asociado: Orquita por sus capturas; el auditor empieza en el arranque y no necesita recibir un mensaje para funcionar. Precondición: lector.start() e instancia compartida de ArchivoEstomago.

1. En PAR con el escritor, LectorEstomago.run() repite un LOOP mientras sistemaActivo().
2. leerRegistros() espera mientras haya escrituras pendientes. Adquiere el semáforo compartido y lee en CRITICAL; finally libera el permiso.
3. contarRegistrosValidos(registros) obtiene el número de capturas válidas únicas del archivo.
4. Si cantidad >= 3 y aún no se notificó, crea EventoModoEscape y llama ActivadorModoEscape.notificarCapturasCompletas(evento), CU-11.
5. Marca notificado tras la llamada y espera el próximo ciclo (500 ms en el arranque).

Alternativas: archivo inexistente equivale a lista vacía. Líneas inválidas y duplicados no cuentan. IOException impide usar resultados parciales y se reintenta en el ciclo siguiente. Interrupción de cierre termina el hilo. Reiniciar con tres registros debe activar ESCAPE sin nuevas capturas.

## CU-11 — Activar modo escape

Actor asociado: Orquita por CU-10. Precondición: aviso interno del auditor. Postcondición: Navegador permanece en ESCAPE al alcanzar tres registros.

1. ActivadorModoEscape valida el evento.
2. Si ya está en ESCAPE, no modifica el estado.
3. Si cantidad < 3, mantenerModoBusqueda().
4. En otro caso, cambiarModo(ESCAPE) y Navegador.establecerModoOperativo(modoActual).

Un tipo de evento inválido se rechaza. No se envía ESTADO por TCP. La navegación actual evita obstáculos en ambos modos; todavía no implementa el recorrido hasta la salida.

## Trazabilidad

El archivo de clases incluye los nombres y tipos extraídos de las clases compiladas. Las secuencias representan estas llamadas, sus retornos y los bloques LOOP/PAR/CRITICAL. Los documentos de historico-linareada describen la versión recibida y no deben presentarse como esta versión. CU-12/Safe Mode físico queda fuera de esta demostración.
