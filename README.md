# Orquitas — Hito 2 de demostración

Implementación Java 17 / Maven / NetBeans sobre la Linareada del ZIP recibido.

## Abrir y ejecutar

Abrir esta carpeta (la que contiene pom.xml) como proyecto Maven en NetBeans. Elegir JDK 17 o posterior. Clean and Build ejecuta las pruebas. Ejecutar `orquitas.servidor.ServidorOrquita` y, en otra ejecución, `orquitas.cliente.SimuladorTelemetriaTCP`. El servidor usa puerto 5000, estomago.txt y modelos/entorno-demo.nnet. El modelo incluido ya está entrenado. El servidor **no entrena**: si falta el modelo termina con un mensaje que indica ejecutar `./ejecutar.ps1 entrenar`. Un modelo ilegible o incompatible produce un error: no se sustituye silenciosamente.

En PowerShell, desde esta carpeta, con Java y Maven instalados:

```powershell
mvn clean package dependency:copy-dependencies -DincludeScope=runtime
java -cp "target/classes;target/dependency/*" orquitas.servidor.ServidorOrquita 5000 estomago-prueba.txt
```

En otra terminal:

```powershell
java -cp "target/classes;target/dependency/*" orquitas.cliente.SimuladorTelemetriaTCP localhost 5000 DEMO
```

También se incluyen `preparar.ps1` y `ejecutar.ps1 servidor|simulador|entrenar`. En Linux/macOS cambiar `;` por `:` en el classpath. Maven descarga dependencias en el primer uso; se verificó aquí con las dependencias locales. El JAR por sí solo no contiene Neuroph: usar el classpath indicado.

Servidor: `[puerto] [archivoEstomago] [modelo]`. Simulador: `[host] [puerto] [prefijoCapturas]`. Repetir el mismo prefijo no suma duplicados. Usar un archivo nuevo para demostrar una misión desde cero. Ctrl+C detiene la recepción, espera a los hilos Escritor en curso y detiene el Auditor. El tiempo máximo sin recepción se ajusta con `-Dorquitas.timeoutMaximoMs=...` (5000 por defecto, siempre menor a 10000). No iniciar varios servidores sobre el mismo archivo.

## Qué funciona

- RECOPILACION con 12 distancias en mm: `EnlaceOrquita` la decodifica con `Recopilacion.decodificar`; si es válida, `Recopilacion.procesar` registra la captura opcional (`Estomago.registrarCaptura`) y pide el movimiento a `Navegacion`, y la ORDEN vuelve por el mismo socket. Si no es válida: `ERROR|TRAMA_INVALIDA`.
- MLP 12-6-2 sigmoid con MomentumBackpropagation real; guarda/carga el modelo. Clasifica LIBRE/OBJETO.
- Frente: sectores 4..7. Derecha: 0..5; izquierda: 6..11. LIBRE → AVANZAR. OBJETO → girar hacia la suma mayor; empate a izquierda; ambas sumas menores a 1200 mm → DETENER.
- El mensaje legado `CAPTURA|...` fue eliminado: la captura viaja como id opcional de RECOPILACION.
- Un hilo `Escritor` por captura y un hilo `Auditor` periódico, semáforo justo `Semaphore(1, true)` y prioridad de escrituras pendientes. `estomago.txt` es la única fuente del conteo. Con tres capturas válidas únicas el Auditor notifica a `ModoEscape`, que pasa `Navegacion` a ESCAPE una sola vez.
- CU-12: sin tramas durante `TIMEOUT_MAXIMO` (5 s) se registra la falla y se envía `ESTADO|RECONECTAR` hasta recibir tráfico o cerrar a los 30 s. CU-08: reintento acotado (3 intentos) de una respuesta que no se pudo enviar.

## Pruebas verificadas

`mvn clean verify`: 42 pruebas, cero fallos (13 de Linareada, 9 de Hito 2, 8 de robustez y 12 de la corrección de diseño v2: timeout y ESTADO|RECONECTAR, reintento acotado, duplicados, un Escritor por captura, ModoEscape con 2 y 3 capturas, trama inválida). El test de cierre espera que el cliente esté aceptado; otra prueba repite ese cierre 30 veces. Las pruebas entrenan una vez por ejecución un modelo temporal y verifican inferencia, validación, persistencia del modelo, ambos giros, detención, telemetría inválida y TCP con dos conexiones, fragmentación, agrupación y reconexión.

Modelo entregado: 2400 ejemplos ficticios de entrenamiento, 1000 de validación con otra semilla; **98,00 %** de exactitud sintética. Matriz: LIBRE 486 correctos / 14 errores; OBJETO 494 correctos / 6 errores. Una evaluación posterior con semilla nueva obtuvo 97,80 % (978/1000); en la frontera 300–500 mm obtuvo 90,625 % (3625/4000). La frontera usa un generador distinto. Las semillas de desarrollo no se presentan como evaluación final ciega. No es precisión del sensor ni una garantía ante toda lectura posible.

`evidencias/` incluye la evaluación y la ejecución de servidor + simulador: seis órdenes correctas, tres registros y recuperación de ESCAPE al reiniciar. Los .log contienen datos de simulación.

## Documentación vigente

- Diseño v2 (DCU v3.3, vistas de secuencia v2, diagrama de clases v2): mantenido por el equipo; el código sigue a ese diseño.
- [Protocolo](docs/PROTOCOLO.md): mensajes, separadores, valores válidos, inválidos/incompletos y CU-12.
- [Informe de ejemplos y resultados](docs/Informe_simulacion_Hito2.docx) de la red neuronal.
- `docs/historico/`: DCU, secuencias, clases y diagramas del Hito 2 rechazado (generados desde el código). No usar como versión actual.
- `docs/historico-linareada/` conserva lo recibido de La Linareada.

Clases del módulo servidor: `EnlaceOrquita`, `Recopilacion`, `Telemetria`, `Navegacion`, `RedNeuronal`, `Estomago`, `Captura`, `Escritor`, `Auditor`, `ModoEscape` y los enum `Movimiento`, `ModoOperativo`, `EntornoClasificado`. `ReceptorIngestaTCP`, los simuladores y las clases de `orquitas.simulacion` son de simulación (marcados con `// Archivo de simulacion`).

## Alcance y pendientes reales

Esta es una **demostración preliminar con datos ficticios**, no el robot final ni una entrega docente completa. BUSQUEDA y ESCAPE comparten evitación: ESCAPE no localiza la salida todavía. No distingue pared/lobito/obstáculo/salida. El único mensaje de estado implementado es `ESTADO|RECONECTAR` (CU-12). Bluetooth, actuadores y Safe Mode físico quedan pendientes. La detección de pérdida de comunicación solo envía la señal de reconexión y cierra la conexión; no equivale a detener motores.

La consigna de La Orquita Pensante exige ejemplos de sensor real y éxito en campo: faltan esas mediciones. Los 12 sectores representan una convención simulada de 180°, centros 7,5°+15°i; no afirman que un sensor real tenga esa geometría. Antes de conectar hardware hay que calibrar cobertura, rango y errores. La red aprende del generador con ruido ±5 % y no fue validada con hardware. No se presenta la simulación como evidencia de campo.

La publicación del código en GitHub no reemplaza la entrega docente. No se publicaron documentos en Google Docs. La entrega docente requiere sus formatos y revisión del equipo; para Java plano hay una carpeta `entrega-java/` con los fuentes de ejecución.

## Robustez y reproducción

Las distancias admiten dígitos y punto decimal, sin signos, exponentes, hexadecimales ni espacios. Una trama completa inválida o excesiva recibe `ERROR|TRAMA_INVALIDA`; no produce captura ni orden. EOF sin LF se descarta. Leer también ERROR en los clientes evita dejar respuestas pendientes antes de cerrar el socket. El simulador interrumpe su ejecución ante un error; no mueve motores.

El entrenamiento conserva 12-6-2 y añade casos de frontera. Usa 4000 iteraciones máximas y error objetivo 0.003; esta ejecución alcanzó el límite de iteraciones con error 0.005529142110638303, no el error objetivo. EvaluadorDemostracion exporta predicciones de validación, frontera y ejemplos a evidencias/predicciones.csv. Los resultados se pueden recalcular desde ese CSV. La red aproxima una regla sintética; no se afirma que mejore a una regla determinista ni que identifique objetos reales.

GitHub Actions verifica Linux/JDK 21 y Windows/JDK 17; el estado de cada ejecución está en el pull request. No confundir una configuración de CI con un resultado aprobado.
