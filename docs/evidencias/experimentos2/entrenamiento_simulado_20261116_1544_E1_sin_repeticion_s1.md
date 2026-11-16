# Entrenamiento con la Bahía de Caza simulada (2026-11-16)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 80; ejemplos: 39805; tiempo: 23.3 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261113; percepción reforzada: no; repetición máxima de clases (movimiento): 1.

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 257/300 (86 %) | 2.88 | 0.05 | 74.9 | 213 |
| Redes (solo telemetría) | 156/300 (52 %) | 2.57 | 2.82 | 38.7 | 347 |

Acuerdo de dirección red/maestro en barridos nuevos: 77.2 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 199 | 0 | 0 | 0 | 4 | 98.0 % |
| PARED | 44 | 9941 | 215 | 206 | 460 | 91.5 % |
| OBSTACULO | 4 | 368 | 969 | 153 | 53 | 62.6 % |
| LOBITO | 15 | 23 | 73 | 321 | 29 | 69.6 % |
| SALIDA | 55 | 329 | 146 | 81 | 1119 | 64.7 % |

Acierto total por sector: 84.8 %.
