# Entrenamiento con la Bahía de Caza simulada (2026-11-17)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 80; ejemplos: 39880; tiempo: 32.6 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (salida lineal, entropía cruzada, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261999; percepción reforzada: no; repetición máxima de clases (movimiento): 2.
- Milímetros: solo en avances y retrocesos, peso 1.0; episodios que arrancan en escape: 0 (+0 por ronda DAgger).

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 257/300 (86 %) | 2.88 | 0.05 | 74.9 | 213 |
| Redes (solo telemetría) | 150/300 (50 %) | 2.56 | 2.21 | 41.1 | 342 |

Acuerdo de dirección red/maestro en barridos nuevos: 74.4 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 184 | 0 | 0 | 0 | 19 | 90.6 % |
| PARED | 43 | 9915 | 247 | 196 | 465 | 91.2 % |
| OBSTACULO | 6 | 347 | 960 | 178 | 56 | 62.1 % |
| LOBITO | 14 | 29 | 41 | 347 | 30 | 75.3 % |
| SALIDA | 52 | 340 | 104 | 73 | 1161 | 67.1 % |

Acierto total por sector: 84.9 %.
