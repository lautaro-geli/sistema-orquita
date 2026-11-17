# Neuroevolución de la red de movimiento (2026-11-17)

Generado por `orquitas.herramientas.Neuroevolucion`. Datos SINTÉTICOS del simulador 2D.

- Red de partida: `C:\Users\pc\Documents\orquita-corridas\experimentos2_20261116_1544\modelos_E2_repeticion_2_s3` (9615 pesos de movimiento; la percepción no cambia).
- 59 generaciones de 20 pares (+/- ruido) en 20 bahías; sigma 0.010, paso 0.010 (Adam); semilla 1; 28 min.
- Recompensa: puntaje de la grilla (choques hasta 5 por partida) + 2.0 puntos por cada minuto que sobra de los 7 al liberarse.
- Girar y avanzar: no; barrido 1.91 s; ruido por ROI x1.51,1.73,1.94.
- Mejor red: generación 40 (elegida por la validación en 200 bahías).

## Evaluación final (300 bahías nuevas, las mismas de los reportes)

| Red | Misiones | <=1 min | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Antes (imitación) | 79 % | 1 % | 2.92 | 55.9 | 237 |
| Después (neuroevolución) | 71 % | 1 % | 2.03 | 55.2 | 259 |

## Curva (validación)

| Generación | Minutos | Recompensa media (entrenamiento) | Misiones | <=1 min | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|---|---|
| 0 | 0 | - | 77 % | 1 % | 4.35 | 46.4 | 246 |
| 10 | 5 | 70.2 | 74 % | 1 % | 5.44 | 39.6 | 258 |
| 20 | 9 | 77.2 | 75 % | 0 % | 3.26 | 51.0 | 270 |
| 30 | 14 | 67.7 | 70 % | 1 % | 2.70 | 50.8 | 264 |
| 40 | 19 | 62.7 | 76 % | 0 % | 1.35 | 60.4 | 267 |
| 50 | 23 | 66.5 | 71 % | 0 % | 3.20 | 48.4 | 265 |
