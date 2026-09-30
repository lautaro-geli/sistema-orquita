# Orquitas — La Linareada corregida

Revisión del 30/09/2026. Recepción TCP de capturas, escritor independiente, auditor del archivo y transición interna a ESCAPE. Proyecto Maven para NetBeans y Java 17 o posterior.

## Alcance

CU-06 sólo capturas; CU-09 Actualizar estómago; CU-10 Leer estómago; CU-11 Activar modo escape. Esta prueba no implementa telemetría, Neuroph, Bluetooth, órdenes de motores ni Safe Mode del robot. El Hito 2 general necesita esas integraciones adicionales; no presentar esta carpeta como el proyecto completo.

## Ejecutar en NetBeans

1. Abrir esta carpeta como proyecto Maven.
2. Usar JDK 17 o posterior y ejecutar Clean and Build.
3. Ejecutar `orquitas.servidor.ServidorOrquita` como clase principal. Escucha en el puerto 5000 y usa estomago.txt en el directorio de trabajo.
4. Sin cerrar el servidor, ejecutar `orquitas.cliente.SimuladorIngestaTCP` con Run File. Envía tres capturas.
5. Verificar tres líneas en estomago.txt y MODO ESCAPE ACTIVADO en la salida del servidor.

No se borra el archivo al reiniciar. Si ya contiene tres capturas, el auditor activa escape sin nuevos mensajes. Para una prueba vacía, pasar otra ruta al servidor; no editar registros durante la misión.

## Ejecutar en terminal

Desde esta carpeta:

```powershell
mvn clean package
java -cp target/classes orquitas.servidor.ServidorOrquita 5000 estomago-demo-nuevo.txt
```

En otra terminal, desde la misma carpeta:

```powershell
java -cp target/classes orquitas.cliente.SimuladorIngestaTCP localhost 5000 3 1000
```

Servidor: `[puerto] [rutaArchivo]`. Simulador: `[host] [puerto] [cantidadCapturas] [delayMs]`. El simulador usa ids 001, 002, 003; reenviarlos al mismo archivo no agrega capturas. El anterior argumento --reset se reemplazó por una ruta de prueba explícita para conservar la evidencia persistida.

Las dependencias de ejecución son sólo del JDK. JUnit 4.13.2 se usa únicamente al probar. Maven necesita descargar sus dependencias la primera vez; en esta PC se verificó con la caché local y Maven de NetBeans.

## Recorrido para defender el código

1. ServidorOrquita.main crea una instancia de ArchivoEstomago y la comparte con ambos hilos. Ejecuta start() en escritor y lector; Java invoca sus run().
2. ReceptorIngestaTCP delimita los mensajes; Recopilador.procesarRecopilacion valida y llama a ReceptorCapturas.notificarCaptura.
3. ReceptorCapturas crea Captura y llama a ActualizadorEstomago.encolarCaptura.
4. Se anuncia una escritura pendiente, se agrega a la cola y notifyAll() despierta al escritor.
5. ActualizadorEstomago.run llama a ArchivoEstomago.escribirRegistro. Semaphore(1, true) protege el archivo; finally libera el permiso.
6. LectorEstomago.run repite el LOOP; espera las escrituras pendientes y ejecuta leerRegistros en CRITICAL. Cuenta únicamente registros válidos y únicos del archivo.
7. Con tres o más, envía EventoModoEscape a ActivadorModoEscape.notificarCapturasCompletas(evento), que cambia Navegador a ESCAPE.

La cola y escriturasPendientes coordinan trabajo; no son un contador biológico. Una lectura iniciada termina; las nuevas esperan a que se procesen las capturas pendientes. No se usa Thread.suspend().

## Protocolo y fallos

- TCP: CAPTURA|id|fechaISO terminado con LF; se admite CRLF. UTF-8; máximo 256 caracteres antes de LF.
- Id: 1 a 64 caracteres alfanuméricos ASCII, guion o guion bajo. Fecha: LocalDateTime.parse.
- Campos inválidos, tipo desconocido o trama excesiva: descartar. EOF sin LF: descartar el fragmento pendiente.
- Archivo: id;fecha en UTF-8. No se duplica un id persistido; el lector descarta líneas corruptas y repetidas.
- Escritura fallida: registrar error y liberar recursos; no confirmar. No hay ACK TCP ni reintento automático de disco.
- Lectura fallida: no usar resultados parciales; reintentar en el siguiente ciclo.
- Escape: EventoModoEscape(MODO_ESCAPE_ACTIVADO, cantidad, timestamp), interno en la JVM; no se transmite al robot por red.
- Cierre normal: detener recepción, drenar escritor, detener auditor. Un cierre abrupto puede perder avisos aún en cola.
- >= 3 evita omitir la transición cuando se acumulan capturas entre auditorías. El hito no impide físicamente una cuarta presa.
- No iniciar varios servidores sobre el mismo archivo: la exclusión es para una instancia compartida en una JVM.

## Pruebas y documentos

Ejecutar `mvn test`. LinareadaTest contiene 13 pruebas de hilos independientes, exclusión, prioridad, concurrencia, duplicados, errores, reinicio y TCP real. Resultados en target/surefire-reports.

La carpeta vecina entrega-linareada-corregida contiene DCU, PNG blancos, editables draw.io, Java sin árbol de carpetas y evidencias. Consultar REVISION_Y_JUSTIFICACION.md para saber por qué cambió cada cosa.

Los archivos exclusivos de simulación empiezan con // Archivo de simulacion: SimuladorIngestaTCP, ReceptorIngestaTCP, ServidorOrquita y LinareadaTest. Las clases reutilizables no llevan esa marca.
