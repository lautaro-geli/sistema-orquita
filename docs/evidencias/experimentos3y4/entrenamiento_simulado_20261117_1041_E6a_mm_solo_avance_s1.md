# Entrenamiento con la Bahía de Caza simulada (2026-11-17)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 80; ejemplos: 39938; tiempo: 33.7 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (salida lineal, entropía cruzada, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261113; percepción reforzada: no; repetición máxima de clases (movimiento): 2.
- Milímetros: solo en avances y retrocesos, peso 1.0; episodios que arrancan en escape: 0 (+0 por ronda DAgger).

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 257/300 (86 %) | 2.88 | 0.05 | 74.9 | 213 |
| Redes (solo telemetría) | 157/300 (52 %) | 2.61 | 2.67 | 41.5 | 334 |

Acuerdo de dirección red/maestro en barridos nuevos: 75.4 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 194 | 0 | 0 | 0 | 9 | 95.6 % |
| PARED | 41 | 9628 | 376 | 215 | 606 | 88.6 % |
| OBSTACULO | 3 | 308 | 1025 | 155 | 56 | 66.3 % |
| LOBITO | 14 | 19 | 72 | 325 | 31 | 70.5 % |
| SALIDA | 53 | 272 | 183 | 54 | 1168 | 67.5 % |

Acierto total por sector: 83.3 %.
