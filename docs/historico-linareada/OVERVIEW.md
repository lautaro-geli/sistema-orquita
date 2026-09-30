# Orquitas corregido - LEER PRIMERO

Este paquete contiene la version corregida de La Linareada: proyecto Java para NetBeans, documentacion, diagramas y pruebas. Descomprimir el ZIP completo antes de abrir el proyecto.

## Que contiene

- proyecto-orquitas-linareada-corregido/: proyecto Maven, codigo fuente, pruebas y README tecnico.
- entrega-linareada-corregida/: DCU en Word, diagramas PNG con fondo blanco, editables draw.io, explicacion de los cambios y evidencias.
- entrega-linareada-corregida/java/: los 14 archivos Java de ejecucion sin arbol de carpetas, preparados para la entrega solicitada por la catedra. Para trabajar en NetBeans, usar el proyecto Maven.

## Abrir y ejecutar en NetBeans

1. Instalar o seleccionar JDK 17 o posterior.
2. Elegir Archivo > Abrir proyecto y seleccionar proyecto-orquitas-linareada-corregido (la carpeta que contiene pom.xml).
3. Ejecutar Clean and Build. Maven necesita conexion a Internet la primera vez para descargar sus herramientas y JUnit.
4. Ejecutar orquitas.servidor.ServidorOrquita. Por defecto escucha en el puerto 5000.
5. Sin detener el servidor, abrir SimuladorIngestaTCP.java y ejecutar Run File (Shift+F6).
6. El simulador envia tres capturas. Deben aparecer tres registros en estomago.txt y MODO ESCAPE ACTIVADO en la consola del servidor.

## Alternativa por terminal

Desde la carpeta del proyecto, con Maven y Java disponibles:

```powershell
mvn clean package
java -cp target/classes orquitas.servidor.ServidorOrquita 5000 estomago-prueba.txt
```

En otra terminal, desde esa misma carpeta:

```powershell
java -cp target/classes orquitas.cliente.SimuladorIngestaTCP localhost 5000 3 1000
```

Para correr las pruebas:

```powershell
mvn test
```

El servidor conserva las capturas al reiniciar. Para otra prueba desde cero, elegir un nombre de archivo nuevo. Reenviar los mismos identificadores no duplica capturas. No iniciar dos servidores sobre el mismo archivo.

## Que se corrigio

- Escritor permanente ActualizadorEstomago y auditor LectorEstomago, ambos con run() propio.
- Semaforo compartido y espera de nuevas lecturas mientras existen escrituras pendientes.
- Coincidencia entre DCU, secuencias, clases y nombres del codigo.
- Actor Orquita en las secuencias del servidor; LOOP y CRITICAL en la lectura.
- Recopilador real y nombres en espanol.
- Marca // Archivo de simulacion en los archivos exclusivos de simulacion.
- Manejo de errores de archivo, tramas invalidas y retransmisiones duplicadas.

La justificacion detallada esta en entrega-linareada-corregida/REVISION_Y_JUSTIFICACION.md. Para editar los diagramas, abrir diagramas/Orquitas_Linareada.drawio con draw.io / diagrams.net. El DOCX se puede importar en Google Docs.

## Verificacion y alcance

Compilacion limpia para Java 17 y 13 pruebas aprobadas. Se ejecuto servidor + simulador, se persistieron tres capturas, se activo ESCAPE y se comprobo el reinicio sin nuevos mensajes.

Este paquete corrige La Linareada (capturas, persistencia, auditoria y cambio interno de modo). No es el Hito 2 general completo: faltan Neuroph, protocolo completo, Bluetooth y navegacion fisica. El evento de escape es interno entre objetos del servidor.

Revision: 30/09/2026. No se incluyen versiones anteriores, archivos temporales de construccion ni registros de capturas de trabajo. Las capturas dentro de evidencias son solamente el resultado de una prueba.
