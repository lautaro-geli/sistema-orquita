# Modelo de demostración

entorno-demo.nnet: Neuroph 2.98, MLP 12-6-3 sigmoid, salidas LIBRE/OBJETO/LOBITO (argmax, en el orden del enum).
Entrada: cercanía de cada sector, clamp((1500 − d) / 1500, 0, 1), con `Telemetria.ALCANCE_CERCANIA_MM` = 1500. Solo distancia; no se usa la intensidad del sensor.
Datos ficticios (sensor VL53L1X simulado, 12 sectores de 15°): 2400 de entrenamiento, semilla 101, clases balanceadas; pesos aleatorios, semilla 20260930. MomentumBackpropagation: learningRate 0.1, momentum 0.7, maxIterations 4000, maxError 0.003. Paró por el límite de 4000 iteraciones con error 0.008098925265313278; no alcanzó el error objetivo.

Regla de generación (`EntrenamientoDemostracion.generar`): todos los sectores lejos, en [1200, 4000] mm. LOBITO: 1 sector al azar cerca, en [50, 900] mm (un lobito de 17 mm subtiende menos de 15°). OBJETO: un bloque contiguo cerca, en [50, 900] mm; la mitad son obstáculos de 2 a 6 sectores (~90 mm) y la otra mitad muros de 7 a 12. LIBRE: nada cerca. Ruido uniforme de ±10 mm. La regla solo genera etiquetas: la inferencia usa exclusivamente la red.

Validación independiente: 1000 muestras, semilla 202, 98,90 % de exactitud. Frontera de distancia (un sector entre 600–900 o 1200–1500 mm, otro generador) y frontera de ancho (1 contra 2 sectores): ver evidencias/predicciones.csv. Límite: la distinción por ancho es confiable a rango corto/medio; muy lejos, todo tiende a LIBRE. La franja de 900–1200 mm no tiene ejemplos: el paso a LOBITO de un sector aislado ocurre entre unos 825 y 925 mm. Ninguna lectura de hardware se utilizó ni se afirma éxito en campo.

Para entrenar otro modelo sin sobreescribir el entregado: ejecutar EntrenamientoDemostracion con una ruta inexistente. Si existe, se carga y evalúa.

SHA-256 del modelo entregado: e81a950b93d0af6ead34758bb2d6c30ce4b8613dbb015279e6d72d90b122f3c1
