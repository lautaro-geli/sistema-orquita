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
 *   r      = medir con el VL53L1X en el angulo actual (ROI 4x4, 8x8 y 16x16)
 *   h      = ayuda
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
  decirLn(F("w/s/a/d mover, x frenar, +/- paso, j/l barrido, b frente, i/k elevacion, n elev 0, r medir, h ayuda"));
  decir(F("Paso ms: ")); numLn(paso);
}

void atender(char c) {
  if (c >= 'A' && c <= 'Z') c = c - 'A' + 'a';
  switch (c) {
    case 'w': case 's': case 'a': case 'd': mover(c); break;
    case 'x': case ' ': detener(); decirLn(F("Freno")); break;
    case '+': if (paso < 1500) paso += 100; decir(F("Paso ms: ")); numLn(paso); break;
    case '-': if (paso > 100) paso -= 100; decir(F("Paso ms: ")); numLn(paso); break;
    case 'j': angBarrido = min(180, angBarrido + 15); barrido.write(angBarrido); decir(F("Barrido: ")); numLn(angBarrido); break;
    case 'l': angBarrido = max(0, angBarrido - 15); barrido.write(angBarrido); decir(F("Barrido: ")); numLn(angBarrido); break;
    case 'b': angBarrido = 90; barrido.write(angBarrido); decirLn(F("Barrido: 90 (frente)")); break;
    case 'i': angElevacion = min(180, angElevacion + 15); elevacion.write(angElevacion); decir(F("Elevacion: ")); numLn(angElevacion); break;
    case 'k': angElevacion = max(0, angElevacion - 15); elevacion.write(angElevacion); decir(F("Elevacion: ")); numLn(angElevacion); break;
    case 'n': angElevacion = 0; elevacion.write(0); decirLn(F("Elevacion: 0")); break;
    case 'r': {
      detener();
      decir(F("Medida mm (4x4 / 8x8 / 16x16) a ")); num(angBarrido); decir(F(" grados: "));
      num(medir(4)); decir(F(" / ")); num(medir(8)); decir(F(" / ")); numLn(medir(16));
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
  barrido.attach(PIN_SERVO_BARRIDO, PULSO_0_US, PULSO_180_US);
  elevacion.attach(PIN_SERVO_ELEVACION, PULSO_0_US, PULSO_180_US);
  barrido.write(angBarrido);
  elevacion.write(angElevacion);
  delay(800);
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
