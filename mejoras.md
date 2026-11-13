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
