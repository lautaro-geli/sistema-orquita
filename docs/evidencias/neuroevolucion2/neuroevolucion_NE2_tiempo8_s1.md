# Neuroevolución de la red de movimiento (2026-11-18)

Generado por `orquitas.herramientas.Neuroevolucion`. Datos SINTÉTICOS del simulador 2D.

- Red de partida: `C:\Users\pc\Documents\orquita-corridas\neuroevolucion40_20261117_1713\modelos_NE40_E2_s2` (9615 pesos de movimiento; la percepción no cambia).
- 133 generaciones de 20 pares (+/- ruido) en 20 bahías; sigma 0.010, paso 0.010 (Adam); semilla 11; 122 min.
- Recompensa: puntaje de la grilla (choques hasta 5 por partida) + 8.0 puntos por cada minuto que sobra de los 7 al liberarse; 10.0 menos si no se libera.
- Girar y avanzar: no; barrido 1.91 s; ruido por ROI x1.51,1.73,1.94.
- Mejor red: generación 80 (elegida por la validación en 200 bahías).

## Evaluación final (300 bahías nuevas, las mismas de los reportes)

| Red | Misiones | <=1 min | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Antes (imitación) | 81 % | 0 % | 2.23 | 60.8 | 237 |
| Después (neuroevolución) | 81 % | 0 % | 2.14 | 60.7 | 241 |

## Curva (validación)

| Generación | Minutos | Recompensa media (entrenamiento) | Misiones | <=1 min | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|---|---|
| 0 | 0 | - | 79 % | 0 % | 2.91 | 55.4 | 247 |
| 10 | 9 | 82.2 | 72 % | 1 % | 2.98 | 51.7 | 258 |
| 20 | 18 | 95.4 | 74 % | 0 % | 4.58 | 43.9 | 259 |
| 30 | 28 | 79.3 | 74 % | 1 % | 1.97 | 57.2 | 261 |
| 40 | 37 | 73.3 | 79 % | 1 % | 2.11 | 59.4 | 246 |
| 50 | 47 | 85.2 | 73 % | 1 % | 5.70 | 37.9 | 266 |
| 60 | 56 | 85.2 | 75 % | 0 % | 5.10 | 42.4 | 255 |
| 70 | 65 | 85.4 | 75 % | 0 % | 5.19 | 41.2 | 252 |
| 80 | 73 | 99.3 | 81 % | 1 % | 2.94 | 56.2 | 251 |
| 90 | 82 | 90.8 | 76 % | 1 % | 5.16 | 42.4 | 251 |
| 100 | 91 | 87.0 | 71 % | 1 % | 5.26 | 39.7 | 262 |
| 110 | 100 | 85.6 | 80 % | 0 % | 3.39 | 53.4 | 248 |
| 120 | 108 | 77.2 | 78 % | 0 % | 3.86 | 50.4 | 254 |
| 130 | 118 | 85.2 | 78 % | 0 % | 3.97 | 49.6 | 251 |
