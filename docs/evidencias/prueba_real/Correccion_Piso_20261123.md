# Corrección del piso en `PreprocesadorTelemetria` (23/11/2026)

Pendiente 2 del traspaso (§9). Arregla la salida y el piso, no los obstáculos confundidos con lobitos.

## Qué hace

Por sector, antes de la regla del -1, y solo para el 8x8 y el 16x16 con dato **a menos de 200 mm** (`PISO_ALCANCE_MM`):

- **4x4 con dato:** si el ROI grande mide más de 20 mm (`PISO_MARGEN_MM`) por debajo del 4x4, toma el valor del 4x4.
- **4x4 sin eco:** el ROI grande queda sin eco.

Más lejos de 200 mm no se toca nada: que el ROI grande mida menos que el 4x4 ahí es una pared oblicua (el simulador también lo modela así). Se apaga con `-Dorquita.correccionPiso=false`.

La corrección está en `corregir()`, así que la ven las dos redes, el reflejo de seguridad (que mide el frente con el mínimo del 8x8 y el 16x16, justo los ROI que ven el piso), el candado y el detector de atasco.

## Datos reales (prueba de detección del 22/11, robot quieto, red de percepción E2)

| Escena | Barridos | LOBITO por barrido, sin corrección | Con corrección |
|---|---|---|---|
| 19:26, mirando la salida | 18 | 4,0 | 2,1 |
| 19:19, cosas a 8–30 cm sin anotar qué eran; barridos 47–49 con nota `lolobito` | 49 | 0,80 | 0,55 |

- **Esto no mide la detección de lobitos.** En la escena `lolobito` no está anotado qué sector era cada cosa. Según lo que vio el equipo en el robot, la red marca los obstáculos como LOBITO con mucha seguridad y los lobitos reales con poca. El sector que sale LOBITO al 89–90 % en los barridos 47–49 (sector 2: 98/95/93 mm) puede ser un obstáculo. La corrección no lo cambia: la diferencia entre ROI está dentro del margen.
- Lo que queda en la salida: el sector 0 (algo a ~8 cm, 86/81/77: los tres ROI casi iguales, no es el patrón del piso) y sectores que quedan sin dato y la red igual llama LOBITO con ~50 %. Lo segundo lo puede atacar el filtro de forma (pendiente 3), una vez ajustado con datos etiquetados.
- Probé margen 15/20/30 mm y alcance 150/200/250/300 mm: casi no cambia el resultado.

## Simulación (900 bahías de evaluación, semillas 900000+, barrido 1,91 s, ruido por ROI ×1,51/×1,73/×1,94, redes E2 sin reentrenar)

| Variante | Misión | Choques | Puntaje |
|---|---|---|---|
| Sin corrección | 80,4 % | 3,10 | 55,4 |
| Corrección en todo (la del commit) | 74,6 % | 4,16 | 45,7 |
| Solo la rama "4x4 con dato" | 78,7 % | 3,57 | 51,7 |
| Solo la rama "4x4 sin eco" | 75,7 % | 4,31 | 45,7 |
| Corrección solo en lo que ven las redes | 72,3 % | 3,19 | 49,2 |
| Percepción E2 con ajuste fino (10 épocas, 600 episodios, con corrección) | 72,2 % | 4,05 | 44,8 |

Los choques extra salen de la rama "4x4 sin eco" en el reflejo: el sensor simulado pierde el eco del 4x4 al azar (~6 % con el ruido ×1,51) y, si justo hay una pared a menos de 20 cm, la corrección la borra.

En el sensor real eso casi no pasa. En la escena de las 19:19 (casi todo a 8–30 cm), el 4x4 sin eco con un ROI grande a menos de 20 cm aparece en el 0,3 % de los sectores. Mirando la salida (19:26) aparece en el 22 %, porque es el piso. Con la pérdida del 4x4 más parecida a la real (ruido 4x4 ×0,25, el resto igual):

| Variante | Misión | Choques | Puntaje |
|---|---|---|---|
| Sin corrección | 79,7 % | 2,52 | 57,7 |
| Con corrección | 76,9 % | 2,62 | 55,2 |

La diferencia queda dentro del ruido (con 900 bahías pareadas, unos ±3–4 puntos). **Decisión:** dejar la corrección prendida en todo. El ajuste fino de la percepción no mejoró y se descartó.

Ojo: esa escena es una sola (49 barridos) y no está anotada. Conviene confirmar en la prueba completa (pendiente 9) que el reflejo no deja de ver paredes cercanas.
