@echo off
setlocal
REM Servidor Orquita portable: necesita Java 17 o mas nuevo (java en el PATH).
REM Todo lo que muestra queda en consolas\consola_<fecha>.txt
cd /d "%~dp0"
java -version >nul 2>&1
if errorlevel 1 (
  echo No encuentro Java. Instalar Java 17 o mas nuevo ^(por ejemplo Temurin de adoptium.net^) y volver a abrir.
  pause
  exit /b 1
)
echo.
echo Navegacion:
echo   1 = redes E2 ^(percepcion + movimiento^)
echo   2 = reglas ^(plan B^)
set /p MODO=Elegi 1 o 2: 
echo.
echo Puertos COM de esta PC:
powershell -NoProfile -Command "[System.IO.Ports.SerialPort]::GetPortNames() | Sort-Object"
echo ^(El del HC-06 es el "saliente" en Configuracion ^> Bluetooth ^> Mas opciones de Bluetooth ^> Puertos COM.^)
set /p PUERTO=Puerto del HC-06 ^(por ejemplo COM5^): 
if not exist consolas mkdir consolas
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmm"') do set FECHA=%%i
set CP=%~dp0classes;%~dp0lib\*
echo.
echo == Arrancando el Servidor ^(%PUERTO%, umbral 3^). Prender la Orquita. Cortar con Ctrl+C.
if "%MODO%"=="2" (
  cd reglas
  powershell -NoProfile -Command "& java -cp '%CP%' orquitas.servidor.ServidorOrquita %PUERTO% --umbral=3 --reglas 2>&1 | ForEach-Object { \"$_\" } | Tee-Object -FilePath '..\consolas\consola_%FECHA%_reglas.txt'"
) else (
  powershell -NoProfile -Command "& java -cp '%CP%' orquitas.servidor.ServidorOrquita %PUERTO% --umbral=3 2>&1 | ForEach-Object { \"$_\" } | Tee-Object -FilePath 'consolas\consola_%FECHA%_redes.txt'"
)
echo.
echo == Fin. La consola quedo en la carpeta consolas.
pause
