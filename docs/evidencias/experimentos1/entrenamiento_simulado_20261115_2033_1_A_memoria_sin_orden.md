# Entrenamiento con la Bahía de Caza simulada (2026-11-15)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 300; rondas DAgger: 2; épocas: 80; ejemplos: 20469; tiempo: 33.7 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 189-48-15 (sigmoide, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA_MEMORIA_SIN_ORDEN (189).
- Semilla de recolección: 20261113; percepción reforzada: no.

## Pruebas completas (100 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 87/100 (87 %) | 2.88 | 0.01 | 75.3 | 217 |
| Redes (solo telemetría) | 27/100 (27 %) | 2.43 | 1.44 | 32.2 | 392 |

Acuerdo de dirección red/maestro en barridos nuevos: 71.2 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 189 | 0 | 0 | 7 | 7 | 93.1 % |
| PARED | 43 | 9920 | 247 | 177 | 479 | 91.3 % |
| OBSTACULO | 6 | 366 | 931 | 185 | 59 | 60.2 % |
| LOBITO | 13 | 28 | 71 | 315 | 34 | 68.3 % |
| SALIDA | 51 | 372 | 137 | 53 | 1117 | 64.6 % |

Acierto total por sector: 84.2 %.
