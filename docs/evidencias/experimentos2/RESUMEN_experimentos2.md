# Experimentos 2 — 16/11/2026 (PC del equipo, Neuroph 2.98, rama `experimentos-2`, commit `8a04d8f`)

Origen: análisis `claude/Analisis_Redes_Techo_27_vs_Maestro_87.md` (repetir hasta 6 veces las clases raras de la red de movimiento la empeora).
Todas: red A (184 entradas, 48 ocultas), 600 episodios del maestro, 2 rondas DAgger de 300, evaluación en **300** bahías nuevas (semillas 900000+), 3 semillas de recolección (20261113, 20261999, 20262777). Datos sintéticos: no reemplazan la prueba en la pista real. Total: 74 min (5 corridas a la vez).

| Experimento | Semilla 1 | Semilla 2 | Semilla 3 | Misión (media) | Capturas | Choques | Puntaje | Minutos |
|---|---|---|---|---|---|---|---|---|
| Referencia: A con repetición 6 (4 redes, medidas aparte en 300 bahías) | 16 % | 27 % | 25 % / 17 % | 21 % | — | — | — | 31–66 |
| **E1** sin repetición, 80 épocas | 52 % | 52 % | 51 % | **52 %** | 2,56 | 2,67 | 39,2 | 23–24 |
| **E2** repetición máx. 2, 80 épocas | 48 % | 55 % | 53 % | **52 %** | 2,64 | **1,37** | **47,4** | 29–32 |
| **E3** sin repetición, 160 épocas | 54 % | 47 % | 61 % | **54 %** | 2,63 | 2,09 | 44,5 | 42–44 |
| Maestro | — | — | — | 86 % | 2,88 | 0,05 | 74,9 | — |

Percepción: 84–86 % por sector en todas (sin cambios).

## Lectura

- **Diagnóstico confirmado:** sacar (o bajar a 2) la repetición de las clases raras lleva la red A de ~21 % a ~52 % de misiones en 300 bahías, en las 3 semillas. Criterio de éxito fijado de antemano (media ≥ 40 % y las 3 > 33 %): cumplido por E1, E2 y E3.
- **E2 es la mejor opción hoy:** misma misión que E1 pero con la mitad de choques (1,37 contra 2,67), y por eso el mejor puntaje. Su dispersión entre semillas (48–55 %) es menor que la de E3.
- **E3 (doble de épocas)** no se distingue de E1/E2 en misiones (47–61 %: más dispersión) y tarda el doble: no compensa.
- Sigue la brecha con el maestro (52 % contra 86 %). Próximas causas según el análisis: función de error (entropía cruzada) y lobitos falsos de la percepción.
