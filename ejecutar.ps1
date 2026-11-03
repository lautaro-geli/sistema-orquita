# Uso:
#   ./ejecutar.ps1 servidor COM5 --umbral=1 [--estomago=estomago.txt] [--modelo=modelos/entorno.nnet]
#   ./ejecutar.ps1 recolectar COM5 LOBITO 30 [datos/barridos.csv]
#   ./ejecutar.ps1 entrenar [datos/barridos.csv] [modelos/entorno.nnet] [ocultas]
#   ./ejecutar.ps1 provisional [modelos/entorno.nnet]   (modelo SINTETICO, no es evidencia de campo)
param(
    [ValidateSet('servidor','recolectar','entrenar','provisional')][string]$Modo = 'servidor',
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$Parametros = @()
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path 'target/dependency')) { throw 'Ejecutar primero ./preparar.ps1' }
$claseOrquita = @{
    servidor    = 'orquitas.servidor.ServidorOrquita'
    recolectar  = 'orquitas.herramientas.RecolectorBarridos'
    entrenar    = 'orquitas.herramientas.EntrenamientoReal'
    provisional = 'orquitas.simulacion.EntrenamientoDemostracion'
}[$Modo]
& java -cp 'target/classes;target/dependency/*' $claseOrquita @Parametros
exit $LASTEXITCODE
