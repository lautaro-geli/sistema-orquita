# Revisión y justificación de La Linareada

Revisión del 30 de septiembre de 2026 sobre la carpeta nueva del escritorio, no sobre la versión anterior de Descargas.

## Resultado y alcance

La versión corregida implementa y documenta ingesta de capturas por TCP, escritura en un hilo independiente, auditoría periódica y transición interna a ESCAPE. La compilación limpia para Java 17 terminó con BUILD SUCCESS. Se ejecutaron **13 pruebas: 0 fallas, 0 errores y 0 omitidas**.

También ejecuté ServidorOrquita.main y SimuladorIngestaTCP.main: se persistieron 001, 002 y 003 y se activó ESCAPE. Al reiniciar el servidor con el mismo archivo, activó ESCAPE sin recibir nuevas capturas. Los logs y el archivo están en `evidencias`. La demostración usó un archivo nuevo dentro de target; no alteró un estómago existente del usuario.

Esto cubre **La Linareada**, según el apartado 6 del contexto adjunto. **No equivale a completar el Hito 2 general**: faltan demo Neuroph, protocolo de telemetría/órdenes/estado por red, Bluetooth, navegación y Safe Mode físico. Los documentos anteriores mostraban capacidades previstas que no existían en los 13 Java recibidos. El código corregido tiene 14 archivos de ejecución y una clase de pruebas.

## Qué encontré y por qué lo corregí

| Observación | Cambio | Justificación |
|---|---|---|
| La versión nueva ya tenía ActualizadorEstomago con run(), pero creaba un hilo por captura. | Un escritor permanente, una cola y despertar con notifyAll(). | Da un proceso escritor identificable y evita crear hilos sin límite por aviso. No sería correcto afirmar que esta versión nueva no tenía run(). |
| El semáforo original impedía simultaneidad, pero no reservaba prioridad desde el aviso. | anunciarEscritura antes del encolado; nuevas lecturas esperan mientras haya pendientes. | Implementa el freno a la lectura sin suspenderla a mitad de una operación. La lectura en curso termina; la siguiente espera. |
| Secuencia llamaba activar(captura), inexistente en el código. | encolarCaptura(captura), run(), escribirRegistro(captura) y finalizarEscritura(). | Las operaciones corresponden a métodos reales; los ref y acciones de Java se distinguen. |
| Secuencia llamaba notificarCapturasCompletas() sin argumento. | notificarCapturasCompletas(evento) en toda la documentación. | Mantiene el contrato con tipo, cantidad y fecha que recibe el código. |
| Recopilador estaba dibujado, pero TCP lo salteaba. | Se agregó Recopilador como validador real de la trama. | Evita una clase sólo dibujada y separa transporte de interpretación. En esta carpeta no existía RecopilacionService para renombrar. |
| ListenerIngestaTCP no seguía el criterio de nombres pedido. | ReceptorIngestaTCP en código y diagramas. | Describe en español su responsabilidad. |
| PrintWriter podía ocultar errores durante la escritura. | BufferedWriter UTF-8, IOException propagada y confirmación después del cierre. | No registrar éxito sólo porque el archivo se pudo abrir. |
| Un error al leer podía devolver una lista parcial. | Propagar IOException; el auditor omite decisión y reintenta. | Evita decidir a partir de datos incompletos. |
| Un reenvío podía sumar otro Lobito. | Id único persistido y filtrado de duplicados al leer. | Una retransmisión no es una nueva captura física. |
| El DCU confundía actor, disparador periódico y ejecutores. | Orquita como actor; clases del servidor como ejecutores internos. | El robot origina capturas, pero no escribe el archivo de la PC. No se reemplazó “Servidor” en todas las actividades. |
| Documentos mezclaban el sistema futuro con el hito implementado. | Recorte CU-06 capturas, CU-09, CU-10 y CU-11. | La consigna exige sólo el alcance del hito. Los originales completos se conservan como referencia. |
| Marca de simulación incompleta. | Primera línea // Archivo de simulacion en cliente, adaptador TCP, arranque y pruebas. | Distingue archivos temporales del hito de las clases reutilizables. |

## Dónde demostrarlo al profesor

1. **Arranque de ambos hilos:** [ServidorOrquita.java, línea 27](<../proyecto-orquitas-linareada-corregido/src/main/java/orquitas/servidor/ServidorOrquita.java>). Comparten ArchivoEstomago. start() inicia cada hilo y la JVM invoca run().
2. **Recopilador real:** [Recopilador.java, línea 13](<../proyecto-orquitas-linareada-corregido/src/main/java/orquitas/servidor/captura/Recopilador.java>). Valida el mensaje y llama a ReceptorCapturas.
3. **Aviso al escritor:** [ReceptorCapturas.java, línea 13](<../proyecto-orquitas-linareada-corregido/src/main/java/orquitas/servidor/captura/ReceptorCapturas.java>) y [ActualizadorEstomago.java, línea 19](<../proyecto-orquitas-linareada-corregido/src/main/java/orquitas/servidor/persistencia/ActualizadorEstomago.java>). Anuncia trabajo antes de despertarlo.
4. **Hilo escritor:** [ActualizadorEstomago.java, línea 30](<../proyecto-orquitas-linareada-corregido/src/main/java/orquitas/servidor/persistencia/ActualizadorEstomago.java>). Espera, extrae una captura y ejecuta escribirRegistro; no es el hilo TCP.
5. **CRITICAL de escritura:** [ArchivoEstomago.java, línea 42](<../proyecto-orquitas-linareada-corregido/src/main/java/orquitas/servidor/persistencia/ArchivoEstomago.java>). acquire, archivo y release en finally.
6. **Freno a nuevas lecturas:** [ArchivoEstomago.java, línea 61](<../proyecto-orquitas-linareada-corregido/src/main/java/orquitas/servidor/persistencia/ArchivoEstomago.java>). wait mientras haya pendientes; luego toma el mismo semáforo.
7. **LOOP del auditor:** [LectorEstomago.java, línea 29](<../proyecto-orquitas-linareada-corregido/src/main/java/orquitas/servidor/persistencia/LectorEstomago.java>). while(sistemaActivo()), lectura, conteo y pausa.
8. **Condición de escape:** [LectorEstomago.java, línea 34](<../proyecto-orquitas-linareada-corregido/src/main/java/orquitas/servidor/persistencia/LectorEstomago.java>). La cantidad es un resultado local de la lectura, no un contador acumulado en RAM.
9. **CU-11:** [ActivadorModoEscape.java, línea 27](<../proyecto-orquitas-linareada-corregido/src/main/java/orquitas/servidor/escape/ActivadorModoEscape.java>). Valida EventoModoEscape y cambia Navegador a ESCAPE.

El diagrama de clases se contrastó con javap -private sobre las clases compiladas. `firmas-verificadas.json` conserva el inventario. Se muestran miembros públicos y privados, incluidos constructores implícitos; se omiten miembros sintéticos de Java y se abrevian paquetes. Los campos static conservan ese modificador en el código.

## Semáforo y contador pendiente

Semaphore(1, true) deja entrar a un hilo por vez. escriturasPendientes indica solicitudes en espera, incluidas posibles retransmisiones; no cuenta Lobitos. La cantidad biológica se reconstruye desde leerDelArchivo y contarRegistrosValidos.

Orquita envía → Recopilador valida → ReceptorCapturas encola → escritor persiste → auditor lee → ActivadorModoEscape cambia el modo. PAR significa que los hilos concurren; CRITICAL impide que lean y escriban el archivo simultáneamente.

## Límites que hay que explicar

- La misión pide tres Lobitos. >= 3 evita perder la transición cuando se acumulan registros entre auditorías. Este hito no controla motores ni impide físicamente una cuarta presa.
- TCP sólo recibe capturas. EventoModoEscape es un mensaje interno entre objetos, no una transmisión por Bluetooth o TCP.
- No hay ACK TCP ni reintento automático de disco. Los errores quedan en consola; se puede reenviar el mismo id. Al cerrar normalmente se drena la cola; un cierre abrupto puede perder avisos no persistidos.
- La exclusión se verificó con una instancia de ArchivoEstomago compartida en una JVM. No iniciar varios servidores sobre el mismo archivo.
- El DOCX está preparado para importar en Google Docs; no se subió a una cuenta ni se realizó el envío del SCRUM Master.

## Archivos a usar

- Orquitas_Casos_de_Uso_Linareada.docx: DCU corregido de siete páginas, revisado visualmente después de renderizarlo.
- diagramas/Orquitas_Linareada.drawio: siete páginas editables, sólo servidor: CU, cuatro secuencias y dos vistas de clases.
- diagramas/*.png: las mismas vistas, con fondo blanco.
- java/*.java: 14 archivos de ejecución sin árbol de carpetas. Conservan sus declaraciones package.
- Orquitas_Vistas_Secuencia_Modulo_Servidor.md: detalle de flujos y alternativas.
- Orquitas_Diagrama_Clases_Modulo_Servidor.md: inventario de clases y métodos.
- evidencias: JUnit y logs de servidor, simulador y reinicio.

El proyecto NetBeans está en la carpeta vecina proyecto-orquitas-linareada-corregido. Este paquete incluye únicamente la versión corregida; los respaldos anteriores permanecen fuera del paquete.
