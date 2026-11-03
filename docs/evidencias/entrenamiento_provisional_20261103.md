# Entrenamiento PROVISIONAL (sintético) — 2026-11-03

> **Provisional: no es evidencia de campo.** Datos generados por `EntrenamientoDemostracion`, no medidos con el VL53L1X. Se reemplaza por el reporte que genera `EntrenamientoReal` con `datos/barridos.csv`.

- Categorías: LIBRE, OBJETO, LOBITO, SALIDA (balanceadas, 500 ejemplos de cada una).
- Ejemplos: 2000 de entrenamiento (semilla 101); validación independiente con 1000 ejemplos de otra semilla (202).
- Entrada: 39 valores (13 sectores × ROI 4x4, 8x8, 16x16) normalizados con `RedNeuronal.normalizar`.
- Red: MLP 39-16-4 sigmoide, MomentumBackpropagation (tasa 0,1, momento 0,7). Paró en 89 iteraciones con error 0,0049 (objetivo 0,005).
- Criterio: exactitud = aciertos / ejemplos de validación, con el umbral de confianza de 0,5 (si no se reconoce, cuenta como error).
- **Exactitud de validación sintética: 99,50 %.**
- Modelo: `modelos/entorno.nnet` (SHA-256 6180621c1a85fd2c7568a5a881364f92f1a00da5106788038fe2058fa68404b7).
