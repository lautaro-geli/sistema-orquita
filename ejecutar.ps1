param([ValidateSet('servidor','simulador','entrenar')][string]$Modo='servidor', [string[]]$Parametros=@())
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path 'target/dependency')) { throw 'Ejecutar primero ./preparar.ps1' }
$claseOrquita = @{servidor='orquitas.servidor.ServidorOrquita'; simulador='orquitas.cliente.ClienteTelemetriaTCP'; entrenar='orquitas.simulacion.EntrenamientoDemostracion'}[$Modo]
& java -cp 'target/classes;target/dependency/*' $claseOrquita @Parametros
exit $LASTEXITCODE
