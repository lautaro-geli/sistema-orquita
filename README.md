# Orquitas — Hito 2 de demostración

Implementación Java 17 / Maven / NetBeans sobre la Linareada del ZIP recibido.

## Abrir y ejecutar

Abrir esta carpeta (la que contiene pom.xml) como proyecto Maven en NetBeans. Elegir JDK 17 o posterior. Clean and Build ejecuta las pruebas. Ejecutar `orquitas.servidor.ServidorOrquita` y, en otra ejecución, `orquitas.cliente.SimuladorTelemetriaTCP`. El servidor usa puerto 5000, estomago.txt y modelos/entorno-demo.nnet. El modelo incluido ya está entrenado; si falta, el arranque entrena uno ficticio. Un modelo ilegible o incompatible produce un error: no se sustituye silenciosamente.

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

Servidor: `[puerto] [archivoEstomago] [modelo]`. Simulador: `[host] [puerto] [prefijoCapturas]`. Repetir el mismo prefijo no suma duplicados. Usar un archivo nuevo para demostrar una misión desde cero. Ctrl+C detiene recepción, drena el escritor y detiene el auditor. No iniciar varios servidores sobre el mismo archivo.

## Qué funciona

- RECOPILACION con 12 distancias en mm: validar todo, encolar captura opcional, inferir con Neuroph, decidir y devolver ORDEN por el mismo socket.
- MLP 12-6-2 sigmoid con MomentumBackpropagation real; guarda/carga el modelo. Clasifica LIBRE/OBJETO.
- Frente: sectores 4..7. Derecha: 0..5; izquierda: 6..11. LIBRE → AVANZAR. OBJETO → girar hacia la suma mayor; empate a izquierda; ambas sumas menores a 1200 mm → DETENER.
- CAPTURA legado sigue funcionando sin respuesta de navegación.
- Escritor y lector independientes, semáforo y prioridad de escrituras pendientes. Estómago como única fuente del conteo. Tres capturas válidas únicas activan ESCAPE.

## Pruebas verificadas

`mvn test`: 22 pruebas, cero fallos (13 originales sin modificar y 9 nuevas). Las nuevas vuelven a entrenar un modelo temporal y verifican inferencia, validación, persistencia del modelo, ambos giros, detención, telemetría inválida y TCP con dos conexiones, fragmentación, agrupación y reconexión.

Modelo entregado: 2400 ejemplos ficticios de entrenamiento, 1000 de validación con otra semilla; **98,50 %** de exactitud sintética. Matriz: LIBRE 498 correctos / 2 errores; OBJETO 487 correctos / 13 errores. No es precisión del sensor ni una garantía ante toda lectura posible.

`evidencias/` incluye la evaluación y la ejecución de servidor + simulador: seis órdenes correctas, tres registros y recuperación de ESCAPE al reiniciar. Los .log contienen datos de simulación.

## Documentación vigente

- [DCU](docs/DCU_Hito2.md): CU-06 a CU-11.
- [Secuencias](docs/Orquitas_Vistas_Secuencia_Modulo_Servidor.md).
- [Clases](docs/Orquitas_Diagrama_Clases_Modulo_Servidor.md): firmas extraídas del bytecode compilado.
- `docs/diagramas/`: PNG blancos y draw.io editable; abrir Orquitas_Hito2.drawio.
- [Protocolo](docs/PROTOCOLO.md).
- `docs/historico-linareada/` conserva lo recibido: no usar sus diagramas como versión actual.

Cambios justificados: `procesarRecopilacion(PaqueteRecopilacion)` y `determinarMovimiento(Telemetria)` devuelven Movimiento. Así ComunicadorOrquita conserva el destino de cada respuesta sin guardar un socket global. `calcularMovimiento` recibe también Telemetria porque necesita las sumas laterales. Se mantiene el escritor permanente probado; la secuencia representa encolar y despertar ese hilo. Las firmas y llamadas nuevas figuran en los diagramas vigentes.

## Alcance y pendientes reales

Esta es una **demostración preliminar con datos ficticios**, no el robot final ni una entrega docente completa. BUSQUEDA y ESCAPE comparten evitación: ESCAPE no localiza la salida todavía. No distingue pared/lobito/obstáculo/salida. No se agregó ESTADO por la decisión del usuario en el contexto recibido; la consigna general pide comunicación de estados, pendiente. Bluetooth, actuadores y Safe Mode físico quedan pendientes. El timeout TCP solo libera una conexión silenciosa; no equivale a detener motores.

La consigna de La Orquita Pensante exige ejemplos de sensor real y éxito en campo: faltan esas mediciones. Los 12 sectores representan una convención simulada de 180°, centros 7,5°+15°i; no afirman que un sensor real tenga esa geometría. Antes de conectar hardware hay que calibrar cobertura, rango y errores. La red aprende del generador con ruido ±5 % y no fue validada con hardware. No se presenta la simulación como evidencia de campo.

La publicación del código en GitHub no reemplaza la entrega docente. No se publicaron documentos en Google Docs. La entrega docente requiere sus formatos y revisión del equipo; para Java plano hay una carpeta `entrega-java/` con los fuentes de ejecución.
