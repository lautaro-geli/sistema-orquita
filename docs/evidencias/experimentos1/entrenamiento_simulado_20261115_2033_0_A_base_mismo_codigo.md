# Entrenamiento con la Bahía de Caza simulada (2026-11-15)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 300; rondas DAgger: 2; épocas: 80; ejemplos: 20873; tiempo: 32.0 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261113; percepción reforzada: no.

## Pruebas completas (100 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 87/100 (87 %) | 2.88 | 0.01 | 75.3 | 217 |
| Redes (solo telemetría) | 11/100 (11 %) | 2.21 | 1.50 | 21.7 | 410 |

Acuerdo de dirección red/maestro en barridos nuevos: 67.1 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 199 | 0 | 0 | 0 | 4 | 98.0 % |
| PARED | 50 | 9807 | 171 | 177 | 661 | 90.3 % |
| OBSTACULO | 7 | 360 | 866 | 220 | 94 | 56.0 % |
| LOBITO | 17 | 31 | 67 | 300 | 46 | 65.1 % |
| SALIDA | 65 | 326 | 103 | 36 | 1200 | 69.4 % |

Acierto total por sector: 83.6 %.
