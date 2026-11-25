@echo off
REM Noche 24-25/11: reentrena percepcion y movimiento con la simulacion realista (N: maestro de siempre; B: maestro barrido).
REM Dejar la ventana abierta y la PC prendida (sin suspender). Resultados en Documents\orquita-corridas\noche_realista_<fecha>.
powershell -NoExit -NoProfile -ExecutionPolicy Bypass -File "%~dp0orquita_noche_realista.ps1"
