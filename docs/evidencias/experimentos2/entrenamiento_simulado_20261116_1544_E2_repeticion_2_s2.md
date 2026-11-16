# Entrenamiento con la Bahía de Caza simulada (2026-11-16)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 80; ejemplos: 39623; tiempo: 31.3 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261999; percepción reforzada: no; repetición máxima de clases (movimiento): 2.

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 257/300 (86 %) | 2.88 | 0.05 | 74.9 | 213 |
| Redes (solo telemetría) | 166/300 (55 %) | 2.65 | 2.06 | 45.8 | 340 |

Acuerdo de dirección red/maestro en barridos nuevos: 75.5 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 199 | 0 | 0 | 0 | 4 | 98.0 % |
| PARED | 50 | 9897 | 197 | 207 | 515 | 91.1 % |
| OBSTACULO | 6 | 389 | 857 | 212 | 83 | 55.4 % |
| LOBITO | 17 | 24 | 37 | 352 | 31 | 76.4 % |
| SALIDA | 67 | 331 | 83 | 72 | 1177 | 68.0 % |

Acierto total por sector: 84.3 %.
