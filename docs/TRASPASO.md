# Traspaso del Proyecto Orquitas (23/11/2026, actualizado el 24/11)

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

**Novedades del 24/11** (detalle: `docs/evidencias/prueba_real/Toma_Guiada_20261124.md`):

- **El robot mandaba los sectores espejados:** el servo tiene 0° a la izquierda y el Servidor supone sector 0 = derecha. Está corregido en `Orquita.ino` (`anguloSector`). **Hay que volver a cargar el sketch.** Todas las pruebas reales anteriores (21 y 22/11) se hicieron con la izquierda y la derecha cambiadas.
- Toma guiada real (25 escenas): el sensor ve la canica **solo a ~10 cm, de frente**. La red E2 marca LOBITO en el 15 % de los sectores que no son canica, también en sectores sin ningún dato.
- Filtro para LOBITO en el Servidor, por defecto `eco`: LOBITO solo si el 4x4 tiene eco. Es neutro en la simulación.
- **Noche del 24/11** (`docs/evidencias/prueba_real/Sim_Realista_20261124.md`):
  - Simulación realista (`-Dorquita.simRealista=true`), ajustada a la toma guiada. Con ella, E2 completa solo el 13,8 % de las misiones (en la de siempre, el 73 %).
  - Modo barrido en el Servidor (`--modo=barrido`, zigzag de 15 cm). En la simulación realista todavía es peor que E2 (7,7 %).
  - Entrenamiento de la noche con la simulación realista: **N_maestro_s2 completa el 59 % (E2, el 13,8 %) en esa simulación**, con choques dentro del ruido. Está en `orquita-corridas\noche_realista_20261124_2319\modelos_N_maestro_s2`. Falta confirmarlo en la pista.

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
| `src/main/java/orquitas/herramientas/` | Entrenamiento (`EntrenamientoSimulado`, `Neuroevolucion`, `EntrenamientoReal`), `RecolectorBarridos`, `PruebaDeteccion` y `AnalisisDeteccion` (con `NotaEscena`, que lee las notas) |
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

**Plan B:** `--reglas` usa `entorno.nnet` (una sola clase por barrido) con paso fijo. Las dos opciones se eligen desde `orquita_prueba_real.bat`. **Ojo: las reglas no pasan por el reflejo ni por el candado de salida.** En BUSQUEDA, si ven SALIDA, avanzan, así que pueden salir sin las 3 canicas.

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

Más detalles del sensor: está a ~0,8 cm del piso. El 16x16 (y en parte el 8x8) **ve el piso a 5–8 cm**. En la toma guiada del 24/11, el 4x4 vio la canica solo a ~10 cm, de frente; a 20–30 cm, o de costado, casi nunca. El ruido real es de 1–2 mm.

## 6. Cómo correr cada cosa (PC Windows)

Los `.bat` están en `herramientas_pc/`. En la PC del equipo se usaban desde `C:\Users\pc\Documents`. Corren sobre copias del repo en `Documents\orquita-corridas\` y dejan todo ahí.

| Para | Qué usar |
|---|---|
| **Prueba en la bahía con el Servidor** | `orquita_prueba_real.bat` → 1 (redes E2) o 2 (reglas) → COM saliente. Consola en `orquita-corridas\prueba_real_<fecha>\consola.txt` |
| **Tomar los datos etiquetados (pendiente 1)** | `orquita_toma_guiada.bat`: recorre 25 escenas y dice qué poner en cada una; con Enter descarta el barrido en curso y guarda 3; después Enter = seguir, R = repetir, S = saltear. Si se corta, volver a abrirlo y poner la escena que seguía. Al terminar corre el análisis. Queda en `orquita-corridas\toma_guiada_<fecha>` |
| Ver qué detecta la red con datos reales | `orquita_prueba_deteccion.bat` (robot quieto, tabla por sector, CSV con notas). Al escribir la nota, contesta "Entiendo: …" o avisa si le falta algo |
| Analizar esos datos por sector | `orquita_analisis_deteccion.bat`: elegís las carpetas `deteccion_*` y deja `analisis.md` (qué confunde la red, por distancia, y la prueba del filtro de forma), `analisis_sectores.csv` y `analisis_filtro.csv` |
| Ver cuál COM es el saliente del HC-06 | `orquita_puertos_bt.bat` |
| Probar solo el Bluetooth | `orquita_probar_bt.bat` |
| Manejar el robot con el teclado | `orquita_control_manual.bat` (con `ControlManual.ino` cargado) |
| Servidor en otra PC sin el repo | `orquita_armar_paquete.bat` arma `OrquitaPortable` (incluye Java 17 si se le agrega la carpeta `jre`) |
| Entrenar o experimentar | `orquita_experimentos_N`, `orquita_neuroevolucion*` (historial; ver §8) |

**Armar una PC nueva para los `.bat`** (los scripts tienen rutas fijas de la PC original):
1. Instalar Java 17 o más nuevo y Git. Maven es opcional: si no está, los scripts lo buscan en `Documents\herramientas\apache-maven-3.9.11` (bajar el zip de Maven 3.9.11 y descomprimirlo ahí).
2. Clonar el repo en **`Documents\sistema-orquita`**, en la rama `maestro-v2`.
3. Copiar los modelos E2 del zip a **`Documents\orquita-corridas\experimentos2_20261116_1544\modelos_E2_repeticion_2_s3\`**. Es la ruta que buscan `orquita_prueba_real` y `orquita_prueba_deteccion`.
4. Copiar los `.bat`/`.ps1` de `herramientas_pc/` a `Documents`.
5. **Ojo:** `orquita_prueba_real.ps1` compila un commit fijo (`$Commit`, línea 8). Desde el 24/11 es `002419c`, que trae la corrección del piso y el filtro `eco` para LOBITO (antes `af9023e`). Si cambiás el Servidor (por ejemplo, la corrección del piso), poné ahí el commit nuevo. Si no, la prueba corre el código viejo. `orquita_prueba_deteccion` ya compila siempre lo último de `origin/maestro-v2`.

**Requisitos de la PC:** Java 17 o más nuevo, Git y Maven (los scripts usan `Documents\herramientas\apache-maven-3.9.11` si no hay `mvn`). Desde el repo: `./preparar.ps1` compila y corre las pruebas; `./ejecutar.ps1 servidor COMx --umbral=3` corre el Servidor.

**Arduino:** librería **VL53L1X de Pololu** (≥ 1.3.0). Servo, SoftwareSerial y Wire vienen con el IDE. Si la subida falla con `not in sync`, cerrá el Monitor Serie, desconectá el cable de D10 y probá de nuevo. Si hace falta, apretá RESET cuando aparece "Uploading".

**Bluetooth:** el HC-06 crea dos COM en Windows. Se usa el **saliente** (Configuración > Bluetooth > Más opciones > Puertos COM). Al volver a vincularlo, los números cambian. "Acceso denegado" significa que otra ventana tiene el COM abierto: un solo programa por puerto.

**Orden de encendido:** primero el robot (esperar `Orquita lista` en el Monitor Serie, a 115200), después el Servidor. El Servidor espera 15 s el primer dato. Cada arranque **vacía `estomago.txt`**.

## 7. Redes y modelos

**Red elegida: E2.** Es imitación del Maestro con DAgger, error cuadrático y repetición máxima 2. En simulación, con ruido real y barrido de ~2 s (900 bahías): 78 % de misiones, 3,8 choques, 93 decisiones por partida, 51 puntos. El Maestro v2 (con trampa, porque ve la geometría) llega a 95 %.

**Dónde están los modelos E2:** los `.nnet` **no se suben a git** (regla del proyecto). Vienen en el zip del traspaso (`modelos_E2/percepcion.nnet` y `movimiento.nnet`). En la PC original estaban en `Documents\orquita-corridas\experimentos2_20261116_1544\modelos_E2_repeticion_2_s3\`. Para usarlos con `ejecutar.ps1`, copialos a `modelos/` del repo. `orquita_prueba_real.bat` los busca solo en la PC original.

**Problema abierto: percepción con datos reales** (prueba de detección del 22/11, CSV en el zip):

- El piso que ve el 16x16 queda como "ROI grande cerca, 4x4 lejos o sin eco". En la simulación eso solo pasaba con canicas, así que la red marca **LOBITO** en la salida (4–6 sectores por barrido, al 97–100 %) y a veces cerca de paredes u obstáculos.
- **Obstáculos confundidos con lobitos (lo más grave).** En la escena "lolobito" (obstáculos y lobitos), según lo que vio el equipo, la red marca los **obstáculos como LOBITO con mucha seguridad** y a los **lobitos reales con poca**. El CSV no tiene anotado qué sector era cada cosa, así que no está medido por sector. Si la red persigue obstáculos, el robot choca (−5 por choque) y no captura.
- **Por qué pasa** (explicación probable, a confirmar con datos etiquetados):
  - La red aprendió en la simulación que un lobito es "4x4 cerca, 16x16 más lejos" (objeto chico).
  - En la realidad, el **borde de un obstáculo** que entra solo en el cono angosto del 4x4 deja ese mismo patrón.
  - En cambio, la canica real (1,6 cm de alto, con el sensor a 0,8 cm) se ve débil: a 20 cm el 4x4 ya mezcla la canica con el fondo.
- Probé sobre esos CSV, sin reentrenar, una corrección: si el 8x8 o el 16x16 miden bastante menos que el 4x4, es piso y se usa el 4x4. Con eso, los lobitos falsos en la salida bajaron de 4–6 a 1–2 por barrido. **No resuelve los obstáculos confundidos con lobitos.** **Implementada en el Servidor el 23/11** (pendiente 2): con los CSV, los lobitos falsos mirando la salida bajan de 4,0 a 2,1 por barrido. Detalle en `docs/evidencias/prueba_real/Correccion_Piso_20261123.md`.

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

0. **Antes de competir (25/11), con el robot:**
   - Cargar `arduino/Orquita/Orquita.ino` nuevo (commit 3358855 o posterior; actualizar la PC con `armar_pc_orquita.bat`).
   - **Probar el lado del sensor:** con `orquita_prueba_deteccion.bat`, parado detrás del robot, poner una caja a su izquierda a ~10 cm. Tiene que aparecer ~100 mm en las filas "izq 75 / izq 90". Si aparece en "der", algo quedó mal: no competir con redes hasta arreglarlo.
   - **Probar el sentido de giro:** `GIRAR_IZQ` también tiene un `TODO confirmar` en el sketch. Con `ControlManual.ino`, la tecla `a` es lo mismo que `GIRAR_IZQ`: visto desde arriba, el robot tiene que girar a su izquierda (contra las agujas del reloj). Si gira al revés, intercambiar los `motores(...)` de `GIRAR_IZQ` y `GIRAR_DER` en `Orquita.ino`.
   - **Prueba real de los tres pilotos** con `orquita_prueba_real.bat`: 1 = E2, 3 = barrido, 4 = red nueva (`modelos_N_maestro_s2`, la que ganó en `comparacion.md`). Misma bahía, mismas canicas y obstáculos y misma partida para los tres; anotar capturas, choques y tiempo, y guardar `consola.txt`.
1. ~~**Tomar telemetrías reales etiquetadas**~~ **Hecho (24/11)** con `orquita_toma_guiada.bat`: 25 escenas, 75 barridos. Análisis en `docs/evidencias/prueba_real/Toma_Guiada_20261124.md`. Para repetir otra toma: el sensor tiene que medir parejo antes de empezar, e izquierda y derecha son **las del robot**.
2. ~~**Corrección del piso en el Servidor**~~ **Hecho (23/11).** En `PreprocesadorTelemetria.corregir()`, solo para el 8x8 y el 16x16 a menos de 200 mm: si el 4x4 tiene dato y el ROI grande mide más de 20 mm menos, toma el valor del 4x4; si el 4x4 no tiene eco, el ROI grande queda sin eco. Se apaga con `-Dorquita.correccionPiso=false`. Arregla la salida y el piso, **no los obstáculos**.
   - CSV reales del 22/11 (red E2): lobitos falsos mirando la salida 4,0 → 2,1 por barrido.
   - Simulación (900 bahías): con el ruido actual cuesta ~10 puntos, porque el 4x4 simulado pierde el eco al azar mucho más que el real y la corrección borra paredes cercanas; con una pérdida del 4x4 parecida a la real queda neutra (dentro del ruido). Un ajuste fino de la percepción con la corrección no mejoró y se descartó.
   - Detalle: `docs/evidencias/prueba_real/Correccion_Piso_20261123.md`. Falta copiarla a `entrega-java/` (pendiente 10).
3. ~~**Filtro para LOBITO**~~ **Hecho (24/11)** en `RedPercepcion` (`-Dorquita.filtroLobito`):
   - **`eco`** (por defecto): LOBITO solo si el 4x4 tiene eco. En la simulación es neutro (−1 ± 3,4 puntos de misión, sin más choques). En los datos reales saca los LOBITO de sectores sin dato: falsos 141 → 97, en la salida 18 → 14.
   - **`forma`**: además, 4x4 a menos de 250 mm, 16x16 al menos 10 mm más lejos y vecinos a más de 50 mm. Se ajustó dejando una escena afuera. En los datos reales baja los falsos 141 → 13 (obstáculos 24 → 4, salida 18 → 0), pero en la simulación la misión cae 37 puntos, porque ahí la canica se ve lejos y ancha. Solo para probar en la pista si con `eco` el robot va a buscar obstáculos.
   - **`no`**: la red sola. Los análisis corren así.
4. **Ajuste fino de la percepción con esos datos** (ojo: son pocos; la canica vista de verdad aparece en 4–6 barridos) (con los obstáculos etiquetados como OBSTACULO). Seguir entrenando `percepcion.nnet` de E2 con Neuroph, **mezclando** barridos reales (ya corregidos por la 2) con simulados, y separando algunos reales para medir antes y después. Si se entrena solo con lo real, la red se olvida de lo que sabía.
5. **Recalibrar avance y giro.** Ahora los motores van con 9,6 V y `CalibracionRobot` dice 200 mm/s y 212 °/s, medidos con otra fuente. Con `ControlManual`: medir cuánto avanza y gira con 300 ms y actualizar `CalibracionRobot`.
6. **Ángulo de elevación para atrapar.** Con `ControlManual` (`i`/`k`), buscar el menor ángulo con el que la canica entra al embudo sin tocar el sensor y ponerlo en `SENSOR_ARRIBA_GRADOS` de `Orquita.ino`. Hoy está en 180° (lento). Ojo: el sensor sube solo si el último barrido vio algo a menos de 35 cm al frente.
7. **`BARRIDO_RAPIDO` en `Orquita.ino`.** En el repo está en `true` (configuración D, ~2 s). En la PC quedó en `false` (~4,8 s) por el diagnóstico de alimentación. Con la alimentación arreglada, volver a probar `true`. Si aparece `I2C trabado`, dejar un solo tiempo de medición para los 3 ROI (~30 ms).
8. **Pulsos muy cortos:** el reflejo manda `RETROCEDER 50 ms` (1 cm), que en el robot real puede no mover nada. Subir el mínimo de `OrdenMovimiento` o del reflejo (por ejemplo a 150 ms) y probar.
9. **Prueba completa en la bahía** con E2, umbral 3, filmada. Verificar: Safe Mode (apagar el Bluetooth de la PC), `CAPTURA|id` con el FC-51, candado de salida, Modo Escape y que el robot no quede quieto más de 1 minuto.
10. **Dejar lista la entrega:** unir `maestro-v2` a la rama de entrega o a `main`; copiar las redes finales a `modelos/` de la PC de la competencia; congelar todo el 25/11; probar el plan B `--reglas` (también le sirve la 2; ojo, no tiene candado de salida).

### Si sobra tiempo

- **Confirmar lobitos levantando el sensor** (la forma física más segura de separar canica de obstáculo). Con el sensor arriba (6,5 cm), la canica no se ve y los obstáculos y paredes (10 cm de alto) sí. Si la red marca LOBITO en un sector, se mide ese sector con el sensor arriba: si el objeto sigue, es obstáculo. Cuesta ~1,5 s por verificación y obliga a cambiar el sketch y, según cómo se haga, el protocolo (§8: Servidor, sketch, pruebas y documentación juntos). Se puede probar primero a mano con `ControlManual` (`i`/`k` para subir y `r` para medir).

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
- **Sector:** cada uno de los 13 ángulos del barrido: sector 0 = derecha, 6 = frente, 12 = izquierda. El servo de barrido está al revés (0° = izquierda), y `anguloSector()` del sketch lo traduce. En las tablas del Servidor, el ángulo relativo es positivo a la izquierda.
- **ROI 4x4 / 8x8 / 16x16:** cuánto de la grilla del sensor se usa. Más chico = más angosto. El 4x4 ve la canica; el 16x16 ve el piso.
- **Maestro:** piloto de reglas del simulador que conoce la geometría real (hace trampa). Las redes lo imitan; es el techo.
- **E2:** la red de movimiento elegida (experimentos2, repetición máxima 2, semilla 3).
- **DAgger:** entrenamiento por imitación en rondas, donde la red maneja y el Maestro corrige.
- **Candado de salida:** regla del Servidor que no deja salir de la bahía en BUSQUEDA.
- **Safe Mode:** el robot frena si no oye al Servidor durante 3 s.
