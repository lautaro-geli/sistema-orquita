# Continuación de la revisión — 30/09/2026

Esta versión completa las correcciones de robustez y los documentos de resultados de la simulación. No equivale a una validación con el robot físico.

## Qué cambió y por qué

| Cambio | Justificación | Dónde comprobarlo |
|---|---|---|
| La prueba de cierre espera un cliente aceptado y registrado | `Socket.connect()` puede terminar antes de `accept()`. Detener entonces no prueba el cierre de un cliente que ya atiende el servidor. Se espera una condición con límite, en lugar de dormir 300 ms. | `LinareadaTest.detenerCierraClientesQueNoEnvianNada`, `ReceptorIngestaTCP.obtenerCantidadClientes` |
| Se repite ese cierre 30 veces | Ejercita la coordinación corregida; no demuestra que todo problema de concurrencia sea imposible. | `RobustezHitoDosTest.cierreDeClientesSilenciososRepetidoSinEsperaFija` |
| Distancias con decimal estricto | El protocolo describe dígitos y punto; `Double.parseDouble` solo también admitía exponentes y hexadecimal. Se valida la sintaxis antes del rango 0–4000. | `PaqueteRecopilacion.decodificar` |
| Respuesta `ERROR\|TRAMA_INVALIDA` | Una trama completa rechazada ya no deja al cliente esperando una orden que nunca llegará. No se registra su captura; una lectura posterior válida puede usar la misma conexión. | `ComunicadorOrquita.enviarErrorTrama`, `ReceptorIngestaTCP.manejarCliente`, `docs/PROTOCOLO.md` |
| Entrenamiento con ejemplos próximos a 400 mm | Un buen porcentaje en ejemplos fáciles ocultaba errores cerca del límite. Se agregaron casos de frontera y se informa su resultado por separado. | `EntrenadorDemostracion`, `EvaluadorDemostracion`, `evidencias/predicciones.csv` |
| Diagramas y DCU actualizados | El flujo de error y las firmas nuevas deben aparecer también en la documentación vigente. | `docs/diagramas/`, `docs/firmas-verificadas.json` |
| Dos documentos Word | Entregar el DCU y un informe con ejemplos, cálculo de porcentajes, errores y limitaciones. Se renderizaron y revisaron sus siete páginas. | `DCU_Hito2.docx`, `Informe_simulacion_Hito2.docx` |
| CI Linux/JDK 21 y Windows/JDK 17 | Verificar también el entorno donde se informó la intermitencia y la versión mínima del proyecto. El resultado efectivo se consulta en el PR. | `.github/workflows/pruebas.yml` |

Se corrigieron además textos con codificación dañada. Se conserva Neuroph 2.98, la versión declarada por este proyecto.

## Resultados reproducidos

- Maven: 30 pruebas, 0 fallos, 0 errores, 0 omitidas. Trece de Linareada, nueve de Hito 2 y ocho de robustez. Ejecución local con JDK 25, compilación para Java 17.
- Seis clientes concurrentes: 240 respuestas comprobadas y 24 capturas únicas persistidas.
- Programa completo: seis órdenes esperadas, tres capturas persistidas y recuperación de ESCAPE al reiniciar sin enviar capturas nuevas. `evidencias/integracion.json` identifica el modelo utilizado.
- Modelo guardado: 978/1000 aciertos (97,80 %) en evaluación posterior con semilla 424242. En la franja 300–500 mm: 3625/4000 (90,625 %), semilla 987654. Hubo 86 objetos clasificados como libres y 289 libres clasificados como objetos en esa franja.
- El entrenamiento terminó en 4000 iteraciones, error 0,00552914. No alcanzó el objetivo 0,003. Las semillas usadas para ajustar el modelo se distinguen de las evaluadas después.

Los porcentajes son de clasificación de datos ficticios; no son éxito de captura ni de navegación en campo. La evaluación posterior usa la misma familia general de simulación; tampoco sustituye ejemplos de un sensor real.

## Dónde están las pruebas

Las pruebas automáticas están separadas del código ejecutable, en `src/test/java/orquitas/verificacion/`: `LinareadaTest.java`, `HitoDosTest.java` y `RobustezHitoDosTest.java`. Ejecutar `mvn test` desde la carpeta que contiene `pom.xml`. Maven deja los resultados de cada ejecución en `target/surefire-reports/`; `evidencias/` conserva una copia de la ejecución documentada.

El programa está en `src/main/java/`. `entrega-java/` contiene copias de esos fuentes para el formato de entrega plano, no una segunda implementación para editar por separado. Los archivos propios de la simulación llevan la marca solicitada en la primera línea.

## Pendientes fuera de esta simulación

Faltan mediciones de sensor real y pruebas de campo. ESCAPE cambia el modo y conserva la evitación, pero todavía no calcula una ruta hasta la salida. No hay identificación de pared/lobito/salida, integración Bluetooth, actuadores ni Safe Mode físico. Tampoco se añadió ESTADO, según el alcance previamente elegido. Los documentos Word están listos para importar; no se publicaron en Google Docs ni se realizó la entrega docente.
