# Entrenamiento con la Bahía de Caza simulada (2026-11-17)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 80; ejemplos: 45404; tiempo: 36.3 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, error cuadrático, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261999; percepción reforzada: no; repetición máxima de clases (movimiento): 2.
- Milímetros: en todas las filas, peso 1.0; episodios que arrancan en escape: 0 (+0 por ronda DAgger).
- Maestro: actual; barrido 1.91 s; ruido por ROI (4x4, 8x8, 16x16) x1.51,1.73,1.94.

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 281/300 (94 %) | 2.96 | 0.03 | 82.2 | 126 |
| Redes (solo telemetría) | 218/300 (73 %) | 2.73 | 5.79 | 36.5 | 256 |

Acuerdo de dirección red/maestro en barridos nuevos: 71.6 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 200 | 0 | 0 | 0 | 1 | 99.5 % |
| PARED | 41 | 12606 | 233 | 115 | 506 | 93.4 % |
| OBSTACULO | 8 | 482 | 1320 | 164 | 55 | 65.1 % |
| LOBITO | 13 | 52 | 76 | 319 | 40 | 63.8 % |
| SALIDA | 57 | 586 | 246 | 67 | 1065 | 52.7 % |

Acierto total por sector: 85.0 %.
