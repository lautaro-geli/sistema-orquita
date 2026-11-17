# E5 y E6 — 16 y 17/11/2026 (PC del equipo, Neuroph 2.98, rama `experimentos-2`)

Todas: red A (184 entradas, 48 ocultas), 600 episodios + 2 rondas DAgger, 80 épocas, repetición máxima 2 (salvo E5 sin repetición), 300 bahías (semillas 900000+), semillas de recolección 20261113 / 20261999 / 20262777. Datos sintéticos.

| Experimento | Commit | S1 | S2 | S3 | Misión | Capturas | Choques | Puntaje |
|---|---|---|---|---|---|---|---|---|
| E2 (error cuadrático) — referencia | `8a04d8f` | 48 % | 55 % | 53 % | 52 % | 2,64 | 1,37 | 47,4 |
| E5 entropía cruzada, repetición 2 | `a347e89` | 39 % | 22 % | 37 % | 33 % | 2,45 | 0,61 | 38,9 |
| E5 entropía cruzada, sin repetición | `a347e89` | 49 % | 32 % | 49 % | 43 % | 2,42 | 1,88 | 37,2 |
| **E6a** E5 rep. 2 + mm solo en avances | `32e457b` | 52 % | 50 % | 60 % | **54 %** | 2,61 | 2,18 | 44,0 |
| E6b E6a + peso de mm 5 | `32e457b` | 15 % | 23 % | 21 % | 20 % | 1,89 | 5,09 | 2,3 |
| E6c E6a + 300 episodios de escape | `32e457b` | 55 % | 45 % | 50 % | 50 % | 2,48 | 2,20 | 39,6 |
| Maestro | — | — | — | — | 86 % | 2,88 | 0,05 | 74,9 |

## Lectura

- **E5:** con entropía cruzada la salida de mm se descalibró (avanzaba ~120 mm contra ~230 del maestro). Diagnóstico en `claude/Analisis_Redes_Techo_27_vs_Maestro_87.md` (proyecto).
- **E6a corrige el avance** (242 mm contra 241 del maestro, medido aparte) y recupera la misión (33 % → 54 %), pero **no supera a E2**: misma misión dentro del ruido y más choques (2,18 contra 1,37), así que menos puntaje. Criterio fijado (media ≥ 55 % y las 3 > 45 %): no se cumple por poco (54 %).
- **E6b:** pesar 5 veces el error de mm arruina la dirección (acuerdo con el maestro 65–68 % contra ~75 %): descartado.
- **E6c:** más episodios de escape no mejoran la misión en promedio (50 %). En una semilla medida aparte, salen 54 de 66 que entran en escape (E6a: 44 de 65), pero entre semillas no se distingue del ruido.
- Lo que sigue limitando: el escape (cerca de un tercio de los que llegan a escape no salen en 7 min) y el tiempo (3.ª captura a ~205–215 s contra 136 s del maestro).
