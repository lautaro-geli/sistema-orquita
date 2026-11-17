@echo off
REM Neuroevolucion (refuerzo) de la red de movimiento desde E2 y desde E7b; 2 semillas cada una, unas 2 horas.
powershell -NoExit -NoProfile -ExecutionPolicy Bypass -File "%~dp0orquita_neuroevolucion.ps1"
