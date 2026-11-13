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

## percepcion.nnet y movimiento.nnet — EXPERIMENTALES (bahía simulada)

**Todavía no conviene usarlos en la pista.** Si están en esta carpeta, el Servidor los usa por defecto; para la navegación anterior agregar `--reglas`: `./ejecutar.ps1 servidor COM5 --umbral=1 --reglas`.

- Entrenados el 13/11/2026 en la PC del equipo con Neuroph 2.98 (`./ejecutar.ps1 simular-entrenar 300 80 modelos 2`): 300 episodios del maestro, 80 épocas, 2 rondas DAgger, 33 636 ejemplos. Bahía con 2 obstáculos y partida abajo a la izquierda. Calibración estimada (250 mm/s, 120 °/s) **[A CONFIRMAR]**.
- `percepcion.nnet`: MLP 46-24-5 por ventana (se aplica a los 13 sectores): LIBRE, PARED, OBSTACULO, LOBITO, SALIDA. Acierto por sector en barridos nuevos: 83,2 %.
- `movimiento.nnet`: MLP 184-48-6: AVANZAR, RETROCEDER, GIRAR_IZQ, GIRAR_DER, DETENER y duración (50–1500 ms).
- En 100 bahías simuladas nuevas: misión completa 1 % (maestro 36 %), 0,78 capturas, 2,35 choques, −4,9 puntos. Reporte: `docs/evidencias/entrenamiento_simulado_20261113.md`.
- SHA-256 percepcion.nnet: dccdad85000332ca38760dd2dda507cbec057967c27c0159aa706c816c272f64
- SHA-256 movimiento.nnet: 15ed39e8a58e62522ab3fcc02bcdbb1a67dbce8e41b59ef3cc0a3bb0c6a97a1a
