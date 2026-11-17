# Neuroevolución corta desde E2 (17/11/2026)

Corrido en la PC del usuario con `herramientas_pc/orquita_neuroevolucion_40min`, commit `bbb0172`. Datos SINTÉTICOS del simulador 2D.

- Parte de E2 semilla 3 (`experimentos2_20261116_1544/modelos_E2_repeticion_2_s3`).
- 2 corridas de unos 28 minutos (unas 60 generaciones cada una), con 2 hilos.
- Por generación, 20 pares de variantes en 20 bahías; sigma 0,01; Adam con paso 0,01.
- Recompensa: puntaje de la grilla, con los choques topeados en 5, más 2 puntos por cada minuto que sobra.
- Barrido de 1,91 s, con ruido.

Las redes se validaron después en 900 bahías nuevas (semillas 900000, 800000 y 700000; 300 de cada una):

| Red | Misiones | Choques | Puntaje |
|---|---|---|---|
| E2 (de partida) | 77 % | 4,3 | 47,7 |
| NE40 semilla 1 | 74 % | 3,1 | 51,1 |
| NE40 semilla 2 | 81 % | 4,0 | 51,7 |

Cuándo termina cada una (900 bahías, semillas 700000 en adelante):

| Termina en… | E2 | NE40 s1 | NE40 s2 |
|---|---|---|---|
| Minuto 1 | 0,1 % | 0,2 % | 0,8 % |
| Minuto 2 | 16,8 % | 14,2 % | 13,3 % |
| Minuto 3 | 22,7 % | 20,4 % | 23,3 % |
| Minuto 4 | 17,2 % | 16,2 % | 17,2 % |
| Minuto 5 | 10,4 % | 11,0 % | 13,2 % |
| Minuto 6 | 8,3 % | 6,9 % | 5,9 % |
| Minuto 7 | 4,0 % | 6,7 % | 5,3 % |
| No termina | 20,4 % | 24,3 % | 20,9 % |

**Conclusiones:**
- El refuerzo mejora el puntaje unos 4 puntos (la semilla 2 le gana a E2 en los tres grupos de bahías), pero no acelera: las tres redes siguen con unas 97 decisiones por partida.

**Por qué decide tanto** (300 bahías, barrido de 1,91 s; decisiones por partida, comparadas con el Maestro):

| | Maestro | E2 y NE40 s2 |
|---|---|---|
| Explorando | 24 | 58 |
| Acercándose a un lobito | 7 | 19–20 |
| Escapando | 12 | 20–21 |
| Retrocesos | 3 | 12–13 |
| Lobitos que deja de ver sin capturarlos | 0,9 | 2,6 |

- La percepción ve un lobito falso en el 31 % de los barridos sin lobito.
- La red no tiene memoria de lo que dejó de ver.

**Siguiente:** seguimiento de lobitos y salida en el Servidor.
