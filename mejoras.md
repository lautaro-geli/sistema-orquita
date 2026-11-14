# Mejoras para que la red neuronal ande lo mejor posible

Lista de lo que hay que tener, por bloques. Acá solo se anota; se implementa más adelante, de a un bloque.
Estado: ✅ está · 🟡 a medias · ❌ falta.

---

## Bloque 2 — Un simulador fiel a la pista

### 2.1 Geometría de la bahía ❌ (las medidas cambiaron)

Medidas reales de la pista (13/11/2026, aproximadas):

- **Base recta: 152 cm** (1520 mm). Hoy el simulador usa 115 cm.
- **De la base recta a la salida: 114 cm** (1140 mm). Hoy el simulador usa 120 cm.
- **Forma: un rectángulo con curvas en vez de esquinas**, todo dentro del rectángulo de 152 × 114 cm. **No tan circular** como la media elipse actual: los costados y el fondo tienen que ser casi rectos, y solo las esquinas redondeadas.
- Salida (estrecho) de 30 cm en el centro del lado de enfrente de la base (se mantiene).
- 2 obstáculos de ~9 × 9 cm y canicas como Lobitos (se mantiene).

Cómo modelarlo (propuesta, sin implementar):

- Opción A: rectángulo de 1520 × 1140 mm con esquinas redondeadas de radio R (arcos de circunferencia unidos por tramos rectos).
- Opción B: superelipse |x/760|^n + |y/1140|^n = 1 (mitad superior), con n ≈ 4 a 6. Cuanto mayor n, más parecida a un rectángulo.
- Cualquiera de las dos queda parametrizada (ancho, alto, radio o n) para ajustarla cuando se midan bien las curvas.

Confirmado por el equipo (13/11/2026):

- **Curvas: radio de ~15 cm** en las esquinas → conviene la Opción A (rectángulo con esquinas redondeadas de R = 150 mm).
- **Las 4 esquinas son curvas**, también las dos de la base.
- **La salida es solo un hueco de 30 cm**, sin pasillo. Del otro lado no hay nada relacionado con la competencia: para el sensor, mirar por el hueco es "nada al alcance" (`-1` o lejos).
- **Partida: a unos 15–20 cm de la pared izquierda y de la base**, mirando hacia la salida. Con la base de 1520 mm (x de -760 a 760) y el robot de 150 mm de ancho y 200 mm de largo, si los 15–20 cm se miden desde el costado y la cola del robot, el centro queda en x ≈ -760 + 175 + 75 = -510 mm, y ≈ 175 + 100 = 275 mm. [A CONFIRMAR si los cm son al costado del robot o a su centro.] En el simulador conviene sortear la partida dentro de ese rango (15 a 20 cm) y con unos ±3° de rumbo.

Todavía a confirmar:

- [ ] Altura de las paredes y de los obstáculos frente a la altura del sensor (que el haz no pase por encima).
- [ ] Material/color de paredes, obstáculos y canicas (cuánto reflejan el láser).
- [ ] Zonas donde nunca se ponen obstáculos o canicas (por ejemplo, delante de la salida o de la partida).

Consecuencias cuando se cambie:

- Cambian `Bahia` (paredes, salida, `adentro`, generación de obstáculos y canicas) y la partida en `SimuladorBahia`.
- El maestro y el reflejo no dependen de la forma, pero hay que volver a medir al maestro (techo) en la bahía nueva.
- Hay que **volver a entrenar** las dos redes: las actuales aprendieron una bahía más chica y más redonda.
- Actualizar las pruebas de geometría (`SimuladorBahiaTest`) y el README.

### 2.2 Sensor simulado igual al real 🟡

- Hoy: 13 sectores × 3 ROI (conos de 15°/20°/27° [A CONFIRMAR]), promedio ponderado por señal, ruido, `-1` fuera de 40–1300 mm, pérdidas y bloques sin eco. Se parece a los barridos reales en el patrón canica/pared, pero **no está contrastado**.
- Hay que tener: comparación sistemática simulado vs real en las mismas escenas (pared plana a varias distancias, obstáculo, canica a 5/10/20/30 cm, mirando la salida), con el sensor nivelado. Ajustar con eso el ruido, la tasa de `-1`, los conos de cada ROI y la reflectividad de la canica.

### 2.3 Robot simulado igual al real 🟡

- Hoy: 200 × 150 mm, 250 mm/s, 120 °/s, boca de 60 mm, sensor 30 mm detrás del frente, 10 % de ruido de velocidad, todo estimado.
- Hay que tener: las medidas reales (bloque 1, calibración) y, si se puede, el error real de los giros y avances (cuánto varía de una vez a otra) para usarlo como ruido.

---

## Bloque 5 — Representación intermedia (entre percepción y movimiento) ❌

**Decisión del equipo (13/11/2026): se implementa como se describe abajo.** Encargo detallado para Claude Code: `docs/encargos/ENCARGO_bloques_2_5_6.md`.

### Idea

Hoy la red de movimiento recibe 184 números (117 de telemetría + 65 de percepción + 2 de modo) y tiene que deducir sola dónde está la meta. Los sistemas que funcionan separan el problema en módulos: la percepción dice **qué hay**, un paso intermedio resume **dónde está lo importante** en unos pocos números, y la red de movimiento decide con eso (por ejemplo, el TurtleBot3 de ROBOTIS recibe 24 lecturas del láser + distancia y ángulo al objetivo).

```
39 distancias ──► regla del -1 ──► Red de percepción ──► Representación intermedia ──► Red de movimiento ──► Reflejo + atasco ──► ORDEN|mov|ms
                                   (13 etiquetas)          (~14 números, calculados)     (sector meta + mm)
```

La representación intermedia **no es una red**: la calcula el Servidor con reglas fijas a partir de las etiquetas y probabilidades de la percepción y de las distancias corregidas. Es la misma función para entrenar y para decidir.

### Qué números tendría (propuesta, ~14 entradas, todas en [0, 1])

**Meta** (en BUSQUEDA la meta es LOBITO; en ESCAPE es SALIDA):

| Entrada | Cómo se calcula |
|---|---|
| hayMeta | 1 si algún sector tiene la etiqueta meta con probabilidad ≥ 0,5; si no, 0. |
| ánguloMeta | Ángulo del sector meta más probable, afinado con el promedio ponderado por probabilidad de ese sector y sus 2 vecinos (así no se promedian dos Lobitos separados). De −90° (derecha) a +90° (izquierda), llevado a [0, 1]. Si no hay meta: 0,5. |
| distanciaMeta | Cercanía del 4x4/8x8 de ese sector (la canica se ve mejor en el haz angosto). Si no hay meta: 0. |
| confianzaMeta | Probabilidad de la percepción en ese sector. |
| anchoMeta | Cuántos sectores seguidos tienen la etiqueta meta (sirve para la salida: alineado con el hueco se ven 2–3 sectores), dividido por 13. |

**Espacio libre** (cercanía de lo más cercano que no sea la meta, por zona; 1 = pegado, 0 = libre):

| Entrada | Sectores |
|---|---|
| obstáculoFrente | 5, 6, 7 |
| obstáculoDiagonalIzq | 8, 9, 10 |
| obstáculoDiagonalDer | 2, 3, 4 |
| obstáculoCostadoIzq | 11, 12 |
| obstáculoCostadoDer | 0, 1 |
| ladoMásAbierto | (libre izquierda − libre derecha), llevado a [0, 1] |

**Estado:**

| Entrada | Valor |
|---|---|
| modoBusqueda / modoEscape | 0/1 (como hoy) |
| fracciónDesconocidos | sectores sin dato / 13 (sensor fallando o mirando por el hueco) |

(La memoria —orden anterior y atasco— es el bloque 6 y se sumaría acá como entradas extra.)

### Salida de la red de movimiento

Se mantiene la de ahora: **sector meta (13 sectores) o ATRAS, más distancia en mm** (`MetaMovimiento`), porque:

- Es una clasificación: si el maestro a veces gira a la izquierda y a veces a la derecha en situaciones parecidas, una salida continua de "ángulo" promediaría y daría "recto" (mal); con clases no pasa.
- Redondear a sectores de 15° casi no le cuesta al maestro (35 % contra 36 % de misiones completas).

La red quedaría chica, por ejemplo **14 → 16 → 15**, en lugar de 184 → 48 → 15.

### Qué se gana

- La red aprende una relación casi directa ("la meta está a +30° → sector 8"), que es lo que hace que funcionen los proyectos de "buscar un objetivo".
- Entrena mucho más rápido (minutos en lugar de ~35) y con menos ejemplos.
- Se puede probar con pruebas JUnit sin red (dado un barrido armado a mano, el ángulo de la meta tiene que dar tal valor).
- Se puede mostrar en la consola del Servidor (`[Navegación] Lobito a +30°, 25 cm; frente libre`), lo que ayuda a entender qué decide el robot en la pista y sirve para la documentación.
- Si el sensor real se ve distinto que el simulado, alcanza con corregir la percepción: la red de movimiento no cambia.

### Riesgos y cómo cubrirlos

- **Se pierde información** que no entra en los ~14 números. Para cubrirlo, se puede comparar contra la versión actual (184 entradas) en las mismas 100 bahías; si rinde peor, se agregan entradas.
- **Si la percepción se equivoca, la representación también** (error que se propaga). Por eso se entrena la red de movimiento con la representación calculada desde la percepción de la red (no desde la verdad del simulador), como hoy.
- **Dos metas a la vez** (dos Lobitos): se toma la más probable y, a igualdad, la más cercana.

### Dónde viviría (para cuando se implemente)

- Clase nueva `RepresentacionIntermedia` en `orquitas.servidor.navegacion` (una función estática `calcular(telemetria, probabilidadesPercepcion, modo)` → `double[14]`, más un `toString` legible para la consola).
- `RedMovimiento.entradas(...)` pasaría a usar esa función.
- `EntrenamientoSimulado` no cambia de estructura; solo cambia el tamaño de la red.
- Pruebas: ángulo y distancia de la meta en barridos armados a mano, meta ausente, dos metas, modo escape con la salida.

---

## Bloque 6 — Memoria ❌ (hoy solo hay memoria fuera de la red: el DetectorAtasco)

**Decisión del equipo (13/11/2026): la red de movimiento va a tener memoria explícita, como se describe abajo.** Pendiente de implementar; encargo en `docs/encargos/ENCARGO_bloques_2_5_6.md`.

### Por qué hace falta

La red de movimiento decide mirando **un solo barrido**. Sin memoria:

- **Pierde el Lobito justo antes de tragarlo.** Cuando la canica queda a menos de 4 cm, o debajo y delante del sensor, el VL53L1X da `-1` o deja de verla. La red cree que no hay nada y se pone a explorar, en vez de seguir avanzando 5 cm más.
- **Oscila.** Gira a la izquierda, en el barrido siguiente ve algo parecido y gira a la derecha, y así sigue.
- **Se traba en esquinas.** Es el problema clásico de los robots que solo reaccionan al presente; el detector de atasco lo tapa desde afuera, pero la red no aprende a evitarlo.
- **No sabe hacia dónde queda la salida** si no la está viendo, aunque la tenía enfrente al arrancar.

El maestro no tiene memoria, pero hace "trampa" (conoce la posición de todo). La memoria le da a la red parte de esa información que el sensor no muestra.

### Cómo sería (propuesta: memoria explícita, no red recurrente)

Un objeto `MemoriaNavegacion`, uno por misión, que el Servidor actualiza en cada ciclo con lo que **ya sabe**: la orden que mandó y lo que vio. Sus valores se suman como entradas a las 14 del bloque 5.

**Qué recuerda** (~10 entradas, todas en [0, 1]):

| # | Entrada | Qué es | Para qué sirve |
|---|---|---|---|
| 15–18 | **ordenAnterior** | La última orden, una entrada por tipo: avanzar, retroceder, girar izq., girar der. (1 en la que fue) | Evitar oscilar (no deshacer lo que acaba de hacer sin motivo) |
| 19 | **cuántoAnterior** | Cuánto duró esa orden (ms / 1500) | Distinguir un ajuste chico de un giro grande |
| 20 | **atasco** | Barridos seguidos sin cambios después de moverse (el mismo cálculo del DetectorAtasco), acotado | Que la red aprenda a salir de los atascos, no solo el reflejo |
| 21 | **cicloDesdeMeta** | Ciclos desde la última vez que vio la meta (0 = la ve ahora; 1 = hace 5 o más) | Seguir hacia el Lobito un par de ciclos aunque deje de verlo de cerca |
| 22 | **ánguloMetaAnterior** | Dónde estaba la meta la última vez que la vio, corregido por los giros hechos desde entonces | Saber hacia dónde ir si la perdió de vista |
| 23–24 | **rumboEstimado** (seno y coseno) | Hacia dónde mira el robot respecto de la partida, sumando los giros que mandó el Servidor (odometría por órdenes) | En escape: saber hacia dónde queda la salida aunque no se vea (al arrancar mira hacia ella) |

Total con el bloque 5: **14 + 10 = 24 entradas**. La red seguiría siendo chica (por ejemplo 24 → 20 → 15).

### Detalles importantes

- **Se reinicia** al empezar cada misión (el Servidor ya vacía el estómago al arrancar; la memoria también).
- **El rumbo estimado acumula error**: cada giro real difiere un poco del pedido. Sirve como pista, no como verdad, y mejora mucho con la calibración real (bloque 1). Se puede limitar a "hacia qué mitad mira" (frente o espalda) si el error es muy grande.
- **Hay que entrenarla con memoria.** En la simulación, cada prueba tiene su propia `MemoriaNavegacion`, calculada igual que en el Servidor y con las órdenes que realmente se ejecutaron (incluidas las de la red en las rondas DAgger). Si se entrena sin memoria y se usa con memoria, no sirve.
- **El maestro no cambia**: sigue decidiendo con la geometría real; la red usa la memoria para acercarse a lo que el maestro "sabe".
- Al cambiar las entradas hay que volver a entrenar (el `movimiento.nnet` anterior deja de ser compatible).

### Alternativa descartada por ahora: red recurrente

Una red que guarde su propio estado de un ciclo al siguiente (Elman o Jordan) aprende sola qué recordar, pero se entrena mucho peor con Neuroph (sin retropropagación en el tiempo) y es más difícil de explicar y depurar. La memoria explícita es más simple, se puede probar con JUnit y mostrar en la consola (`[Memoria] vio el Lobito hace 1 ciclo a +15°; rumbo +40° desde la partida`).

### Dónde viviría (para cuando se implemente)

- Clase nueva `MemoriaNavegacion` en `orquitas.servidor.navegacion`: `actualizar(ordenEnviada, representacionActual)`, `entradas()` → `double[10]`, `reiniciar()`.
- `Navegacion` tendría una instancia y la actualizaría después de decidir cada orden; el `DetectorAtasco` pasaría a alimentar la entrada `atasco`.
- `Piloto.redes` (simulación) tendría otra, una por prueba.
- Pruebas: oscilación (izq. → der.) visible en `ordenAnterior`; meta perdida y `cicloDesdeMeta` que crece; giros acumulados en `rumboEstimado`; reinicio.
