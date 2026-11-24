# Toma guiada de datos etiquetados (pendiente 1 del traspaso). El robot queda quieto y el programa va
# diciendo que poner en cada escena; con Enter guarda 3 barridos. Compila la ultima version de maestro-v2.
# Todo queda en orquita-corridas\toma_guiada_<fecha>\deteccion.csv y consola.txt. Al terminar corre el analisis.
$ErrorActionPreference = 'Continue'
$docs = Join-Path $env:USERPROFILE 'Documents'
$repo = Join-Path $docs 'sistema-orquita'
$base = Join-Path $docs 'orquita-corridas'
$e2 = Join-Path $base 'experimentos2_20261116_1544\modelos_E2_repeticion_2_s3'
$fecha = Get-Date -Format 'yyyyMMdd_HHmm'
$compilado = Join-Path $base "toma-compilado_$fecha"
$salida = $null

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
    if (-not (Test-Path $red)) { throw "No encuentro la red de percepcion E2 en $red" }

    $puertos = [System.IO.Ports.SerialPort]::GetPortNames() | Sort-Object
    Write-Host ''
    Write-Host ('Puertos COM: ' + ($puertos -join ', '))
    $puerto = Read-Host 'Puerto saliente del HC-06 (por ejemplo COM4)'
    $respuesta = Read-Host 'Escena desde la que empezar (Enter = 1; si se corto, la que seguia)'
    $desde = 1
    if (-not [string]::IsNullOrWhiteSpace($respuesta)) { $desde = [int]$respuesta }

    if ($desde -gt 1) {
        # Seguir en la ultima toma guiada, para que todo quede en el mismo archivo.
        $anterior = Get-ChildItem -Path $base -Directory -Filter 'toma_guiada_*' | Sort-Object Name | Select-Object -Last 1
        if ($anterior) { $salida = $anterior.FullName; Write-Host "Sigo en $salida desde la escena $desde." }
    }
    if (-not $salida) { $salida = Join-Path $base "toma_guiada_$fecha" }
    New-Item -ItemType Directory -Force -Path $salida | Out-Null

    Set-Location $salida
    Write-Host ''
    Write-Host '== Prender la Orquita (con Orquita.ino) y seguir las instrucciones. Ctrl+C para cortar.'
    & java -cp $classpath orquitas.herramientas.TomaGuiada $puerto $red $salida "--desde=$desde" 2>&1 |
        ForEach-Object { "$_" } | Tee-Object -FilePath (Join-Path $salida 'consola.txt') -Append
    if ($LASTEXITCODE -ne 0) { throw "La toma se corto (codigo $LASTEXITCODE). Para seguir, volve a abrir este .bat y pone la escena que seguia." }

    $csv = Join-Path $salida 'deteccion.csv'
    if (Test-Path $csv) {
        Write-Host '== Analizando los datos (AnalisisDeteccion)'
        & java -cp $classpath orquitas.herramientas.AnalisisDeteccion $red $csv "--salida=$salida" 2>&1 | ForEach-Object { "$_" } | Out-Null
        $informe = Join-Path $salida 'analisis.md'
        if (Test-Path $informe) { Start-Process notepad.exe $informe }
    }
} catch {
    Write-Host "== ERROR: $_"
} finally {
    Set-Location $docs
    if ($salida) { Write-Host "== Fin. Pasale a Claude la carpeta $salida" }
}
