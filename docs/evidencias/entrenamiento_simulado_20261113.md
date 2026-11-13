# Entrenamiento con la Bahía de Caza simulada (2026-11-13)

> **Nota (13/11/2026):** este reporte se generó en el entorno de desarrollo con Neuroph compilado desde su código
> fuente (no la 2.98 de Maven), así que esos modelos no se suben: el Servidor usa Neuroph 2.98. Para tener
> `modelos/percepcion.nnet` y `modelos/movimiento.nnet` hay que correr `./ejecutar.ps1 simular-entrenar`
> (o el workflow "Entrenar en la bahía simulada"), que vuelve a escribir este reporte con los números nuevos.
> Con el detector de atasco final (recorre todas las opciones), las mismas 100 bahías dieron: redes 3/100,
> 1,19 capturas, 5,15 choques, −16,2 puntos.


Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 300; rondas DAgger: 2; épocas: 80; ejemplos: 31786; tiempo: 13.9 min.
- Calibración usada: 250 mm/s, 120 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-6 (sigmoide, MomentumBackpropagation).

## Pruebas completas (100 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 34/100 (34 %) | 2.08 | 0.29 | 35.1 | 344 |
| Redes (solo telemetría) | 3/100 (3 %) | 1.19 | 5.40 | -17.4 | 421 |

Acuerdo de dirección red/maestro en barridos nuevos: 72.7 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 65 | 0 | 0 | 0 | 0 | 100.0 % |
| PARED | 28 | 14233 | 663 | 124 | 827 | 89.7 % |
| OBSTACULO | 18 | 509 | 5167 | 238 | 132 | 85.2 % |
| LOBITO | 0 | 69 | 149 | 537 | 61 | 65.8 % |
| SALIDA | 12 | 473 | 261 | 55 | 1391 | 63.5 % |

Acierto total por sector: 85.5 %.
