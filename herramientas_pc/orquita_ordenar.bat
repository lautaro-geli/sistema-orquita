@echo off
REM Ordena Documentos: archiva los scripts y datos viejos del proyecto Orquita (no borra nada).
cd /d "%~dp0"
set ARCH=orquita-archivo
if not exist "%ARCH%\scripts" mkdir "%ARCH%\scripts"
if not exist "%ARCH%\datos" mkdir "%ARCH%\datos"
if not exist "orquita-paquete" mkdir "orquita-paquete"
for %%f in (orquita_corridas_abc_v2 orquita_corridas_abc_v3 orquita_e4 orquita_entrenar orquita_entrenar_v2 orquita_entrenar_v3 orquita_experimentos_1 orquita_experimentos_2 orquita_experimentos_3 orquita_experimentos_4 orquita_experimentos_5 orquita_neuroevolucion orquita_neuroevolucion_40min orquita_neuroevolucion2) do (
  if exist "%%f.bat" move /y "%%f.bat" "%ARCH%\scripts\" >nul
  if exist "%%f.ps1" move /y "%%f.ps1" "%ARCH%\scripts\" >nul
)
for %%f in (orquita_entrenar_log.txt ruido.txt ruido_canicas.txt Ruido_roi.txt) do (
  if exist "%%f" move /y "%%f" "%ARCH%\datos\" >nul
)
if exist servidor_portable.bat move /y servidor_portable.bat "orquita-paquete\" >nul
echo.
echo Listo. Quedan a mano en Documentos:
echo   orquita_prueba_real.bat    ^<- probar la Orquita en esta PC
echo   orquita_puertos_bt.bat     ^<- ver cual COM es el saliente del HC-06
echo   orquita_armar_paquete.bat  ^<- armar el paquete para otra PC
echo Scripts y datos viejos: %ARCH%\scripts y %ARCH%\datos
echo servidor.bat del paquete:  orquita-paquete\servidor_portable.bat
pause
(goto) 2>nul & move /y "%~f0" "%ARCH%\scripts\" >nul
