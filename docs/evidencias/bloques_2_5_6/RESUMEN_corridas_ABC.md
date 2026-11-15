# Corridas A/B/C — 15/11/2026 (PC del equipo, Neuroph 2.98 de Maven)

Mismo presupuesto en las tres: `EntrenamientoSimulado 300 80 modelos 2` (300 episodios del maestro, 80 épocas, 2 rondas DAgger), bahía real (152 × 114 cm, R = 15 cm, hueco de 30 cm), calibración medida (200 mm/s, 212 °/s, giro sobre el eje a 8 cm de la boca, embudo de 15 cm) y canicas a ≥ 15 cm de paredes y obstáculos. Evaluación en las mismas 100 bahías nuevas (semillas 900000+). Datos sintéticos: no reemplazan la prueba en la pista real.

| Piloto | Red de movimiento | Misión completa | Capturas | Choques | Puntaje | s | Acuerdo dir. | Percepción | Entrenamiento |
|---|---|---|---|---|---|---|---|---|---|
| Maestro (geometría real) | — | 87 % | 2,88 | 0,01 | 75,3 | 217 | — | — | — |
| **A** bahía nueva (commit `7839ae5`) | 184-48-15 | **17 %** | **2,35** | 1,02 | **29,5** | 399 | 69,2 % | 84,9 % | 30,9 min |
| **B** representación (`1bbeb4d`) | 14-16-15 | 0 % | 1,63 | 0,33 | 12,0 | 423 | 63,7 % | 83,9 % | 4,6 min |
| **C** memoria (`eb613cb`) | 24-20-15 | 0 % | 0,29 | 0,04 | 1,5 | 422 | 65,2 % | 83,9 % | 5,2 min |

Acierto de percepción por clase (LIBRE / PARED / OBSTÁCULO / LOBITO / SALIDA): A 77,8 / 92,2 / 62,0 / 65,9 / 65,5 %; B 98,0 / 91,6 / 66,7 / 74,4 / 52,0 %; C 97,0 / 90,8 / 57,8 / 67,0 / 67,1 %.

## Lectura

- **C no supera a A**: es la peor en capturas. En las imágenes `redesC_*.png` se queda cerca de la partida dando giros cortos (GIRAR_IZQ 71 ms ≈ 15°) los 7 minutos, aun viendo un Lobito cerca. Hipótesis principal: "copycat" de la clonación de comportamiento con historia — la red aprende a repetir la orden anterior (que está en sus entradas) en lugar de mirar la escena. A revisar antes de seguir con la memoria.
- **B pierde contra A**: los 14 números resumen demasiado; A, con las 117 entradas crudas + percepción, captura más (2,35 contra 1,63) aunque choca más.
- Las tres percepciones son parecidas (~84 %); OBSTÁCULO y SALIDA siguen siendo lo más difícil.
- Primera vez que una red completa misiones en la simulación (A: 17 de 100).

Archivos: reportes `docs/evidencias/entrenamiento_simulado_20261115_{A_bahia_nueva,B_representacion,C_memoria}.md`; consolas e imágenes del visor (maestro y redes C, semillas 900000–900002) en esta carpeta. Modelos `.nnet` en la PC (`Documents\orquita-corridas\resultados_20261115_1933\modelos_{A,B,C}`), no se suben.
