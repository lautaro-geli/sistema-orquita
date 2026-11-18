# Neuroevolución de la red de movimiento (2026-11-18)

Generado por `orquitas.herramientas.Neuroevolucion`. Datos SINTÉTICOS del simulador 2D.

- Red de partida: `C:\Users\pc\Documents\orquita-corridas\neuroevolucion40_20261117_1713\modelos_NE40_E2_s2` (9615 pesos de movimiento; la percepción no cambia).
- 137 generaciones de 20 pares (+/- ruido) en 20 bahías; sigma 0.010, paso 0.010 (Adam); semilla 13; 121 min.
- Recompensa: puntaje de la grilla (choques hasta 5 por partida) + 2.0 puntos por cada minuto que sobra de los 7 al liberarse; 0.0 menos si no se libera.
- Girar y avanzar: no; barrido 1.91 s; ruido por ROI x1.51,1.73,1.94.
- Mejor red: generación 10 (elegida por la validación en 200 bahías).

## Evaluación final (300 bahías nuevas, las mismas de los reportes)

| Red | Misiones | <=1 min | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Antes (imitación) | 81 % | 0 % | 2.23 | 60.8 | 237 |
| Después (neuroevolución) | 77 % | 1 % | 3.47 | 52.0 | 240 |

## Curva (validación)

| Generación | Minutos | Recompensa media (entrenamiento) | Misiones | <=1 min | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|---|---|
| 0 | 0 | - | 79 % | 0 % | 2.91 | 55.4 | 247 |
| 10 | 9 | 69.4 | 79 % | 0 % | 4.54 | 48.1 | 240 |
| 20 | 18 | 78.3 | 77 % | 0 % | 3.78 | 49.6 | 244 |
| 30 | 27 | 70.6 | 78 % | 2 % | 5.03 | 44.6 | 251 |
| 40 | 36 | 62.8 | 77 % | 2 % | 4.51 | 46.0 | 247 |
| 50 | 44 | 72.1 | 78 % | 1 % | 4.20 | 48.6 | 253 |
| 60 | 53 | 70.3 | 77 % | 2 % | 5.13 | 42.4 | 258 |
| 70 | 61 | 72.7 | 78 % | 1 % | 5.25 | 42.7 | 245 |
| 80 | 70 | 78.6 | 74 % | 1 % | 4.03 | 46.5 | 256 |
| 90 | 78 | 73.3 | 74 % | 1 % | 4.26 | 45.6 | 257 |
| 100 | 87 | 70.7 | 74 % | 0 % | 3.38 | 49.9 | 260 |
| 110 | 96 | 70.8 | 74 % | 1 % | 3.29 | 50.4 | 252 |
| 120 | 104 | 65.9 | 73 % | 0 % | 3.09 | 50.4 | 263 |
| 130 | 114 | 69.9 | 76 % | 1 % | 4.81 | 44.1 | 259 |
