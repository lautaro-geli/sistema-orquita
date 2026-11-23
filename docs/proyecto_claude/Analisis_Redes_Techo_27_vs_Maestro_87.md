# Análisis de las redes de la Orquita: del techo del ~27 % a E2, Maestro v2 y refuerzo (actualizado 19/11/2026)

> Copia del documento del Proyecto de Claude (traspaso del 23/11/2026). Lo posterior al 19/11 (pruebas con el robot real, alimentación, prueba de detección) está en `docs/evidencias/prueba_real/Bitacora_Prueba_Real_20261121.md` y en `docs/TRASPASO.md`.

Empezó como el análisis de por qué ninguna red pasaba del ~27 % de misiones mientras el Maestro llegaba al 87 % (15/11). Las secciones van de la más nueva a la más vieja. Los análisis se hicieron en un entorno aparte, con los modelos `.nnet` entrenados en la PC del usuario y scripts propios; los entrenamientos corren en la PC con `.bat`.

## Estado al 19/11/2026 (sensor real, refuerzo largo, seguimiento y primera prueba real)

Rama `maestro-v2`. Competencia el **26/11**.

**Prioridad: probar el sistema completo en el robot real.** Todo lo anterior se midió en el simulador, y las pruebas del sensor mostraron que el sensor real no ve como el simulador supone.

### Prueba real del sensor (VL53L1X, robot quieto; sketch `arduino/PruebaRuidoSensor`)

Datos en `docs/evidencias/ruido_sensor/`. Configuraciones: A = 75 ms y espera de 100 ms; B = 33/100; C = 33/60; D = 4x4 33, 8x8 25 y 16x16 20 ms, con espera de 60; E = 200/150 (referencia).

| Config | Barrido real | Ruido (desvío entre barridos) | Desvío contra E |
|---|---|---|---|
| A | 4,33 s | ~1 mm | mediana 1–2 mm |
| B | 2,70 s | ~1,4 mm | hasta 21 mm |
| C | 2,19 s | 1,3–2,4 mm | hasta 31 mm |
| **D** | **1,98 s** | **1,6–2,1 mm** | **hasta 32 mm** |

- **El ruido real es de 1 a 2 mm.** El simulador suponía 4 mm + 2 % y encima lo escalaba ×1,5–1,9: **era entre 5 y 10 veces pesimista.** El ruido es configurable con `-Dorquita.ruidoBase` y `-Dorquita.ruidoRelativo` (real ≈ 1,5 y 0,005).
- **No hay más lecturas fallidas con las mediciones cortas.**
- **La espera de 60 ms corre el 4x4** (el primero que mide después de mover el servo) hasta 30 mm. Se arregla midiendo en el orden 16x16 → 8x8 → 4x4.
- **Los primeros ~45 s después de prender, las lecturas derivan** 20–100 mm; parece que el sensor necesita calentar.

**El sensor está a ~0,8 cm del piso** (a la altura media de una canica). Con el servo de elevación en 180° queda a 6,5 cm, también horizontal; en posiciones intermedias apunta en diagonal.

**Recorrido del centro del ROI** (cada fila de la grilla de 16×16 mueve la mira ~1,7°). Con el sensor abajo:
- **El 4x4 no ve el piso en ninguna fila;** mide limpio el fondo, a ~72 cm.
- **El 16x16 ve el piso a ~19 cm adelante,** y el 8x8 hacia los costados. (En la prueba de detección del 22/11, con el robot en la bahía, el 16x16 marcó el piso a 5–8 cm.)
- **Canica a 20 cm:** con el 4x4 en las filas 3 a 6 marca 35–43 cm (distancia mezclada con el fondo); con el centro 199 apenas se nota; **con el centro 197 se ve mejor.**
- **Canica a 35 cm:** apenas baja 5 a 7 cm contra el fondo; está en el límite.
- **Con el sensor arriba (6,5 cm) la canica no se ve.**
- **A 60° y 120° del servo de barrido aparecen lecturas de 0 a 46 mm con estado "válido",** sin nada físico. Causa sin confirmar. **El Servidor toma toda lectura de menos de 40 mm como sin dato (-1)**, igual que el simulador.

### Refuerzo largo NE2 (18/11; `docs/evidencias/neuroevolucion2/`)

4 corridas de 2 h desde NE40 s2, premiando el tiempo con 8 puntos por minuto y −10 si no termina, contra 2 puntos por minuto. **Ninguna superó a la red de partida** (81 %, 2,23 choques, 60,8 puntos en 300 bahías). **Se deja el refuerzo.**

### Seguimiento + piloto de reglas (`Seguimiento`, `PilotoSeguimiento`; experimental, el Servidor no lo usa)

- En el simulador, el sensor casi no mide la canica de lejos: el 8x8 y el 16x16 ven la pared de atrás. El seguimiento se guía por la dirección y usa el 4x4 cuando mide claramente menos.
- **Resultado:** al nivel de E2 (74 % de misiones, 4,1 choques) pero con más decisiones (~108). No se usa.

### Todos los pilotos con el ruido real (900 bahías, barrido de 1,98 s)

| Piloto | Misiones | ≤1 min | ≤4 min | Choques | Decisiones | Puntaje |
|---|---|---|---|---|---|---|
| **E2** | **78 %** | 0 % | **59 %** | **3,8** | 93 | **51,3** |
| NE40 s2 (refuerzo corto) | 78 % | 0 % | 56 % | 4,4 | 96 | 48,1 |
| Reglas + seguimiento | 68 % | 3 % | 46 % | 5,9 | 105 | 36,0 |
| Maestro v2 (referencia) | 95 % | 36 % | 91 % | 1,0 | 35 | 78,9 |

- El ruido no era lo que frenaba a las redes. **E2 queda como red para la competencia.**
- Ninguna red termina en 1 minuto: con ~2 s por barrido y unas 93 decisiones, solo los barridos ya suman más de 3 minutos.

## Estado al 17/11/2026 (objetivos de la competencia, Maestro v2, E7 y refuerzo)

**Objetivos acordados:** salir en **1 minuto**, **0 choques** y los 3 lobitos. Grilla confirmada por el profesor: minuto 1 = +15; minutos 2 a 4 = +10; minutos 5 a 10 = +5; cada choque −5. Salir sin completar descalifica.

**Dónde pierde tiempo E2 frente al Maestro** (100 bahías, barrido de 4,8 s; decisiones por prueba):

| Fase | Maestro | E2 | Diferencia |
|---|---|---|---|
| Exploración | 23 | 38 | E2 pierde el 30 % de los lobitos que ve (el Maestro, el 2 %) |
| Acercamiento | 7 | 12 | E2 corrige de a 15° y va y viene |
| Escape | 9 | 13 | E2 ve la salida pero no se alinea con el hueco |
| **Total** | **39** | **64** | |

**Candado de la salida** (`ReflejoSeguridad.candadoSalida`). En BUSQUEDA:
- no avanza hacia un frente marcado SALIDA: gira hacia lo más abierto (como máximo 45°);
- si ya está en el estrecho mirando afuera (7 o más sectores sin nada), retrocede;
- los lobitos sin dato o a más de 60 cm no lo abren;
- se aplica también después del detector de atasco.

Sin candado, E2 salía sin completar en el 3–5 % de las partidas; con candado, **0 en más de 2.000**. **Ojo:** con más ruido, el detector de atasco puede no reconocer dos barridos iguales; hay que ajustar su umbral en el robot.

**Maestro v2** (`MaestroV2`): girar y avanzar con un solo barrido, avanzar menos tras un giro grande ("seguro") y apuntar con la tolerancia de la boca de 15 cm ("embudo"). Con barrido de 1,91 s y 900 bahías: 96 % de misiones (Maestro actual 92 %), 38 % en ≤1 minuto (25 %), 34 decisiones (47). Explorar con memoria salió peor (68 %).

**E7** (imitar con barrido corto y ruido; 300 bahías, 3 semillas):

| Red | Misiones | ≤4 min | Choques | Decisiones | Puntaje |
|---|---|---|---|---|---|
| **E2** | **80 %** | **57 %** | **3,6** | 98 | **53** |
| E7a: imita al Maestro actual con 1,91 s | 73 % | 52 % | 4,1 | 104 | 46 |
| E7b: imita al Maestro v2 | 73 % | 54 % | 6,4 | 91 | 35 |

Imitar al Maestro v2 no sirvió; entrenar con barrido corto tampoco. **E2 sigue siendo la mejor.**

**Refuerzo** (`orquitas.herramientas.Neuroevolucion`): estrategia evolutiva sobre los 9.615 pesos de la red de movimiento, con Adam. NE40 (2 semillas de ~28 min): NE40 s2 llegó a 81 % y 51,7 puntos (E2: 77 % y 47,7) pero **no acelera**.

**Por qué la red decide tanto** (300 bahías): Maestro 43 decisiones, E2 97. La percepción ve un **lobito falso en el 31 %** de los barridos sin lobito y la red lo persigue; **no tiene memoria** y olvida un lobito apenas deja de verlo.

## Estado al 17/11/2026 (E4, E5 y E6)

| Experimento | Misión (3 semillas, 300 bahías) | Choques | Puntaje |
|---|---|---|---|
| **E2** error cuadrático, repetición 2 | **52 %** | **1,37** | **47,4** |
| E4 umbral de LOBITO para el reflejo | 50–52 % | ~1,4 | 46–47 |
| E5 entropía cruzada, repetición 2 | 33 % | 0,61 | 38,9 |
| E6a entropía + mm solo en avances | 54 % | 2,18 | 44,0 |
| E6b E6a + peso de mm 5 | 20 % | 5,09 | 2,3 |
| E6c E6a + 300 episodios de escape | 50 % | 2,20 | 39,6 |
| Maestro | 86 % | 0,05 | 74,9 |

(Con barrido de 4,8 s; por eso las misiones son más bajas que en las tablas de arriba.)

## Diagnóstico original (15–16/11)

La causa principal del techo del ~27 % era **cómo se entrenaba la red de movimiento**: repetía hasta 6 veces los ejemplos raros (`REPETICION_MAXIMA = 6` en `EntrenamientoSimulado`). Sin esa repetición pasa a ~52 % (E1); con repetición máxima 2, 52 % y menos choques (E2). Detalle en `docs/evidencias/experimentos2/RESUMEN_experimentos2.md`.

**Ruido estadístico:** la misma red, cambiando solo el sorteo del entrenamiento, da 16 % o 27 % en 300 bahías. Margen con 300 bahías ≈ ±6 puntos. Comparar siempre con varias semillas.

## Hipótesis

| Hipótesis | Veredicto |
|---|---|
| La repetición de giros perjudica | Confirmada y corregida (E1/E2) |
| El error cuadrático limita | Probada (E5/E6): la entropía cruzada no supera a E2 en Neuroph |
| La percepción ve lobitos que no están | Real; en simulación no limitaba la misión (E4). **En el robot real sí: piso y salida (22/11)** |
| El escape es la parte más floja | Confirmada |
| Falta tiempo (barrido largo) | Confirmada |
| Reflejo o detector de atasco estorban | Descartada |
| Imitar a un Maestro mejor baja las decisiones | Descartada (E7b) |

## Fuentes

- Wen et al., Fighting Copycat Agents in Behavioral Cloning (NeurIPS 2020).
- Codevilla et al., Exploring the Limitations of Behavior Cloning (ICCV 2019).
- Agarwal et al., Deep RL at the Edge of the Statistical Precipice (NeurIPS 2021).
- Salimans et al., Evolution Strategies as a Scalable Alternative to Reinforcement Learning (2017).
- Karpathy, A Recipe for Training Neural Networks (2019).
