# Entrenamiento con la Bahía de Caza simulada (2026-11-16)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 160; ejemplos: 39440; tiempo: 43.0 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261999; percepción reforzada: no; repetición máxima de clases (movimiento): 1.

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 257/300 (86 %) | 2.88 | 0.05 | 74.9 | 213 |
| Redes (solo telemetría) | 140/300 (47 %) | 2.54 | 1.89 | 40.7 | 346 |

Acuerdo de dirección red/maestro en barridos nuevos: 75.5 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 200 | 0 | 0 | 0 | 3 | 98.5 % |
| PARED | 39 | 9787 | 310 | 230 | 500 | 90.1 % |
| OBSTACULO | 3 | 316 | 1072 | 94 | 62 | 69.3 % |
| LOBITO | 12 | 20 | 52 | 344 | 33 | 74.6 % |
| SALIDA | 58 | 308 | 119 | 68 | 1177 | 68.0 % |

Acierto total por sector: 85.0 %.
