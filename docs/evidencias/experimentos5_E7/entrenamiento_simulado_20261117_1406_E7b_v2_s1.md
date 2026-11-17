# Entrenamiento con la Bahía de Caza simulada (2026-11-17)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 80; ejemplos: 34490; tiempo: 27.5 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, error cuadrático, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261113; percepción reforzada: no; repetición máxima de clases (movimiento): 2.
- Milímetros: en todas las filas, peso 1.0; episodios que arrancan en escape: 0 (+0 por ronda DAgger).
- Maestro: v2 Opciones[giroYAvance=true, memoria=false, toleranciaEmbudo=true, memoriaSuave=false, giroSeguro=true]; barrido 1.91 s; ruido por ROI (4x4, 8x8, 16x16) x1.51,1.73,1.94.

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro v2 (geometría real) | 293/300 (98 %) | 2.97 | 0.14 | 84.4 | 101 |
| Redes (solo telemetría) | 224/300 (75 %) | 2.75 | 6.22 | 36.2 | 236 |

Acuerdo de dirección red/maestro en barridos nuevos: 63.5 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 201 | 0 | 0 | 0 | 0 | 100.0 % |
| PARED | 53 | 12646 | 255 | 217 | 330 | 93.7 % |
| OBSTACULO | 9 | 529 | 1243 | 214 | 34 | 61.3 % |
| LOBITO | 16 | 46 | 76 | 338 | 24 | 67.6 % |
| SALIDA | 64 | 602 | 243 | 131 | 981 | 48.5 % |

Acierto total por sector: 84.4 %.
