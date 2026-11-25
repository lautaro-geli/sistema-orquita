# Simulación realista y modo barrido (noche del 24/11/2026)

## Simulación realista (`-Dorquita.simRealista=true`)

`SensorSimulado` puede imitar al sensor real de la toma guiada (`Toma_Guiada_20261124.md`). Está apagada por defecto, así que la simulación de siempre no cambia; se verificó corriendo 40 bahías antes y después, con resultados idénticos.

- **Canica:** pesa entera a 7–12,5 cm del sensor. Hasta 22 cm se mezcla cada vez más con lo que hay detrás. Más lejos, o a menos de 7 cm, el rayo mide el fondo.
- **Piso:** el 8x8 y el 16x16 suman un eco del piso a 5–8 cm. El peso es aleatorio en cada barrido (log-normal), como si el sensor se inclinara.
- **Lecturas perdidas:** el 8x8 pierde un 8 % más de lecturas y el 16x16 un 17 % más. Los bloques sin eco son más frecuentes (25 %) y más largos (hasta 9 sectores).

### Comparación con los 75 barridos reales

Sectores sin canica ni obstáculo. Los cocientes son cuantiles 10/25/50/75/90 %. Simulación: 60 bahías, 2.962 barridos.

| | Real | Simulación realista | Simulación de siempre |
|---|---|---|---|
| 16x16 ÷ 4x4, 4x4 a 15–25 cm | 0,72 / 0,75 / **0,85** / 0,96 / 1,06 | 0,77 / 0,85 / **0,92** / 0,99 / 1,06 | 0,88 / 0,94 / 1,01 / 1,07 / 1,14 |
| 16x16 ÷ 4x4, 4x4 a 25–40 cm | 0,65 / 0,69 / **0,82** / 0,93 / 0,99 | 0,57 / 0,70 / **0,81** / 0,90 / 0,97 | 0,88 / 0,93 / 0,99 / 1,05 / 1,10 |
| 16x16 ÷ 4x4, 4x4 a 40–70 cm | 0,51 / 0,59 / **0,67** / 0,83 / 0,92 | 0,32 / 0,44 / **0,60** / 0,75 / 0,86 | 0,88 / 0,93 / 0,98 / 1,04 / 1,09 |
| 8x8 ÷ 4x4, 4x4 a 40–70 cm | 0,80 / 0,88 / **0,95** / 1,00 / 1,02 | 0,76 / 0,85 / **0,91** / 0,97 / 1,02 | 0,91 / 0,95 / 0,99 / 1,04 / 1,08 |
| Lecturas sin eco 4x4 / 8x8 / 16x16 | 17 / 18 / 25 % | 22 / 21 / 29 % | 17 / 12 / 11 % |
| Barridos con 6 o más sectores sin dato | 13 % | 13 % | 4 % |

Canica (60 escenas por fila, robot mirando una pared a 45–70 cm): ¿el 4x4 mide algo a esa distancia (±8 cm)?

| Distancia | Real (toma guiada) | Simulación realista (0° / 30° / 45°) | Simulación de siempre (0°) |
|---|---|---|---|
| 5 cm | 0 de 3 | 0 / 0 / 0 de 60 | 51 de 60 |
| 10 cm | 3 de 6 (de frente) | 49 / 52 / 49 de 60 | 53 de 60 |
| 15 cm | 0 de 9 | 0 / 2 / 1 de 60 | 19 de 60 |
| 20 cm | 2 de 12 | 0 / 0 / 0 de 60 | 0 de 60 (pero la etiqueta dice LOBITO) |
| 30 cm | 0 de 3 | 0 / 0 / 0 de 60 | 1 de 60 |

Donde más se aleja: con la pared a 15–25 cm, el piso pesa un poco menos que en la realidad (mediana 0,92 contra 0,85).

## Pilotos en la simulación realista

900 bahías con las mismas semillas, barrido de 1,91 s y ruido ×1,51/×1,73/×1,94. Diferencia pareada ± 2 errores estándar.

| Piloto | Misión | Capturas | Choques | Puntaje | Misión vs E2 | Choques vs E2 |
|---|---|---|---|---|---|---|
| E2 | 13,8 % | 1,84 | 2,76 | 9,0 | — | — |
| Barrido (percepción E2), commit ed04b06 | 7,7 % | 1,54 | 3,03 | 2,0 | −6,1 ± 2,8 | +0,3 ± 0,9 |

En la simulación de siempre, E2 completa el 72,9 % de las misiones (`Toma_Guiada_20261124.md`). Con el sensor realista baja al 13,8 %. Coincide con lo de la pista: E2 nunca completó una misión.

## Modo barrido (`--modo=barrido`, `PilotoBarrido`)

- Zigzag en carriles de 15 cm (el ancho de la boca), con pasos de hasta 45 cm. Una canica que está en el carril entra en la boca aunque el sensor no la vea.
- Al llegar a una pared mide su inclinación y endereza el rumbo. Si dos carriles seguidos terminan casi sin avanzar, sale hacia lo más abierto.
- Va a la canica si el 4x4 la ve a menos de 15 cm con forma de canica. Para eso usa el filtro "forma", que acá sí sirve porque no hay red de movimiento.
- En ESCAPE usa `PilotoSeguimiento`. Pasa por el reflejo, el atasco y el candado de salida.

En la simulación realista todavía es **peor que E2**. Dónde se pierde el tiempo (60 bahías, etiquetas perfectas):

- La mayoría de los choques pasan al girar 90° al final del carril: la cola barre lo que el sensor no ve.
- Muchos barridos se van en esperar con el frente ciego.
- La 1.ª captura llega a los ~150 s y la 3.ª, cuando llega, a los ~330 s, cerca del límite de 7 min.

Con etiquetas perfectas (el maestro "sin trampa" del entrenamiento) completa el 16 % en 100 bahías.

## Entrenamiento de la noche (`orquita_noche_realista.bat`)

- **Fase 1 (`N_maestro_s1..s3`):** maestro de siempre (conoce la geometría), commit d018e50.
- **Fase 2 (`B_barrido_s1..s3`):** `--maestro=barrido`, con lo último de maestro-v2 al arrancar la fase.
- **En las dos:** simulación realista, receta de E2 (600 episodios, 80 épocas, 2 rondas DAgger, entradas CRUDA, repetición 2) y los 75 barridos reales en la percepción (`toma_guiada_20261124/etiquetado_para_entrenar.csv`, ×20).
- **Etiquetas de los barridos reales:** LOBITO solo donde el sensor puede ver la canica (7–12 cm); si no, lo de atrás.

A la mañana, `orquita_comparar_realista.bat` compara E2, el barrido y las 6 redes nuevas en 900 bahías.
