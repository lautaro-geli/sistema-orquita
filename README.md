# Orquitas — Hito 3 "Liberen a Willy"

Sistema distribuido Cliente–Servidor por **Bluetooth (RFCOMM/SPP)**:

- **Servidor** (Java 17, Maven, en la PC Windows):
  - recibe los barridos del robot y los clasifica con una red neuronal Neuroph;
  - decide el movimiento y lo envía como orden;
  - anota las capturas en `estomago.txt` con un hilo Escritor por captura y un semáforo justo;
  - un Auditor independiente activa el Modo Escape.
- **Cliente** (sketch `arduino/Orquita/Orquita.ino`, Arduino UNO en el robot):
  - barre con el VL53L1X y detecta capturas con el FC-51;
  - envía por el HC-06 y ejecuta las órdenes;
  - frena solo (Safe Mode) si pierde al Servidor.

Fuente de verdad del diseño: `docs/diseno/` (clases v5, secuencias v5, DCU v3.6). Protocolo: [docs/PROTOCOLO.md](docs/PROTOCOLO.md). Decisiones por defecto a confirmar: [docs/DECISIONES_FASE2.md](docs/DECISIONES_FASE2.md).

## Dependencias

Maven las resuelve solas desde Maven Central (requiere internet la primera vez):

| Dependencia | Versión | Para qué |
|---|---|---|
| `com.github.neuroph:neuroph-core` | 2.98 | Red neuronal (MLP). |
| `com.fazecast:jSerialComm` | 2.11.4 | Abrir el puerto COM del Bluetooth con tiempos de espera de lectura. |
| `junit:junit` (solo pruebas) | 4.13.2 | Pruebas automáticas. |

Arduino (Gestor de librerías del IDE): **VL53L1X de Pololu** (≥ 1.3.0). `Servo`, `SoftwareSerial` y `Wire` vienen con el IDE.

## Construir y probar

Abrir la carpeta (la que tiene `pom.xml`) como proyecto Maven en NetBeans con JDK 17 o posterior; *Clean and Build* compila y corre las pruebas. Desde PowerShell:

```powershell
./preparar.ps1          # mvn clean package + copia de dependencias a target/dependency
mvn clean verify        # solo las pruebas (no necesitan hardware)
```

Las pruebas (JUnit 4) simulan el puerto con streams en memoria. Cubren:

- el protocolo y las respuestas;
- latido, silencio y falla de escritura;
- Escritores concurrentes con el Auditor;
- la navegación por modo y la red neuronal;
- un ciclo completo con una Orquita simulada: telemetría, captura, ESCAPE con umbral 1 y LIBERADA.

## Emparejar el HC-06 en Windows

1. Encender el robot (el HC-06 parpadea).
2. Windows → Configuración → Bluetooth y dispositivos → Agregar dispositivo → Bluetooth → elegir el HC-06. El PIN suele ser `1234` (o `0000`).
3. Ver el puerto: Administrador de dispositivos → **Puertos (COM y LPT)** → "Vínculo serie estándar a través de Bluetooth (COMx)". Hay dos; se usa el **saliente**: Configuración de Bluetooth → Más opciones de Bluetooth → pestaña *Puertos COM*, la fila "Saliente" con el nombre del HC-06.

El Servidor no empareja ni crea el puerto: solo abre el COM ya creado.

## Ejecutar

```powershell
./ejecutar.ps1 servidor COM5 --umbral=1
# opciones: --estomago=estomago.txt  --modelo=modelos/entorno.nnet  --reglas
#           --percepcion=modelos/percepcion.nnet  --movimiento=modelos/movimiento.nnet
```

Si existen `modelos/percepcion.nnet` y `modelos/movimiento.nnet`, la navegación usa las **dos redes de la bahía simulada** (etiqueta por sector y orden con ms; ver abajo). Con `--reglas`, o si no están, usa la red de una sola clase (`--modelo`) con paso fijo. El banner dice cuál está activa.

**Orden de encendido:** primero el robot, después el Servidor.

- Al abrir el COM, Windows conecta con el HC-06.
- El Servidor espera hasta 15 s el primer dato del robot.
- Cada arranque **vacía `estomago.txt`**: la misión empieza de cero.
- El banner muestra el puerto, el modelo y el umbral activo.

`--umbral=1` es la prueba de campo del Hito 3; el reglamento de la competencia usa 3.

La consola muestra:

- **Estados:** `[Estado] ANTERIOR -> NUEVO` en cada cambio de modo (BUSQUEDA, ESCAPE, LIBERADA) y `[Estado] ERROR_SEGURO (causa)` cuando falla el enlace.
- **Capturas:** `[Captura]` y `[Auditor] n/umbral`.
- **Órdenes:** `[Orden]`.
- **Navegación y memoria** (solo con las dos redes): una línea por barrido con lo que resume la representación intermedia y lo principal de la memoria, por ejemplo `[Navegación] Lobito a +30°, 25 cm (0,99); frente libre; izq. cerrado | [Memoria] última GIRAR_IZQ 250 ms; meta vista hace 0 ciclos; rumbo +40°`.

### Procedimiento tras un Fail-Safe

Si el robot calla más de 15 s o falla la escritura al puerto, el Servidor informa `ERROR_SEGURO`, cierra el enlace y termina con código ≠ 0. El robot, al no recibir nada durante 3 s, frena solo (Safe Mode). Para reanudar:

1. Verificar el robot (alimentación, HC-06 parpadeando).
2. Si Windows perdió el vínculo, quitar el dispositivo y volver a emparejarlo.
3. Reiniciar el Servidor con el mismo comando. Reiniciar **vacía el estómago**.

## Datos reales y red neuronal

El modelo incluido (`modelos/entorno.nnet`) es **provisional y sintético**: no es evidencia de campo. Ver [modelos/README.md](modelos/README.md).

1. **Recolectar** barridos reales, con el robot quieto en cada escena (pista, Estrecho de Salida y campo abierto de afuera):
   ```powershell
   ./ejecutar.ps1 recolectar COM5 LIBRE 40
   ./ejecutar.ps1 recolectar COM5 OBJETO 40
   ./ejecutar.ps1 recolectar COM5 LOBITO 40
   ./ejecutar.ps1 recolectar COM5 SALIDA 40
   ```
   El recolector responde `ORDEN|DETENER` (el robot no se mueve) y `LATIDO` (no entra en Safe Mode). Cada barrido se agrega a `datos/barridos.csv` como `ETIQUETA,v0,...,v38`, con el `-1` crudo. Conviene variar posición, ángulo y distancia dentro de cada etiqueta.
2. **Entrenar:** `./ejecutar.ps1 entrenar` (opcional: `[datos/barridos.csv] [modelos/entorno.nnet] [ocultas]`).
   - Mezcla con semilla fija y separa 80/20.
   - Normaliza igual que el Servidor (`RedNeuronal.normalizar`) y entrena un MLP 39-16-4.
   - Imprime la exactitud y la matriz de confusión, guarda `modelos/entorno.nnet` y el reporte `docs/evidencias/entrenamiento_AAAAMMDD.md` (evidencia de la consigna).
3. **Modelo provisional** (sin robot): `./ejecutar.ps1 provisional`.

## Bahía simulada: percepción por sector y movimiento con ms

Problema que resuelve: en la pista un mismo barrido trae varias cosas a la vez (pared, obstáculo, Lobito, salida) y la red de una sola clase por barrido decide mal. Ahora hay dos redes:

- **Percepción por ventana** (`RedPercepcion`, 46 → 24 → 5, aplicada a los 13 sectores): mira un sector, sus 2 vecinos de cada lado y la posición, y le pone una etiqueta: `LIBRE`, `PARED`, `OBSTACULO`, `LOBITO` o `SALIDA`. Al mirar de a un sector entrena con 13 ejemplos por barrido y se pueden balancear los Lobitos (pocos).
- **Representación intermedia** (`RepresentacionIntermedia`, no es una red): resume con reglas fijas la telemetría corregida, la percepción y el modo en **14 números** entre 0 y 1. La meta es el Lobito en BUSQUEDA y la salida en ESCAPE.

  | # | Entrada | Qué es |
  |---|---|---|
  | 0 | hayMeta | 1 si algún sector tiene la meta con probabilidad ≥ 0,5 |
  | 1 | anguloMeta | ángulo del sector meta más probable, afinado con sus 2 vecinos (−90° derecha … +90° izquierda, llevado a 0…1; sin meta 0,5) |
  | 2 | distanciaMeta | cercanía de la meta (4x4 y 8x8 de ese sector) |
  | 3 | confianzaMeta | probabilidad de la percepción en ese sector |
  | 4 | anchoMeta | sectores seguidos con la meta / 13 |
  | 5–9 | obstáculo por zona | cercanía de lo más cercano que no sea la meta: frente (5–7), diagonal izq. (8–10), diagonal der. (2–4), costado izq. (11–12), costado der. (0–1) |
  | 10 | ladoMasAbierto | (libre izquierda − libre derecha), llevado a 0…1 |
  | 11–12 | modoBusqueda, modoEscape | 0/1 |
  | 13 | fraccionDesconocidos | sectores sin ningún dato / 13 |

- **Memoria** (`MemoriaNavegacion`, tampoco es una red; una por misión): **10 números** entre 0 y 1 con lo que el Servidor ya sabe de los ciclos anteriores.

  | # | Entrada | Qué es |
  |---|---|---|
  | 14–17 | ordenAnterior | una entrada por tipo de la última orden enviada: avanzar, retroceder, girar izq., girar der. (DETENER o ninguna: todo 0) |
  | 18 | cuantoAnterior | ms de esa orden / 1500 |
  | 19 | atasco | barridos seguidos iguales al anterior después de moverse (la comparación del `DetectorAtasco`), hasta 3, dividido 3 |
  | 20 | cicloDesdeMeta | ciclos desde la última vez que vio la meta (0 = la ve ahora; 1 = hace 5 o más, o nunca) |
  | 21 | anguloMetaAnterior | dónde quedó la meta la última vez que la vio, corregido por los giros hechos desde entonces (−180° … +180°, llevado a 0…1; nunca vista 0,5) |
  | 22–23 | rumboEstimado | seno y coseno del giro acumulado desde la partida (sumando los giros que mandó el Servidor), llevados a 0…1 |

  Ciclo de cada barrido: representación → `memoria.observar` (meta y atasco) → red de movimiento → reflejo → atasco → `memoria.registrarOrden` con la orden final. Al pasar a ESCAPE la meta cambia de Lobito a salida: se olvida la meta anterior, no el rumbo.
- **Movimiento** (`RedMovimiento`, 24 → 20 → 15): con la representación intermedia (14) y la memoria (10) elige **a qué sector ir** (0 a 12) o **retroceder**, y **cuántos mm** (`MetaMovimiento`). El Servidor lo traduce a milisegundos: sector 6 = avanzar esos mm; otro sector = girar 15° por sector hacia él (en el ciclo siguiente la red vuelve a decidir); atrás = retroceder. La orden viaja como `ORDEN|movimiento|ms` (protocolo v3, ver `docs/PROTOCOLO.md`). Elegir un sector es más fácil de aprender que dirección + ms, porque coincide con el sector donde la percepción ve el Lobito o la salida.
- **Reflejo de seguridad** (`ReflejoSeguridad`): sobre la orden de la red, y solo con la telemetría, acorta un avance si el frente ve algo más cerca (sin contar un Lobito) y retrocede antes de un giro si las esquinas tocarían. Un sector sin eco con vecinos a menos de 200 mm se toma como pegado (el sensor da `-1` por debajo de 40 mm).
- **Detector de atasco** (`DetectorAtasco`): si después de moverse el barrido llega casi igual (diferencia media < 20 mm), la orden no tuvo efecto (robot trabado): prueba el giro contrario o retrocede, en vez de repetir y sumar choques.

Las entradas de la percepción (`PreprocesadorTelemetria.entradas`, 117) son 39 cercanías, 39 marcas de desconocido y 3 rasgos por sector (4x4 más cerca que 16x16, 4x4 más cerca que 8x8, sector más cerca que sus vecinos: la firma de una canica cerca).

Antes de entrar a las redes, la telemetría pasa por la **regla del -1** (`PreprocesadorTelemetria`): si el 4x4 o el 16x16 son `-1` se usa el 8x8; si el 8x8 es `-1` queda desconocido; si los tres son `-1`, los tres quedan desconocidos.

Las redes se entrenan en un **modelo 2D de la Bahía de Caza** (`orquitas.simulacion.bahia`) con las medidas de la pista (13/11/2026):

- **Forma:** rectángulo de 1520 mm de ancho × 1140 mm de alto (de la base a la salida) con las **4 esquinas redondeadas** (radio 150 mm; cada arco se aproxima con 8 tramos rectos). Origen en el centro de la base: x de −760 a 760, y de 0 a 1140.
- **Salida:** un hueco de 300 mm centrado en el lado de arriba, sin pasillo. Afuera no hay nada: para el sensor, mirar por el hueco es "sin eco".
- **Adentro:** siempre 2 obstáculos de 90 × 90 mm girados al azar (a 320 mm o más entre centros, lejos de la partida y del estrecho) y 3 canicas de 16 mm.
- **Partida:** abajo a la izquierda, mirando hacia la salida (90° ± 3°), con el costado izquierdo del robot a 150–200 mm de la pared izquierda y la cola a 150–200 mm de la base (centro del robot alrededor de (−510, 275) mm). **[A CONFIRMAR]** si los 15–20 cm se miden al borde o al centro del robot: son las constantes `PARTIDA_...` de `Bahia`.
- **Sensor:** un VL53L1X simulado (13 sectores × 3 ROI, ruido, pérdidas y bloques sin eco).

Un piloto "maestro" que conoce la geometría real enseña qué orden dar en cada barrido (imitación + DAgger). Como la memoria depende de lo que pasó antes, los ejemplos se guardan por episodio y en orden, con la orden que realmente se ejecutó; al entrenar la red de movimiento cada episodio se reproduce con el mismo ciclo del Servidor, así la memoria se calcula igual en los dos lados.

```powershell
./ejecutar.ps1 simular-entrenar                 # 250 episodios, 60 épocas, 2 rondas DAgger -> modelos/percepcion.nnet y movimiento.nnet
./ejecutar.ps1 simular-entrenar 300 80 modelos 2 --hilos=4   # [episodios] [epocas] [carpeta] [rondasDagger] y la simulación en 4 hilos
./ejecutar.ps1 simular-ver 7                    # ventana: la bahía 7 manejada por las redes
./ejecutar.ps1 simular-ver 7 --maestro          # la misma bahía manejada por el maestro
./ejecutar.ps1 simular-ver 7 --png=prueba.png   # corre la prueba y guarda la imagen final
```

El reporte queda en `docs/evidencias/entrenamiento_simulado_AAAAMMDD.md` (redes contra maestro en 100 bahías nuevas y matriz de confusión por sector).

**Núcleos (`--hilos=N`):** la simulación se reparte entre hilos, porque cada bahía es independiente: los episodios del maestro, los de las rondas DAgger, la evaluación en 100 bahías, la matriz de confusión y el acuerdo de dirección.

- `--hilos=N` es opcional y va en cualquier posición. Por defecto usa los núcleos disponibles menos 1 (mínimo 1): en la PC del equipo, con 5 núcleos, son 4 hilos. `--hilos=1` corre todo en un solo hilo.
- Cada hilo tiene sus propias copias de las redes (las de Neuroph no son seguras entre hilos) y cada prueba su propio piloto y su propia memoria.
- **El resultado es exactamente el mismo con 1 hilo o con N:** cada prueba depende solo de la semilla de su índice y los ejemplos se juntan en el orden del índice, no en el que terminan los hilos.
- Neuroph entrena las redes en un solo hilo: esa parte no se reparte.

**Calibración [A CONFIRMAR]:** el Servidor y el simulador usan 250 mm/s al avanzar, 120 °/s al girar y un robot de 200 × 150 mm (`CalibracionRobot`). Los ms que decide la red dependen de esto: con las medidas reales del robot hay que cambiar esos valores y volver a entrenar.

## Estructura

| Carpeta | Contenido |
|---|---|
| `src/main/java/orquitas/servidor` | Servidor según el diagrama v5: `ServidorOrquita`, `comunicacion` (`EnlaceOrquita`, `EstadoEnlace`), `navegacion` (`Navegacion`, `RedNeuronal`, `RedPercepcion`, `RepresentacionIntermedia`, `MemoriaNavegacion`, `RedMovimiento`, enums), `persistencia` (`Estomago`, `Escritor`, `Auditor`). |
| `src/main/java/orquitas/herramientas` | `RecolectorBarridos`, `EntrenamientoReal`, `EntrenamientoSimulado` (no forman parte de los diagramas). |
| `src/main/java/orquitas/simulacion` | `EntrenamientoDemostracion` (modelo provisional sintético) y `bahia` (simulador 2D: `Bahia`, `RobotSimulado`, `SensorSimulado`, `SimuladorBahia`, `Maestro`, `Piloto`, `VisorBahia`). |
| `arduino/Orquita/Orquita.ino` | Sketch del robot. |
| `entrega-java/` | Lo que se sube a ClassRoom: los `.java` planos del Servidor, de las herramientas y de la bahía simulada (marcados `// Archivo de simulacion`), y `Orquita.ino`. |
| `docs/historico/` | Material de los hitos anteriores (no usar como versión actual). |
