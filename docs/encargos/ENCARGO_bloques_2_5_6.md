# Encargo para Claude Code — Bloques 2, 5 y 6 de `mejoras.md`

Fecha: 13/11/2026. Repo `lautaro-geli/sistema-orquita`, rama base **`hito3-simulador-bahia`** (último commit al escribir esto: el que agrega este archivo).
Trabajar en una rama nueva **`hito3-bloques-2-5-6`** creada desde `hito3-simulador-bahia`, con **un commit por bloque, en este orden: 2, 5, 6**. Hacer push de la rama. No hacer merge.

Idioma del código, comentarios, commits y documentos: **español** (como el resto del repo).

---

## 0. Contexto mínimo del proyecto

Robot "Orquita" (Arduino UNO + VL53L1X sobre servo + HC-06) que explora la "Bahía de Caza", captura 3 canicas ("Lobitos") y sale por un estrecho de 30 cm. El Servidor (Java, PC) recibe la telemetría por Bluetooth y decide cada movimiento con redes de **Neuroph 2.98** (obligatorio por la consigna: la clasificación del entorno la hace una red de Neuroph; la estrategia de navegación es de libre diseño).

### Construir y probar

- Java 17 (`maven.compiler.release` 17), Maven, JUnit 4.13.2, Neuroph `com.github.neuroph:neuroph-core:2.98`, jSerialComm 2.11.4.
- `mvn -B clean verify` corre todas las pruebas (hoy **77**, todas en verde). CI: `.github/workflows/pruebas.yml` (Ubuntu Java 21 y Windows Java 17, más un humo de `preparar.ps1`/`ejecutar.ps1`).
- Entrenar en la bahía simulada: `java -cp "target/classes:target/dependency/*" orquitas.herramientas.EntrenamientoSimulado [episodios=250] [epocas=60] [carpeta=modelos] [rondasDagger=2]` (en Windows: `./ejecutar.ps1 simular-entrenar ...` después de `./preparar.ps1`). Escribe `percepcion.nnet`, `movimiento.nnet` y un reporte `docs/evidencias/entrenamiento_simulado_AAAAMMDD.md` (el nombre lleva la fecha: si se corre varias veces el mismo día, se pisa; ver §5).
- Visor: `orquitas.simulacion.bahia.VisorBahia [semilla] [--maestro] [--percepcion=ruta --movimiento=ruta] [--png=archivo]`.

### Convenciones que hay que respetar

- Los archivos de simulación (paquete `orquitas.simulacion.bahia`, `EntrenamientoSimulado` y sus pruebas) llevan como **primera línea** `// Archivo de simulacion`.
- `entrega-java/` es una copia plana de **todos** los `.java` de `src/main/java/orquitas/servidor`, `src/main/java/orquitas/herramientas` y `src/main/java/orquitas/simulacion/bahia`, más `arduino/Orquita/Orquita.ino`. Hay que mantenerla idéntica (copiar después de cada cambio).
- Sin dependencias nuevas. Sin POO en el sketch (no se toca el sketch en este encargo).
- La normalización y el armado de entradas se hacen con **una sola función** que usan el entrenamiento y el Servidor (no duplicar lógica).
- **No commitear modelos `.nnet`** en este encargo (los entrena el equipo en su PC con Neuroph 2.98). Si el entorno tiene Maven Central y se entrena con la 2.98 real, igual dejarlos fuera del commit y adjuntar solo los reportes.

### Cómo funciona hoy (lo que hay que cambiar está en §2–§4)

```
TELEMETRIA|39 valores (13 sectores 0°..180° cada 15° × ROI 4x4, 8x8, 16x16; -1 = sin eco; sector 0 = derecha, 6 = frente, 12 = izquierda)
  └─► PreprocesadorTelemetria.corregir  (regla del -1: 4x4/16x16 sin eco toman el 8x8; 8x8 sin eco = desconocido; los tres sin eco = desconocidos)
  └─► PreprocesadorTelemetria.entradas  (117: 39 cercanías + 39 marcas de desconocido + 3 rasgos por sector; cercanía = clamp((1300 − d)/1300, 0, 1), desconocido = 0)
  └─► RedPercepcion (por ventana: 46 → 24 → 5, aplicada a los 13 sectores) → probabilidades[65] = 13 × {LIBRE, PARED, OBSTACULO, LOBITO, SALIDA}
  └─► RedMovimiento (184 → 48 → 15): entradas = 117 + 65 + 2 (modo BUSQUEDA/ESCAPE)
        salidas = 14 clases (sector meta 0..12 o ATRAS) + 1 distancia normalizada → MetaMovimiento → OrdenMovimiento(mov, ms)
  └─► ReflejoSeguridad.ajustar  (acorta avances / se aleja antes de un giro que tocaría)
  └─► DetectorAtasco.revisar    (si el barrido no cambió tras moverse, cambia la orden)
  └─► ORDEN|movimiento|ms
```

Clases clave (`src/main/java/orquitas/...`):

| Clase | Rol |
|---|---|
| `servidor/navegacion/Navegacion` | Orquesta: con redes (`RedPercepcion` + `RedMovimiento`) o con reglas (`RedNeuronal`, paso fijo). Detecta LIBERADA en ESCAPE con `campoLibre`. |
| `servidor/navegacion/PreprocesadorTelemetria` | Regla del -1, `entradas`, `entradasVentana`. |
| `servidor/navegacion/RedPercepcion`, `RedMovimiento`, `MetaMovimiento`, `OrdenMovimiento` | Redes y traducción de la salida. |
| `servidor/navegacion/ReflejoSeguridad`, `DetectorAtasco`, `CalibracionRobot` | Seguridad, atasco, medidas del robot (250 mm/s, 120 °/s, 200 × 150 mm, sensor 30 mm detrás del frente). |
| `simulacion/bahia/Bahia` | Geometría (hoy media elipse 1150 × 1200, estrecho 300 mm), obstáculos, canicas, `lanzarRayo`. |
| `simulacion/bahia/RobotSimulado`, `SensorSimulado`, `SimuladorBahia` | Robot, sensor y prueba completa (puntaje del reglamento, 7 min, 4,8 s por barrido). |
| `simulacion/bahia/Maestro`, `Piloto`, `VisorBahia` | Piloto con la geometría real (profesor), pilotos (maestro o redes) y visor. |
| `herramientas/EntrenamientoSimulado` | Datos del maestro → percepción → movimiento (con balanceo de clases) → 2 rondas DAgger → evaluación → reporte. |

### Resultados de referencia (bahía vieja, antes de este encargo; 100 bahías, semillas 900000–900099)

| Piloto | Misión completa | Capturas | Choques | Puntaje |
|---|---|---|---|---|
| Maestro | 36 % | 1,98 | 0,32 | 35,9 |
| Redes (184 entradas, sector meta, con balanceo) | 2 % | 1,26 | 2,50 | −0,2 |

Diagnóstico: la percepción anda (≈ 84 % por sector), la red de movimiento no aprende bien a ir hacia la meta y tragarla desde 184 números, y no tiene memoria. Los bloques 5 y 6 atacan eso; el bloque 2 hace la bahía igual a la real.

---

## 1. Bloque 2 — Bahía con las medidas reales (commit 1)

### Geometría (reemplaza la media elipse)

- Rectángulo de **1520 mm de ancho × 1140 mm de alto**, origen en el centro de la base: x ∈ [−760, 760], y ∈ [0, 1140]. La base es y = 0 y la salida está arriba (y = 1140).
- **Las 4 esquinas redondeadas con radio R = 150 mm** (arcos de cuarto de circunferencia; centros en (±610, 150) y (±610, 990)). Lados rectos entre los arcos.
- **Salida: hueco de 300 mm centrado en el lado de arriba** (x ∈ [−150, 150], y = 1140). Sin pasillo. **Afuera no hay nada** (los rayos que salen por el hueco no tocan nada).
- Aproximar cada arco con segmentos (por ejemplo 8 por esquina). El segmento virtual `salida` va de (−150, 1140) a (150, 1140).
- Todo como constantes con nombre: `BASE_MM = 1520`, `ALTO_MM = 1140`, `RADIO_ESQUINA_MM = 150`, `SALIDA_MM = 300`.
- `adentro(x, y)` / `adentroConMargen(x, y, m)`: dentro del rectángulo redondeado achicado en m. `afuera(x, y)`: pasó el hueco (y > 1140 + 60 y |x| dentro de lo razonable).
- Obstáculos: **siempre 2** (`Bahia.OBSTACULOS = 2`, ya existe), de 90 × 90 mm, girados al azar. Canicas: 3, de 16 mm. Revisar las restricciones de ubicación con la bahía nueva (márgenes a paredes, lejos de la partida, no delante del estrecho, separación entre obstáculos de al menos ~320 mm entre centros para que pase el robot).

### Partida

- El robot arranca **abajo a la izquierda, mirando hacia la salida (rumbo 90°)**, con su **costado izquierdo a 150–200 mm de la pared izquierda** y su **cola a 150–200 mm de la base** (interpretación elegida; el equipo dijo "a 15–20 cm de la pared izquierda y de la base" y falta confirmar si es al costado del robot o a su centro: dejarlo como constante fácil de cambiar y anotarlo como [A CONFIRMAR]).
- Con el robot de 150 × 200 mm: centro en x = −760 + 75 + U(150, 200), y = 100 + U(150, 200), rumbo 90° ± U(3°). Sortear con el `Random` de la prueba (reproducible por semilla).
- Constantes `PARTIDA_X`, `PARTIDA_Y` (centro del rango) y el rango de sorteo; `SimuladorBahia.alAzar` las usa. Mantener el chequeo de que la pose inicial no choque.

### Lo que no cambia

`SensorSimulado`, `RobotSimulado`, `Maestro`, el reflejo, el atasco y las redes no dependen de la forma; solo verificar que el maestro siga funcionando (ver §5, corrida A).

### Pruebas a ajustar o agregar

- `SimuladorBahiaTest.geometriaDeLaBahia`: desde (0, 100) hacia abajo toca la base a 100 mm; desde (0, 1) hacia la derecha toca la pared a ~760 mm; desde el centro hacia arriba cruza la salida; un rayo desde (−700, 1080) a 45° hacia arriba-izquierda toca el arco (no se escapa por la esquina).
- Nueva: las 4 esquinas son curvas (un punto en (−755, 5) está **afuera** de la bahía por el redondeo; (−600, 150) está adentro).
- Nueva: 30 bahías al azar → 2 obstáculos, 3 canicas, la partida dentro del rango y sin chocar.
- Actualizar las pruebas que dependían de 575/1200 (por ejemplo `conElUmbralCumplidoPasaAEscapeYElMaestroSaleDeLaBahia`, `escenaMixta...`) para la bahía nueva, sin cambiar lo que comprueban.

---

## 2. Bloque 5 — Representación intermedia (commit 2)

Clase nueva **`orquitas.servidor.navegacion.RepresentacionIntermedia`** (no es una red: reglas fijas). La red de movimiento deja de recibir las 184 entradas y recibe estas 14 (más las 10 de memoria del bloque 6).

### Firma sugerida

```java
public static double[] calcular(int[] telemetria, double[] probabilidadesPercepcion /*65*/, ModoOperativo modo); // → double[14]
public static String describir(double[] r, ModoOperativo modo); // texto para la consola, por ejemplo "Lobito a +30°, 25 cm (0,99); frente libre; izq. cerrado"
public static final int ENTRADAS = 14;
```

### Definiciones exactas (todas en [0, 1])

Notación: `c = PreprocesadorTelemetria.corregir(telemetria)`; `cerc(d) = d desconocido ? 0 : clamp((1300 − d)/1300, 0, 1)`; `p[s][e]` = probabilidad de la etiqueta e en el sector s; `etiqueta[s]` = argmax de p[s]; ángulo del sector s = 15·s − 90 grados (negativo = derecha, positivo = izquierda). **Meta M** = LOBITO en BUSQUEDA, SALIDA en ESCAPE (en LIBERADA no se usa: devolver la meta como ausente).

| # | Nombre | Fórmula |
|---|---|---|
| 0 | hayMeta | s* = argmax_s p[s][M]; 1 si p[s*][M] ≥ 0,5, si no 0 |
| 1 | anguloMeta | si hayMeta: promedio de los ángulos de s*−1, s*, s*+1 (los que existan) ponderado por p[s][M], llevado a [0,1] con (a + 90)/180; si no: 0,5 |
| 2 | distanciaMeta | si hayMeta: max(cerc(c[s*·3+0]), cerc(c[s*·3+1])) (4x4 y 8x8); si no: 0 |
| 3 | confianzaMeta | p[s*][M] (aunque sea < 0,5) |
| 4 | anchoMeta | si hayMeta: largo de la racha de sectores contiguos con p[s][M] ≥ 0,5 que contiene a s*, dividido 13; si no: 0 |
| 5 | obstaculoFrente | zona {5, 6, 7} |
| 6 | obstaculoDiagonalIzq | zona {8, 9, 10} |
| 7 | obstaculoDiagonalDer | zona {2, 3, 4} |
| 8 | obstaculoCostadoIzq | zona {11, 12} |
| 9 | obstaculoCostadoDer | zona {0, 1} |
| 10 | ladoMasAbierto | libreIzq = 1 − max(#6, #8); libreDer = 1 − max(#7, #9); valor = (libreIzq − libreDer + 1)/2 |
| 11 | modoBusqueda | 1 si modo = BUSQUEDA |
| 12 | modoEscape | 1 si modo = ESCAPE |
| 13 | fraccionDesconocidos | cantidad de sectores con los 3 ROI desconocidos / 13 |

**Zona** (#5–#9): máximo, sobre los sectores s de la zona **con etiqueta[s] ≠ M**, de max(cerc(c[s·3+1]), cerc(c[s·3+2])) (8x8 y 16x16). Si no queda ningún sector: 0.

### Prueba de referencia obligatoria (valores exactos)

Telemetría (ejemplo real del simulador, robot recién salido con un Lobito de frente):

```
-1,685,650,975,929,-1,561,529,527,421,406,465,972,880,718,979,1031,1023,571,662,713,665,672,590,412,388,383,280,299,281,-1,234,238,201,207,191,190,192,177
```

Percepción falsa one-hot (probabilidad 1 en la etiqueta, 0 en el resto) con estas etiquetas por sector 0..12:
`OBSTACULO, PARED, OBSTACULO, OBSTACULO, PARED, SALIDA, LOBITO, PARED, PARED, PARED, PARED, PARED, PARED`; modo BUSQUEDA.

Resultado esperado (tolerancia 1e-4):

```
[1, 0.5, 0.560769, 1, 0.076923, 0.546154, 0.82, 0.687692, 0.863846, 0.5, 0.411923, 1, 0, 0]
```

(distanciaMeta = cerc(571); frente = cerc(590) del sector 7, el 6 se excluye por ser la meta; diag. izq. = cerc(234); diag. der. = cerc(406); costado izq. = cerc(177); costado der. = cerc(650); lado = (0,136154 − 0,312308 + 1)/2.)

Otras pruebas: sin meta (todo PARED) → hayMeta 0, ángulo 0,5, distancia 0, ancho 0; dos Lobitos (sectores 2 y 10, probabilidades 0,9 y 0,6) → toma el sector 2 y el ángulo no se promedia con el 10; ESCAPE con SALIDA en 5, 6, 7 → ancho 3/13 y ángulo 0,5; sector con los 3 ROI en -1 → cuenta en fraccionDesconocidos; LIBERADA → meta ausente.

---

## 3. Bloque 6 — Memoria (commit 3)

Clase nueva **`orquitas.servidor.navegacion.MemoriaNavegacion`**, una por misión (se crea nueva con cada `Navegacion` y con cada `Piloto.redes`; `reiniciar()` la deja como al principio).

### Ciclo (el orden importa)

1. Llega la telemetría T_t.
2. `R_t = RepresentacionIntermedia.calcular(T_t, percepción, modo)`.
3. `memoria.observar(T_t, R_t)` — actualiza lo que depende de lo que se ve ahora (meta y atasco).
4. Entradas de la red de movimiento = `R_t` (14) ++ `memoria.entradas()` (10) → **24**.
5. Red de movimiento → `MetaMovimiento` → orden → `ReflejoSeguridad` → `DetectorAtasco` → **orden final** (la que se envía).
6. `memoria.registrarOrden(ordenFinal)` — actualiza lo que depende de lo que se hizo.

### Las 10 entradas (índices 14–23 de la entrada completa; todas en [0, 1])

| # | Nombre | Definición |
|---|---|---|
| 14–17 | ordenAnterior | one-hot de la última orden final: AVANZAR, RETROCEDER, GIRAR_IZQ, GIRAR_DER (DETENER o ninguna = todo 0) |
| 18 | cuantoAnterior | ms de esa orden / 1500 |
| 19 | atasco | min(k, 3)/3, con k = barridos seguidos "iguales" al anterior después de una orden distinta de DETENER. "Iguales" = `DetectorAtasco.iguales(anteriorCorregida, actualCorregida)` (hacerlo accesible). Se calcula en `observar` (la memoria guarda la telemetría corregida anterior). |
| 20 | cicloDesdeMeta | n = ciclos desde la última vez que R tuvo hayMeta = 1 (0 si la ve ahora); valor = min(n, 5)/5; al principio (nunca la vio) = 1 |
| 21 | anguloMetaAnterior | ángulo a (grados, relativo al robot, + = izquierda) de la meta la última vez que se vio. En `observar`, si hayMeta: a = ángulo actual de la meta. En `registrarOrden`: GIRAR_IZQ de θ grados → a −= θ; GIRAR_DER → a += θ (θ = ms · CalibracionRobot.GRADOS_POR_SEGUNDO / 1000); acotar a [−180, 180]. Valor = (a + 180)/360; si nunca se vio: 0,5 |
| 22–23 | rumboEstimado | ψ = giro acumulado desde la partida (0 al empezar; GIRAR_IZQ suma θ, GIRAR_DER resta θ); valores (sen ψ + 1)/2 y (cos ψ + 1)/2 |

La meta cambia de LOBITO a SALIDA al pasar a ESCAPE: en ese momento reiniciar `cicloDesdeMeta` y `anguloMetaAnterior` (no reiniciar el rumbo).

### Integración

- `RedMovimiento.ENTRADAS = 24`; `RedMovimiento.entradas(double[] representacion, double[] memoria)` (una sola función para entrenar y decidir); la capa oculta pasa a **20** (`OCULTAS_MOVIMIENTO` en `EntrenamientoSimulado`). Salidas sin cambios (15).
- `Navegacion`: tiene una `MemoriaNavegacion`; sigue el ciclo de arriba. Imprime en consola una línea por ciclo con `RepresentacionIntermedia.describir` y lo principal de la memoria (por ejemplo `[Navegación] Lobito a +30°, 25 cm; frente libre | [Memoria] última GIRAR_IZQ 250 ms; meta vista hace 0 ciclos; rumbo +40°`).
- `DetectorAtasco` sigue actuando como seguridad por fuera de la red (no se quita).
- `Piloto.redes`: una `MemoriaNavegacion` por piloto, mismo ciclo. `EntrenamientoSimulado.evaluar` ya crea un piloto por prueba.
- **Entrenamiento (lo más delicado):** la memoria depende de la secuencia de la prueba y de la percepción, que se entrena antes que el movimiento. Por eso:
  - Guardar los ejemplos **agrupados por episodio y en orden**, con la telemetría, el modo, las etiquetas verdaderas, la orden del maestro (etiqueta de entrenamiento) y **la orden realmente ejecutada** (puede ser la al azar, la de la red en DAgger o la del maestro).
  - `datosMovimiento` **reproduce** cada episodio en orden con la red de percepción ya entrenada: calcula R_t, hace `observar`, arma las 24 entradas, y llama `registrarOrden(ordenEjecutada)`. Así la memoria del entrenamiento se calcula exactamente igual que en el Servidor.
  - El balanceo de clases (`REPETICION_MAXIMA`) se mantiene.
- `CalibracionRobot` no cambia.

### Pruebas

- Golden de `RepresentacionIntermedia` (§2) y las demás de §2.
- `MemoriaNavegacion`: estado inicial (`[0,0,0,0, 0, 0, 1, 0.5, 0.5, 1]`); tras GIRAR_IZQ de 90° (750 ms con 120 °/s): one-hot correcto, cuanto = 0,5, ψ = +90° → (1, 0,5); meta vista a +30° y después GIRAR_IZQ 30° → anguloMetaAnterior vuelve a ~0° (0,5); cicloDesdeMeta crece 0 → 0,2 → … → 1 y se satura; atasco sube con barridos iguales tras moverse y vuelve a 0 cuando cambia; cambio a ESCAPE reinicia la meta y no el rumbo; `reiniciar()`.
- `Navegacion` con redes falsas: las entradas que recibe la red de movimiento tienen largo 24 y las últimas 10 cambian después de una orden.
- `RedMovimiento` rechaza modelos que no tengan 24 entradas.

---

## 4. Documentación a actualizar

- `README.md` (sección "Bahía simulada"): medidas y forma de la bahía, partida, la representación intermedia (14) y la memoria (10), red de movimiento 24 → 20 → 15.
- `mejoras.md`: marcar los bloques 2, 5 y 6 como ✅ implementados (con fecha y rama) y anotar cualquier desvío de este encargo.
- `modelos/README.md`: el `percepcion.nnet` del repo se entrenó con la bahía vieja (sirve, pero conviene reentrenar); el `movimiento.nnet` nuevo tiene 24 entradas (incompatible con cualquiera anterior).
- `docs/PROTOCOLO.md` no cambia (el protocolo no se toca). El sketch no se toca.
- `entrega-java/` sincronizada.

---

## 5. Lo que tenés que entregar (para que se pueda evaluar)

Al terminar, dejar en la rama y resumir en el último mensaje:

1. **Rama `hito3-bloques-2-5-6` con 3 commits** (bloque 2, 5, 6) y push hecho. Lista de archivos cambiados por commit.
2. **`mvn -B clean verify` en verde** después de cada commit, con la cantidad de pruebas (hoy 77) y la lista de pruebas nuevas con una línea de qué comprueba cada una.
3. **Tres corridas comparables**, mismas semillas y mismo presupuesto (`EntrenamientoSimulado 300 80 <carpeta> 2`), todas en la **bahía nueva**:
   - **A** = después del commit 1 (bahía nueva, red de movimiento como hoy: 184 entradas, sin memoria).
   - **B** = después del commit 2 (14 entradas, sin memoria).
   - **C** = después del commit 3 (24 entradas, con memoria).
   Cada una deja su reporte; renombrarlos a `docs/evidencias/entrenamiento_simulado_<fecha>_A_bahia_nueva.md`, `..._B_representacion.md`, `..._C_memoria.md` y commitearlos (solo los reportes, **no** los `.nnet`). Indicar si se usó Neuroph 2.98 de Maven o no.
4. **Tabla resumen A/B/C** (más el maestro en la bahía nueva): misión completa, capturas, choques, puntaje, segundos, acuerdo de dirección red/maestro, acierto de percepción por clase, tiempo de entrenamiento.
5. **Imágenes del visor** (`--png`) de las semillas 900000, 900001 y 900002 con el maestro y con las redes de C, y una de la bahía vacía (para revisar la geometría: esquinas redondeadas, hueco, partida). Guardarlas en `docs/evidencias/bloques_2_5_6/` y commitearlas.
6. **Extracto de consola** de ~15 ciclos de una prueba simulada con las redes de C, con las líneas `[Navegación]` y `[Memoria]` (archivo de texto en la misma carpeta).
7. **Desvíos**: todo lo que no se pudo hacer como dice este encargo, o se hizo distinto, y por qué.

### Cómo lo voy a evaluar

- Que la geometría coincida con §1 (imagen de la bahía vacía y pruebas).
- Que la prueba de referencia de §2 dé exactamente los valores indicados.
- Que la memoria siga el ciclo de §3 y que el entrenamiento **reproduzca** los episodios (no que calcule la memoria con datos que el Servidor no tiene).
- Que no haya lógica duplicada entre entrenamiento y Servidor.
- Que `entrega-java/` esté idéntica a las fuentes y que los archivos de simulación tengan la marca.
- Resultados: C debería superar a A en capturas y puntaje con choques iguales o menores. Si no mejora, no es un error del encargo, pero tiene que estar reportado honestamente con las tres corridas.
