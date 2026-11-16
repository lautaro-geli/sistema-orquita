# Experimentos 1 — 15/11/2026 (PC del equipo, Neuroph 2.98, rama `experimentos-1`, commit `97cfd29`)

Todas con la bahía real, la calibración medida y las canicas a ≥ 15 cm; 80 épocas; evaluación en las mismas 100 bahías nuevas (semillas 900000+). Datos sintéticos: no reemplazan la prueba en la pista real. Corrieron de a 5 en paralelo (1 hilo cada una, núcleos 1–5); total 1 h 13 min.

| # | Prueba | Entradas | Episodios / rondas | Misión completa | Capturas | Choques | Puntaje | Percepción | Tiempo |
|---|---|---|---|---|---|---|---|---|---|
| — | Maestro | — | — | 87 % | 2,88 | 0,01 | 75,3 | — | — |
| ref | Corrida A (rama `corrida-A`, código anterior) | 184 | 300 / 2 | 17 % | 2,35 | 1,02 | 29,5 | 84,9 % | 31 min |
| 0 | A, mismo código que el resto | 184 | 300 / 2 | 11 % | 2,21 | 1,50 | 21,7 | 83,6 % | 32 min |
| 3 | A, otra semilla (20261999) | 184 | 300 / 2 | 27 % | 2,50 | 1,68 | 32,1 | 83,9 % | 34 min |
| 1 | A + memoria sin la orden anterior | 189 | 300 / 2 | 27 % | 2,43 | 1,44 | 32,2 | 84,2 % | 34 min |
| 5 | A + los 14 números | 198 | 300 / 2 | 27 % | 2,46 | 1,70 | 32,1 | 83,5 % | 37 min |
| 2 | A con 4 rondas DAgger | 184 | 300 / 4 | 24 % | 2,55 | 1,09 | **36,0** | 84,3 % | 66 min |
| 7 | A con 600 episodios | 184 | 600 / 2 | 18 % | **2,58** | **0,83** | 35,9 | 84,2 % | 62 min |
| 8 | A con percepción reforzada | 184 | 300 / 2 | 20 % | 2,31 | 1,17 | 28,1 | 77,5 % | ~60 min |
| 4 | C sin la orden anterior | 19 | 300 / 2 | 0 % | 1,78 | 0,34 | 15,0 | 84,8 % | 6 min |
| ref | Corrida C (con la orden anterior) | 24 | 300 / 2 | 0 % | 0,29 | 0,04 | 1,5 | 83,9 % | 5 min |

## Lectura

- **El azar pesa mucho:** la misma red A, solo cambiando la semilla o detalles del código, dio 11 %, 17 % y 27 % de misiones (puntaje 21,7 a 32,1). Diferencias de ±10 puntos de misión entre una corrida y otra no prueban nada.
- **Lo que sí se separa del ruido:** más datos. Con 4 rondas DAgger (2) o 600 episodios (7), el puntaje sube a ~36 (el máximo de las A de base es 32,1) y los choques bajan (1,09 y 0,83 contra 1,50–1,70), con más capturas (2,55–2,58).
- **La orden anterior en la memoria era el problema de C** ("copycat"): sin ella, C pasa de 0,29 a 1,78 capturas. La memoria sin la orden (1) y los 14 números (5) en A no se distinguen del ruido.
- **Percepción reforzada (8):** OBSTÁCULO 56 → 81 % y SALIDA 69 → 84 %, pero LIBRE cae a 13 % (se confunde con SALIDA), PARED 78 % y LOBITO 54 %; total 77,5 %. No mejoró la misión: hay que ajustar el balanceo, no solo repetir.

## Próximo paso propuesto

Combinar lo que ayuda (más datos: 600 episodios + 4 rondas) y medir con varias semillas y más bahías de evaluación, para que las diferencias se puedan creer.
