# Orquitas — Hito 2 de demostración

Implementación Java 17 / Maven / NetBeans sobre la Linareada del ZIP recibido.

## Abrir y ejecutar

Abrir esta carpeta (la que contiene pom.xml) como proyecto Maven en NetBeans. Elegir JDK 17 o posterior. Clean and Build ejecuta las pruebas. Ejecutar `orquitas.servidor.ServidorOrquita` y, en otra ejecución, `orquitas.cliente.ClienteTelemetriaTCP`. El servidor usa puerto 5000, estomago.txt y modelos/entorno-demo.nnet. El modelo incluido ya está entrenado. El servidor **no entrena**: si falta el modelo termina con un mensaje que indica ejecutar `./ejecutar.ps1 entrenar`. Un modelo ilegible o incompatible produce un error: no se sustituye silenciosamente.

En PowerShell, desde esta carpeta, con Java y Maven instalados:

```powershell
mvn clean package dependency:copy-dependencies -DincludeScope=runtime
java -cp "target/classes;target/dependency/*" orquitas.servidor.ServidorOrquita 5000 estomago-prueba.txt
```

En otra terminal:

```powershell
java -cp "target/classes;target/dependency/*" orquitas.cliente.ClienteTelemetriaTCP localhost 5000 DEMO
```

También se incluyen `preparar.ps1` y `ejecutar.ps1 servidor|simulador|entrenar`. En Linux/macOS cambiar `;` por `:` en el classpath. Maven descarga dependencias en el primer uso; se verificó aquí con las dependencias locales. El JAR por sí solo no contiene Neuroph: usar el classpath indicado.

Servidor: `[puerto] [archivoEstomago] [modelo]`. Cliente de telemetría: `[host] [puerto] [prefijoCapturas]`. Repetir el mismo prefijo no suma duplicados dentro de una misma ejecución del servidor. Cada arranque del servidor vacía `estomago.txt` (misión nueva); durante la ejecución el archivo es la única fuente del conteo. Ctrl+C detiene la recepción, espera a los hilos Escritor en curso y detiene el Auditor. El tiempo máximo sin recepción se ajusta con `-Dorquitas.timeoutMaximoMs=...` (5000 por defecto, siempre menor a 10000). No iniciar varios servidores sobre el mismo archivo.

## Qué funciona

- RECOPILACION con 12 distancias en mm: `EnlaceOrquita` la decodifica con `Recopilacion.decodificar`; si es válida, `Recopilacion.procesar` registra la captura opcional (`Estomago.registrarCaptura`) y pide el movimiento a `Navegacion`, y la ORDEN vuelve por el mismo socket. Si no es válida: `ERROR|TRAMA_INVALIDA`.
- MLP 12-6-3 sigmoid con MomentumBackpropagation real; guarda/carga el modelo. Clasifica en 3 clases según el **ancho de la banda cercana** que ve el VL53L1X (ToF, solo distancia, haz angosto): **LIBRE** (nada cerca), **LOBITO** (un solo sector cercano: la presa de 17 mm subtiende menos de 15°) y **OBJETO** (un bloque contiguo de sectores cercanos: obstáculo de ~90 mm o muro). La clasificación es interna; no viaja en el protocolo. Si la salida ganadora no alcanza el **50 %** (`RedNeuronal.UMBRAL_CONFIANZA`) el entorno queda **sin reconocer** y `Navegacion` conserva la última decisión.
- Entrada de la red: cercanía de cada sector, clamp((1500 − d)/1500, 0, 1), con `Telemetria.ALCANCE_CERCANIA_MM` = 1500 para alinearlo luego al rango real del sensor. La navegación decide con las distancias crudas en mm.
- Sectores: derecha 0..5 e izquierda 6..11 para las sumas laterales; para orientar hacia el sector más cercano, 0..3 derecha, 4..7 frente, 8..11 izquierda.
- La red clasifica en los dos modos; el modo cambia la meta. La captura la sigue reportando el robot (`idOpcional`); la red no captura ni escribe en el estómago.
- BUSQUEDA: LOBITO → lo persigue hacia su sector (frente AVANZAR, derecha GIRAR_DER, izquierda GIRAR_IZQ); OBJETO → gira hacia la suma lateral mayor (empate a izquierda) y solo DETENER si está encerrada (ambos lados < 1200 mm, 6 × 200 mm); LIBRE → AVANZAR.
- ESCAPE (ya tiene las 3 capturas): LOBITO se ignora y cuenta como LIBRE → AVANZAR; OBJETO → gira hacia lo más abierto, nunca DETENER.
- El mensaje legado `CAPTURA|...` fue eliminado: la captura viaja como id opcional de RECOPILACION.
- Un hilo `Escritor` por captura y un hilo `Auditor` periódico, semáforo justo `Semaphore(1, true)` compartido por lecturas y escrituras, sin prioridad extra al escritor: el Auditor nunca queda postergado y, si lee entre dos escrituras, vuelve a contar en el ciclo siguiente. `estomago.txt` es la única fuente del conteo. Con tres capturas válidas únicas (`Auditor.UMBRAL_CAPTURAS`) el Auditor llama a `Navegacion.activarModoEscape()`, que pasa a ESCAPE una sola vez; la Orquita lo recibe como `ESTADO|ESCAPE` antes de la siguiente orden.
- CU-12: sin tramas durante `TIMEOUT_MAXIMO` (5 s) se registra la falla y se envía `ESTADO|RECONECTAR` hasta recibir tráfico o cerrar a los 30 s. CU-08: reintento acotado (3 intentos) de una respuesta que no se pudo enviar.

## Pruebas verificadas

`mvn clean verify`: 52 pruebas, cero fallos (13 de Linareada, 9 de Hito 2, 8 de robustez y 22 de la corrección de diseño: timeout y ESTADO|RECONECTAR, reintento acotado, duplicados, un Escritor por captura, activación idempotente del Modo Escape, reinicio del estómago, umbral de confianza de la red, trama inválida, ESTADO|ESCAPE/BUSQUEDA, persecución del lobito y navegación por modo, auditor sin postergación). El test de cierre espera que el cliente esté aceptado; otra prueba repite ese cierre 30 veces. Las pruebas entrenan una vez por ejecución un modelo temporal y verifican inferencia de 3 clases, fronteras de distancia y de ancho, validación, persistencia del modelo, ambos giros, detención, telemetría inválida y TCP con dos conexiones, fragmentación, agrupación y reconexión.

Modelo entregado: 2400 ejemplos ficticios de entrenamiento (clases balanceadas), 1000 de validación con otra semilla; **98,80 %** de exactitud sintética (LIBRE 328/334, OBJETO 331/333, LOBITO 329/333; 2 lecturas de LOBITO quedaron sin reconocer por estar bajo el umbral de confianza del 50 %). Con otras 11 semillas: 97,4–98,6 %. Frontera de distancia (un sector entre 600–900 o 1200–1500 mm, otro generador): 96,3–96,5 %. Frontera de ancho (1 contra 2 sectores contiguos, la diferencia más fina): 95,1–95,3 %. Un sector aislado pasa a LOBITO entre unos 775 y 925 mm. Las semillas de desarrollo no se presentan como evaluación final ciega. No es precisión del sensor ni una garantía ante toda lectura posible.

`evidencias/` incluye la evaluación y la ejecución de servidor + cliente simulado: nueve órdenes, tres registros, `ESTADO|ESCAPE` recibido por el cliente y vaciado de `estomago.txt` y regreso a BUSQUEDA al reiniciar el servidor. En BUSQUEDA persigue al lobito (GIRAR_DER hacia la derecha, AVANZAR al frente), esquiva la pared y el obstáculo y se detiene encerrada; en ESCAPE avanza ignorando un lobito a la izquierda y esquiva el obstáculo sin frenar. Los .log contienen datos de simulación.

## Documentación vigente

- Diseño v3 (DCU v3.5, vistas de secuencia v3, diagrama de clases v3): mantenido por el equipo; el código sigue a ese diseño.
- [Protocolo](docs/PROTOCOLO.md): mensajes, separadores, valores válidos, inválidos/incompletos y CU-12.
- [Informe de ejemplos y resultados](docs/Informe_simulacion_Hito2.docx) de la red neuronal.
- `docs/historico/`: DCU, secuencias, clases y diagramas del Hito 2 rechazado (generados desde el código). No usar como versión actual.
- `docs/historico-linareada/` conserva lo recibido de La Linareada.

Clases del módulo servidor: `EnlaceOrquita`, `Recopilacion`, `Telemetria`, `Navegacion`, `RedNeuronal`, `Estomago`, `Captura`, `Escritor`, `Auditor` y los enum `Movimiento`, `ModoOperativo`, `EntornoClasificado`. `CanalIngestaTCP`, los simuladores y las clases de `orquitas.simulacion` son de simulación (marcados con `// Archivo de simulacion`).

## Alcance y pendientes reales

Esta es una **demostración preliminar con datos ficticios**, no el robot final ni una entrega docente completa. ESCAPE cambia la política de navegación (esquivar y seguir) pero no localiza la salida de la bahía: no hay sensor de posición ni rumbo en este hito. La red distingue lobito de objeto por el ancho; no distingue pared de obstáculo ni detecta la salida. La distinción por ancho es confiable a rango corto/medio (donde importa para cazar); muy lejos, todo tiende a LIBRE. No se usa la señal de intensidad del sensor. Mensajes de estado implementados: `ESTADO|ESCAPE` / `ESTADO|BUSQUEDA` (cambio de modo, enviado con la siguiente RECOPILACION) y `ESTADO|RECONECTAR` (CU-12). Bluetooth, actuadores y Safe Mode físico quedan pendientes. La detección de pérdida de comunicación solo envía la señal de reconexión y cierra la conexión; no equivale a detener motores.

La consigna de La Orquita Pensante exige ejemplos de sensor real y éxito en campo: faltan esas mediciones. Los 12 sectores representan una convención simulada de 180°, centros 7,5°+15°i; no afirman que un sensor real tenga esa geometría. Antes de conectar hardware hay que calibrar cobertura, rango y errores. La red aprende del generador con ruido de ±10 mm y no fue validada con hardware. No se presenta la simulación como evidencia de campo.

La publicación del código en GitHub no reemplaza la entrega docente. No se publicaron documentos en Google Docs. La entrega docente requiere sus formatos y revisión del equipo; para Java plano hay una carpeta `entrega-java/` con los fuentes de ejecución.

## Robustez y reproducción

Las distancias admiten dígitos y punto decimal, sin signos, exponentes, hexadecimales ni espacios. Una trama completa inválida o excesiva recibe `ERROR|TRAMA_INVALIDA`; no produce captura ni orden. EOF sin LF se descarta. Leer también ERROR en los clientes evita dejar respuestas pendientes antes de cerrar el socket. El simulador interrumpe su ejecución ante un error; no mueve motores.

El entrenamiento usa 12-6-3, 4000 iteraciones máximas y error objetivo 0.003; esta ejecución alcanzó el límite de iteraciones con error 0.008098925265313278, no el error objetivo. Regla de generación y semillas en `modelos/README.md`. EvaluacionDemostracion exporta predicciones de validación, fronteras de distancia y de ancho, ejemplos y un barrido de un sector aislado a evidencias/predicciones.csv; `evidencias/metricas-modelo.json` resume cada grupo con su matriz 3×3. Los resultados se recalculan desde ese CSV con `herramientas/calcular_metricas.py`; los ejemplos de entrenamiento están en `evidencias/datos-entrenamiento.csv`. La red aproxima una regla sintética; no se afirma que mejore a una regla determinista ni que identifique objetos reales.

GitHub Actions verifica Linux/JDK 21 y Windows/JDK 17; el estado de cada ejecución está en el pull request. No confundir una configuración de CI con un resultado aprobado.
