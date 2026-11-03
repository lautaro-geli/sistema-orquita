/*
 * Orquita - Cliente del Hito 3 ("Liberen a Willy"), Proyecto Orquitas.
 *
 * Ciclo del robot: parar - medir - enviar - mover (un paso acotado).
 *   1. Motores detenidos. Si hay una captura sin confirmar: CAPTURA|id y espera CONFIRMACION (~1 s).
 *   2. Barrido: 13 posiciones (0°, 15°, ..., 180°) x 3 ROI (4x4, 8x8, 16x16) = 39 distancias en mm
 *      (-1 = sin eco). Entre posiciones atiende al Servidor (Safe Mode) y revisa el sensor de captura.
 *   3. Envía TELEMETRIA|v0;...;v38 y espera la ORDEN (~2 s). Sin orden o con RECHAZO: no se mueve.
 *   4. Ejecuta la orden durante un paso corto y vuelve a detenerse.
 *
 * Safe Mode (Fail-Safe): si pasan más de 3000 ms sin recibir NADA del Servidor (ni LATIDO), frena de
 * inmediato (motores y colector) y espera; sale cuando vuelve a recibir algo.
 *
 * Protocolo v2 (una línea por mensaje, terminada en \n):
 *   Orquita -> Servidor: TELEMETRIA|v0;...;v38    CAPTURA|id
 *   Servidor -> Orquita: ORDEN|movimiento   CONFIRMACION   RECHAZO   LATIDO (cada 1 s)
 *
 * Librerías (Gestor de librerías del IDE de Arduino):
 *   - VL53L1X de Pololu (versión 1.3.0 o posterior: usa readSingle()).
 *   - Adafruit PWM Servo Driver Library (Adafruit_PWMServoDriver).
 *   - SoftwareSerial y Wire (vienen con el IDE).
 *
 * Alimentación: servos y motores con fuente propia, separada del Arduino; todas las masas (GND) unidas.
 * Memoria: el UNO tiene ~2 KB de SRAM. No se usa String; los textos fijos van con F() y los números
 * se envían de a uno (la línea de telemetría no se arma completa en RAM).
 * Sin POO: funciones simples (decisión del proyecto).
 */

#include <Wire.h>
#include <SoftwareSerial.h>
#include <VL53L1X.h>
#include <Adafruit_PWMServoDriver.h>

// #define DEPURAR  // Descomentar para ver mensajes por USB (Serial, 115200 baudios).
#ifdef DEPURAR
  #define DEP(x) Serial.print(x)
  #define DEPLN(x) Serial.println(x)
#else
  #define DEP(x)
  #define DEPLN(x)
#endif

// ---------------- Pines (preliminares) ----------------
const uint8_t HC_RX = 10;        // Arduino recibe del HC-06 (TX del HC-06).
const uint8_t HC_TX = 11;        // Arduino envía al HC-06 (RX del HC-06, con divisor de tensión a 3,3 V).
const uint8_t PIN_ENA = 5;       // L298N: PWM motor izquierdo.
const uint8_t PIN_ENB = 6;       // L298N: PWM motor derecho.
const uint8_t PIN_IN1 = 7;
const uint8_t PIN_IN2 = 8;
const uint8_t PIN_IN3 = 12;
const uint8_t PIN_IN4 = 13;
const uint8_t PIN_CAPTURA = 2;   // FC-51: salida activa en BAJO. Cualquier detección es un Lobito.

// ---------------- Canales del PCA9685 ----------------
const uint8_t CANAL_BARRIDO = 0;    // SG90 que gira el sensor de 0° a 180°.
const uint8_t CANAL_ELEVACION = 1;  // SG90 que levanta/baja el sensor.
const uint8_t CANAL_COLECTOR = 2;   // SG90 de rotación continua (colector).

// ---------------- Constantes a calibrar ----------------
const int PULSO_SERVO_0 = 110;        // Pulso (de 4096) para 0°. Calibrar con el SG90 real.
const int PULSO_SERVO_180 = 500;      // Pulso para 180°.
const int COLECTOR_DETENIDO = 307;    // ~1,5 ms: el servo continuo no gira. Ajustar si se arrastra.
const int COLECTOR_GIRANDO = 340;     // Velocidad del colector.
const int SENSOR_ABAJO_GRADOS = 0;    // TODO confirmar: posición del sensor durante el barrido.
const int SENSOR_ARRIBA_GRADOS = 180; // TODO confirmar: sensor levantado (deja pasar la canica).
const uint8_t VELOCIDAD_MOTORES = 180;     // PWM 0..255.
const unsigned long PASO_AVANZAR_MS = 400; // Duración de un paso de AVANZAR/RETROCEDER.
const unsigned long PASO_GIRO_MS = 300;    // Duración de un paso de giro.
const unsigned long ESPERA_SERVO_MS = 40;  // Asentamiento del servo entre posiciones de 15°.
const unsigned long ESPERA_INICIO_BARRIDO_MS = 300; // Volver de 180° a 0°.
const unsigned long ESPERA_ELEVACION_MS = 250;      // Subir o bajar el sensor.
const uint16_t PRESUPUESTO_MEDICION_US = 50000;     // ~50 ms por medición (modo Long).
// Sentido de los ángulos: TODO confirmar. Se asume 0° = derecha del robot, 90° = frente, 180° = izquierda.

// ---------------- Protocolo y tiempos ----------------
const unsigned long BAUDIOS_HC06 = 9600;
const unsigned long SAFE_MODE_MS = 3000;        // Sin recibir nada del Servidor: frenar.
const unsigned long ESPERA_CONFIRMACION_MS = 1000;
const unsigned long ESPERA_ORDEN_MS = 2000;
const unsigned long ANTIRREBOTE_MS = 20;        // El FC-51 debe marcar BAJO al menos este tiempo.
const unsigned long BLOQUEO_CAPTURA_MS = 1500;  // Una canica no genera dos capturas.
const uint8_t SECTORES = 13;
const uint8_t ROIS = 3;
const uint8_t VALORES = SECTORES * ROIS;        // 39
const int SIN_ECO = -1;
const int ALCANCE_MM = 4000;
const uint8_t LARGO_LINEA = 32;                 // Las respuestas del Servidor son cortas.

// Órdenes (en el mismo orden que el enum Movimiento del Servidor).
const int8_t SIN_ORDEN = -1;
const int8_t AVANZAR = 0;
const int8_t RETROCEDER = 1;
const int8_t GIRAR_IZQ = 2;
const int8_t GIRAR_DER = 3;
const int8_t DETENER = 4;

// ---------------- Estado global ----------------
SoftwareSerial bt(HC_RX, HC_TX);
VL53L1X sensor;
Adafruit_PWMServoDriver pca = Adafruit_PWMServoDriver(0x40);

int lecturas[VALORES];                  // 78 bytes: un barrido completo.
char linea[LARGO_LINEA];                // Respuesta del Servidor en armado.
uint8_t largoLinea = 0;
unsigned long ultimoContacto = 0;       // millis() del último byte recibido del Servidor.
bool enSafeMode = false;
int8_t ordenRecibida = SIN_ORDEN;
bool confirmacionRecibida = false;
bool rechazoRecibido = false;
unsigned int capturasGeneradas = 0;     // Ids 1, 2, 3...: los genera el robot.
unsigned int capturasConfirmadas = 0;   // Pendiente = generadas > confirmadas.
unsigned long inicioDeteccion = 0;      // 0 = el FC-51 no está marcando.
unsigned long ultimaCaptura = 0;
bool hayCapturaPrevia = false;

// ======================================================================
// Movimiento y servos
// ======================================================================

/** Detiene los dos motores (sin rueda libre: IN en bajo y PWM en 0). */
void detenerMotores() {
  analogWrite(PIN_ENA, 0);
  analogWrite(PIN_ENB, 0);
  digitalWrite(PIN_IN1, LOW); digitalWrite(PIN_IN2, LOW);
  digitalWrite(PIN_IN3, LOW); digitalWrite(PIN_IN4, LOW);
}

/** Fija el sentido de cada motor (true = adelante) y aplica la velocidad. */
void motores(bool izquierdoAdelante, bool derechoAdelante) {
  digitalWrite(PIN_IN1, izquierdoAdelante ? HIGH : LOW);
  digitalWrite(PIN_IN2, izquierdoAdelante ? LOW : HIGH);
  digitalWrite(PIN_IN3, derechoAdelante ? HIGH : LOW);
  digitalWrite(PIN_IN4, derechoAdelante ? LOW : HIGH);
  analogWrite(PIN_ENA, VELOCIDAD_MOTORES);
  analogWrite(PIN_ENB, VELOCIDAD_MOTORES);
}

/** Lleva un SG90 posicional al ángulo indicado (0..180). */
void moverServo(uint8_t canal, int grados) {
  pca.setPWM(canal, 0, map(grados, 0, 180, PULSO_SERVO_0, PULSO_SERVO_180));
}

void colector(bool girando) {
  pca.setPWM(CANAL_COLECTOR, 0, girando ? COLECTOR_GIRANDO : COLECTOR_DETENIDO);
}

/** Frena todo: motores y colector. Se usa al entrar en Safe Mode. */
void detenerTodo() {
  detenerMotores();
  colector(false);
}

// ======================================================================
// Comunicación con el Servidor y Safe Mode
// ======================================================================

/** Interpreta una línea completa del Servidor. Busca el mensaje dentro de la línea por si llegó pegado
 *  a un resto de otro mensaje (SoftwareSerial pierde bytes mientras transmite). */
void procesarLinea() {
  linea[largoLinea] = '\0';
  char *orden = strstr(linea, "ORDEN|");
  if (orden != NULL) {
    char *mov = orden + 6;
    if (strcmp(mov, "AVANZAR") == 0) ordenRecibida = AVANZAR;
    else if (strcmp(mov, "RETROCEDER") == 0) ordenRecibida = RETROCEDER;
    else if (strcmp(mov, "GIRAR_IZQ") == 0) ordenRecibida = GIRAR_IZQ;
    else if (strcmp(mov, "GIRAR_DER") == 0) ordenRecibida = GIRAR_DER;
    else if (strcmp(mov, "DETENER") == 0) ordenRecibida = DETENER;
  } else if (strstr(linea, "CONFIRMACION") != NULL) {
    confirmacionRecibida = true;
  } else if (strstr(linea, "RECHAZO") != NULL) {
    rechazoRecibido = true;
  }
  // LATIDO u otra cosa: solo sirvió para el reloj del Safe Mode.
  DEP(F("<- ")); DEPLN(linea);
  largoLinea = 0;
}

/** Drena lo que llegó por Bluetooth, anota la hora de cada byte y controla el Safe Mode. */
void atenderServidor() {
  while (bt.available() > 0) {
    char c = (char) bt.read();
    ultimoContacto = millis();
    if (enSafeMode) {
      enSafeMode = false;
      DEPLN(F("Sale del Safe Mode"));
    }
    if (c == '\r') continue;
    if (c == '\n') { procesarLinea(); continue; }
    if (largoLinea < LARGO_LINEA - 1) linea[largoLinea++] = c;
    else largoLinea = 0; // Línea demasiado larga: se descarta.
  }
  if (!enSafeMode && millis() - ultimoContacto > SAFE_MODE_MS) {
    enSafeMode = true;
    detenerTodo();
    DEPLN(F("SAFE MODE: sin noticias del Servidor"));
  }
}

/** Revisa el FC-51 con antirrebote y bloqueo: cada detección genera el siguiente id de captura. */
void revisarCaptura() {
  unsigned long ahora = millis();
  if (digitalRead(PIN_CAPTURA) == HIGH) { inicioDeteccion = 0; return; }
  if (inicioDeteccion == 0) { inicioDeteccion = ahora == 0 ? 1 : ahora; return; }
  if (ahora - inicioDeteccion < ANTIRREBOTE_MS) return;
  if (hayCapturaPrevia && ahora - ultimaCaptura < BLOQUEO_CAPTURA_MS) return;
  capturasGeneradas++;
  ultimaCaptura = ahora;
  hayCapturaPrevia = true;
  DEP(F("Captura detectada id=")); DEPLN(capturasGeneradas);
}

/** Espera sin bloquear: atiende al Servidor y al FC-51. Devuelve false si entró en Safe Mode. */
bool esperarMs(unsigned long ms) {
  unsigned long inicio = millis();
  while (millis() - inicio < ms) {
    atenderServidor();
    revisarCaptura();
    if (enSafeMode) return false;
  }
  return true;
}

/** Envía CAPTURA|id de la primera captura sin confirmar y espera la CONFIRMACION. */
void enviarCapturaPendiente() {
  unsigned int id = capturasConfirmadas + 1;
  confirmacionRecibida = false;
  rechazoRecibido = false;
  bt.print(F("CAPTURA|"));
  bt.print(id);
  bt.write('\n');
  DEP(F("-> CAPTURA|")); DEPLN(id);
  unsigned long inicio = millis();
  while (millis() - inicio < ESPERA_CONFIRMACION_MS) {
    if (!esperarMs(10)) return;
    if (confirmacionRecibida) { capturasConfirmadas = id; return; }
    if (rechazoRecibido) return; // Se reintenta en el próximo ciclo.
  }
}

/** Envía la línea TELEMETRIA número por número (sin armarla en RAM). */
void enviarTelemetria() {
  bt.print(F("TELEMETRIA|"));
  for (uint8_t i = 0; i < VALORES; i++) {
    if (i > 0) bt.write(';');
    bt.print(lecturas[i]);
  }
  bt.write('\n');
  DEPLN(F("-> TELEMETRIA"));
}

/** Espera la ORDEN de la telemetría. Devuelve SIN_ORDEN si llegó RECHAZO, si no llegó o si hubo Safe Mode. */
int8_t esperarOrden() {
  unsigned long inicio = millis();
  while (millis() - inicio < ESPERA_ORDEN_MS) {
    if (!esperarMs(10)) return SIN_ORDEN;
    if (ordenRecibida != SIN_ORDEN) return ordenRecibida;
    if (rechazoRecibido) return SIN_ORDEN;
  }
  return SIN_ORDEN;
}

// ======================================================================
// Sensor y barrido
// ======================================================================

/** Una medición con el ROI indicado (lado en SPADs: 4, 8 o 16). -1 si falla o no hay eco válido. */
int medir(uint8_t lado) {
  sensor.setROISize(lado, lado);
  sensor.setROICenter(199);
  uint16_t mm = sensor.readSingle();
  if (sensor.timeoutOccurred()) return SIN_ECO;
  if (sensor.ranging_data.range_status != VL53L1X::RangeValid) return SIN_ECO;
  if (mm > ALCANCE_MM) return SIN_ECO; // El Servidor rechaza valores mayores a 4000.
  return (int) mm;
}

/** Barrido de 13 posiciones x 3 ROI. Devuelve false si se cortó por Safe Mode. */
bool barrer() {
  moverServo(CANAL_BARRIDO, 0);
  if (!esperarMs(ESPERA_INICIO_BARRIDO_MS)) return false;
  for (uint8_t s = 0; s < SECTORES; s++) {
    moverServo(CANAL_BARRIDO, s * 15);
    if (!esperarMs(ESPERA_SERVO_MS)) return false;
    lecturas[s * ROIS + 0] = medir(4);
    lecturas[s * ROIS + 1] = medir(8);
    lecturas[s * ROIS + 2] = medir(16);
    atenderServidor(); // Drena el buffer entre posiciones (LATIDO) y controla el Safe Mode.
    revisarCaptura();
    if (enSafeMode) return false;
  }
  return true;
}

/** Ejecuta un paso de la orden y vuelve a detenerse. El sensor sube durante el paso (TODO confirmar). */
void ejecutarOrden(int8_t orden) {
  if (orden == DETENER || orden == SIN_ORDEN) return;
  moverServo(CANAL_ELEVACION, SENSOR_ARRIBA_GRADOS);
  if (!esperarMs(ESPERA_ELEVACION_MS)) return;
  unsigned long paso = PASO_AVANZAR_MS;
  switch (orden) {
    case AVANZAR:    motores(true, true); break;
    case RETROCEDER: motores(false, false); break;
    case GIRAR_IZQ:  motores(false, true); paso = PASO_GIRO_MS; break; // TODO confirmar sentido.
    case GIRAR_DER:  motores(true, false); paso = PASO_GIRO_MS; break;
  }
  esperarMs(paso); // Si entra en Safe Mode, atenderServidor() ya frenó.
  detenerMotores();
}

// ======================================================================
// Arranque y ciclo
// ======================================================================

void setup() {
#ifdef DEPURAR
  Serial.begin(115200);
#endif
  pinMode(PIN_ENA, OUTPUT); pinMode(PIN_ENB, OUTPUT);
  pinMode(PIN_IN1, OUTPUT); pinMode(PIN_IN2, OUTPUT);
  pinMode(PIN_IN3, OUTPUT); pinMode(PIN_IN4, OUTPUT);
  pinMode(PIN_CAPTURA, INPUT);
  detenerMotores();

  bt.begin(BAUDIOS_HC06);
  Wire.begin();
  Wire.setClock(400000);

  pca.begin();
  pca.setPWMFreq(50);
  colector(false);
  moverServo(CANAL_ELEVACION, SENSOR_ABAJO_GRADOS);
  moverServo(CANAL_BARRIDO, 0);

  sensor.setTimeout(500);
  if (!sensor.init()) {
    DEPLN(F("No se encontró el VL53L1X: todas las lecturas serán -1"));
  }
  sensor.setDistanceMode(VL53L1X::Long);
  sensor.setMeasurementTimingBudget(PRESUPUESTO_MEDICION_US);

  ultimoContacto = millis(); // Hasta que hable el Servidor, a los 3 s entra en Safe Mode.
  DEPLN(F("Orquita lista"));
}

void loop() {
  detenerMotores();
  atenderServidor();
  if (enSafeMode) { // Quieta hasta volver a recibir algo del Servidor.
    detenerTodo();
    esperarMs(50);
    return;
  }
  colector(true); // TODO confirmar: el colector gira siempre fuera del Safe Mode.

  if (capturasGeneradas > capturasConfirmadas) {
    enviarCapturaPendiente();
    if (enSafeMode) return;
  }

  moverServo(CANAL_ELEVACION, SENSOR_ABAJO_GRADOS);
  if (!esperarMs(ESPERA_ELEVACION_MS)) return;
  if (!barrer()) return;

  ordenRecibida = SIN_ORDEN;
  confirmacionRecibida = false;
  rechazoRecibido = false;
  enviarTelemetria();
  int8_t orden = esperarOrden();
  if (orden == SIN_ORDEN) return; // RECHAZO o sin respuesta: queda detenida y repite el ciclo.
  ejecutarOrden(orden);
}
