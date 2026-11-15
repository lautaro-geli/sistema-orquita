# Entrenamiento con la Bahía de Caza simulada (2026-11-15)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 300; rondas DAgger: 2; épocas: 80; ejemplos: 21035; tiempo: 30.9 min.
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, MomentumBackpropagation).

## Pruebas completas (100 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 87/100 (87 %) | 2.88 | 0.01 | 75.3 | 217 |
| Redes (solo telemetría) | 17/100 (17 %) | 2.35 | 1.02 | 29.5 | 399 |

Acuerdo de dirección red/maestro en barridos nuevos: 69.2 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 158 | 1 | 0 | 2 | 42 | 77.8 % |
| PARED | 27 | 10022 | 204 | 145 | 468 | 92.2 % |
| OBSTACULO | 3 | 391 | 959 | 133 | 61 | 62.0 % |
| LOBITO | 12 | 35 | 63 | 304 | 47 | 65.9 % |
| SALIDA | 46 | 386 | 131 | 33 | 1134 | 65.5 % |

Acierto total por sector: 84.9 %.
