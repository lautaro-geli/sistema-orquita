# Entrenamiento con la Bahía de Caza simulada (2026-11-16)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 80; ejemplos: 38424; tiempo: 22.8 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20262777; percepción reforzada: no; repetición máxima de clases (movimiento): 1.

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 257/300 (86 %) | 2.88 | 0.05 | 74.9 | 213 |
| Redes (solo telemetría) | 153/300 (51 %) | 2.54 | 2.89 | 37.7 | 346 |

Acuerdo de dirección red/maestro en barridos nuevos: 74.5 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 187 | 0 | 0 | 7 | 9 | 92.1 % |
| PARED | 31 | 9883 | 296 | 242 | 414 | 91.0 % |
| OBSTACULO | 1 | 319 | 1013 | 151 | 63 | 65.5 % |
| LOBITO | 8 | 23 | 59 | 341 | 30 | 74.0 % |
| SALIDA | 47 | 328 | 123 | 78 | 1154 | 66.7 % |

Acierto total por sector: 84.9 %.
