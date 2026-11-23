# Traspaso del Proyecto Orquitas (23/11/2026)

Documento para quien sigue el proyecto. Reúne el contexto, cómo está armado todo, qué no tocar, qué se probó y descartó, y la lista de pendientes en orden. **La competencia es el jueves 26/11.**

## 0. Cómo leer esto

1. Este archivo completo (unos 20 minutos).
2. `docs/evidencias/prueba_real/Bitacora_Prueba_Real_20261121.md`: las pruebas con el robot real (20 al 22/11). Incluye las conexiones y la alimentación que funcionan.
3. Si vas a tocar las redes: `docs/proyecto_claude/Analisis_Redes_Techo_27_vs_Maestro_87.md`.
   Para la defensa del diseño: `docs/proyecto_claude/Decisiones_Diseno_Hito3.md`.
4. Las consignas del profesor: `docs/consignas/` (los PDF del Hito 3 y de la competencia mandan sobre todo lo demás).

Si usás Claude (Code o el chat), el archivo `CLAUDE.md` de la raíz del repo ya le da las reglas. Conviene crear un Proyecto de Claude propio y subirle este archivo, la bitácora y los PDF de `docs/consignas/`.

## 1. El proyecto en pocas líneas

Sistema cliente-servidor por **Bluetooth (SPP)** para el robot **Orquita**:

- **Robot (Cliente):** Arduino UNO con el sketch `arduino/Orquita/Orquita.ino`. Barre con un sensor láser VL53L1X (13 ángulos × 3 tamaños de ROI = 39 distancias), manda la telemetría por un HC-06, ejecuta las órdenes de movimiento y avisa las capturas (sensor FC-51 en el embudo). Si deja de oír al Servidor 3 s, frena solo (Safe Mode).
- **Servidor:** Java 17 + Maven + **Neuroph 2.98** (obligatorio por la consigna), en la PC Windows. Clasifica cada sector con una red de percepción, decide el movimiento con una red de movimiento, anota las capturas en `estomago.txt` (un hilo Escritor por captura y un semáforo justo) y un Auditor activa el Modo Escape al llegar al umbral.
- **Misión:** capturar 3 canicas ("Lobitos") en la "Bahía de Caza" y salir por el estrecho. **Salir sin completar descalifica.**

**Estado al 23/11:**

- El sistema completo funciona de punta a punta en el robot real: Bluetooth, telemetría, órdenes, motores, servos y sensor.
- **El problema abierto es la percepción con datos reales:** la red ve lobitos donde hay piso o salida (ver §7).
- Las redes se eligieron y midieron en simulación. **En la pista real nunca completaron una misión.**

## 2. Competencia (26/11)

- Puntaje por tiempo (confirmado por el profesor): terminar en el minuto 1 = +15; minutos 2 a 4 = +10; minutos 5 a 10 = +5. Cada choque −5. Salir de la bahía sin las 3 capturas descalifica.
- Objetivo que se había fijado el equipo: 1 minuto, 0 choques, 3 lobitos. Con barridos de ~2 s y ~90 decisiones por partida, en simulación la red tarda ~3–4 min.
- Umbral de capturas: **3** en la competencia. La prueba de campo del Hito 3 usaba 1 (`--umbral=1`).
- Prueba de Fail-Safe: el docente apaga el Bluetooth de la PC y el robot tiene que frenar solo.

## 3. Repo y ramas

Repo: `lautaro-geli/sistema-orquita`. **Rama de trabajo: `maestro-v2`.** Lleva unos 100 commits por delante de `main`, que quedó vieja. Unir `maestro-v2` a `main` (o a la rama de entrega) es un pendiente.

| Carpeta | Qué hay |
|---|---|
| `src/main/java/orquitas/servidor/` | Servidor: `ServidorOrquita` (main), `comunicacion/EnlaceOrquita` (Bluetooth y protocolo), `persistencia/` (Estomago, Escritor, Auditor), `navegacion/` (redes, reglas, reflejos) |
| `src/main/java/orquitas/simulacion/` | Simulador de la bahía (`bahia/`): Bahia, RobotSimulado, SensorSimulado, SimuladorBahia, Maestro, MaestroV2, Piloto, VisorBahia. **Archivos de simulación: no van en los diagramas.** |
| `src/main/java/orquitas/herramientas/` | Entrenamiento (`EntrenamientoSimulado`, `Neuroevolucion`, `EntrenamientoReal`), `RecolectorBarridos` y `PruebaDeteccion` |
| `arduino/Orquita/` | **El sketch de la competencia** |
| `arduino/ControlManual/` | Manejar el robot a mano y diagnosticar (motores, servos, sensor) |
| `arduino/EscanerI2C/` | Ver si el sensor responde en el bus I2C |
| `arduino/PruebaRuidoSensor/` | Pruebas de ruido y del ROI del sensor (19/11) |
| `herramientas_pc/` | Los `.bat`/`.ps1` de la PC Windows (ver §6) |
| `docs/diseno/` | **Diseño entregable vigente:** clases v6, secuencias v5, DCU v3.6 en PDF |
| `docs/PROTOCOLO.md` | Protocolo v3 (mensajes, validación, regla del -1) |
| `docs/evidencias/` | Resultados de cada experimento, la bitácora de pruebas reales y el ruido del sensor |
| `docs/proyecto_claude/` | Copia de los documentos de análisis que estaban en el Proyecto de Claude |
| `docs/consignas/` | Los PDF del profesor |
| `modelos/` | `entorno.nnet` (red vieja de una clase, para `--reglas`) y un `percepcion.nnet` **viejo del 13/11: no es E2**. Las redes E2 van aparte (§7) |

## 4. Cómo funciona

**Ciclo:** el robot barre (~2 s) → manda `TELEMETRIA|39 valores` → el Servidor responde `CONFIRMACION` y `ORDEN|movimiento|ms` → el robot mueve esos ms → barre de nuevo. El Servidor manda `LATIDO` cada 1 s. Detalle en `docs/PROTOCOLO.md`.

**Navegación con redes** (`Navegacion`, con las dos redes en `modelos/`):

1. `PreprocesadorTelemetria`: aplica la regla del -1. Las lecturas de menos de 40 mm se toman como sin dato.
2. `RedPercepcion` (46 → 24 → 5): mira cada sector con 2 vecinos de cada lado y lo etiqueta como LIBRE, PARED, OBSTACULO, LOBITO o SALIDA.
3. `RedMovimiento` (184 → 48 → 15): elige el sector meta (o ATRAS) y los mm. `MetaMovimiento` lo traduce a orden y ms con `CalibracionRobot` (200 mm/s, 212 °/s).
4. Capas de seguridad: `ReflejoSeguridad` (frente cerca → retrocede; **candado de salida**: en BUSQUEDA no sale de la bahía), `DetectorAtasco`.

**Plan B:** `--reglas` usa `entorno.nnet` (una sola clase por barrido) con paso fijo. Las dos opciones se eligen desde `orquita_prueba_real.bat`.

## 5. Hardware (lo que funciona al 22/11)

Pines (los mismos en todos los sketches):

| Elemento | Conexión |
|---|---|
| Servo de barrido (gira el sensor) | señal D4 |
| Servo de elevación (levanta el servo del sensor) | señal D12 |
| L298N | IN1 D7, IN2 D8 (motor izquierdo); IN3 D9, IN4 D11 (motor derecho); jumpers ENA y ENB puestos; borne 5V sin conectar |
| HC-06 | TXD → D10; RXD ← A2 con divisor; VCC al 5V del Arduino; 9600 baudios |
| VL53L1X | **VIN al 5V del Arduino** (nunca al pin VIN), GND, SDA A4, SCL A5 |
| FC-51 | A0 (activo en bajo), VCC al 5V del Arduino |

Alimentación (**todas las masas unidas**):

| Para | Fuente |
|---|---|
| Arduino | 4 AA (6 V) + pack de 4,8 V en serie = **10,8 V** al jack o a VIN/GND |
| L298N (motores) | 2 packs de 4,8 V en serie = **9,6 V** al +12V |
| Servos | 4 AA (6 V) |

**Lección más importante:** casi todas las fallas "raras" fueron de **alimentación**. Por ejemplo: I2C trabado, "VL53L1X no detectado", reinicios del Arduino (`Orquita lista` repetido), servos o ruedas que no se mueven aunque el Arduino manda la orden. Con 6 V en el jack, el Arduino no da 5 V. **Antes de tocar código, cargá las pilas y medí tensiones.**

Más detalles del sensor: está a ~0,8 cm del piso. El 16x16 (y en parte el 8x8) **ve el piso a 5–8 cm**. La canica se ve bien con el 4x4 hasta ~20 cm. El ruido real es de 1–2 mm.

## 6. Cómo correr cada cosa (PC Windows)

Los `.bat` están en `herramientas_pc/`. En la PC del equipo se usaban desde `C:\Users\pc\Documents`. Corren sobre copias del repo en `Documents\orquita-corridas\` y dejan todo ahí.

| Para | Qué usar |
|---|---|
| **Prueba en la bahía con el Servidor** | `orquita_prueba_real.bat` → 1 (redes E2) o 2 (reglas) → COM saliente. Consola en `orquita-corridas\prueba_real_<fecha>\consola.txt` |
| Ver qué detecta la red con datos reales | `orquita_prueba_deteccion.bat` (robot quieto, tabla por sector, CSV con notas) |
| Ver cuál COM es el saliente del HC-06 | `orquita_puertos_bt.bat` |
| Probar solo el Bluetooth | `orquita_probar_bt.bat` |
| Manejar el robot con el teclado | `orquita_control_manual.bat` (con `ControlManual.ino` cargado) |
| Servidor en otra PC sin el repo | `orquita_armar_paquete.bat` arma `OrquitaPortable` (incluye Java 17 si se le agrega la carpeta `jre`) |
| Entrenar o experimentar | `orquita_experimentos_N`, `orquita_neuroevolucion*` (historial; ver §8) |

**Requisitos de la PC:** Java 17 o más nuevo, Git y Maven (los scripts usan `Documents\herramientas\apache-maven-3.9.11` si no hay `mvn`). Desde el repo: `./preparar.ps1` compila y corre las pruebas; `./ejecutar.ps1 servidor COMx --umbral=3` corre el Servidor.

**Arduino:** librería **VL53L1X de Pololu** (≥ 1.3.0). Servo, SoftwareSerial y Wire vienen con el IDE. Si la subida falla con `not in sync`, cerrá el Monitor Serie, desconectá el cable de D10 y probá de nuevo. Si hace falta, apretá RESET cuando aparece "Uploading".

**Bluetooth:** el HC-06 crea dos COM en Windows. Se usa el **saliente** (Configuración > Bluetooth > Más opciones > Puertos COM). Al volver a vincularlo, los números cambian. "Acceso denegado" significa que otra ventana tiene el COM abierto: un solo programa por puerto.

**Orden de encendido:** primero el robot (esperar `Orquita lista` en el Monitor Serie, a 115200), después el Servidor. El Servidor espera 15 s el primer dato. Cada arranque **vacía `estomago.txt`**.

## 7. Redes y modelos

**Red elegida: E2.** Es imitación del Maestro con DAgger, error cuadrático y repetición máxima 2. En simulación, con ruido real y barrido de ~2 s (900 bahías): 78 % de misiones, 3,8 choques, 93 decisiones por partida, 51 puntos. El Maestro v2 (con trampa, porque ve la geometría) llega a 95 %.

**Dónde están los modelos E2:** los `.nnet` **no se suben a git** (regla del proyecto). Vienen en el zip del traspaso (`modelos_E2/percepcion.nnet` y `movimiento.nnet`). En la PC original estaban en `Documents\orquita-corridas\experimentos2_20261116_1544\modelos_E2_repeticion_2_s3\`. Para usarlos con `ejecutar.ps1`, copialos a `modelos/` del repo. `orquita_prueba_real.bat` los busca solo en la PC original.

**Problema abierto: percepción con datos reales** (prueba de detección del 22/11, CSV en el zip):

- El piso que ve el 16x16 queda como "ROI grande cerca, 4x4 lejos o sin eco". En la simulación eso solo pasaba con canicas, así que la red marca **LOBITO** en la salida (4–6 sectores por barrido, al 97–100 %) y a veces cerca de paredes u obstáculos.
- Las canicas reales a 7–14 cm sí las detecta (LOBITO 90–98 %).
- Probé sobre esos CSV, sin reentrenar, una corrección: si el 8x8 o el 16x16 miden bastante menos que el 4x4, es piso y se usa el 4x4. Con eso, los lobitos falsos en la salida bajaron de 4–6 a 1–2 por barrido y la canica sigue detectada. **No está implementada en el Servidor todavía** (ver pendientes).

**Lo que ya se probó y no sirvió** (para no repetirlo; detalle en `docs/proyecto_claude/Analisis_Redes_Techo_27_vs_Maestro_87.md`):

| Probado | Resultado |
|---|---|
| Entropía cruzada (E5/E6) | No supera a E2 |
| Umbral más alto para LOBITO en el reflejo (E4) | Baja falsos, no cambia misión |
| Imitar al Maestro v2 (E7b) | Más choques, no baja decisiones |
| Entrenar con barrido corto y ruido (E7a) | Peor que E2 |
| Refuerzo / neuroevolución (NE40, NE2) | +4 puntos como mucho, no acelera; abandonado |
| Seguimiento de lobitos + piloto de reglas | Al nivel de E2 con más decisiones; queda en el código, no se usa |
| Explorar con memoria (Maestro) | Peor |

## 8. Qué NO tocar (reglas del proyecto)

- **Neuroph** para la clasificación: lo exige la consigna. No cambiar a otra biblioteca.
- **No subir `.nnet` a git.**
- **No poner simulación en los diagramas** (las clases de `orquitas.simulacion` y de herramientas quedan afuera). Coherencia 1:1 entre diagramas, DCU y código. Los diagramas vigentes son los de `docs/diseno/`.
- **Protocolo v3** (`docs/PROTOCOLO.md`): si se cambia, hay que cambiar a la vez el Servidor, el sketch, las pruebas y los diagramas.
- **Concurrencia:** solo semáforos o `synchronized` (lo pide la consigna). El estómago se escribe con Escritor + semáforo justo; el Auditor solo lee.
- **Comentarios verdaderos.** No afirmar mediciones reales ni éxito en la pista que no existan. En los informes no usar "sintético", "ficticio", "simulado" ni "demo" para describir datos (el equipo lo explica en persona).
- **Candado de salida:** no sacarlo. Sin él, la red salía sin completar en el 3–5 % de las partidas, y eso descalifica.
- **Sensor:** su VIN va al **5V**. El pin VIN del Arduino tiene 10,8 V.
- **Pines y alimentación** de §5: los sketches los asumen.

## 9. Pendientes, en orden

### Antes de la competencia (prioridad alta)

1. **Corrección del piso en el Servidor** (`PreprocesadorTelemetria`): si el 8x8 o el 16x16 miden menos que el 4x4 menos un margen (~15–30 mm), o el 4x4 no tiene eco y el ROI grande está a menos de ~20 cm, usar el valor del 4x4. Probar en la simulación que no empeora, y con los CSV reales del zip. Estimado: 1 h.
2. **Regla "LOBITO solo si lo ve el 4x4"** después de la percepción: un sector es LOBITO solo si el 4x4 tiene eco a menos de ~40 cm. Si no, se toma como LIBRE o SALIDA. Junto con la 1 deberían eliminar casi todos los lobitos falsos. 30 min.
3. **Tomar telemetrías reales etiquetadas** con `orquita_prueba_deteccion.bat`: unas 15 de lobito, 15 de salida y 15 de obstáculo, en 5 posiciones distintas × 3 barridos cada una.
   - La nota tiene que decir tipo, ángulo y distancia: `lobito -15 20`, `obstaculo +30 15`, `salida -30 +15`. Ángulo positivo a la izquierda, 0 al frente.
   - Sumar canicas muy cerca (5–10 cm).
4. **Ajuste fino de la percepción con esos datos.** Seguir entrenando `percepcion.nnet` de E2 con Neuroph, **mezclando** barridos reales (ya corregidos por la 1) con simulados, y separando algunos reales para medir antes y después. Si se entrena solo con lo real, la red se olvida de lo que sabía.
5. **Recalibrar avance y giro.** Ahora los motores van con 9,6 V y `CalibracionRobot` dice 200 mm/s y 212 °/s, medidos con otra fuente. Con `ControlManual`: medir cuánto avanza y gira con 300 ms y actualizar `CalibracionRobot`.
6. **Ángulo de elevación para atrapar.** Con `ControlManual` (`i`/`k`), buscar el menor ángulo con el que la canica entra al embudo sin tocar el sensor y ponerlo en `SENSOR_ARRIBA_GRADOS` de `Orquita.ino`. Hoy está en 180° (lento). Ojo: el sensor sube solo si el último barrido vio algo a menos de 35 cm al frente.
7. **`BARRIDO_RAPIDO` en `Orquita.ino`.** En el repo está en `true` (configuración D, ~2 s). En la PC quedó en `false` (~4,8 s) por el diagnóstico de alimentación. Con la alimentación arreglada, volver a probar `true`. Si aparece `I2C trabado`, dejar un solo tiempo de medición para los 3 ROI (~30 ms).
8. **Pulsos muy cortos:** el reflejo manda `RETROCEDER 50 ms` (1 cm), que en el robot real puede no mover nada. Subir el mínimo de `OrdenMovimiento` o del reflejo (por ejemplo a 150 ms) y probar.
9. **Prueba completa en la bahía** con E2, umbral 3, filmada. Verificar: Safe Mode (apagar el Bluetooth de la PC), `CAPTURA|id` con el FC-51, candado de salida, Modo Escape y que el robot no quede quieto más de 1 minuto.
10. **Dejar lista la entrega:** unir `maestro-v2` a la rama de entrega o a `main`; copiar las redes finales a `modelos/` de la PC de la competencia; congelar todo el 25/11; probar el plan B `--reglas` (también le sirven la 1 y la 2).

### Si sobra tiempo

11. **Piso en la simulación + reentrenar** (solución de fondo). Agregar el piso a `SensorSimulado` (lo que ve cada ROI según la altura del sensor), reentrenar percepción y movimiento (~2–3 h de PC) y repetir el ajuste fino (4) sobre la red nueva con los mismos datos reales.
12. Umbral del `DetectorAtasco` con el ruido real (1–2 mm: puede no reconocer dos barridos iguales).
13. Lecturas de 0–46 mm a 60° y 120° sin nada físico (19/11, causa sin confirmar). Hoy el Servidor descarta lo menor a 40 mm.
14. Conseguir un VL53L1X de repuesto.

### Documentación del Hito 3 (verificar qué falta entregar)

- DCU v3.6 (PDF), clases v6 y secuencias v5 están en `docs/diseno/`.
- En los documentos viejos figuraban como pendientes las vistas de **Despliegue y Estados**, que dependían de las especificaciones del docente de Desarrollo de Sistemas. Confirmar si se entregaron.
- **Coherencia diagramas–código:** los diagramas v6/v5 describen la navegación con la red de una sola clase (`RedNeuronal`, `entorno.nnet`). La navegación con dos redes (`RedPercepcion`, `RedMovimiento`, `RepresentacionIntermedia`, `MemoriaNavegacion`, `ReflejoSeguridad` con el candado, `DetectorAtasco`, etc.) es posterior y **no está en los diagramas**. Si se compite y entrega con E2, decidir si se actualizan los diagramas o se justifica la diferencia en la carpeta técnica. Las clases de simulación y de herramientas no van.
- Los `.drawio` de Estados y Despliegue no están en el repo: los tenía el equipo.

## 10. Glosario

- **Lobito:** canica que hay que capturar. **Estómago:** `estomago.txt`, con una línea por captura (`id;fecha`).
- **Sector:** cada uno de los 13 ángulos del barrido (0° = derecha, 90° = frente, 180° = izquierda). En las tablas del Servidor, el ángulo relativo es positivo a la izquierda.
- **ROI 4x4 / 8x8 / 16x16:** cuánto de la grilla del sensor se usa. Más chico = más angosto. El 4x4 ve la canica; el 16x16 ve el piso.
- **Maestro:** piloto de reglas del simulador que conoce la geometría real (hace trampa). Las redes lo imitan; es el techo.
- **E2:** la red de movimiento elegida (experimentos2, repetición máxima 2, semilla 3).
- **DAgger:** entrenamiento por imitación en rondas, donde la red maneja y el Maestro corrige.
- **Candado de salida:** regla del Servidor que no deja salir de la bahía en BUSQUEDA.
- **Safe Mode:** el robot frena si no oye al Servidor durante 3 s.
