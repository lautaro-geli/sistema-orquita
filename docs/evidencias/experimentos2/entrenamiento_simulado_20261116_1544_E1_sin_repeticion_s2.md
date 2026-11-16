# Entrenamiento con la Bahía de Caza simulada (2026-11-16)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 80; ejemplos: 39042; tiempo: 22.7 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261999; percepción reforzada: no; repetición máxima de clases (movimiento): 1.

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 257/300 (86 %) | 2.88 | 0.05 | 74.9 | 213 |
| Redes (solo telemetría) | 155/300 (52 %) | 2.58 | 2.29 | 41.3 | 346 |

Acuerdo de dirección red/maestro en barridos nuevos: 76.9 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 187 | 0 | 0 | 5 | 11 | 92.1 % |
| PARED | 46 | 9804 | 280 | 278 | 458 | 90.2 % |
| OBSTACULO | 5 | 319 | 951 | 216 | 56 | 61.5 % |
| LOBITO | 15 | 20 | 45 | 361 | 20 | 78.3 % |
| SALIDA | 55 | 335 | 117 | 88 | 1135 | 65.6 % |

Acierto total por sector: 84.0 %.
