# Modelo de demostración

entorno-demo.nnet: Neuroph 2.98, MLP 12-6-2 sigmoid, salidas LIBRE/OBJETO.
Datos ficticios: 2400 de entrenamiento, semilla 101; pesos aleatorios, semilla 20260930. MomentumBackpropagation: learningRate 0.1, momentum 0.7, maxIterations 2000, maxError 0.006. Paró en 292 iteraciones, error 0.005986271302766318.
Validación independiente: 1000 muestras, semilla 202, 98.50 % de exactitud. Ninguna lectura de hardware se utilizó ni se afirma éxito en campo.

La distribución sintética incluye frente despejado, paredes laterales, objeto estrecho, todo cercano y todo lejano; ruido uniforme ±5 %. Se etiqueta OBJETO si alguna distancia frontal (4..7) es <400 mm. Esa regla solo genera etiquetas: la inferencia usa exclusivamente la red. No hay garantía del 95 % fuera de esta distribución, especialmente cerca de los 400 mm.

Para entrenar otro modelo sin sobreescribir el entregado: ejecutar EntrenadorDemostracion con una ruta inexistente. Si existe, se carga y evalúa. Las semillas permiten reproducir el conjunto; generar(cantidad,semilla) define los datos.

SHA-256 del modelo entregado: e13b97f47d06433a35073fa2e796e36ae14bfec48ebb162f3deefec3a2bd4f8a
