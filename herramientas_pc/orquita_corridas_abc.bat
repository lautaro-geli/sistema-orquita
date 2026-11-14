@echo off
REM Corridas A/B/C (bloques 2, 5 y 6) en paralelo, dejando libre el nucleo 0 para Windows.
powershell -NoExit -NoProfile -ExecutionPolicy Bypass -File "%~dp0orquita_corridas_abc.ps1"
