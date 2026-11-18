# Neuroevolución de la red de movimiento (2026-11-18)

Generado por `orquitas.herramientas.Neuroevolucion`. Datos SINTÉTICOS del simulador 2D.

- Red de partida: `C:\Users\pc\Documents\orquita-corridas\neuroevolucion40_20261117_1713\modelos_NE40_E2_s2` (9615 pesos de movimiento; la percepción no cambia).
- 135 generaciones de 20 pares (+/- ruido) en 20 bahías; sigma 0.010, paso 0.010 (Adam); semilla 12; 121 min.
- Recompensa: puntaje de la grilla (choques hasta 5 por partida) + 8.0 puntos por cada minuto que sobra de los 7 al liberarse; 10.0 menos si no se libera.
- Girar y avanzar: no; barrido 1.91 s; ruido por ROI x1.51,1.73,1.94.
- Mejor red: generación 130 (elegida por la validación en 200 bahías).

## Evaluación final (300 bahías nuevas, las mismas de los reportes)

| Red | Misiones | <=1 min | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Antes (imitación) | 81 % | 0 % | 2.23 | 60.8 | 237 |
| Después (neuroevolución) | 78 % | 0 % | 3.18 | 53.9 | 238 |

## Curva (validación)

| Generación | Minutos | Recompensa media (entrenamiento) | Misiones | <=1 min | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|---|---|
| 0 | 0 | - | 79 % | 0 % | 2.91 | 55.4 | 247 |
| 10 | 9 | 80.4 | 74 % | 1 % | 4.13 | 46.5 | 251 |
| 20 | 18 | 97.1 | 75 % | 0 % | 3.69 | 48.8 | 257 |
| 30 | 28 | 85.4 | 78 % | 0 % | 5.09 | 42.9 | 266 |
| 40 | 37 | 72.3 | 74 % | 1 % | 3.48 | 50.2 | 258 |
| 50 | 46 | 87.1 | 78 % | 1 % | 3.07 | 53.6 | 250 |
| 60 | 55 | 84.2 | 80 % | 1 % | 2.33 | 58.9 | 249 |
| 70 | 63 | 86.6 | 79 % | 0 % | 3.64 | 51.8 | 251 |
| 80 | 72 | 94.1 | 77 % | 1 % | 2.52 | 56.3 | 256 |
| 90 | 81 | 89.6 | 77 % | 0 % | 4.39 | 47.1 | 260 |
| 100 | 90 | 85.5 | 76 % | 0 % | 3.67 | 50.2 | 251 |
| 110 | 98 | 87.5 | 74 % | 1 % | 4.40 | 44.8 | 254 |
| 120 | 107 | 78.0 | 77 % | 0 % | 6.54 | 35.5 | 248 |
| 130 | 116 | 87.3 | 82 % | 0 % | 4.50 | 49.2 | 242 |
