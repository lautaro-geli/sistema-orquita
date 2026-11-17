# Entrenamiento con la Bahía de Caza simulada (2026-11-17)

Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 600; rondas DAgger: 2; épocas: 80; ejemplos: 42087; tiempo: 32.0 min.
- Hilos de simulación: 1 (el resultado no depende de la cantidad de hilos; las redes se entrenan en uno solo).
- Calibración usada: 200 mm/s, 212 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (salida lineal, entropía cruzada, MomentumBackpropagation).
- Entradas de la red de movimiento: variante CRUDA (184).
- Semilla de recolección: 20261113; percepción reforzada: no; repetición máxima de clases (movimiento): 2.
- Milímetros: solo en avances y retrocesos, peso 5.0; episodios que arrancan en escape: 0 (+0 por ronda DAgger).

## Pruebas completas (300 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 257/300 (86 %) | 2.88 | 0.05 | 74.9 | 213 |
| Redes (solo telemetría) | 45/300 (15 %) | 1.83 | 6.27 | -6.4 | 401 |

Acuerdo de dirección red/maestro en barridos nuevos: 65.3 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 187 | 0 | 0 | 0 | 16 | 92.1 % |
| PARED | 41 | 10028 | 178 | 161 | 458 | 92.3 % |
| OBSTACULO | 5 | 379 | 775 | 319 | 69 | 50.1 % |
| LOBITO | 12 | 28 | 39 | 346 | 36 | 75.1 % |
| SALIDA | 50 | 359 | 115 | 66 | 1140 | 65.9 % |

Acierto total por sector: 84.3 %.
