# Entrenamiento con la Bahía de Caza simulada (2026-11-16)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 80; ejemplos: 38538; tiempo: 28.9 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20262777; percepción reforzada: no; repetición máxima de clases (movimiento): 2.

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 257/300 (86 %) | 2.88 | 0.05 | 74.9 | 213 |
| Redes (solo telemetría) | 158/300 (53 %) | 2.62 | 1.14 | 48.4 | 346 |

Acuerdo de dirección red/maestro en barridos nuevos: 75.5 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 199 | 0 | 0 | 0 | 4 | 98.0 % |
| PARED | 34 | 10008 | 254 | 217 | 353 | 92.1 % |
| OBSTACULO | 4 | 328 | 1049 | 107 | 59 | 67.8 % |
| LOBITO | 9 | 25 | 74 | 326 | 27 | 70.7 % |
| SALIDA | 49 | 353 | 125 | 67 | 1136 | 65.7 % |

Acierto total por sector: 85.9 %.
