# Instrucciones para Claude — Proyecto Orquitas

Antes de proponer o cambiar nada, leé `docs/TRASPASO.md`: tiene el contexto, el estado y los pendientes en orden. Las pruebas con el robot real están en `docs/evidencias/prueba_real/Bitacora_Prueba_Real_20261121.md`. Las consignas del profesor están en `docs/consignas/` y mandan sobre todo lo demás.

## Cómo trabajar

- Respondé en **español rioplatense**, conciso. Lo técnico, explicado para alguien que no es experto.
- Rama de trabajo: `maestro-v2`. Commits chicos y con mensaje en español.
- Java 17 + Maven: `./preparar.ps1` (Windows) o `mvn clean package` compila y corre las pruebas (JUnit 4; no necesitan hardware). Las pruebas tienen que quedar en verde.
- Sketches de Arduino: Arduino UNO, librería VL53L1X de Pololu. Compilá antes de entregar un `.ino` (Arduino IDE o `arduino-cli compile -b arduino:avr:uno`). Verificá el uso de memoria (2 KB de RAM).
- Los entrenamientos y pruebas largas corren en la PC Windows del equipo con los `.bat` de `herramientas_pc/`. Dejan los resultados en `Documents\orquita-corridas\<experimento>_<fecha>`.
- Si algo del robot falla de forma "rara" (I2C trabado, sensor no detectado, reinicios, servos o ruedas que no se mueven), **primero revisá la alimentación** (§5 del traspaso). Así fue casi siempre.

## Reglas que no se rompen

- La clasificación se hace con **Neuroph** (lo exige la consigna).
- **No subir archivos `.nnet` a git.**
- Los archivos de simulación llevan `// Archivo de simulacion` y **no van en los diagramas**. Coherencia 1:1 entre diagramas (`docs/diseno/`), DCU y código.
- Concurrencia solo con semáforos o `synchronized`.
- Protocolo v3 (`docs/PROTOCOLO.md`): un cambio se hace a la vez en el Servidor, el sketch, las pruebas y la documentación.
- No sacar el **candado de salida** (salir sin las 3 capturas descalifica).
- Comentarios verdaderos. No afirmar mediciones reales ni éxito en la pista que no existan. En informes, no describir los datos como "sintéticos", "ficticios", "simulados" ni "demo".
- El VL53L1X va al **5V** del Arduino, nunca al pin VIN.
- Separá siempre señal de ruido: en simulación, con 300 bahías el margen es de ±6 puntos. Una sola corrida o semilla no prueba una mejora.
