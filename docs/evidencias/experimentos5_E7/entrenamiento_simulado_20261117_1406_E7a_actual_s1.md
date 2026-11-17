# Entrenamiento con la Bahía de Caza simulada (2026-11-17)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 80; ejemplos: 46729; tiempo: 35.9 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, error cuadrático, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261113; percepción reforzada: no; repetición máxima de clases (movimiento): 2.
- Milímetros: en todas las filas, peso 1.0; episodios que arrancan en escape: 0 (+0 por ronda DAgger).
- Maestro: actual; barrido 1.91 s; ruido por ROI (4x4, 8x8, 16x16) x1.51,1.73,1.94.

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 281/300 (94 %) | 2.96 | 0.03 | 82.2 | 126 |
| Redes (solo telemetría) | 209/300 (70 %) | 2.75 | 3.37 | 47.9 | 277 |

Acuerdo de dirección red/maestro en barridos nuevos: 73.3 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 199 | 0 | 0 | 0 | 2 | 99.0 % |
| PARED | 27 | 12617 | 166 | 147 | 544 | 93.5 % |
| OBSTACULO | 2 | 508 | 1239 | 214 | 66 | 61.1 % |
| LOBITO | 8 | 49 | 63 | 320 | 60 | 64.0 % |
| SALIDA | 43 | 488 | 232 | 77 | 1181 | 58.4 % |

Acierto total por sector: 85.2 %.
