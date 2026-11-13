# Mejoras para que la red neuronal ande lo mejor posible

Lista de lo que hay que tener, por bloques. Acá solo se anota; se implementa más adelante, de a un bloque.
Estado: ✅ está · 🟡 a medias · ❌ falta.

---

## Bloque 2 — Un simulador fiel a la pista

### 2.1 Geometría de la bahía ❌ (las medidas cambiaron)

Medidas reales de la pista (13/11/2026, aproximadas):

- **Base recta: 152 cm** (1520 mm). Hoy el simulador usa 115 cm.
- **De la base recta a la salida: 114 cm** (1140 mm). Hoy el simulador usa 120 cm.
- **Forma: un rectángulo con curvas en vez de esquinas**, todo dentro del rectángulo de 152 × 114 cm. **No tan circular** como la media elipse actual: los costados y el fondo tienen que ser casi rectos, y solo las esquinas redondeadas.
- Salida (estrecho) de 30 cm en el centro del lado de enfrente de la base (se mantiene).
- 2 obstáculos de ~9 × 9 cm y canicas como Lobitos (se mantiene).

Cómo modelarlo (propuesta, sin implementar):

- Opción A: rectángulo de 1520 × 1140 mm con esquinas redondeadas de radio R (arcos de circunferencia unidos por tramos rectos).
- Opción B: superelipse |x/760|^n + |y/1140|^n = 1 (mitad superior), con n ≈ 4 a 6. Cuanto mayor n, más parecida a un rectángulo.
- Cualquiera de las dos queda parametrizada (ancho, alto, radio o n) para ajustarla cuando se midan bien las curvas.

A confirmar en la pista:

- [ ] Radio aproximado de las curvas de las esquinas (o cuánto "entra" la curva desde cada esquina del rectángulo).
- [ ] ¿Las esquinas de la base (abajo) también son curvas, o solo las dos de enfrente (del lado de la salida)?
- [ ] Ancho exacto del estrecho y si tiene "paredes" hacia afuera (un pasillo) o es solo un hueco.
- [ ] Qué hay afuera de la salida (espacio libre, otra pared a cierta distancia): cambia lo que mide el sensor al mirar por el estrecho.
- [ ] Altura de las paredes y de los obstáculos frente a la altura del sensor (que el haz no pase por encima).
- [ ] Material/color de paredes, obstáculos y canicas (cuánto reflejan el láser).
- [ ] Posición de partida exacta: distancia del robot a la pared izquierda y a la base, y hacia dónde mira. Con 152 cm de ancho, la partida "abajo a la izquierda" ya no es x = -420 mm: hay que recalcularla con la medida real.
- [ ] Zonas donde nunca se ponen obstáculos o canicas (por ejemplo, delante de la salida o de la partida).

Consecuencias cuando se cambie:

- Cambian `Bahia` (paredes, salida, `adentro`, generación de obstáculos y canicas) y la partida en `SimuladorBahia`.
- El maestro y el reflejo no dependen de la forma, pero hay que volver a medir al maestro (techo) en la bahía nueva.
- Hay que **volver a entrenar** las dos redes: las actuales aprendieron una bahía más chica y más redonda.
- Actualizar las pruebas de geometría (`SimuladorBahiaTest`) y el README.

### 2.2 Sensor simulado igual al real 🟡

- Hoy: 13 sectores × 3 ROI (conos de 15°/20°/27° [A CONFIRMAR]), promedio ponderado por señal, ruido, `-1` fuera de 40–1300 mm, pérdidas y bloques sin eco. Se parece a los barridos reales en el patrón canica/pared, pero **no está contrastado**.
- Hay que tener: comparación sistemática simulado vs real en las mismas escenas (pared plana a varias distancias, obstáculo, canica a 5/10/20/30 cm, mirando la salida), con el sensor nivelado. Ajustar con eso el ruido, la tasa de `-1`, los conos de cada ROI y la reflectividad de la canica.

### 2.3 Robot simulado igual al real 🟡

- Hoy: 200 × 150 mm, 250 mm/s, 120 °/s, boca de 60 mm, sensor 30 mm detrás del frente, 10 % de ruido de velocidad, todo estimado.
- Hay que tener: las medidas reales (bloque 1, calibración) y, si se puede, el error real de los giros y avances (cuánto varía de una vez a otra) para usarlo como ruido.
