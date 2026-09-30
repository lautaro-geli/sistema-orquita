# Modelo de demostración

entorno-demo.nnet: Neuroph 2.98, MLP 12-6-2 sigmoid, salidas LIBRE/OBJETO.
Datos ficticios: 2400 de entrenamiento, semilla 101; pesos aleatorios, semilla 20260930. MomentumBackpropagation: learningRate 0.1, momentum 0.7, maxIterations 4000, maxError 0.003. Paró por límite de 4000 iteraciones, error 0.005529142110638303; no alcanzó el error objetivo.
Validación independiente: 1000 muestras, semilla 202, 98.00 % de exactitud (desarrollo). Evaluación posterior: semilla 424242, 97.80 %; frontera con otro generador, semilla 987654, 90.625 %. Ninguna lectura de hardware se utilizó ni se afirma éxito en campo.

La distribución sintética incluye frente despejado, paredes laterales, objeto estrecho, todo cercano, todo lejano y casos de frontera 330–500 mm; ruido uniforme ±5 %. Se etiqueta OBJETO si alguna distancia frontal (4..7) es <400 mm. Esa regla solo genera etiquetas: la inferencia usa exclusivamente la red. No hay garantía del 95 % fuera de esta distribución, especialmente cerca de los 400 mm.

Para entrenar otro modelo sin sobreescribir el entregado: ejecutar EntrenadorDemostracion con una ruta inexistente. Si existe, se carga y evalúa. Las semillas permiten reproducir el conjunto; generar(cantidad,semilla) define los datos.

SHA-256 del modelo entregado: 15f312013f62ed4c846399f364cece623bcc81963321cb403f324ce32ef5dde5
