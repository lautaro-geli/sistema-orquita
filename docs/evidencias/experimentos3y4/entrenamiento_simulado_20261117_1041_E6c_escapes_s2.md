# Entrenamiento con la Bahía de Caza simulada (2026-11-17)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 80; ejemplos: 47512; tiempo: 39.9 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (salida lineal, entropía cruzada, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261999; percepción reforzada: no; repetición máxima de clases (movimiento): 2.
- Milímetros: solo en avances y retrocesos, peso 1.0; episodios que arrancan en escape: 300 (+150 por ronda DAgger).

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 257/300 (86 %) | 2.88 | 0.05 | 74.9 | 213 |
| Redes (solo telemetría) | 135/300 (45 %) | 2.43 | 2.94 | 33.4 | 345 |

Acuerdo de dirección red/maestro en barridos nuevos: 73.9 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 180 | 0 | 0 | 0 | 23 | 88.7 % |
| PARED | 42 | 10030 | 188 | 167 | 439 | 92.3 % |
| OBSTACULO | 6 | 362 | 978 | 156 | 45 | 63.2 % |
| LOBITO | 11 | 30 | 56 | 329 | 35 | 71.4 % |
| SALIDA | 55 | 342 | 146 | 57 | 1130 | 65.3 % |

Acierto total por sector: 85.4 %.
