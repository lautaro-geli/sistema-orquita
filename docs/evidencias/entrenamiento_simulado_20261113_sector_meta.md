# Entrenamiento con la Bahía de Caza simulada (2026-11-13)

> **Opción 1 (sector meta + distancia, con balanceo de clases).** Generado el 13/11/2026 en el entorno de
> desarrollo con Neuroph compilado desde su código (no la 2.98): sirve para comparar, pero esos modelos no se
> suben. Para los modelos del Servidor, correr `./ejecutar.ps1 simular-entrenar` en la PC del equipo.
>
> | Versión de la red de movimiento | Misión completa | Capturas | Choques | Puntaje |
> |---|---|---|---|---|
> | Dirección + ms (PC, Neuroph 2.98) | 1 % | 0,78 | 2,35 | −4,9 |
> | Sector meta, sin balancear | 1 % | 0,52 | 2,11 | −6,1 |
> | Sector meta, con balanceo (este reporte) | 2 % | 1,26 | 2,50 | −0,2 |


Generado por `orquitas.herramientas.EntrenamientoSimulado`. Datos SINTÉTICOS del simulador 2D: sirven para entrenar y comparar estrategias, no reemplazan la prueba en la pista real.

- Episodios del maestro: 300; rondas DAgger: 2; épocas: 80; ejemplos: 33958; tiempo: 34.0 min.
- Calibración usada: 250 mm/s, 120 °/s, robot 200 x 150 mm [A CONFIRMAR con el robot real].
- Redes: percepción por ventana 46-24-5 (x 13 sectores), movimiento 184-48-15 (sigmoide, MomentumBackpropagation).

## Pruebas completas (100 bahías nuevas, 7 min, 3 Lobitos)

| Piloto | Misión completa | Capturas | Choques | Puntaje | Segundos |
|---|---|---|---|---|---|
| Maestro (geometría real) | 36/100 (36 %) | 1.98 | 0.32 | 35.9 | 354 |
| Redes (solo telemetría) | 2/100 (2 %) | 1.26 | 2.50 | -0.2 | 419 |

Acuerdo de dirección red/maestro en barridos nuevos: 72.1 %.

## Percepción por sector (barridos nuevos)

Filas: etiqueta real. Columnas: etiqueta de la red.

| real \ red | LIBRE | PARED | OBSTACULO | LOBITO | SALIDA | acierto |
|---|---|---|---|---|---|---|
| LIBRE | 24 | 2 | 0 | 0 | 0 | 92.3 % |
| PARED | 35 | 19497 | 709 | 241 | 818 | 91.5 % |
| OBSTACULO | 5 | 696 | 4235 | 354 | 114 | 78.4 % |
| LOBITO | 0 | 70 | 260 | 722 | 54 | 65.3 % |
| SALIDA | 3 | 709 | 469 | 156 | 1676 | 55.6 % |

Acierto total por sector: 84.8 %.
