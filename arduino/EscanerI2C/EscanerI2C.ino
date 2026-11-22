/*
 * EscanerI2C - dice si el Arduino "ve" al VL53L1X (direccion 0x29) y en que estado estan SDA y SCL.
 * Monitor Serie a 115200. Repite cada 2 s: se pueden mover cables y ver el cambio en vivo.
 */
#include <Wire.h>

void setup() {
  Serial.begin(115200);
  Serial.println(F("== Escaner I2C (SDA = A4, SCL = A5) =="));
}

void loop() {
  // Estado de las lineas sin el Wire: con el bus libre, las dos tienen que estar en 1.
  Wire.end();
  pinMode(SDA, INPUT_PULLUP); pinMode(SCL, INPUT_PULLUP);
  delay(5);
  Serial.print(F("SDA (A4) = ")); Serial.print(digitalRead(SDA));
  Serial.print(F("   SCL (A5) = ")); Serial.print(digitalRead(SCL));
  if (digitalRead(SDA) == 0 || digitalRead(SCL) == 0) Serial.print(F("   <- una linea en 0: trabada o en corto a GND"));
  Serial.println();
  // Destrabe y busqueda.
  pinMode(SCL, OUTPUT);
  for (uint8_t i = 0; i < 9; i++) { digitalWrite(SCL, LOW); delayMicroseconds(10); digitalWrite(SCL, HIGH); delayMicroseconds(10); }
  pinMode(SCL, INPUT_PULLUP);
  Wire.begin();
  Wire.setClock(100000);
  Wire.setWireTimeout(25000, true);
  uint8_t encontrados = 0;
  for (uint8_t dir = 1; dir < 127; dir++) {
    Wire.beginTransmission(dir);
    uint8_t r = Wire.endTransmission();
    if (r == 0) {
      Serial.print(F("  Dispositivo en 0x")); Serial.print(dir, HEX);
      Serial.println(dir == 0x29 ? F("  <- VL53L1X OK") : F(""));
      encontrados++;
    } else if (r == 5) {
      Serial.println(F("  Timeout del bus: SDA o SCL trabadas")); break;
    }
  }
  if (encontrados == 0) Serial.println(F("  Ningun dispositivo: el sensor no responde (VIN, GND, SDA, SCL)."));
  delay(2000);
}
