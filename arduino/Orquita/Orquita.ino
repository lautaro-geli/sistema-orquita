/*
 * Orquita - Cliente del Hito 3 ("Liberen a Willy"), Proyecto Orquitas.
 *
 * Ciclo del robot: parar - medir - enviar - mover (un paso acotado).
 *   1. Motores detenidos. Si hay una captura sin confirmar: CAPTURA|id y espera CONFIRMACION (~1 s).
 *   2. Barrido: 13 posiciones (0°, 15°, ..., 180°) x 3 ROI (4x4, 8x8, 16x16) = 39 distancias en mm
 *      (-1 = sin eco). Entre posiciones atiende al Servidor (Safe Mode) y revisa el sensor de captura.
 *   3. Envía TELEMETRIA|v0;...;v38 y espera la ORDEN (~2 s). Sin orden o con RECHAZO: no se mueve.
 *   4. Ejecuta la orden durante los ms que indica el Servidor (tope PASO_MAXIMO_MS) y vuelve a detenerse.
 *
 * Safe Mode (Fail-Safe): si pasan más de 3000 ms sin recibir NADA del Servidor (ni LATIDO), frena de
 * inmediato los motores y espera; sale cuando vuelve a recibir algo.
 *
 * Protocolo v3 (una línea por mensaje, terminada en \n):
 *   Orquita -> Servidor: TELEMETRIA|v0;...;v38    CAPTURA|id
 *   Servidor -> Orquita: ORDEN|movimiento|ms   CONFIRMACION   RECHAZO   LATIDO (cada 1 s)
 *   (ORDEN|movimiento sin ms, del protocolo v2, se sigue aceptando con el paso fijo.)
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
const uint8_t PIN_SERVO_BARRIDO = 4;    // Servo del sensor: gira el sensor de 0° a 180° (cableado real en D4).
const uint8_t PIN_SERVO_ELEVACION = 12; // Servo de elevación: levanta/baja el sensor (D3 no funcionó; cableado en D12).
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

// ---------------- Constantes a calibrar ----------------
const int PULSO_SERVO_0_US = 544;     // Pulso en µs para 0°. Calibrar con el SG90 real.
const int PULSO_SERVO_180_US = 2400;  // Pulso en µs para 180°.
const int SENSOR_ABAJO_GRADOS = 0;    // TODO confirmar: posición del sensor durante el barrido.
const int SENSOR_ARRIBA_GRADOS = 180; // Sensor levantado (deja pasar la canica). Solo se usa si SUBIR_SENSOR_AL_MOVER.
const bool SUBIR_SENSOR_AL_MOVER = false; // true: sube en todos los movimientos (lento). false: solo al ir a atrapar.
const unsigned long PASO_AVANZAR_MS = 400; // Paso de AVANZAR/RETROCEDER si la ORDEN no trae ms.
const unsigned long PASO_GIRO_MS = 300;    // Paso de giro si la ORDEN no trae ms.
const unsigned long PASO_MAXIMO_MS = 1500; // Tope de seguridad de la duración que pide el Servidor.
const unsigned long ESPERA_SERVO_MS = 70;  // Asentamiento del servo en cada posición (config D de la prueba de ruido: 60 ms).
const unsigned long ESPERA_INICIO_BARRIDO_MS = 700; // Si el barrido arranca lejos (más de 15°): el servo tiene que llegar antes de soltarlo.
const unsigned long ESPERA_ELEVACION_MS = 700;      // Subir o bajar el sensor 180° (con 250 ms se soltaba a mitad de camino).
// Parámetros del barrido que funcionó bien (modo Short, medición más larga = más estable).
// Configuración D de la prueba de ruido (barrido de ~2 s, ruido de 1-2 mm). Orden de medición 16 -> 8 -> 4:
// la primera medición después de mover el servo sale sesgada, y el 16x16 es el que menos lo sufre.
const uint32_t PRESUPUESTO_MEDICION_US = 75000;     // Solo para el arranque del sensor.
const uint32_t PRESUPUESTO_4_US = 33000;
const uint32_t PRESUPUESTO_8_US = 25000;
const uint32_t PRESUPUESTO_16_US = 20000;
const uint8_t CENTRO_ROI_CHICO = 197;  // 4x4 y 8x8 un poco hacia abajo: ven mejor las canicas (prueba ruido_canicas).
const uint8_t CENTRO_ROI_16 = 199;     // 16x16: la matriz entera.
// Subir el sensor solo para atrapar: en AVANZAR, si el último barrido vio algo cerca al frente (60° a 120°).
const int CERCA_FRENTE_MM = 350;
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
Servo servos[2];                        // Barrido y elevación.

int lecturas[VALORES];                  // 78 bytes: un barrido completo.
char linea[LARGO_LINEA];                // Respuesta del Servidor en armado.
uint8_t largoLinea = 0;
unsigned long ultimoContacto = 0;       // millis() del último byte recibido del Servidor.
bool enSafeMode = false;
int8_t ordenRecibida = SIN_ORDEN;
unsigned long duracionRecibida = 0;     // ms de la ORDEN; 0 = no vino (se usa el paso fijo).
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
  // Los motores están cableados con la polaridad invertida: acá se corrige para que "adelante" sea adelante.
  digitalWrite(PIN_IN1, izquierdoAdelante ? LOW : HIGH);
  digitalWrite(PIN_IN2, izquierdoAdelante ? HIGH : LOW);
  digitalWrite(PIN_IN3, derechoAdelante ? LOW : HIGH);
  digitalWrite(PIN_IN4, derechoAdelante ? HIGH : LOW);
}

/** Lleva un SG90 posicional (BARRIDO o ELEVACION) al ángulo indicado (0..180). */
// Los servos se sueltan (detach) cuando quedan quietos: SoftwareSerial frena las interrupciones ~1 ms por byte
// (al mandar y al recibir por el HC-06) y deforma los pulsos del servo, que entonces da tirones. Suelto, el SG90
// se sostiene solo por el rozamiento de los engranajes y no recibe pulsos que se puedan deformar.
const uint8_t PIN_CANAL[2] = {PIN_SERVO_BARRIDO, PIN_SERVO_ELEVACION};
int anguloServo[2] = {-1, -1};

/** Devuelve true si el servo tuvo que moverse (entonces hay que esperar que llegue antes de soltarlo). */
bool moverServo(uint8_t canal, int grados) {
  if (grados == anguloServo[canal]) return false; // Ya está ahí: no se vuelve a enganchar (evita un tirón).
  servos[canal].write(grados);              // Primero el ángulo, después attach: no pasa por 90°.
  if (!servos[canal].attached()) servos[canal].attach(PIN_CANAL[canal], PULSO_SERVO_0_US, PULSO_SERVO_180_US);
  anguloServo[canal] = grados;
  return true;
}

/** Suelta los dos servos (dejan de recibir pulsos). Llamar cuando ya llegaron a su posición. */
void soltarServos() {
  for (uint8_t c = 0; c < 2; c++) if (servos[c].attached()) servos[c].detach();
}

/** Frena todo (los motores). Se usa al entrar en Safe Mode. */
void detenerTodo() {
  detenerMotores();
}

// ======================================================================
// Comunicación con el Servidor y Safe Mode
// ======================================================================

/** Lee de 1 a 5 dígitos decimales; cualquier otra cosa devuelve 0 (se usa el paso fijo). */
unsigned long leerMilisegundos(const char *texto) {
  unsigned long valor = 0;
  uint8_t digitos = 0;
  for (; *texto != '\0'; texto++) {
    if (*texto < '0' || *texto > '9' || digitos == 5) return 0;
    valor = valor * 10 + (unsigned long) (*texto - '0');
    digitos++;
  }
  return digitos == 0 ? 0 : valor;
}

/** Interpreta una línea completa del Servidor. Busca el mensaje dentro de la línea por si llegó pegado
 *  a un resto de otro mensaje (SoftwareSerial pierde bytes mientras transmite). */
void procesarLinea() {
  linea[largoLinea] = '\0';
  char *orden = strstr(linea, "ORDEN|");
  if (orden != NULL) {
    char *mov = orden + 6;
    duracionRecibida = 0;
    char *separador = strchr(mov, '|');
    if (separador != NULL) { // ORDEN|movimiento|ms
      *separador = '\0';
      duracionRecibida = leerMilisegundos(separador + 1);
    }
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

uint32_t presupuestoActual = 0;

/** Una medición con el ROI indicado (lado en SPADs: 4, 8 o 16). -1 si falla o no hay eco válido. */
int medir(uint8_t lado, uint8_t centro, uint32_t presupuestoUs) {
  if (!sensorListo) return SIN_ECO;
  if (presupuestoUs != presupuestoActual) { sensor.setMeasurementTimingBudget(presupuestoUs); presupuestoActual = presupuestoUs; }
  sensor.setROISize(lado, lado);
  sensor.setROICenter(centro);
  uint16_t mm = sensor.readSingle();
  if (Wire.getWireTimeoutFlag()) { Wire.clearWireTimeoutFlag(); DEPLN(F("[Robot] I2C trabado: lectura descartada")); return SIN_ECO; }
  if (sensor.timeoutOccurred()) return SIN_ECO;
  if (sensor.ranging_data.range_status != VL53L1X::RangeValid) return SIN_ECO;
  if (mm < DIST_MIN_MM || mm > DIST_MAX_MM) return SIN_ECO;
  return (int) mm; // El Servidor reemplaza -1 por el alcance máximo.
}

bool barridoDeIda = true; // Alterna 0->180 y 180->0: no pierde tiempo volviendo a 0 en cada ciclo.

/** Barrido de 13 posiciones x 3 ROI. Las lecturas quedan siempre ordenadas por ángulo (0° a 180°).
 *  Devuelve false si se cortó por Safe Mode. */
bool barrer() {
  unsigned long inicio = millis();
  DEPLN(F("[Robot] Barriendo..."));
  for (uint8_t k = 0; k < SECTORES; k++) {
    uint8_t s = barridoDeIda ? k : SECTORES - 1 - k;
    int antes = anguloServo[CANAL_BARRIDO];
    moverServo(CANAL_BARRIDO, s * 15);
    bool lejos = antes < 0 || abs(antes - s * 15) > 15; // Arranque o barrido cortado: viene de lejos.
    if (!esperarMs(lejos ? ESPERA_INICIO_BARRIDO_MS : ESPERA_SERVO_MS)) return false;
    soltarServos(); // Quietos mientras mide.
    lecturas[s * ROIS + 2] = medir(16, CENTRO_ROI_16, PRESUPUESTO_16_US);
    lecturas[s * ROIS + 1] = medir(8, CENTRO_ROI_CHICO, PRESUPUESTO_8_US);
    lecturas[s * ROIS + 0] = medir(4, CENTRO_ROI_CHICO, PRESUPUESTO_4_US);
    atenderServidor(); // Drena el buffer entre posiciones (LATIDO) y controla el Safe Mode.
    revisarCaptura();
    if (enSafeMode) return false;
  }
  barridoDeIda = !barridoDeIda;
  DEP(F("[Robot] Barrido listo en ms: ")); DEPLN(millis() - inicio);
  return true;
}

/** true si el último barrido vio algo a menos de CERCA_FRENTE_MM entre 60° y 120° (4x4 u 8x8). */
bool algoCercaAlFrente() {
  for (uint8_t s = 4; s <= 8; s++)
    for (uint8_t r = 0; r < 2; r++) {
      int v = lecturas[s * ROIS + r];
      if (v != SIN_ECO && v < CERCA_FRENTE_MM) return true;
    }
  return false;
}

/** Ejecuta la orden durante duracionRecibida ms (o el paso fijo) y vuelve a detenerse. El sensor sube durante el paso (TODO confirmar). */
void ejecutarOrden(int8_t orden) {
  if (orden == DETENER || orden == SIN_ORDEN) return;
  bool subir = SUBIR_SENSOR_AL_MOVER || (orden == AVANZAR && algoCercaAlFrente());
  if (subir && moverServo(CANAL_ELEVACION, SENSOR_ARRIBA_GRADOS)) {
    if (!esperarMs(ESPERA_ELEVACION_MS)) return;
    soltarServos();
  }
  unsigned long paso = PASO_AVANZAR_MS;
  switch (orden) {
    case AVANZAR:    motores(true, true); break;
    case RETROCEDER: motores(false, false); break;
    case GIRAR_IZQ:  motores(false, true); paso = PASO_GIRO_MS; break; // TODO confirmar sentido.
    case GIRAR_DER:  motores(true, false); paso = PASO_GIRO_MS; break;
  }
  if (duracionRecibida > 0) paso = duracionRecibida < PASO_MAXIMO_MS ? duracionRecibida : PASO_MAXIMO_MS;
  DEP(F("[Robot] Paso de ms: ")); DEPLN(paso);
  DEPLN(F("[Robot] Motores en marcha"));
  esperarMs(paso); // Si entra en Safe Mode, atenderServidor() ya frenó.
  detenerMotores();
  if (moverServo(CANAL_ELEVACION, SENSOR_ABAJO_GRADOS)) { // Vuelve abajo (solo si había subido).
    esperarMs(ESPERA_ELEVACION_MS);
    soltarServos();
  }
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

  moverServo(CANAL_ELEVACION, SENSOR_ABAJO_GRADOS);
  moverServo(CANAL_BARRIDO, 0);
  delay(1000); // Como en el barrido probado: los servos terminan de moverse antes de iniciar el sensor.
  soltarServos();

  Wire.begin();
  Wire.setClock(400000);
  Wire.setWireTimeout(25000, true); // Si el bus I2C se traba (ruido de servos o motores), no se cuelga el Arduino.
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

  if (capturasGeneradas > capturasConfirmadas) {
    enviarCapturaPendiente();
    if (enSafeMode) return;
  }

  if (moverServo(CANAL_ELEVACION, SENSOR_ABAJO_GRADOS)) { // Solo si no estaba abajo.
    if (!esperarMs(ESPERA_ELEVACION_MS)) return;
    soltarServos();
  }
  if (!barrer()) return;

  ordenRecibida = SIN_ORDEN;
  duracionRecibida = 0;
  confirmacionRecibida = false;
  rechazoRecibido = false;
  enviarTelemetria();
  int8_t orden = esperarOrden();
  DEP(F("[Robot] Orden recibida (0=AVANZAR 1=RETROCEDER 2=IZQ 3=DER 4=DETENER -1=ninguna): ")); DEPLN(orden);
  if (orden == SIN_ORDEN) return; // RECHAZO o sin respuesta: queda detenida y repite el ciclo.
  ejecutarOrden(orden);
}
