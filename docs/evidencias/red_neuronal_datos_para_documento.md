# Red neuronal de la Orquita — datos para el documento (04/11/2026)

> Material de base para redactar el informe de la red neuronal y sus porcentajes de éxito. Todos los números salen de `docs/evidencias/comparacion_20261104/` y son reproducibles con el código que está ahí.

## 1. Qué hace la red

- **Función:** clasificar el entorno que ve la Orquita en cada barrido, para decidir el movimiento (CU-07). Clases: **LIBRE, OBJETO, LOBITO, SALIDA**.
- **Entrada:** 39 valores por barrido del sensor VL53L1X (ToF, distancia en mm):
  - 13 posiciones del servo (0° a 180°, cada 15°) × 3 tamaños de ROI (4x4, 8x8, 16x16);
  - `-1` = sin eco.
- **Normalización (`RedNeuronal.normalizar`):** cada distancia se convierte en "cercanía" = clamp((1500 − d) / 1500, 0, 1). Lejos vale 0 y pegado al sensor vale 1; sin eco vale 0.
- **Arquitectura:** perceptrón multicapa (Neuroph 2.98) 39 → 16 ocultas → 4 salidas, sigmoide, aprendizaje MomentumBackpropagation (tasa 0,1, momento 0,7).
- **Decisión:** gana la salida mayor (argmax), pero solo si supera el **umbral de confianza de 0,5**; si no, el entorno queda "no reconocido" (la Orquita se detiene y, tras 3 lecturas seguidas, retrocede).

## 2. Datos

### 2.1 Datos sintéticos (modelo provisional que estaba en el repo)

- 2000 ejemplos generados por programa (`EntrenamientoDemostracion`), balanceados en las 4 clases, con semilla 101. No son mediciones reales.
- Exactitud sobre 1000 sintéticos de otra semilla (202): **99,5 %** (LIBRE 100 %, OBJETO 98,4 %, LOBITO 100 %, SALIDA 99,6 %).
- **Esa cifra mide solo cuánto aprendió la red las reglas del generador, no su desempeño con el sensor real.**

### 2.2 Datos reales (escaneos del robot)

| Archivo | Escena | Escaneos | Telemetrías (barridos de 39 valores) | Etiqueta |
|---|---|---|---|---|
| `scaneo` (CSV) | Solo paredes | 18 (Lote 1–4, Lectura anterior, Dataset 01–13) | 26 | OBJETO |
| `scaneo_nuevas_telemetrias` (xlsx) | Todos con lobito | 6 (Grupo 1–6, 3 telemetrías c/u) | 18 | LOBITO |
| **Total** | | **24** | **44** | |

- **Paredes:** distancias de 12 a 511 mm (mediana 144,5 mm), 48 lecturas sin eco.
- **Lobitos:** distancias de 50 a 496 mm (mediana 170,5 mm), 10 lecturas sin eco.
- Ninguna lectura supera ~0,5 m: todos los escaneos se tomaron en un espacio reducido.
- **No hay ejemplos reales de LIBRE ni de SALIDA.**
- Archivo convertido: `datos/barridos.csv`. Escaneo de cada fila: `datos/barridos_grupos.txt`.

## 3. Método de validación

**Validación cruzada dejando un escaneo afuera (leave-one-group-out, 24 pliegues).**

- En cada pliegue se entrena la red sin ese escaneo y después se clasifican sus telemetrías.
- Se separa por escaneo, no por telemetría, porque las 3 telemetrías de un mismo escaneo son casi idénticas: separarlas inflaría el resultado.
- Cada telemetría se evalúa una sola vez, con una red que nunca vio ese escaneo.
- **Criterio de éxito:** la clase predicha es igual a la etiqueta real. "No reconocido" (certeza menor a 0,5) cuenta como error.

## 4. Variantes comparadas

| Variante | Descripción |
|---|---|
| **A — de cero** | Red nueva con pesos al azar, entrenada solo con los escaneos reales (cercanía con alcance 1500 mm). Máx. 3000 iteraciones, error objetivo 0,01. |
| A′ — de cero, alcance 600 mm | Igual que A, pero con la cercanía calculada sobre 600 mm, porque todas las lecturas reales están por debajo de 0,5 m. Experimental: el Servidor hoy usa 1500 mm. |
| **B — red del repo + reales** | Parte de los pesos del modelo provisional del repo y sigue entrenando con sus 2000 sintéticos más los escaneos reales repetidos 20 veces (para que pesen frente a los sintéticos). Máx. 1500 iteraciones. |
| **C — red del repo sin cambios** | El modelo provisional tal como está (referencia). |

## 5. Resultados (validación cruzada, escaneos no vistos)

| Variante | Lobitos nuevos acertados | Paredes acertadas | Total |
|---|---|---|---|
| **A — de cero** | **10/18 (55,6 %)** | 18/26 (69,2 %) | 28/44 (63,6 %) |
| A′ — de cero, 600 mm | 9/18 (50,0 %) | 21/26 (80,8 %) | 30/44 (68,2 %) |
| B — repo + reales | 0/18 (0 %) | 21/26 (80,8 %) | 21/44 (47,7 %) |
| C — repo sin cambios | 0/18 (0 %) | 26/26 (100 %) | 26/44 (59,1 %) |

**Matrices de confusión** (real → predicción):

| Variante | LOBITO→LOBITO | LOBITO→OBJETO | LOBITO→NO RECONOCIDO | OBJETO→OBJETO | OBJETO→LOBITO | OBJETO→NO RECONOCIDO |
|---|---|---|---|---|---|---|
| A | 10 | 8 | 0 | 18 | 8 | 0 |
| A′ | 9 | 9 | 0 | 21 | 5 | 0 |
| B | 0 | 12 | 6 | 21 | 0 | 5 |
| C | 0 | 18 | 0 | 26 | 0 | 0 |

**Por escaneo de lobito, variante A:**

| Escaneo | Aciertos |
|---|---|
| Grupo 1 | 0/3 |
| Grupo 2 | 2/3 |
| Grupo 3 | 3/3 |
| Grupo 4 | 0/3 |
| Grupo 5 | 2/3 |
| Grupo 6 | 3/3 |

Los grupos 1 y 4 nunca se reconocen como lobito: probablemente el lobito no se distingue de la pared en esos barridos (por posición o distancia).

**Paredes confundidas con lobito por A:** Lote 1 (3/3), Dataset 03, 04, 06, 07 y 08.

### Modelos finales (entrenados con los 44 escaneos)

| Modelo | Ajuste a los 44 escaneos con los que entrenó (no es validación) | Exactitud sobre sintéticos por clase |
|---|---|---|
| A — de cero (`modelos/candidatos/entorno_cero.nnet`) | 42/44 | LIBRE 0 %, OBJETO 58,4 %, LOBITO 84,4 %, SALIDA 0 % |
| B — repo + reales (`modelos/candidatos/entorno_mixto.nnet`) | 26/44 | LIBRE 100 %, OBJETO 98,4 %, LOBITO 100 %, SALIDA 98,0 % |
| C — repo (`modelos/entorno.nnet`, sin cambios) | 26/44 (todas las paredes; ningún lobito) | LIBRE 100 %, OBJETO 98,4 %, LOBITO 100 %, SALIDA 99,6 % |

## 6. Conclusiones

1. **Para las telemetrías nuevas, la mejor es la red de cero (A): 55,6 % de los lobitos en escaneos no vistos.** La red del repo, sola o re-entrenada, no reconoce ningún lobito real (0 %).
2. **El resultado es débil.** Con un 59 % de paredes en los datos, una red que responde siempre "OBJETO" ya acierta el 59,1 %. A llega al 63,6 % y A′ al 68,2 %: apenas por encima. Con 24 escaneos no alcanza para afirmar que la red distingue lobitos de paredes con confianza.
3. **Los datos sintéticos no representan al sensor real.** Asumen lobitos como un sector cercano aislado con fondo lejano (> 1,6 m). En los escaneos reales todo está a menos de 0,5 m, así que el lobito no se diferencia por "ancho" del resto. Por eso B y C no los reconocen.
4. **La red de cero solo conoce OBJETO y LOBITO.** No tiene ejemplos reales de LIBRE ni de SALIDA, y en sintéticos acierta 0 % de esas clases. Usarla en la pista haría que la Orquita nunca vea campo libre ni la salida.
5. **El alcance de cercanía importa.** Con 600 mm en vez de 1500, las paredes se reconocen mejor (80,8 % contra 69,2 %), porque todas las lecturas reales están por debajo de 0,5 m.

## 7. Próximos pasos recomendados

- **Juntar más escaneos reales**, unos 30 o más por clase, de las cuatro clases, con `./ejecutar.ps1 recolectar`. Hacen falta sobre todo **LIBRE** (campo abierto) y **SALIDA** (el estrecho), y lobitos en distintas posiciones y distancias, incluso con pared detrás.
- **Ajustar `ALCANCE_CERCANIA_MM`** al rango real de la pista (≈ 600 mm) y re-evaluar.
- **Re-entrenar** con `./ejecutar.ps1 entrenar` y repetir esta validación por escaneo.

## 8. Reproducir

```bash
python3 docs/evidencias/comparacion_20261104/convertir.py <scaneo.csv> <nuevas_telemetrias.xlsx> datos/barridos.csv datos/barridos_grupos.txt
javac -cp "target/classes:target/dependency/*" -d /tmp/exp docs/evidencias/comparacion_20261104/Experimento.java
java -cp "/tmp/exp:target/classes:target/dependency/*" Experimento datos/barridos.csv datos/barridos_grupos.txt modelos/entorno.nnet salida/
```

Duración: unos 22 minutos (24 pliegues × 3 entrenamientos).
