# Prueba de deteccion real: el robot queda quieto en la bahia, barre una y otra vez, y por cada barrido
# se ve sector por sector lo que mide el sensor y lo que cree la red de percepcion N_maestro_s2.
# Compila la ultima version de maestro-v2 en una copia aparte. Todo queda en deteccion.csv y consola.txt.
$ErrorActionPreference = 'Continue'
$docs = Join-Path $env:USERPROFILE 'Documents'
$repo = Join-Path $docs 'sistema-orquita'
$base = Join-Path $docs 'orquita-corridas'
$e2 = Join-Path $base 'modelos_N_maestro_s2'   # Red nueva; la de E2 era experimentos2_20261116_1544\modelos_E2_repeticion_2_s3
$fecha = Get-Date -Format 'yyyyMMdd_HHmm'
$compilado = Join-Path $base "deteccion-compilado_$fecha"
$prueba = Join-Path $base "deteccion_$fecha"
New-Item -ItemType Directory -Force -Path $prueba | Out-Null

function Paso([string]$nombre, [scriptblock]$accion) {
    Write-Host "== $nombre"
    & $accion 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Fallo: $nombre (codigo $LASTEXITCODE)" }
}

try {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        $mavenBin = Join-Path $docs 'herramientas\apache-maven-3.9.11\bin'
        if (-not (Test-Path (Join-Path $mavenBin 'mvn.cmd'))) { throw "No encuentro Maven en $mavenBin." }
        $env:Path = "$mavenBin;" + $env:Path
    }
    Set-Location $repo
    Paso 'Bajando ramas (git fetch)' { git fetch origin }
    Paso 'Copia de maestro-v2' { git worktree add --detach $compilado origin/maestro-v2 }
    Set-Location $compilado
    Paso 'Compilando (mvn clean package)' { powershell -NoProfile -ExecutionPolicy Bypass -File .\preparar.ps1 }
    $classpath = (Join-Path $compilado 'target\classes') + ';' + (Join-Path $compilado 'target\dependency\*')
    $red = Join-Path $e2 'percepcion.nnet'
    if (-not (Test-Path $red)) { throw "No encuentro la red de percepcion N_maestro_s2 en $red" }

    $puertos = [System.IO.Ports.SerialPort]::GetPortNames() | Sort-Object
    Write-Host ''
    Write-Host ('Puertos COM: ' + ($puertos -join ', '))
    $puerto = Read-Host 'Puerto saliente del HC-06 (por ejemplo COM11)'
    Set-Location $prueba
    Write-Host ''
    Write-Host '== Prender la Orquita (con Orquita.ino). Escribi que hay en la escena y Enter; Ctrl+C para terminar.'
    & java -cp $classpath orquitas.herramientas.PruebaDeteccion $puerto $red (Join-Path $prueba 'deteccion.csv') 2>&1 |
        ForEach-Object { "$_" } | Tee-Object -FilePath (Join-Path $prueba 'consola.txt')
} catch {
    Write-Host "== ERROR: $_"
} finally {
    Set-Location $docs
    Write-Host "== Fin. Avisale a Claude: quedo en $prueba"
}
