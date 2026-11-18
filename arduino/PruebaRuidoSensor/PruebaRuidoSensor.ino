/*
 * Prueba de ruido del VL53L1X para acortar el barrido (Proyecto Orquitas, 18/11/2026).
 *
 * El robot queda QUIETO (motores apagados, sin Bluetooth). Hace 20 barridos de 13 posiciones x 3 ROI con
 * cada configuración y manda todo por USB (Serial Monitor a 115200 baudios):
 *   A = como ahora:            75 ms en los 3 ROI, espera del servo 100 ms
 *   B = medición corta:        33 ms en los 3 ROI, espera 100 ms
 *   C = corta y espera corta:  33 ms en los 3 ROI, espera 60 ms   (barrido de ~2,18 s)
 *   D = mixta:                 4x4 33 ms, 8x8 25 ms, 16x16 20 ms, espera 60 ms (~1,91 s)
 *   E = máxima precisión:      200 ms en los 3 ROI, espera 150 ms (~10 s por barrido; 10 barridos).
 *       Sirve de referencia: lo más cerca del valor real que da el sensor.
 *
 * Uso:
 *   1. Poner el robot quieto frente a una escena fija (paredes, un obstáculo, una canica).
 *   2. Abrir el Serial Monitor (115200). Escribir el número de escena (1..9) y Enter:
 *      corre A, B, C, D y E y escribe las líneas de datos.
 *   3. Cambiar la escena (por ejemplo, la canica sola a 20, 35 y 50 cm adelante) y repetir con otro número.
 *   4. Copiar TODO el texto del Serial Monitor a un archivo de texto en Documentos
 *      (por ejemplo Documentos\ruido_sensor.txt).
 *
 * Formato de cada línea de datos (CSV):
 *   D,escena,config,barrido,sector,roi,mm,estado,ms
 *     mm = lectura (sin filtrar: -1 si timeout), estado = range_status del VL53L1X (0 = válida),
 *     ms = tiempo de esa medición.
 * Al final de cada barrido:  T,escena,config,barrido,ms_del_barrido
 *
 * Pines como en Orquita.ino: servo de barrido en D4, servo de elevación en D12 (queda abajo, 0°).
 */
#include <Wire.h>
#include <VL53L1X.h>
#include <Servo.h>

const uint8_t PIN_SERVO_BARRIDO = 4;
const uint8_t PIN_SERVO_ELEVACION = 12;
const uint8_t PIN_IN1 = 7, PIN_IN2 = 8, PIN_IN3 = 9, PIN_IN4 = 11;
const int PULSO_SERVO_0_US = 544, PULSO_SERVO_180_US = 2400;
const int SENSOR_ABAJO_GRADOS = 0;
const unsigned long ESPERA_INICIO_BARRIDO_MS = 500;
const uint8_t SECTORES = 13;
const uint8_t BARRIDOS = 20;
const uint8_t LADOS[3] = {4, 8, 16};

struct Config {
  char nombre;
  uint32_t presupuestoUs[3];  // 4x4, 8x8, 16x16
  unsigned long esperaServoMs;
  uint8_t barridos;
};

const uint8_t CANTIDAD_CONFIGS = 5;
const Config CONFIGS[CANTIDAD_CONFIGS] = {
  {'A', {75000, 75000, 75000}, 100, BARRIDOS},
  {'B', {33000, 33000, 33000}, 100, BARRIDOS},
  {'C', {33000, 33000, 33000}, 60, BARRIDOS},
  {'D', {33000, 25000, 20000}, 60, BARRIDOS},
  {'E', {200000, 200000, 200000}, 150, 10},
};

VL53L1X sensor;
Servo barrido, elevacion;
bool sensorListo = false;

void setup() {
  Serial.begin(115200);
  // En placas con USB propio (Leonardo, Micro) hay que esperar a que se abra el Serial Monitor.
  unsigned long espera = millis();
  while (!Serial && millis() - espera < 3000) { }
  Serial.println(F("# Prueba de ruido: arrancando..."));
  pinMode(PIN_IN1, OUTPUT); pinMode(PIN_IN2, OUTPUT);
  pinMode(PIN_IN3, OUTPUT); pinMode(PIN_IN4, OUTPUT);
  digitalWrite(PIN_IN1, LOW); digitalWrite(PIN_IN2, LOW);
  digitalWrite(PIN_IN3, LOW); digitalWrite(PIN_IN4, LOW);

  barrido.attach(PIN_SERVO_BARRIDO, PULSO_SERVO_0_US, PULSO_SERVO_180_US);
  elevacion.attach(PIN_SERVO_ELEVACION, PULSO_SERVO_0_US, PULSO_SERVO_180_US);
  elevacion.write(SENSOR_ABAJO_GRADOS);
  barrido.write(0);
  delay(1000);

  Serial.println(F("# Servos en posicion. Iniciando el sensor (I2C)..."));
  Wire.begin();
  Wire.setClock(400000);
#if defined(WIRE_HAS_TIMEOUT)
  Wire.setWireTimeout(25000, true); // Si el bus I2C se traba, no se queda colgado para siempre.
#endif
  sensor.setTimeout(300);
  for (uint8_t intento = 0; intento < 3 && !sensorListo; intento++) {
    sensorListo = sensor.init();
    if (!sensorListo) delay(200);
  }
  if (sensorListo) sensorListo = sensor.setDistanceMode(VL53L1X::Short);
  Serial.println(sensorListo ? F("# VL53L1X listo. Escribi el numero de escena (1..9) y Enter.")
                             : F("# ERROR: VL53L1X no detectado."));
}

uint32_t presupuestoActual = 0;
bool traza = false; // Imprime cada paso del primer barrido (para ver dónde se traba).
int lecturas[SECTORES * 3];
uint8_t estados[SECTORES * 3];
uint16_t tiempos[SECTORES * 3];

/** Una medición con el ROI y el presupuesto dados (el presupuesto se cambia solo si hace falta). */
void medir(uint8_t i, uint8_t r, uint32_t presupuestoUs) {
  unsigned long t0 = millis();
  if (traza) { Serial.print(F("#   sector ")); Serial.print(i / 3); Serial.print(F(" ROI ")); Serial.print(LADOS[r]); Serial.flush(); }
  if (presupuestoUs != presupuestoActual) {
    bool ok = sensor.setMeasurementTimingBudget(presupuestoUs);
    if (traza) { Serial.print(ok ? F(" presupuesto ok") : F(" presupuesto RECHAZADO")); Serial.flush(); }
    presupuestoActual = presupuestoUs;
  }
  sensor.setROISize(LADOS[r], LADOS[r]);
  sensor.setROICenter(199);
  uint16_t mm = sensor.readSingle();
  bool timeout = sensor.timeoutOccurred();
  lecturas[i] = timeout ? -1 : (int) mm;
  estados[i] = timeout ? 255 : (uint8_t) sensor.ranging_data.range_status;
  tiempos[i] = (uint16_t) min(65535UL, millis() - t0);
  if (traza) { Serial.print(F(" -> ")); Serial.print(lecturas[i]); Serial.print(F(" mm, estado ")); Serial.print(estados[i]);
               Serial.print(F(", ")); Serial.print(tiempos[i]); Serial.println(F(" ms")); }
}

void correrConfig(char escena, const Config &c) {
  for (uint8_t n = 0; n < c.barridos; n++) {
    traza = (n == 0 && c.nombre == 'A');
    if (traza) Serial.println(F("# Primer barrido de A, paso a paso:"));
    barrido.write(0);
    delay(ESPERA_INICIO_BARRIDO_MS);
    unsigned long inicio = millis();
    for (uint8_t s = 0; s < SECTORES; s++) {
      barrido.write(s * 15);
      delay(c.esperaServoMs);
      for (uint8_t r = 0; r < 3; r++) medir(s * 3 + r, r, c.presupuestoUs[r]);
    }
    unsigned long total = millis() - inicio;
    // Se escribe después del barrido para que el Serial no alargue los tiempos.
    for (uint8_t i = 0; i < SECTORES * 3; i++) {
      Serial.print(F("D,")); Serial.print(escena); Serial.print(',');
      Serial.print(c.nombre); Serial.print(','); Serial.print(n); Serial.print(',');
      Serial.print(i / 3); Serial.print(','); Serial.print(LADOS[i % 3]); Serial.print(',');
      Serial.print(lecturas[i]); Serial.print(','); Serial.print(estados[i]); Serial.print(',');
      Serial.println(tiempos[i]);
    }
    Serial.print(F("T,")); Serial.print(escena); Serial.print(',');
    Serial.print(c.nombre); Serial.print(','); Serial.print(n); Serial.print(',');
    Serial.println(total);
  }
}

/** 's': mueve el servo de barrido de 0° a 180° y vuelve, para ver que se mueve. */
void probarServo() {
  Serial.println(F("# Servo: 0 -> 180 -> 0"));
  for (int g = 0; g <= 180; g += 15) { barrido.write(g); delay(150); }
  barrido.write(0);
  delay(500);
  Serial.println(F("# Servo: listo"));
}

/** 'm': una medición con cada ROI (75 ms), mirando al frente. */
void probarSensor() {
  barrido.write(90);
  delay(300);
  for (uint8_t r = 0; r < 3; r++) {
    unsigned long t0 = millis();
    if (presupuestoActual != 75000) { sensor.setMeasurementTimingBudget(75000); presupuestoActual = 75000; }
    sensor.setROISize(LADOS[r], LADOS[r]);
    sensor.setROICenter(199);
    uint16_t mm = sensor.readSingle();
    Serial.print(F("# ROI ")); Serial.print(LADOS[r]); Serial.print(F(": "));
    if (sensor.timeoutOccurred()) Serial.print(F("TIMEOUT")); else { Serial.print(mm); Serial.print(F(" mm, estado ")); Serial.print(sensor.ranging_data.range_status); }
    Serial.print(F(", ")); Serial.print(millis() - t0); Serial.println(F(" ms"));
  }
  barrido.write(0);
}

unsigned long ultimoAviso = 0;

void loop() {
  if (millis() - ultimoAviso > 5000) {
    ultimoAviso = millis();
    Serial.println(sensorListo ? F("# Esperando: numero de escena (1..9), o 's' = probar servo, 'm' = probar sensor; y Enter.")
                               : F("# ERROR: el VL53L1X no responde. Revisar SDA (A4), SCL (A5), VIN y GND."));
  }
  if (!sensorListo || !Serial.available()) return;
  char escena = Serial.read();
  if (escena == '\n' || escena == '\r') return;
  if (escena == 's' || escena == 'S') { probarServo(); return; }
  if (escena == 'm' || escena == 'M') { probarSensor(); return; }
  if (escena < '1' || escena > '9') {
    Serial.print(F("# No entiendo '")); Serial.print(escena); Serial.println(F("': escribi un numero del 1 al 9."));
    return;
  }
  Serial.print(F("# Escena ")); Serial.print(escena); Serial.println(F(": corriendo A, B, C, D y E (unos 7 minutos)..."));
  for (uint8_t i = 0; i < CANTIDAD_CONFIGS; i++) correrConfig(escena, CONFIGS[i]);
  Serial.print(F("# Escena ")); Serial.print(escena); Serial.println(F(" lista. Cambia la escena y escribi otro numero."));
}
