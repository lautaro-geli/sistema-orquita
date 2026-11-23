@echo off
REM Analisis de la prueba de deteccion con notas por sector: que confunde la red y prueba del filtro de forma.
powershell -NoExit -NoProfile -ExecutionPolicy Bypass -File "%~dp0orquita_analisis_deteccion.ps1"
