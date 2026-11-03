# Modelos de la red neuronal

## entorno.nnet — PROVISIONAL (datos sintéticos)

**No es evidencia de campo.** Sirve para probar el sistema de punta a punta mientras no haya barridos reales. Se reemplaza en cuanto se entrene con datos de la pista (`./ejecutar.ps1 entrenar`).

- Neuroph 2.98, MLP 39-16-4 sigmoide. Salidas en el orden del enum: LIBRE, OBJETO, LOBITO, SALIDA. Se reconoce la clase ganadora solo si llega al 50 % (`RedNeuronal.UMBRAL_CONFIANZA`).
- Entrada: 13 sectores × 3 ROI (4x4, 8x8, 16x16) convertidos en cercanía con `RedNeuronal.normalizar`: clamp((1500 − d)/1500, 0, 1), y `-1` (sin eco) vale 0.
- Generación: `orquitas.simulacion.EntrenamientoDemostracion` (`./ejecutar.ps1 provisional`). 2000 ejemplos balanceados (semilla 101):
  - LIBRE: todo lejos (1600–4000 mm) o sin eco.
  - LOBITO: un sector cercano (60–900 mm); el 16x16 de un vecino puede verlo.
  - OBJETO: un bloque contiguo de 2 a 13 sectores cercanos.
  - SALIDA: paredes cercanas a ambos lados con un hueco lejano de 2 a 4 sectores.
- Entrenamiento: MomentumBackpropagation (tasa 0,1, momento 0,7), 89 iteraciones, error 0,0049. Exactitud sintética con otra semilla (202): 99,50 %. Esa cifra mide solo cuánto aprende la red las reglas del generador, no el sensor real.
- SHA-256: 6180621c1a85fd2c7568a5a881364f92f1a00da5106788038fe2058fa68404b7

## Modelo con datos reales

1. Tomar barridos de cada categoría con el robot quieto en cada escena: `./ejecutar.ps1 recolectar COM5 LIBRE 40` (y lo mismo para OBJETO, LOBITO y SALIDA). Se agregan a `datos/barridos.csv`.
2. Entrenar: `./ejecutar.ps1 entrenar`. Sobrescribe `modelos/entorno.nnet` y deja el reporte `docs/evidencias/entrenamiento_AAAAMMDD.md` (categorías, cantidad de ejemplos, validación 80/20, matriz de confusión).
