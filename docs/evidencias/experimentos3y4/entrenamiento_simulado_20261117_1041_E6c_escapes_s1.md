# Entrenamiento con la Bahía de Caza simulada (2026-11-17)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 80; ejemplos: 46316; tiempo: 39.0 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (salida lineal, entropía cruzada, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261113; percepción reforzada: no; repetición máxima de clases (movimiento): 2.
- Milímetros: solo en avances y retrocesos, peso 1.0; episodios que arrancan en escape: 300 (+150 por ronda DAgger).

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 257/300 (86 %) | 2.88 | 0.05 | 74.9 | 213 |
| Redes (solo telemetría) | 165/300 (55 %) | 2.55 | 1.51 | 46.2 | 336 |

Acuerdo de dirección red/maestro en barridos nuevos: 76.4 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 191 | 0 | 0 | 0 | 12 | 94.1 % |
| PARED | 39 | 9898 | 361 | 134 | 434 | 91.1 % |
| OBSTACULO | 3 | 334 | 1058 | 114 | 38 | 68.4 % |
| LOBITO | 10 | 27 | 83 | 304 | 37 | 65.9 % |
| SALIDA | 49 | 370 | 194 | 35 | 1082 | 62.5 % |

Acierto total por sector: 84.6 %.
