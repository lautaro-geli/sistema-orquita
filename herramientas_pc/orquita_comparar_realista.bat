@echo off
REM Compara en la simulacion realista E2, el modo barrido y las redes nuevas de la noche (900 bahias).
powershell -NoExit -NoProfile -ExecutionPolicy Bypass -File "%~dp0orquita_comparar_realista.ps1"
