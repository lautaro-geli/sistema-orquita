# Toma guiada real del 24/11/2026: qué confunde la red y filtro para LOBITO

Datos: `toma_guiada_20261124/deteccion.csv`. Son 25 escenas × 3 barridos = 75 barridos, tomados con `orquita_toma_guiada.bat` en la bahía, con el robot quieto. La red es E2 (`experimentos2_20261116_1544\modelos_E2_repeticion_2_s3`), con la corrección del piso prendida.

## 1. Primero: el robot mandaba los sectores espejados

En el robot, el servo de barrido en 0° apunta a la **izquierda** (lo confirmó el equipo el 24/11 con una caja). El protocolo y el Servidor usan **sector 0 = derecha**. El sketch tenía un `TODO confirmar` y suponía lo contrario, así que todo lo que veía a la izquierda el Servidor lo ponía a la derecha y al revés. Con las redes, el robot giraba para el lado equivocado.

- **Arreglo** (commit 3358855): en `Orquita.ino`, el sector s se mide con el servo en `180 − 15·s` (`anguloSector`). El protocolo no cambia. **Hay que volver a cargar `Orquita.ino` en el robot.**
- **Los datos de esta toma** se tomaron con el sketch viejo. Para analizarlos, se dan vuelta los sectores (s → 12 − s) antes de pasarlos por la red: es lo que va a mandar el robot con el sketch nuevo. Los datos lo confirman. Los obstáculos de costado aparecen a su distancia en el sector anotado y no en el espejo; por ejemplo, el obstáculo a +60° y 25 cm mide 176–210 mm en el sector anotado y ~350 mm en el espejo.
- **Sigue sin confirmar el sentido de giro de las ruedas** (`GIRAR_IZQ`, otro `TODO confirmar` en el sketch). Ver la recomendación.

## 2. Cómo se etiquetó cada sector

A partir de la nota de cada escena. El ángulo es positivo a la izquierda y el sector es 6 + ángulo/15.

- **Lobito:** un sector, el más cercano a su ángulo.
- **Obstáculo** (9 cm de ancho): los sectores que tapa según la distancia. El ancho angular es 2·atan(4,5 / d). Un sector cuenta si el obstáculo tapa su centro o al menos 5° de sus 15°. Da 3 sectores a 10–20 cm y 1 sector a 25 cm o más.
- **Salida A B:** los sectores entre A y B.
- **Resto:** PARED si algún ROI tiene eco a menos de 1 m; si no, LIBRE.
- **Superposiciones:** si la canica y el obstáculo caen en el mismo sector, gana LOBITO.
- **Notas:** se entendieron todas. Ninguna tiene "fondo X", así que no se puede separar por distancia a la pared de atrás.

## 3. Qué confunde la red (sin filtro)

### Matriz de confusión (sectores × barridos; filas = lo real, columnas = lo que dice la red)

| Real \ Red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | Total | Acierto |
|---|---|---|---|---|---|---|---|
| **LIBRE** | 25 | 2 | 0 | 36 | 54 | 117 | 21 % |
| **PARED** | 0 | 502 | 39 | 75 | 80 | 696 | 72 % |
| **OBSTACULO** | 8 | 22 | 5 | 24 | 19 | 78 | 6 % |
| **LOBITO** | 1 | 10 | 2 | 11 | 9 | 33 | 33 % |
| **SALIDA** | 0 | 37 | 1 | 6 | 7 | 51 | 14 % |

La red acierta las paredes (72 %) y casi nada más. Marca LOBITO en el 15 % de los sectores que no son canica (141 de 942).

### Lobitos: ¿hasta dónde se ve la canica?

| Distancia | Dónde | Sectores | La red dice LOBITO | …con eco del 4x4 | El 4x4 ve algo a esa distancia (±8 cm) |
|---|---|---|---|---|---|
| 5 cm | frente | 3 | 0 | 0 | 0 |
| 10 cm | frente | 6 | 5 | 5 | 3 |
| 15 cm | costado | 6 | 0 | 0 | 0 |
| 15 cm | frente | 3 | 2 | 0 | 0 |
| 20 cm | costado | 6 | 1 | 1 | 1 |
| 20 cm | frente | 6 | 3 | 0 | 1 |
| 30 cm | frente | 3 | 0 | 0 | 0 |

- La última columna es lo que importa: **el sensor ve la canica de verdad solo a ~10 cm, de frente.** A 5 cm queda debajo del haz. A 15–30 cm, o de costado, el 4x4 casi nunca mide algo a esa distancia.
- **5 de los 11 "aciertos"** de la red son sectores **sin ningún dato** (los tres ROI en −1): la red dice LOBITO ahí y a veces coincide con la canica. Pasa en las escenas 18 y 20 ("juntos"), que se tomaron con el sensor perdiendo media vuelta del barrido.
- 10 de los 75 barridos tienen 6 o más sectores sin dato. El sensor (o sus cables) sigue fallando a ratos.

### Obstáculos: ¿la red los confunde con LOBITO?

| Obstáculo | Sectores | Marcados LOBITO | Marcados OBSTACULO |
|---|---|---|---|
| de costado | 57 | 16/57 (28 %) | 3/57 (5 %) |
| de costado, borde | 36 | 12/36 (33 %) | 1/36 (3 %) |
| de costado, centro | 21 | 4/21 (19 %) | 2/21 (10 %) |
| de frente (ángulo 0) | 21 | 8/21 (38 %) | 2/21 (10 %) |
| de frente (ángulo 0), borde | 12 | 3/12 (25 %) | 1/12 (8 %) |
| de frente (ángulo 0), centro | 9 | 5/9 (56 %) | 1/9 (11 %) |

De frente, el centro de un obstáculo sale LOBITO en 5 de 9 barridos. La red casi nunca dice OBSTACULO (6 %).

### Salida: lobitos falsos por barrido

| Escena de salida | Barridos | LOBITO falsos | Por barrido |
|---|---|---|---|
| 13: salida -15 +15 | 3 | 1 | 0.3 |
| 14: salida -15 +15 | 3 | 3 | 1.0 |
| 15: salida +30 +60 | 3 | 0 | 0.0 |
| 16: salida -60 -30 | 3 | 10 | 3.3 |
| 17: salida -30 +30 | 3 | 4 | 1.3 |
| **Total** | 15 | 18 | **1.2** |

### Escenas "juntos": ¿la red elige la canica o el obstáculo?

| Escena con canica y obstáculo | Barrido | LOBITO en la canica | LOBITO en el obstáculo | LOBITO en otro lado |
|---|---|---|---|---|
| 18: lobito 0 15 ; obstaculo +30 20 | 321 | sí | 2 | 0 |
| 18: lobito 0 15 ; obstaculo +30 20 | 322 | no | 1 | 1 |
| 18: lobito 0 15 ; obstaculo +30 20 | 323 | sí | 2 | 3 |
| 19: lobito -30 20 ; obstaculo +15 15 | 363 | no | 0 | 2 |
| 19: lobito -30 20 ; obstaculo +15 15 | 364 | no | 0 | 0 |
| 19: lobito -30 20 ; obstaculo +15 15 | 365 | no | 0 | 2 |
| 20: lobito 0 20 ; obstaculo 0 40 | 393 | sí | 0 | 0 |
| 20: lobito 0 20 ; obstaculo 0 40 | 394 | sí | 0 | 2 |
| 20: lobito 0 20 ; obstaculo 0 40 | 395 | sí | 0 | 4 |
| 21: lobito +45 15 ; obstaculo -45 15 | 19 | no | 0 | 0 |
| 21: lobito +45 15 ; obstaculo -45 15 | 20 | no | 0 | 2 |
| 21: lobito +45 15 ; obstaculo -45 15 | 21 | no | 1 | 2 |
| 22: lobito 0 10 ; obstaculo -30 10 | 132 | sí | 2 | 1 |
| 22: lobito 0 10 ; obstaculo -30 10 | 133 | sí | 1 | 1 |
| 22: lobito 0 10 ; obstaculo -30 10 | 134 | no | 0 | 0 |

En las escenas 18 y 20, el "sí" de la canica cae en sectores sin dato: no es que la vea. Solo en la 22 (canica a 10 cm al frente) la ve con eco, y ahí también marca LOBITO en el obstáculo en 2 de 3 barridos.

## 4. Filtro para LOBITO (pendiente 3)

Va después de la red (`RedPercepcion.filtrarLobitos`). Si un sector no pasa, su certeza de LOBITO queda en 0 y gana la etiqueta que sigue. Lo que cambie llega también a la red de movimiento. Criterios, sobre la telemetría corregida:

- **(a)** el 4x4 tiene eco, a menos de A mm;
- **(b)** el 16x16 mide al menos B mm más que el 4x4, o no tiene eco (objeto chico);
- **(c)** ningún ROI de los sectores vecinos mide a menos de C mm del 4x4 (aislado).

### Ajuste sin hacerse trampa: dejando una escena afuera

Por cada una de las 25 escenas, se eligieron A, B y C mirando solo las otras 24. Grilla: A de 250 a 500, B y C de "sin condición" a 80 mm. Criterio: % de lobitos que la red marca y el filtro conserva, menos % de LOBITO falsos que quedan. Después se midió en la escena que quedó afuera.

- Se eligió **A = 250, B = 10, C = 50** en 15 de las 25 vueltas. En las otras 10 salió A = 350, sin (b) y con C = 50 o 10.
- Con todas las escenas juntas sale A = 250, B = 10, C = 50. Ese es el filtro "forma".

### Antes y después (datos reales)

| | Sin filtro | Filtro eco | Filtro forma (una escena afuera) |
|---|---|---|---|
| Sectores de obstáculo marcados LOBITO | 24 de 78 | 18 | 4 |
| LOBITO falsos en total (sectores) | 141 de 942 | 97 | 13 |
| LOBITO falsos en las 5 escenas de salida (15 barridos) | 18 de 15 | 14 | 0 |
| Lobitos que la red marca | 11 de 33 | 6 | 4 |
| Lobitos que el filtro saca | 0 de 33 | 5 | 7 |

- **"eco"** es solo la mitad de (a): el 4x4 tiene que tener eco, a cualquier distancia. No tiene umbrales, así que no hay nada que ajustar ni con qué hacerse trampa.
- Los 5 lobitos que saca "eco" son los de sectores **sin ningún dato**: la red no los veía, adivinaba.
- "forma" saca además 2 que la red marcaba con eco, pero en esos el 4x4 medía 338 y 348 mm, no la canica.

Matriz con "eco":

| Real \ Red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | Total | Acierto |
|---|---|---|---|---|---|---|---|
| **LIBRE** | 25 | 16 | 0 | 0 | 76 | 117 | 21 % |
| **PARED** | 0 | 502 | 39 | 75 | 80 | 696 | 72 % |
| **OBSTACULO** | 9 | 22 | 5 | 18 | 24 | 78 | 6 % |
| **LOBITO** | 1 | 10 | 2 | 6 | 14 | 33 | 18 % |
| **SALIDA** | 0 | 39 | 1 | 4 | 7 | 51 | 14 % |

Matriz con "forma" (cada escena con los umbrales elegidos sin ella):

| Real \ Red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | Total | Acierto |
|---|---|---|---|---|---|---|---|
| **LIBRE** | 25 | 16 | 0 | 0 | 76 | 117 | 21 % |
| **PARED** | 0 | 539 | 53 | 9 | 95 | 696 | 77 % |
| **OBSTACULO** | 9 | 28 | 8 | 4 | 29 | 78 | 10 % |
| **LOBITO** | 1 | 10 | 4 | 4 | 14 | 33 | 12 % |
| **SALIDA** | 0 | 40 | 2 | 0 | 9 | 51 | 18 % |

### Simulación: 900 bahías, las mismas semillas con y sin filtro

Configuración: semillas 900000 a 900899, barrido de 1,91 s y ruido por ROI ×1,51/×1,73/×1,94, como en `Correccion_Piso_20261123.md`. Redes E2 sin reentrenar, corrección del piso prendida. La diferencia es pareada (misma bahía con y sin) y se da ± 2 errores estándar.

| Filtro | Misión | Choques | Puntaje | Diferencia en misión | Diferencia en choques |
|---|---|---|---|---|---|
| Sin filtro | 72,9 % | 4,17 | 45,4 | — | — |
| **eco** | 71,9 % | 3,99 | 45,7 | −1,0 ± 3,4 | −0,17 ± 1,31 |
| Solo A = 400 | 68,6 % | 5,18 | 37,0 | −4,3 ± 3,6 | +1,01 ± 1,46 |
| Solo C = 30 | 56,7 % | 5,75 | 26,2 | −16,2 ± 3,7 | +1,58 ± 1,58 |
| Solo B = 10 | 54,3 % | 5,18 | 27,4 | −18,6 ± 3,7 | +1,01 ± 1,42 |
| forma (250/10/50) | 35,4 % | 6,55 | 5,7 | −37,4 ± 3,8 | +2,38 ± 1,56 |

Con la pérdida del 4x4 más parecida a la real (ruido 4x4 ×0,25):

| Filtro | Misión | Choques | Puntaje | Diferencia en misión |
|---|---|---|---|---|
| Sin filtro | 77,7 % | 3,07 | 54,0 | — |
| **eco** | 76,3 % | 3,25 | 52,3 | −1,3 ± 3,3 |
| forma | 40,0 % | 4,71 | 18,5 | −37,7 ± 3,7 |

**Por qué "forma" se cae en la simulación:** ahí la canica se ve distinta que en la pista. Se ve a 30–50 cm, ocupa dos sectores y los tres ROI miden casi lo mismo. En 100 bahías, de 996 lobitos que la red ve, el filtro deja 163. Falla (a) en 604, (b) en 541 y (c) en 294. La red de movimiento aprendió a ir a buscar esas canicas lejanas; sin ellas, no encuentra las canicas.

**En la pista** la canica solo se ve a ~10 cm, así que no se sabe qué pasaría. La simulación no lo puede medir.

### Decisión

- **Por defecto: `eco`** (`-Dorquita.filtroLobito=eco`, commit abe0ca0).
  - En la simulación queda dentro del ruido (−1 punto, con ±3,4) y no suma choques.
  - En los datos reales saca los LOBITO de sectores ciegos: 141 → 97 falsos, 18 → 14 en la salida.
- **"forma"** queda disponible (`-Dorquita.filtroLobito=forma`), pero **no** por defecto: no pasa la condición de no empeorar la simulación. Probarlo en la pista solo si con "eco" el robot va a buscar obstáculos.
- **"no"** es la red sola.
- El Servidor muestra el modo al arrancar.
- Los análisis (`orquita_analisis_deteccion.bat` y el final de la toma guiada) corren con `no`, porque miden la red y prueban los filtros aparte.

## 5. Límites

- Son 25 escenas en una sola bahía y una sola tarde. De canica vista de verdad hay 4–6 casos. Alcanza para descartar cosas (por ejemplo, que la canica se vea a 20–30 cm), no para afinar números.
- El sensor perdió media vuelta en varios barridos (ver la sección 3). Las escenas 18 a 20 son las más afectadas.
- El Bluetooth se cortó 3 veces en ~1,5 h ("write timed out"). La toma siguió con `--desde`.

## 6. Recomendación para la competencia

**Competir con redes E2 + filtro `eco`, con el sketch corregido.**

- **Por qué redes:** son lo único medido de punta a punta (72–78 % de misión en la simulación) y tienen el candado de salida.
- **Por qué no reglas:** el modo `--reglas` no tiene candado. En búsqueda, si ve SALIDA, avanza (`Navegacion.calcularMovimiento`), así que puede salir sin las 3 canicas y quedar descalificado. Además, no está medido.
- **Riesgo:** con datos reales, ninguna de las dos completó una misión, y el sensor ve la canica solo a ~10 cm. Antes de competir, sí o sí: cargar `Orquita.ino`, probar el lado del sensor y el sentido de giro (TRASPASO §9, pendiente 0) y hacer una corrida en la bahía.

## Archivos

- `toma_guiada_20261124/deteccion.csv`: los barridos como llegaron (orden viejo, espejado).
- `toma_guiada_20261124/consola.txt`: la consola de la toma.
- `toma_guiada_20261124/red_e2_espejado.txt`: cada barrido dado vuelta, con lo corregido y lo que dice la red E2.
- `toma_guiada_20261124/analizar.py`: etiquetado, tablas y ajuste dejando una escena afuera.
