# Entrenamiento con la Bahía de Caza simulada (2026-11-13)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 300; rondas DAgger: 2; épocas: 80; ejemplos: 33636; tiempo: 17.9 min.
- Calibración usada: 250 mm/s, 120 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-6 (sigmoide, MomentumBackpropagation).

## Pruebas completas (100 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 36/100 (36 %) | 1.98 | 0.32 | 35.9 | 354 |
| Redes (solo telemetría) | 1/100 (1 %) | 0.78 | 2.35 | -4.9 | 422 |

Acuerdo de dirección red/maestro en barridos nuevos: 74.9 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 26 | 0 | 0 | 0 | 0 | 100.0 % |
| PARED | 38 | 19224 | 651 | 371 | 1016 | 90.3 % |
| OBSTACULO | 9 | 677 | 3784 | 753 | 181 | 70.0 % |
| LOBITO | 0 | 53 | 177 | 812 | 64 | 73.4 % |
| SALIDA | 2 | 578 | 403 | 213 | 1817 | 60.3 % |

Acierto total por sector: 83.2 %.
