# Entrenamiento con la Bahía de Caza simulada (2026-11-16)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 160; ejemplos: 38322; tiempo: 41.8 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20262777; percepción reforzada: no; repetición máxima de clases (movimiento): 1.

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 257/300 (86 %) | 2.88 | 0.05 | 74.9 | 213 |
| Redes (solo telemetría) | 184/300 (61 %) | 2.69 | 2.28 | 47.5 | 336 |

Acuerdo de dirección red/maestro en barridos nuevos: 76.8 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 192 | 0 | 0 | 0 | 11 | 94.6 % |
| PARED | 31 | 9969 | 262 | 109 | 495 | 91.7 % |
| OBSTACULO | 3 | 303 | 1037 | 126 | 78 | 67.0 % |
| LOBITO | 10 | 31 | 65 | 305 | 50 | 66.2 % |
| SALIDA | 52 | 327 | 87 | 48 | 1216 | 70.3 % |

Acierto total por sector: 85.9 %.
