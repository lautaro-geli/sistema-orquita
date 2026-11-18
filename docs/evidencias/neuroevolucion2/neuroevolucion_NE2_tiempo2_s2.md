# Neuroevolución de la red de movimiento (2026-11-18)

Generado por `orquitas.herramientas.Neuroevolucion`. Datos SINTÉTICOS del simulador 2D.

- Red de partida: `C:\Users\pc\Documents\orquita-corridas\neuroevolucion40_20261117_1713\modelos_NE40_E2_s2` (9615 pesos de movimiento; la percepción no cambia).
- 134 generaciones de 20 pares (+/- ruido) en 20 bahías; sigma 0.010, paso 0.010 (Adam); semilla 14; 121 min.
- Recompensa: puntaje de la grilla (choques hasta 5 por partida) + 2.0 puntos por cada minuto que sobra de los 7 al liberarse; 0.0 menos si no se libera.
- Girar y avanzar: no; barrido 1.91 s; ruido por ROI x1.51,1.73,1.94.
- Mejor red: generación 90 (elegida por la validación en 200 bahías).

## Evaluación final (300 bahías nuevas, las mismas de los reportes)

| Red | Misiones | <=1 min | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Antes (imitación) | 81 % | 0 % | 2.23 | 60.8 | 237 |
| Después (neuroevolución) | 82 % | 1 % | 3.61 | 54.7 | 243 |

## Curva (validación)

| Generación | Minutos | Recompensa media (entrenamiento) | Misiones | <=1 min | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|---|---|
| 0 | 0 | - | 79 % | 0 % | 2.91 | 55.4 | 247 |
| 10 | 9 | 68.5 | 73 % | 0 % | 4.50 | 43.9 | 263 |
| 20 | 18 | 77.9 | 72 % | 1 % | 5.02 | 40.9 | 262 |
| 30 | 27 | 70.3 | 78 % | 0 % | 5.31 | 42.8 | 248 |
| 40 | 37 | 64.0 | 75 % | 0 % | 3.95 | 48.3 | 263 |
| 50 | 45 | 70.8 | 78 % | 0 % | 4.24 | 48.4 | 258 |
| 60 | 54 | 71.2 | 70 % | 1 % | 6.87 | 29.6 | 261 |
| 70 | 63 | 72.0 | 75 % | 0 % | 3.35 | 50.8 | 262 |
| 80 | 72 | 79.7 | 76 % | 0 % | 5.29 | 41.4 | 256 |
| 90 | 80 | 73.7 | 79 % | 0 % | 1.81 | 61.4 | 241 |
| 100 | 89 | 70.3 | 71 % | 0 % | 5.16 | 39.8 | 258 |
| 110 | 98 | 70.9 | 73 % | 0 % | 2.65 | 53.2 | 265 |
| 120 | 107 | 67.8 | 76 % | 0 % | 2.81 | 53.9 | 260 |
| 130 | 117 | 70.5 | 74 % | 0 % | 5.20 | 40.5 | 259 |
