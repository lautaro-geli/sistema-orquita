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
 *   - Servo, SoftwareSerial y Wire (vienen con el IDE).
 *
 * Alimentación: servos y motores con fuente propia, separada del Arduino; todas las masas (GND) unidas.
 * Memoria: el UNO tiene ~2 KB de SRAM. No se usa String; los textos fijos van con F() y los números
 * se envían de a uno (la línea de telemetría no se arma completa en RAM).
 * Sin POO: funciones simples (decisión del proyecto).
 */

#include <Wire.h>
#include <SoftwareSerial.h>
#include <VL53L1X.h>
#include <Servo.h>

#define DEPURAR  // Mensajes por USB (Serial, 115200 baudios). Comentar esta línea para la entrega final.
#ifdef DEPURAR
  #define DEP(x) Serial.print(x)
  #define DEPLN(x) Serial.println(x)
#else
  #define DEP(x)
  #define DEPLN(x)
#endif

// ---------------- Pines (cableado real) ----------------
const uint8_t PIN_SERVO_BARRIDO = 2;    // Servo 1: gira el sensor de 0° a 180°.
const uint8_t PIN_SERVO_ELEVACION = 3;  // Servo 2: levanta/baja el sensor.
const uint8_t PIN_SERVO_COLECTOR = 4;   // Servo 3: SG90 de rotación continua (colector).
// ENA y ENB del L298N van con sus jumpers puestos (siempre habilitados): no se usan pines del Arduino.
const uint8_t PIN_IN1 = 7;       // Motor A.
const uint8_t PIN_IN2 = 8;       // Motor A.
const uint8_t PIN_IN3 = 9;       // Motor B.
const uint8_t HC_RX = 10;        // Arduino recibe del HC-06 (TXD del HC-06).
const uint8_t PIN_IN4 = 11;      // Motor B.
const uint8_t HC_TX = A2;        // Arduino envía al HC-06 (RXD del HC-06, con divisor de tensión a 3,3 V).
const uint8_t PIN_CAPTURA = A0;  // FC-51: salida activa en BAJO. Cualquier detección es un Lobito.
// I2C del VL53L1X: SDA = A4, SCL = A5 (fijos en el UNO).

// ---------------- Servos ----------------
// Índices dentro del arreglo servos[].
const uint8_t CANAL_BARRIDO = 0;
const uint8_t CANAL_ELEVACION = 1;
const uint8_t CANAL_COLECTOR = 2;

// ---------------- Constantes a calibrar ----------------
const int PULSO_SERVO_0_US = 544;     // Pulso en µs para 0°. Calibrar con el SG90 real.
const int PULSO_SERVO_180_US = 2400;  // Pulso en µs para 180°.
const bool COLECTOR_GIRA = true;       // false = el colector no gira nunca (para probar sin él).
const int COLECTOR_GIRANDO_US = 1660;  // Velocidad del colector (invertir a ~1340 si gira al revés).
const int SENSOR_ABAJO_GRADOS = 0;    // TODO confirmar: posición del sensor durante el barrido.
const int SENSOR_ARRIBA_GRADOS = 180; // TODO confirmar: sensor levantado (deja pasar la canica).
const unsigned long PASO_AVANZAR_MS = 400; // Duración de un paso de AVANZAR/RETROCEDER.
const unsigned long PASO_GIRO_MS = 300;    // Duración de un paso de giro.
const unsigned long ESPERA_SERVO_MS = 100; // Asentamiento del servo en cada posición (probado en el barrido que funcionó).
const unsigned long ESPERA_INICIO_BARRIDO_MS = 500; // Volver de 180° a 0°.
const unsigned long ESPERA_ELEVACION_MS = 250;      // Subir o bajar el sensor.
// Parámetros del barrido que funcionó bien (modo Short, medición más larga = más estable).
const uint32_t PRESUPUESTO_MEDICION_US = 75000;     // Tiempo de captura del VL53L1X.
const uint16_t LECTURA_MS = 78;                     // Presupuesto + ~3 ms de overhead.
const unsigned long SLOT_MS = 370;                  // Tiempo máximo por ángulo (13 x 370 = 4,8 s).
const uint16_t TIMEOUT_SENSOR_MS = 300;
const uint16_t DIST_MIN_MM = 40;                    // Fuera de [40, 1300] mm la lectura es -1.
const uint16_t DIST_MAX_MM = 1300;
// Sentido de los ángulos: TODO confirmar. Se asume 0° = derecha del robot, 90° = frente, 180° = izquierda.

// ---------------- Protocolo y tiempos ----------------
const unsigned long BAUDIOS_HC06 = 9600;
const unsigned long SAFE_MODE_MS = 3000;        // Sin recibir nada del Servidor: frenar.
const unsigned long ESPERA_CONFIRMACION_MS = 1000;
const unsigned long ESPERA_ORDEN_MS = 3000;
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
bool sensorListo = false;               // false: no se detectó el VL53L1X; las lecturas son -1 sin esperar.
Servo servos[3];                        // Barrido, elevación y colector.

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

/** Detiene los dos motores (sin rueda libre: los cuatro IN en bajo). */
void detenerMotores() {
  digitalWrite(PIN_IN1, LOW); digitalWrite(PIN_IN2, LOW);
  digitalWrite(PIN_IN3, LOW); digitalWrite(PIN_IN4, LOW);
}

/** Fija el sentido de cada motor (true = adelante) y aplica la velocidad. */
void motores(bool izquierdoAdelante, bool derechoAdelante) {
  digitalWrite(PIN_IN1, izquierdoAdelante ? HIGH : LOW);
  digitalWrite(PIN_IN2, izquierdoAdelante ? LOW : HIGH);
  digitalWrite(PIN_IN3, derechoAdelante ? HIGH : LOW);
  digitalWrite(PIN_IN4, derechoAdelante ? LOW : HIGH);
}

/** Lleva un SG90 posicional (BARRIDO o ELEVACION) al ángulo indicado (0..180). */
void moverServo(uint8_t canal, int grados) {
  servos[canal].write(grados);
}

/** Gira o frena el colector. Para frenarlo se suelta el servo (sin pulsos), así queda quieto aunque su
 *  punto neutro no sea exactamente 1500 us. */
void colector(bool girando) {
  Servo &c = servos[CANAL_COLECTOR];
  if (girando) {
    if (!c.attached()) c.attach(PIN_SERVO_COLECTOR);
    c.writeMicroseconds(COLECTOR_GIRANDO_US);
  } else if (c.attached()) {
    c.detach();
  }
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
  largoLinea = 0;
}

/** Drena lo que llegó por Bluetooth, anota la hora de cada byte y controla el Safe Mode. */
void atenderServidor() {
  while (bt.available() > 0) {
    char c = (char) bt.read();
    ultimoContacto = millis();
#ifdef DEPURAR
    Serial.write(c); // Eco de todo lo que llega por Bluetooth.
#endif
    if (enSafeMode) {
      enSafeMode = false;
      DEPLN(F("[Robot] Sale del Safe Mode"));
    }
    if (c == '\r') continue;
    if (c == '\n') { procesarLinea(); continue; }
    if (largoLinea < LARGO_LINEA - 1) linea[largoLinea++] = c;
    else largoLinea = 0; // Línea demasiado larga: se descarta.
  }
  if (!enSafeMode && millis() - ultimoContacto > SAFE_MODE_MS) {
    enSafeMode = true;
    detenerTodo();
    DEPLN(F("[Robot] SAFE MODE: sin noticias del Servidor"));
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
  DEP(F("[Robot] -> CAPTURA|")); DEPLN(id);
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
  DEPLN(F("[Robot] -> TELEMETRIA enviada"));
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

/** Una medición con el ROI indicado (lado en SPADs: 4, 8 o 16). -1 si falla o no hay eco válido.
 *  limite = instante (millis) máximo para terminar este ángulo; resto = tiempo que aún necesitan los
 *  ROI que faltan en este ángulo. Reintenta una vez solo si queda tiempo en el presupuesto del ángulo. */
int medir(uint8_t lado, unsigned long limite, unsigned long resto) {
  if (!sensorListo) return SIN_ECO;
  sensor.setROISize(lado, lado);
  sensor.setROICenter(199);
  for (uint8_t intento = 0; intento < 2; intento++) {
    if (intento == 1 && millis() + LECTURA_MS + resto > limite) break;
    uint16_t mm = sensor.readSingle();
    if (sensor.timeoutOccurred()) continue;
    if (sensor.ranging_data.range_status != VL53L1X::RangeValid) continue;
    if (mm < DIST_MIN_MM || mm > DIST_MAX_MM) continue;
    return (int) mm;
  }
  return SIN_ECO; // El Servidor reemplaza -1 por el alcance máximo.
}

/** Barrido de 13 posiciones x 3 ROI. Devuelve false si se cortó por Safe Mode. */
bool barrer() {
  moverServo(CANAL_BARRIDO, 0);
  if (!esperarMs(ESPERA_INICIO_BARRIDO_MS)) return false;
  unsigned long inicio = millis();
  for (uint8_t s = 0; s < SECTORES; s++) {
    unsigned long limite = inicio + (unsigned long)(s + 1) * SLOT_MS;
    moverServo(CANAL_BARRIDO, s * 15);
    if (!esperarMs(ESPERA_SERVO_MS)) return false;
    lecturas[s * ROIS + 0] = medir(4, limite, 2UL * LECTURA_MS);
    lecturas[s * ROIS + 1] = medir(8, limite, 1UL * LECTURA_MS);
    lecturas[s * ROIS + 2] = medir(16, limite, 0);
    atenderServidor(); // Drena el buffer entre posiciones (LATIDO) y controla el Safe Mode.
    revisarCaptura();
    if (enSafeMode) return false;
  }
  DEP(F("[Robot] Barrido listo en ms: ")); DEPLN(millis() - inicio);
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
  pinMode(PIN_IN1, OUTPUT); pinMode(PIN_IN2, OUTPUT);
  pinMode(PIN_IN3, OUTPUT); pinMode(PIN_IN4, OUTPUT);
  pinMode(PIN_CAPTURA, INPUT_PULLUP); // Sin FC-51 conectado queda en alto (sin detección); el FC-51 empuja a bajo.
  detenerMotores();

  bt.begin(BAUDIOS_HC06);

  servos[CANAL_BARRIDO].attach(PIN_SERVO_BARRIDO, PULSO_SERVO_0_US, PULSO_SERVO_180_US);
  servos[CANAL_ELEVACION].attach(PIN_SERVO_ELEVACION, PULSO_SERVO_0_US, PULSO_SERVO_180_US);
  colector(false); // El colector queda sin pulsos hasta que el robot esté activo.
  moverServo(CANAL_ELEVACION, SENSOR_ABAJO_GRADOS);
  moverServo(CANAL_BARRIDO, 0);
  delay(1000); // Como en el barrido probado: los servos terminan de moverse antes de iniciar el sensor.

  Wire.begin();
  Wire.setClock(400000);
  sensor.setTimeout(TIMEOUT_SENSOR_MS);
  for (uint8_t intento = 0; intento < 3 && !sensorListo; intento++) {
    sensorListo = sensor.init();
    if (!sensorListo) delay(200);
  }
  if (sensorListo) {
    sensorListo = sensor.setDistanceMode(VL53L1X::Short)
               && sensor.setMeasurementTimingBudget(PRESUPUESTO_MEDICION_US);
  }
  if (!sensorListo) {
    DEPLN(F("[Robot] VL53L1X no detectado o sin configurar: todas las lecturas serán -1"));
  }

  ultimoContacto = millis(); // Hasta que hable el Servidor, a los 3 s entra en Safe Mode.
  DEPLN(F("[Robot] Orquita lista"));
}

void loop() {
  detenerMotores();
  atenderServidor();
  if (enSafeMode) { // Quieta hasta volver a recibir algo del Servidor.
    detenerTodo();
    esperarMs(50);
    return;
  }
  colector(COLECTOR_GIRA); // TODO confirmar: el colector gira siempre fuera del Safe Mode.

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
