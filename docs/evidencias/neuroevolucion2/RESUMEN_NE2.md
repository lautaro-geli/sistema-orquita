# Neuroevolución larga desde NE40 s2 (18/11/2026)

Datos SINTÉTICOS del simulador 2D.

- Corrida en la PC con `herramientas_pc/orquita_neuroevolucion` (compila el commit `09eff43`).
- 4 corridas de 120 minutos, con 1 hilo cada una (unas 130 generaciones), desde `neuroevolucion40_20261117_1713/modelos_NE40_E2_s2`.
- Barrido de 1,91 s con el ruido escalado del simulador.
- Se comparan dos recompensas, con 2 semillas cada una:
  - **tiempo8:** 8 puntos por minuto ahorrado y −10 si no termina;
  - **tiempo2:** 2 puntos por minuto, sin castigo.

Evaluación final en las 300 bahías de los reportes:

| Corrida | Mejor generación | Misiones | ≤1 min | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|---|
| Partida (NE40 s2) | — | 81 % | 0 % | 2,23 | 60,8 | 237 |
| tiempo2 s1 | 10 | 77 % | 1 % | 3,47 | 52,0 | 240 |
| tiempo2 s2 | 90 | 82 % | 1 % | 3,61 | 54,7 | 243 |
| tiempo8 s1 | 80 | 81 % | 0 % | 2,14 | 60,7 | 241 |
| tiempo8 s2 | 130 | 78 % | 0 % | 3,18 | 53,9 | 238 |

**Conclusiones:**
- Ninguna supera a la red de partida.
- La curva de validación (200 bahías) sube y baja sin tendencia: la señal del refuerzo queda tapada por el azar.
- Premiar el tiempo con 8 puntos tampoco acelera: siguen en unos 240 s de promedio.
- Se deja el refuerzo. La mejor red sigue siendo NE40 s2.
