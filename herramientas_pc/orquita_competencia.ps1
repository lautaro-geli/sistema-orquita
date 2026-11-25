# COMPETENCIA (26/11): Servidor con la red nueva N_maestro_s2 (percepcion + movimiento), umbral 3.
# Compila un commit fijo una sola vez (si ya esta compilado, lo reusa: no hace falta internet).
# Pregunta solo el puerto COM. Todo queda en Documents\orquita-corridas\competencia_<fecha>\consola.txt.
# Respaldo: escribir E2 cuando pregunta la red. Cortar con Ctrl+C (la Orquita frena sola en Safe Mode).
# Consejo: correrlo una vez antes (y cortar con Ctrl+C al pedir el puerto) para dejarlo compilado.
$ErrorActionPreference = 'Continue'

# ---- Configuracion ----
$Commit = 'd3195f7'
$Umbral = 3
# -----------------------

$docs = Join-Path $env:USERPROFILE 'Documents'
$repo = Join-Path $docs 'sistema-orquita'
$base = Join-Path $docs 'orquita-corridas'
$compilado = Join-Path $base 'competencia-compilado'
$e2 = Join-Path $base 'experimentos2_20261116_1544\modelos_E2_repeticion_2_s3'
$fecha = Get-Date -Format 'yyyyMMdd_HHmm'
$carpeta = Join-Path $base "competencia_$fecha"
New-Item -ItemType Directory -Force -Path $carpeta | Out-Null

function Paso([string]$nombre, [scriptblock]$accion) {
    Write-Host "== $nombre"
    & $accion 2>&1 | ForEach-Object { Write-Host "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Fallo: $nombre (codigo $LASTEXITCODE)" }
}

# Donde puede estar la red nueva (la primera que tenga los dos archivos).
function BuscarRedNueva {
    $candidatas = @((Join-Path $base 'modelos_N_maestro_s2'))
    $candidatas += Get-ChildItem $base -Directory -Filter 'noche_realista_*' -ErrorAction SilentlyContinue |
                   Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'modelos_N_maestro_s2' }
    $candidatas += @((Join-Path $env:USERPROFILE 'OneDrive\Desktop\modelos_N_maestro_s2'), (Join-Path $env:USERPROFILE 'Desktop\modelos_N_maestro_s2'),
                     (Join-Path $env:USERPROFILE 'Downloads\modelos_N_maestro_s2'))
    foreach ($c in $candidatas) {
        if ((Test-Path (Join-Path $c 'percepcion.nnet')) -and (Test-Path (Join-Path $c 'movimiento.nnet'))) { return $c }
    }
    return $null
}

try {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        $mavenBin = Join-Path $docs 'herramientas\apache-maven-3.9.11\bin'
        if (Test-Path (Join-Path $mavenBin 'mvn.cmd')) { $env:Path = "$mavenBin;" + $env:Path }
    }
    Set-Location $repo
    $clases = Join-Path $compilado 'target\classes\orquitas\servidor\ServidorOrquita.class'
    $commitCopia = ''
    if (Test-Path $compilado) { $commitCopia = (git -C $compilado rev-parse --short=7 HEAD 2>$null) }
    if ((Test-Path $clases) -and ($commitCopia -eq $Commit)) {
        Write-Host "== Uso la copia ya compilada ($Commit)"
    } else {
        Write-Host '== Bajando cambios (git fetch; si no hay internet sigo con lo que hay)'
        git fetch origin 2>&1 | ForEach-Object { Write-Host "$_" }
        git cat-file -e "$Commit^{commit}" 2>$null
        if ($LASTEXITCODE -ne 0) { throw "No tengo el commit $Commit. Conectate a internet y correlo de nuevo." }
        if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'No encuentro Maven (Documents\herramientas\apache-maven-3.9.11).' }
        if (Test-Path $compilado) {
            git worktree remove --force $compilado 2>&1 | Out-Null
            if (Test-Path $compilado) { Remove-Item -Recurse -Force $compilado -ErrorAction SilentlyContinue }
            git worktree prune 2>&1 | Out-Null
        }
        if (Test-Path $compilado) { $compilado = "$compilado`_$fecha"; Write-Host "== La copia anterior esta en uso; compilo en $compilado" }
        Paso "Copia en $Commit" { git worktree add --detach $compilado $Commit }
        Set-Location $compilado
        Paso 'Compilando y probando (mvn clean package, ~1 min)' { powershell -NoProfile -ExecutionPolicy Bypass -File .\preparar.ps1 }
    }
    $classpath = (Join-Path $compilado 'target\classes') + ';' + (Join-Path $compilado 'target\dependency\*')

    $modelos = Join-Path $carpeta 'modelos'
    New-Item -ItemType Directory -Force -Path $modelos | Out-Null
    $nueva = BuscarRedNueva
    Write-Host ''
    if ($nueva) { Write-Host "Red nueva N_maestro_s2: $nueva" } else { Write-Host 'No encontre la red nueva N_maestro_s2.' }
    $eleccion = (Read-Host 'Enter = red nueva N_maestro_s2  |  E2 = red de respaldo  |  o pega la carpeta de otra red').Trim('"', ' ')
    if ($eleccion -eq '') {
        if (-not $nueva) { throw 'No encuentro la red nueva. Pone la carpeta modelos_N_maestro_s2 en Documents\orquita-corridas o pega su ruta.' }
        $red = $nueva
    } elseif ($eleccion -ieq 'E2') { $red = $e2 } else { $red = $eleccion }
    foreach ($f in 'percepcion.nnet', 'movimiento.nnet') {
        if (-not (Test-Path (Join-Path $red $f))) { throw "Falta $f en $red" }
        Copy-Item (Join-Path $red $f) $modelos -Force
    }
    Write-Host "== Red: $red"

    $puertos = [System.IO.Ports.SerialPort]::GetPortNames() | Sort-Object
    Write-Host ''
    Write-Host ('Puertos COM: ' + ($puertos -join ', ') + '   (el del HC-06 es el SALIENTE, en Configuracion > Bluetooth > Mas opciones > Puertos COM)')
    $puerto = (Read-Host 'Puerto del HC-06 (por ejemplo COM4)').Trim().ToUpper()

    Set-Location $carpeta
    Write-Host ''
    Write-Host "== COMPETENCIA: Servidor en $puerto, umbral $Umbral. Prender la Orquita. Cortar con Ctrl+C."
    Write-Host "   Todo queda en $carpeta\consola.txt"
    & java -cp $classpath orquitas.servidor.ServidorOrquita $puerto "--umbral=$Umbral" 2>&1 |
        ForEach-Object { "$_" } | Tee-Object -FilePath (Join-Path $carpeta 'consola.txt')
} catch {
    Write-Host "== ERROR: $_"
} finally {
    Set-Location $docs
    Write-Host "== Fin. La consola quedo en $carpeta"
}
