/*
 * ControlManual - manejar la Orquita a mano (prueba de hardware, no es el sketch de la competencia).
 *
 * Recibe comandos de una letra por USB (Monitor Serie, 115200) o por Bluetooth (HC-06, 9600):
 *   w = adelante   s = atras   a = girar izq   d = girar der   x o espacio = frenar
 *       Cada comando mueve durante "paso" ms y frena solo. Si el comando se repite antes de que
 *       termine (tecla apretada en orquita_control_manual.bat), sigue andando sin cortes.
 *   + / -  = paso de 100 ms mas o menos (100..1500; arranca en 300)
 *   j / l  = servo de barrido 15 grados a la izquierda / derecha     b = barrido a 90 (frente)
 *   i / k  = servo de elevacion +15 / -15 grados                     n = elevacion a 0
 *   r      = medir con el VL53L1X en el angulo actual (ROI 4x4, 8x8 y 16x16), con los servos quietos
 *   1..4   = prender SOLO el pin IN1, IN2, IN3 o IN4 durante "paso" ms (para ver que rueda y sentido mueve cada uno)
 *   5 / 6  = mover SOLO el servo del pin D4 / D12: 0, 180 y vuelve a 90 (para ver cual es cual)
 *   7 / 8 / 9 = barrido completo como Orquita.ino (13 angulos x 16x16, 8x8, 4x4) y muestra la tabla:
 *            7 = espera 100 ms y suelta el servo; 8 = espera 400 ms y lo suelta; 9 = espera 100 ms sin soltarlo
 *   h      = ayuda
 * Los servos se sueltan (detach) al llegar: SoftwareSerial (HC-06) deforma sus pulsos y les da tirones.
 * Avisa cuando el FC-51 detecta algo.
 * Mismos pines que Orquita.ino. Por seguridad, si deja de recibir comandos, los motores frenan solos.
 */
#include <Wire.h>
#include <SoftwareSerial.h>
#include <VL53L1X.h>
#include <Servo.h>

const uint8_t PIN_SERVO_BARRIDO = 4;
const uint8_t PIN_SERVO_ELEVACION = 12;
const uint8_t PIN_IN1 = 7, PIN_IN2 = 8, PIN_IN3 = 9, PIN_IN4 = 11;
const uint8_t HC_RX = 10, HC_TX = A2;
const uint8_t PIN_CAPTURA = A0;
const int PULSO_0_US = 544, PULSO_180_US = 2400;

SoftwareSerial bt(HC_RX, HC_TX);
VL53L1X sensor;
Servo barrido, elevacion;
bool sensorListo = false;
int angBarrido = 90, angElevacion = 0;
unsigned long paso = 300;
unsigned long finMovimiento = 0;
bool moviendo = false;
bool capturaAnterior = false;
int pinSolo = -1; // Pin IN prendido solo (comandos 1..4), o -1.

void decir(const __FlashStringHelper* t) { Serial.print(t); bt.print(t); }
void decirLn(const __FlashStringHelper* t) { Serial.println(t); bt.print(t); bt.write('\n'); }
void numLn(long v) { Serial.println(v); bt.print(v); bt.write('\n'); }
void num(long v) { Serial.print(v); bt.print(v); }

void detener() {
  digitalWrite(PIN_IN1, LOW); digitalWrite(PIN_IN2, LOW);
  digitalWrite(PIN_IN3, LOW); digitalWrite(PIN_IN4, LOW);
  moviendo = false;
}

// Igual que Orquita.ino: polaridad invertida corregida (true = adelante).
void motores(bool izqAdelante, bool derAdelante) {
  digitalWrite(PIN_IN1, izqAdelante ? LOW : HIGH);
  digitalWrite(PIN_IN2, izqAdelante ? HIGH : LOW);
  digitalWrite(PIN_IN3, derAdelante ? LOW : HIGH);
  digitalWrite(PIN_IN4, derAdelante ? HIGH : LOW);
}

/** Lleva el servo al angulo, espera que llegue y lo suelta. */
void servoA(Servo& sv, uint8_t pin, int grados) {
  sv.write(grados);
  if (!sv.attached()) sv.attach(pin, PULSO_0_US, PULSO_180_US);
  delay(450);
  sv.detach();
}

void probarPinIn(uint8_t n) {
  const uint8_t pines[4] = {PIN_IN1, PIN_IN2, PIN_IN3, PIN_IN4};
  detener();
  pinSolo = pines[n];
  digitalWrite(pinSolo, HIGH);
  moviendo = true;
  finMovimiento = millis() + paso;
  decir(F("Solo IN")); num(n + 1); decir(F(" (pin D")); num(pinSolo); decirLn(F(") en HIGH"));
}

void probarServo(uint8_t pin) {
  Servo& sv = (pin == PIN_SERVO_BARRIDO) ? barrido : elevacion; // Cada Servo nuevo gasta un canal: se reusan.
  decir(F("Servo del pin D")); num(pin); decirLn(F(": 0, 180, 90"));
  servoA(sv, pin, 0); delay(300);
  servoA(sv, pin, 180); delay(300);
  servoA(sv, pin, 90);
  if (pin == PIN_SERVO_BARRIDO) angBarrido = 90; else angElevacion = 90;
}

int medir(uint8_t lado);
int tabla[13][3];

/** Barrido como Orquita.ino: mide en silencio y muestra la tabla al final. */
void barridoPrueba(unsigned long espera, bool soltar) {
  detener();
  unsigned long inicio = millis();
  for (uint8_t s = 0; s < 13; s++) {
    barrido.write(s * 15);
    if (!barrido.attached()) barrido.attach(PIN_SERVO_BARRIDO, PULSO_0_US, PULSO_180_US);
    delay(s == 0 ? 700 : espera);
    if (soltar) barrido.detach();
    tabla[s][2] = medir(16);
    tabla[s][1] = medir(8);
    tabla[s][0] = medir(4);
  }
  unsigned long ms = millis() - inicio;
  barrido.detach();
  angBarrido = 180;
  decir(F("Barrido espera ")); num(espera); decirLn(soltar ? F(" ms, servo suelto al medir") : F(" ms, servo enganchado"));
  uint8_t trabas = 0;
  for (uint8_t s = 0; s < 13; s++) {
    num(s * 15); decir(F(" grados: ")); num(tabla[s][0]); decir(F(" / ")); num(tabla[s][1]); decir(F(" / ")); numLn(tabla[s][2]);
    for (uint8_t r = 0; r < 3; r++) if (tabla[s][r] == -2) trabas++;
  }
  decir(F("Trabas I2C (-2): ")); num(trabas); decir(F(" de 39. Tiempo ms: ")); numLn(ms);
}

void mover(char c) {
  switch (c) {
    case 'w': motores(true, true); break;
    case 's': motores(false, false); break;
    case 'a': motores(false, true); break;
    case 'd': motores(true, false); break;
  }
  if (!moviendo) { decir(F("Movimiento ")); Serial.println(c); bt.write(c); bt.write('\n'); }
  moviendo = true;
  finMovimiento = millis() + paso;
}

int medir(uint8_t lado) {
  if (!sensorListo) return -1;
  sensor.setROISize(lado, lado);
  sensor.setROICenter(199);
  uint16_t mm = sensor.readSingle();
  if (Wire.getWireTimeoutFlag()) { Wire.clearWireTimeoutFlag(); return -2; }
  if (sensor.timeoutOccurred() || sensor.ranging_data.range_status != VL53L1X::RangeValid) return -1;
  return mm;
}

void ayuda() {
  decirLn(F("== Control manual Orquita =="));
  decirLn(F("w/s/a/d mover, x frenar, +/- paso, j/l barrido, b frente, i/k elevacion, n elev 0, r medir"));
  decirLn(F("1-4 un solo pin IN, 5 servo D4, 6 servo D12, 7/8/9 barrido de prueba, h ayuda"));
  decir(F("Paso ms: ")); numLn(paso);
}

void atender(char c) {
  // Solo minusculas: las mayusculas se ignoran, asi los LATIDO/ORDEN de un Servidor abierto no mueven el robot.
  switch (c) {
    case 'w': case 's': case 'a': case 'd': mover(c); break;
    case 'x': case ' ': detener(); decirLn(F("Freno")); break;
    case '+': if (paso < 1500) paso += 100; decir(F("Paso ms: ")); numLn(paso); break;
    case '-': if (paso > 100) paso -= 100; decir(F("Paso ms: ")); numLn(paso); break;
    case 'j': angBarrido = min(180, angBarrido + 15); servoA(barrido, PIN_SERVO_BARRIDO, angBarrido); decir(F("Barrido (D4): ")); numLn(angBarrido); break;
    case 'l': angBarrido = max(0, angBarrido - 15); servoA(barrido, PIN_SERVO_BARRIDO, angBarrido); decir(F("Barrido (D4): ")); numLn(angBarrido); break;
    case 'b': angBarrido = 90; servoA(barrido, PIN_SERVO_BARRIDO, 90); decirLn(F("Barrido (D4): 90 (frente)")); break;
    case 'i': angElevacion = min(180, angElevacion + 15); servoA(elevacion, PIN_SERVO_ELEVACION, angElevacion); decir(F("Elevacion (D12): ")); numLn(angElevacion); break;
    case 'k': angElevacion = max(0, angElevacion - 15); servoA(elevacion, PIN_SERVO_ELEVACION, angElevacion); decir(F("Elevacion (D12): ")); numLn(angElevacion); break;
    case 'n': angElevacion = 0; servoA(elevacion, PIN_SERVO_ELEVACION, 0); decirLn(F("Elevacion (D12): 0")); break;
    case '1': case '2': case '3': case '4': probarPinIn(c - '1'); break;
    case '5': probarServo(PIN_SERVO_BARRIDO); break;
    case '6': probarServo(PIN_SERVO_ELEVACION); break;
    case '7': barridoPrueba(100, true); break;
    case '8': barridoPrueba(400, true); break;
    case '9': barridoPrueba(100, false); break;
    case 'r': {
      detener();
      delay(150); // Que se calme la vibracion de los motores.
      int m4 = medir(4), m8 = medir(8), m16 = medir(16); // Primero mide (en silencio), despues escribe.
      decir(F("Medida mm (4x4 / 8x8 / 16x16) a ")); num(angBarrido); decir(F(" grados: "));
      num(m4); decir(F(" / ")); num(m8); decir(F(" / ")); numLn(m16);
      if (m4 == -2 || m8 == -2 || m16 == -2) decirLn(F("(-2 = I2C trabado: cableado SDA/SCL, GND o ruido)"));
      break;
    }
    case 'h': case '?': ayuda(); break;
    default: break; // \r, \n y otros: se ignoran.
  }
}

void setup() {
  Serial.begin(115200);
  bt.begin(9600);
  pinMode(PIN_IN1, OUTPUT); pinMode(PIN_IN2, OUTPUT);
  pinMode(PIN_IN3, OUTPUT); pinMode(PIN_IN4, OUTPUT);
  pinMode(PIN_CAPTURA, INPUT_PULLUP);
  detener();
  servoA(barrido, PIN_SERVO_BARRIDO, angBarrido);
  servoA(elevacion, PIN_SERVO_ELEVACION, angElevacion);
  // Destraba el bus I2C (si el Arduino se reinicio en medio de una lectura, el sensor puede quedar trabado).
  pinMode(SDA, INPUT_PULLUP); pinMode(SCL, OUTPUT);
  for (uint8_t i = 0; i < 9; i++) { digitalWrite(SCL, LOW); delayMicroseconds(10); digitalWrite(SCL, HIGH); delayMicroseconds(10); }
  pinMode(SDA, OUTPUT); digitalWrite(SDA, LOW); delayMicroseconds(10); pinMode(SDA, INPUT_PULLUP); delayMicroseconds(10);
  Wire.begin();
  Wire.setClock(400000);
  Wire.setWireTimeout(25000, true);
  sensor.setTimeout(300);
  for (uint8_t i = 0; i < 3 && !sensorListo; i++) { sensorListo = sensor.init(); if (!sensorListo) delay(200); }
  if (sensorListo) sensorListo = sensor.setDistanceMode(VL53L1X::Short) && sensor.setMeasurementTimingBudget(75000);
  if (!sensorListo) decirLn(F("VL53L1X no detectado: 'r' devuelve -1"));
  ayuda();
}

void loop() {
  while (Serial.available() > 0) atender((char) Serial.read());
  while (bt.available() > 0) atender((char) bt.read());
  if (moviendo && (long)(millis() - finMovimiento) >= 0) { detener(); decirLn(F("Freno")); }
  bool captura = digitalRead(PIN_CAPTURA) == LOW;
  if (captura && !capturaAnterior) decirLn(F("FC-51: detecta algo (canica en el embudo?)"));
  capturaAnterior = captura;
}
