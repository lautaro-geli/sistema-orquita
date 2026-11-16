# Entrenamiento con la Bahía de Caza simulada (2026-11-15)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 300; rondas DAgger: 2; épocas: 80; ejemplos: 20897; tiempo: 38.1 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261113; percepción reforzada: sí.

## Pruebas completas (100 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 87/100 (87 %) | 2.88 | 0.01 | 75.3 | 217 |
| Redes (solo telemetría) | 20/100 (20 %) | 2.31 | 1.17 | 28.1 | 402 |

Acuerdo de dirección red/maestro en barridos nuevos: 70.0 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 27 | 0 | 0 | 0 | 176 | 13.3 % |
| PARED | 7 | 8490 | 731 | 69 | 1569 | 78.1 % |
| OBSTACULO | 0 | 160 | 1260 | 20 | 107 | 81.4 % |
| LOBITO | 1 | 13 | 118 | 248 | 81 | 53.8 % |
| SALIDA | 2 | 108 | 159 | 11 | 1450 | 83.8 % |

Acierto total por sector: 77.5 %.
