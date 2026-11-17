# Neuroevolución de la red de movimiento (2026-11-17)

Generado por `orquitas.herramientas.Neuroevolucion`. Datos SINTÉTICOS del simulador 2D.

- Red de partida: `C:\Users\pc\Documents\orquita-corridas\experimentos2_20261116_1544\modelos_E2_repeticion_2_s3` (9615 pesos de movimiento; la percepción no cambia).
- 60 generaciones de 20 pares (+/- ruido) en 20 bahías; sigma 0.010, paso 0.010 (Adam); semilla 2; 27 min.
- Recompensa: puntaje de la grilla (choques hasta 5 por partida) + 2.0 puntos por cada minuto que sobra de los 7 al liberarse.
- Girar y avanzar: no; barrido 1.91 s; ruido por ROI x1.51,1.73,1.94.
- Mejor red: generación 60 (elegida por la validación en 200 bahías).

## Evaluación final (300 bahías nuevas, las mismas de los reportes)

| Red | Misiones | <=1 min | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Antes (imitación) | 79 % | 1 % | 2.92 | 55.9 | 237 |
| Después (neuroevolución) | 81 % | 0 % | 2.23 | 60.8 | 237 |

## Curva (validación)

| Generación | Minutos | Recompensa media (entrenamiento) | Misiones | <=1 min | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|---|---|
| 0 | 0 | - | 77 % | 1 % | 4.35 | 46.4 | 246 |
| 10 | 5 | 68.0 | 73 % | 0 % | 4.69 | 42.5 | 253 |
| 20 | 9 | 77.5 | 69 % | 0 % | 5.15 | 37.4 | 260 |
| 30 | 14 | 68.6 | 74 % | 0 % | 5.15 | 41.0 | 253 |
| 40 | 18 | 61.9 | 78 % | 1 % | 1.70 | 60.2 | 248 |
| 50 | 23 | 69.8 | 74 % | 1 % | 4.45 | 45.3 | 259 |
| 60 | 27 | 70.3 | 79 % | 0 % | 2.91 | 55.4 | 247 |
