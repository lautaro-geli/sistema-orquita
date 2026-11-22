# Bitácora: primeras pruebas reales Servidor + Orquita (20 al 22/11/2026)

Rama `maestro-v2`. Servidor Java (redes E2 o reglas) por Bluetooth con el sketch `arduino/Orquita/Orquita.ino`.
Resultado al cierre del 21/11: enlace, motores, servos y sensor funcionando; falta la corrida completa en la bahía.

## Conexiones y alimentación (las que funcionan, 22/11)

| Elemento | Conexión |
|---|---|
| **Arduino** | **4 AA (6 V) + pack 1 de 4,8 V en serie = 10,8 V** al jack o a VIN/GND. Con 6 V solos el regulador no llega a 5 V (ver problema 16) |
| **L298N (motores)** | **pack 2 + pack 3 de 4,8 V en serie = 9,6 V** al +12V; jumpers ENA y ENB puestos; borne 5V sin conectar |
| **Servos** | **4 AA (6 V)**: + y − a los rojos y marrones de los dos servos |
| Servo de barrido (gira el sensor) | señal D4 |
| Servo de elevación (levanta el servo del sensor) | señal D12 |
| L298N, señales | IN1 D7, IN2 D8 (motor izquierdo, OUT1/OUT2); IN3 D9, IN4 D11 (motor derecho, OUT3/OUT4) |
| HC-06 | TXD → D10; RXD ← A2 con divisor; VCC al 5V del Arduino; 9600 baudios |
| VL53L1X | **VIN al 5V del Arduino** (nunca al pin VIN, que ahora tiene 10,8 V), GND, SDA A4, SCL A5 |
| FC-51 | A0 (activo en bajo), VCC al 5V del Arduino |
| Masas | todas unidas (Arduino, pilas de servos, pilas de motores) |

## Problemas y soluciones (en orden)

| # | Síntoma | Causa | Solución |
|---|---|---|---|
| 1 | `UnsupportedClassVersionError` (class 61 vs 52) en otra PC | Esa PC tiene Java 8 | El paquete portátil trae su propio Java 17 (`jre/`); `servidor_portable.bat` lo usa |
| 2 | HC-06 no aparece en Windows 11 | Detección básica | Configuración > Bluetooth > Detección de dispositivos = Avanzada. "Conecta y se desconecta" al vincular es normal (SPP conecta solo al abrir el COM) |
| 3 | `ERROR_SEGURO (sin recibir nada … 15 s)` | Se abrió el COM **entrante** | Usar el **saliente** (Bluetooth > Más opciones > Puertos COM). Al volver a vincular los números se invierten (PC principal: primero COM10, después COM11). Herramienta: `orquita_puertos_bt.bat` |
| 4 | `Acceso denegado` al abrir el COM | Otro programa tenía el puerto | Un solo programa por COM: cerrar control manual / simulador / Servidor / Monitor Serie en ese COM |
| 5 | `servidor_portable.bat` fallaba en Documentos | Ese .bat solo sirve dentro del paquete | En la PC del repo usar `orquita_prueba_real.bat`; el portable quedó en `Documents\orquita-paquete` y avisa si se usa fuera |
| 6 | `orquita_prueba_real.bat` no podía borrar la copia compilada | El IDE de Arduino tenía abierto el sketch de esa carpeta | El script reusa la copia si está entera o compila en otra; el sketch para el IDE vive en `Documents\Arduino\Orquita` |
| 7 | Subida falla: `not in sync: resp=0x5b` | El cargador no arrancó; contestaba el programa (`[`) | Cerrar Monitor Serie, desconectar D10, reintentar; si no, RESET al aparecer "Uploading" |
| 8 | Arduino colgado tras `Orquita lista` | Bus I2C trabado sin timeout | `Wire.setWireTimeout`, destrabe del bus al arrancar (9 pulsos de SCL) y reinicio automático del sensor tras 3 trabas |
| 9 | Servos con tirones; `r` medía con el servo moviéndose | SoftwareSerial (HC-06) frena interrupciones ~1 ms/byte y deforma el pulso del Servo | Los servos se sueltan (`detach`) al llegar; se mide en silencio |
| 10 | Sensor siempre arriba; barrido veía ~8 cm en todos los ángulos | Subía 180° antes de cada orden y se soltaba a los 250 ms, a mitad de camino | El sensor queda abajo; esperas de 700 ms para movimientos grandes |
| 11 | La canica chocó contra el sensor al atraparla | El sensor bajo tapa la entrada del embudo | Sube solo en AVANZAR si el último barrido vio algo < 35 cm entre 60° y 120° (`CERCA_FRENTE_MM`). Falta medir el ángulo mínimo de subida |
| 12 | Barrido de 4,35–6,7 s | Config vieja (75 ms por ROI, reintentos) | Config D (33/25/20 ms, orden 16→8→4, ROI chico en 197, ida y vuelta): **1,90 s medido** |
| 13 | Una sola rueda se movía; después ninguna | Poca tensión en el L298N (≈2 V de caída) y pilas agotándose | Motores a 8 AA (≈12 V); revisar LED del L298N y GND común |
| 14 | ControlManual se movía solo con el simulador abierto | Tomaba las letras de `LATIDO` como comandos | Ignora mayúsculas |
| 15 | `I2C trabado` según el ángulo, empeorando hasta fallar siempre; el escáner lo veía | **VIN del VL53L1X conectado al pin VIN del Arduino (≈9,6 V)**: con el láser prendido se caía | VIN del sensor al **5V** del Arduino. Además I2C a 100 kHz. Diagnóstico con `EscanerI2C` y los barridos 7/8/9/v/u de ControlManual |
| 16 | El sensor fallaba también con ControlManual y quieto, de forma intermitente; `no detectado` al arrancar; `Orquita lista` repetido (reinicios); fallaba más a medida que pasaba el tiempo | **Arduino alimentado con 6 V por el jack**: el regulador necesita ~7 V, el pin 5V quedaba en ~4–4,5 V y bajaba más con el consumo del láser y del HC-06 (y con las pilas descargándose). Con USB conectado además puede alternar entre fuentes | **Arduino a 10,8 V** (4 AA + pack de 4,8 V en serie). Con eso midió bien. Corrección del punto 15: con 6 V en el jack, el pin VIN daba ~5,3 V, no 9,6 V; el sensor no se quemó |
| 17 | Las ruedas no se movían aunque el Arduino mandaba las órdenes | Pilas de los motores agotadas | Cargarlas; motores con 2 packs de 4,8 V (9,6 V) |
| 18 | El servo de barrido no se movía con Orquita.ino | Alimentación baja (mismo problema que el 16 y pilas de servos) | Servos con 4 AA (6 V) aparte |

**Lección:** casi todas las fallas "raras" (I2C, reinicios, servos y motores que no responden) fueron de **alimentación**. Antes de buscar en el código, medir las tensiones con el robot andando.

Descartado en el camino (con datos): cable cortándose por posición (girándolo a mano el escáner lo veía siempre), tiempo de asentamiento y `detach` (7, 8 y 9 fallaban igual), cantidad de lecturas (el barrido al revés falló por ángulo, no por orden).

## Datos medidos

- Ruido del sensor quieto: 1–2 mm (a 90°: 62–70 / 74–75 / 82–86 mm en 4x4/8x8/16x16).
- Barrido config D: 1,90 s (13 ángulos × 3 ROI). Con espera de 400 ms: 5,5 s.
- Calibración del Servidor: 200 mm/s y 212°/s → 50 ms = 1 cm o ≈10,6°. Ahora los motores van con 9,6 V: hay que recalibrar.
- Telemetría real con redes E2 (21/11 20:33, sensor mal elevado): percepción con lobitos fantasma, muchos `RETROCEDER 50 ms` y "Atasco detectado". No sirve para evaluar las redes.

## Herramientas nuevas

- `arduino/ControlManual`: manejo a mano (w/s/a/d, servos, `r`/`u` medir, `1`–`4` pines IN, `5`/`6` servos, `7`/`8`/`9`/`v` barridos de prueba).
- `arduino/EscanerI2C`: estado de SDA/SCL y dispositivos en el bus cada 2 s.
- `PruebaDeteccion` (`orquita_prueba_deteccion.bat`): robot quieto, tabla por sector con lo que mide y lo que ve la red de percepción, y CSV con notas de la escena.
- `herramientas_pc/`: `orquita_control_manual`, `orquita_probar_bt` (LATIDO sin Servidor), `orquita_puertos_bt` (COM saliente), `orquita_ordenar`.
- `Descargas\simulador_servidor.py` (del usuario, Python + pyserial): Servidor falso con órdenes a mano; necesita `Orquita.ino`.

## Pendiente

- Corrida completa en la bahía con `Orquita.ino` actual (config D, 100 kHz, sensor abajo).
- Medir con ControlManual (`i`/`k`) el **ángulo mínimo de elevación** para que entre la canica y ponerlo en `SENSOR_ARRIBA_GRADOS`.
- **Recalibrar avance y giro** con motores a 9,6 V (ControlManual: cuánto avanza y gira con 300 ms) y actualizar el Servidor.
- `RETROCEDER 50 ms` puede no mover el robot real (inercia): revisar un mínimo de pulso.
- Las redes se entrenaron con ROI en 199 en todos los tamaños; el sketch con `BARRIDO_RAPIDO = true` usa 197 para 4x4/8x8: validar con barridos reales.
- `BARRIDO_RAPIDO` quedó en `false` en la PC durante el diagnóstico: con la alimentación arreglada, volver a probar `true` (config D, ~2 s).
- La red de percepción ve LOBITO donde el 16x16 mide mucho menos que el 4x4 (probable piso): prueba de detección real pendiente.
- Conseguir un VL53L1X de repuesto.
