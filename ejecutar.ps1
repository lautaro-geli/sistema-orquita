# Uso:
#   ./ejecutar.ps1 servidor COM5 --umbral=1 [--estomago=estomago.txt] [--modelo=modelos/entorno.nnet] [--reglas]
#   ./ejecutar.ps1 simular-entrenar [episodios] [epocas] [carpeta] [rondasDagger]   (bahía simulada: percepcion.nnet y movimiento.nnet)
#   ./ejecutar.ps1 simular-ver [semilla] [--maestro] [--png=archivo.png]           (ventana con la bahía simulada)
#   ./ejecutar.ps1 recolectar COM5 LOBITO 30 [datos/barridos.csv]
#   ./ejecutar.ps1 entrenar [datos/barridos.csv] [modelos/entorno.nnet] [ocultas]
#   ./ejecutar.ps1 provisional [modelos/entorno.nnet]   (modelo SINTETICO, no es evidencia de campo)
param(
    [ValidateSet('servidor','recolectar','entrenar','provisional','simular-entrenar','simular-ver')][string]$Modo = 'servidor',
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
    'simular-entrenar' = 'orquitas.herramientas.EntrenamientoSimulado'
    'simular-ver' = 'orquitas.simulacion.bahia.VisorBahia'
}[$Modo]
& java -cp 'target/classes;target/dependency/*' $claseOrquita @Parametros
exit $LASTEXITCODE
