# Decisiones por defecto de la Fase 2 (Hito 3)

Valores elegidos para no frenar el desarrollo. Los marcados **[A CONFIRMAR]** hay que verificarlos con el robot real o con el equipo antes de la prueba de campo.

## Robot (sketch `arduino/Orquita/Orquita.ino`)

- **[A CONFIRMAR] Sentido de los ángulos:** 0° = derecha del robot, 90° = frente, 180° = izquierda. Debe coincidir con la convención del Servidor (sector 0 = derecha, 6 = frente, 12 = izquierda). Si el servo de barrido está montado al revés, invertir el ángulo en `barrer()`.
- **[A CONFIRMAR] Sentido de los giros:** `GIRAR_IZQ` = motor izquierdo atrás y derecho adelante; `GIRAR_DER`, lo contrario. Depende del cableado del L298N.
- **[A CONFIRMAR] Colector (SG90 de rotación continua):** gira a velocidad constante mientras no esté en Safe Mode y se detiene en Safe Mode. `COLECTOR_DETENIDO` = 307 (≈1,5 ms) puede necesitar ajuste fino si el servo se arrastra.
- **[A CONFIRMAR] Servo de elevación:** bajado (0°) durante el barrido, levantado (180°) durante el paso de movimiento, y vuelve a bajar antes del siguiente barrido.
- **[A CONFIRMAR] Calibración:**
  - pulsos del SG90: `PULSO_SERVO_0` = 110 y `PULSO_SERVO_180` = 500 (de 4096 a 50 Hz);
  - `VELOCIDAD_MOTORES` = 180;
  - `PASO_AVANZAR_MS` = 400 y `PASO_GIRO_MS` = 300;
  - `ESPERA_SERVO_MS` = 40 entre posiciones de 15° y 300 ms para volver a 0°;
  - `ESPERA_ELEVACION_MS` = 250.
- **[A CONFIRMAR] Pines preliminares:**
  - HC-06: RX = 10 y TX = 11 (con divisor de tensión);
  - L298N: ENA = 5, ENB = 6, IN1 = 7, IN2 = 8, IN3 = 12, IN4 = 13;
  - FC-51: pin 2, activo en bajo.
- **VL53L1X:** modo Long, presupuesto de 50 ms por medición, ROI 4x4/8x8/16x16 centrado en 199, `readSingle()` (requiere la librería de Pololu ≥ 1.3.0). Una lectura con timeout, estado inválido o mayor a 4000 mm se envía como `-1` (el Servidor rechaza valores mayores a 4000).
- **Capturas:** antirrebote de 20 ms y bloqueo de 1500 ms. Ids consecutivos desde 1; una segunda captura antes de la confirmación no se pierde (contadores de generadas y confirmadas).
- **Tiempos de espera:** CONFIRMACION de una captura, hasta 1 s; ORDEN, hasta 2 s. Sin orden o con RECHAZO, el robot queda detenido y repite el ciclo.
- **Depuración:** por USB a 115200 baudios con `#define DEPURAR`, apagada por defecto.

## Servidor

- **[A CONFIRMAR] `RedNeuronal.ALCANCE_CERCANIA_MM` = 1500:** alcance de la cercanía que ve la red. Ajustar con los datos reales.
- **[A CONFIRMAR] `Navegacion.ALCANCE_LIBRE_MM` = 1500:** si en ESCAPE ningún sector ve nada más cerca, la Orquita se considera fuera de la bahía (LIBERADA). Ajustar en la pista: si afuera hay paredes o público a menos de 1,5 m, nunca se liberaría.
- **[A CONFIRMAR] Geometría:** las decisiones usan el ROI 16x16 (`distanciaSector`). Derecha = sectores 0–4, izquierda = 8–12, frente = 5–7. Encerrada (BÚSQUEDA) = ambos lados suman menos de 1200 mm × 5 sectores.
- **Umbral de capturas:** `--umbral=1` por defecto (prueba de campo del Hito 3); el reglamento de la competencia usa 3.
- **Estados por consola:**
  - La transición a `CAPTURA` la imprime `EnlaceOrquita` con el texto fijo `APROXIMACION -> CAPTURA`.
  - La siguiente transición la informa `Navegacion` desde su último estado conocido (BUSQUEDA o APROXIMACION), porque `Navegacion` no se entera de la captura sin un método extra fuera del diagrama.
  - La cantidad de capturas la imprime el `Auditor` (`n/umbral`) cada vez que cambia, justo antes del paso a ESCAPE.
- **[A CONFIRMAR] Orden de encendido:** primero el robot, después el Servidor. Al abrir el COM, Windows conecta con el HC-06. El Servidor da 8 s de margen desde que arranca hasta recibir el primer dato del robot.
- **Entrega a ClassRoom:**
  - `entrega-java/` lleva los `.java` del Servidor (`orquitas.servidor.*`) y de las herramientas de datos (`orquitas.herramientas.*`), más `Orquita.ino`.
  - Quedan afuera la simulación (`orquitas.simulacion`) y las pruebas.
  - **[A CONFIRMAR]** si el docente quiere también las herramientas.
- **Modelo:** `modelos/entorno.nnet` es **provisional** (datos sintéticos) hasta entrenar con barridos reales.
