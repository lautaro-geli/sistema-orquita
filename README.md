# Orquitas — Hito 3 "Liberen a Willy"

Sistema distribuido Cliente–Servidor por **Bluetooth (RFCOMM/SPP)**:

- **Servidor** (Java 17, Maven, en la PC Windows):
  - recibe los barridos del robot y los clasifica con una red neuronal Neuroph;
  - decide el movimiento y lo envía como orden;
  - anota las capturas en `estomago.txt` con un hilo Escritor por captura y un semáforo justo;
  - un Auditor independiente activa el Modo Escape.
- **Cliente** (sketch `arduino/Orquita/Orquita.ino`, Arduino UNO en el robot):
  - barre con el VL53L1X y detecta capturas con el FC-51;
  - envía por el HC-06 y ejecuta las órdenes;
  - frena solo (Safe Mode) si pierde al Servidor.

Fuente de verdad del diseño: `docs/diseno/` (clases v5, secuencias v5, DCU v3.6). Protocolo: [docs/PROTOCOLO.md](docs/PROTOCOLO.md). Decisiones por defecto a confirmar: [docs/DECISIONES_FASE2.md](docs/DECISIONES_FASE2.md).

## Dependencias

Maven las resuelve solas desde Maven Central (requiere internet la primera vez):

| Dependencia | Versión | Para qué |
|---|---|---|
| `com.github.neuroph:neuroph-core` | 2.98 | Red neuronal (MLP). |
| `com.fazecast:jSerialComm` | 2.11.4 | Abrir el puerto COM del Bluetooth con tiempos de espera de lectura. |
| `junit:junit` (solo pruebas) | 4.13.2 | Pruebas automáticas. |

Arduino (Gestor de librerías del IDE): **VL53L1X de Pololu** (≥ 1.3.0), **Adafruit PWM Servo Driver Library** (instala también Adafruit BusIO). `SoftwareSerial` y `Wire` vienen con el IDE.

## Construir y probar

Abrir la carpeta (la que tiene `pom.xml`) como proyecto Maven en NetBeans con JDK 17 o posterior; *Clean and Build* compila y corre las pruebas. Desde PowerShell:

```powershell
./preparar.ps1          # mvn clean package + copia de dependencias a target/dependency
mvn clean verify        # solo las pruebas (no necesitan hardware)
```

Las pruebas (JUnit 4) simulan el puerto con streams en memoria. Cubren:

- el protocolo y las respuestas;
- latido, silencio y falla de escritura;
- Escritores concurrentes con el Auditor;
- la navegación por modo y la red neuronal;
- un ciclo completo con una Orquita simulada: telemetría, captura, ESCAPE con umbral 1 y LIBERADA.

## Emparejar el HC-06 en Windows

1. Encender el robot (el HC-06 parpadea).
2. Windows → Configuración → Bluetooth y dispositivos → Agregar dispositivo → Bluetooth → elegir el HC-06. El PIN suele ser `1234` (o `0000`).
3. Ver el puerto: Administrador de dispositivos → **Puertos (COM y LPT)** → "Vínculo serie estándar a través de Bluetooth (COMx)". Hay dos; se usa el **saliente**: Configuración de Bluetooth → Más opciones de Bluetooth → pestaña *Puertos COM*, la fila "Saliente" con el nombre del HC-06.

El Servidor no empareja ni crea el puerto: solo abre el COM ya creado.

## Ejecutar

```powershell
./ejecutar.ps1 servidor COM5 --umbral=1
# opciones: --estomago=estomago.txt  --modelo=modelos/entorno.nnet
```

**Orden de encendido:** primero el robot, después el Servidor.

- Al abrir el COM, Windows conecta con el HC-06.
- El Servidor espera hasta 8 s el primer dato del robot.
- Cada arranque **vacía `estomago.txt`**: la misión empieza de cero.
- El banner muestra el puerto, el modelo y el umbral activo.

`--umbral=1` es la prueba de campo del Hito 3; el reglamento de la competencia usa 3.

La consola muestra:

- **Estados:** `[Estado] ANTERIOR -> NUEVO (motivo)`, con BUSQUEDA, APROXIMACION, CAPTURA, ESCAPE, FINALIZACION y ERROR_SEGURO.
- **Capturas:** `[Captura]` y `[Auditor] n/umbral`.
- **Órdenes:** `[Orden]`.

### Procedimiento tras un Fail-Safe

Si el robot calla más de 8 s o falla la escritura al puerto, el Servidor informa `ERROR_SEGURO`, cierra el enlace y termina con código ≠ 0. El robot, al no recibir nada durante 3 s, frena solo (Safe Mode). Para reanudar:

1. Verificar el robot (alimentación, HC-06 parpadeando).
2. Si Windows perdió el vínculo, quitar el dispositivo y volver a emparejarlo.
3. Reiniciar el Servidor con el mismo comando. Reiniciar **vacía el estómago**.

## Datos reales y red neuronal

El modelo incluido (`modelos/entorno.nnet`) es **provisional y sintético**: no es evidencia de campo. Ver [modelos/README.md](modelos/README.md).

1. **Recolectar** barridos reales, con el robot quieto en cada escena (pista, Estrecho de Salida y campo abierto de afuera):
   ```powershell
   ./ejecutar.ps1 recolectar COM5 LIBRE 40
   ./ejecutar.ps1 recolectar COM5 OBJETO 40
   ./ejecutar.ps1 recolectar COM5 LOBITO 40
   ./ejecutar.ps1 recolectar COM5 SALIDA 40
   ```
   El recolector responde `ORDEN|DETENER` (el robot no se mueve) y `LATIDO` (no entra en Safe Mode). Cada barrido se agrega a `datos/barridos.csv` como `ETIQUETA,v0,...,v38`, con el `-1` crudo. Conviene variar posición, ángulo y distancia dentro de cada etiqueta.
2. **Entrenar:** `./ejecutar.ps1 entrenar` (opcional: `[datos/barridos.csv] [modelos/entorno.nnet] [ocultas]`).
   - Mezcla con semilla fija y separa 80/20.
   - Normaliza igual que el Servidor (`RedNeuronal.normalizar`) y entrena un MLP 39-16-4.
   - Imprime la exactitud y la matriz de confusión, guarda `modelos/entorno.nnet` y el reporte `docs/evidencias/entrenamiento_AAAAMMDD.md` (evidencia de la consigna).
3. **Modelo provisional** (sin robot): `./ejecutar.ps1 provisional`.

## Estructura

| Carpeta | Contenido |
|---|---|
| `src/main/java/orquitas/servidor` | Servidor según el diagrama v5: `ServidorOrquita`, `comunicacion` (`EnlaceOrquita`, `EstadoEnlace`), `navegacion` (`Navegacion`, `RedNeuronal`, enums), `persistencia` (`Estomago`, `Escritor`, `Auditor`). |
| `src/main/java/orquitas/herramientas` | `RecolectorBarridos`, `EntrenamientoReal` (no forman parte de los diagramas). |
| `src/main/java/orquitas/simulacion` | `EntrenamientoDemostracion` (modelo provisional sintético). |
| `arduino/Orquita/Orquita.ino` | Sketch del robot. |
| `entrega-java/` | Lo que se sube a ClassRoom: los `.java` planos del Servidor y de las herramientas, y `Orquita.ino`. |
| `docs/historico/` | Material de los hitos anteriores (no usar como versión actual). |
