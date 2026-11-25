# Primera prueba real: Servidor Java + Orquita por Bluetooth, en la bahía armada.
# Compila la rama maestro-v2 en una copia aparte, arma una carpeta de prueba con las redes E2 (o la red de
# reglas), pregunta el puerto COM y arranca el Servidor. Todo lo que muestra queda en consola.txt.
# Cortar con Ctrl+C (la Orquita entra sola en Safe Mode si deja de recibir).
$ErrorActionPreference = 'Continue'

# ---- Configuración ----
$Commit = 'd3195f7'
$Umbral = 3   # Lobitos para pasar a ESCAPE (reglamento de la competencia).
# -----------------------

$docs = Join-Path $env:USERPROFILE 'Documents'
$repo = Join-Path $docs 'sistema-orquita'
$base = Join-Path $docs 'orquita-corridas'
$compilado = Join-Path $base 'prueba-real-compilado'
$e2 = Join-Path $base 'experimentos2_20261116_1544\modelos_E2_repeticion_2_s3'
$fecha = Get-Date -Format 'yyyyMMdd_HHmm'
$prueba = Join-Path $base "prueba_real_$fecha"
New-Item -ItemType Directory -Force -Path $prueba | Out-Null

function Paso([string]$nombre, [scriptblock]$accion) {
    Write-Host "== $nombre"
    & $accion 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Falló: $nombre (código $LASTEXITCODE)" }
}

try {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        $mavenBin = Join-Path $docs 'herramientas\apache-maven-3.9.11\bin'
        if (-not (Test-Path (Join-Path $mavenBin 'mvn.cmd'))) { throw "No encuentro Maven en $mavenBin." }
        $env:Path = "$mavenBin;" + $env:Path
    }
    Set-Location $repo
    Paso 'Bajando ramas (git fetch)' { git fetch origin }
    # Si ya hay una copia compilada en este commit, se reusa (no hace falta borrarla ni recompilar).
    $clases = Join-Path $compilado 'target\classes\orquitas\servidor\ServidorOrquita.class'
    $commitCopia = ''
    if (Test-Path $compilado) { $commitCopia = (git -C $compilado rev-parse --short=7 HEAD 2>$null) }
    if ((Test-Path $clases) -and ($commitCopia -eq $Commit)) {
        Write-Host "== Uso la copia ya compilada en $Commit"
    } else {
        if (Test-Path $compilado) {
            git worktree remove --force $compilado 2>&1 | Out-Null
            if (Test-Path $compilado) { Remove-Item -Recurse -Force $compilado -ErrorAction SilentlyContinue }
            git worktree prune 2>&1 | Out-Null
        }
        if (Test-Path $compilado) {
            # Algo la tiene abierta (IDE de Arduino, Explorador, una consola): se compila en otra carpeta.
            $compilado = "$compilado`_$fecha"
            Write-Host "== La copia anterior está en uso; compilo en $compilado"
        }
        Paso "Copia en $Commit" { git worktree add --detach $compilado $Commit }
        Set-Location $compilado
        Paso 'Compilando y probando (mvn clean package)' { powershell -NoProfile -ExecutionPolicy Bypass -File .\preparar.ps1 }
    }
    $classpath = (Join-Path $compilado 'target\classes') + ';' + (Join-Path $compilado 'target\dependency\*')

    # Carpeta de la prueba: modelos/ y estomago.txt quedan acá.
    $modelos = Join-Path $prueba 'modelos'
    New-Item -ItemType Directory -Force -Path $modelos | Out-Null
    Copy-Item (Join-Path $compilado 'modelos\entorno.nnet') $modelos -ErrorAction SilentlyContinue

    Write-Host ''
    Write-Host 'Navegación:'
    Write-Host '  1 = redes E2 (percepción + movimiento)   <- la que queremos probar'
    Write-Host '  2 = reglas (navegación anterior, plan B; ojo: sin candado de salida)'
    Write-Host '  3 = barrido (zigzag con la percepción de E2)'
    Write-Host '  4 = red nueva (percepción + movimiento de otra carpeta, por ejemplo la de anoche)'
    $modo = Read-Host 'Elegí 1, 2, 3 o 4'
    $extra = @()
    if ($modo -eq '2') { $extra = @('--reglas') }
    elseif ($modo -eq '3') {
        if (-not (Test-Path (Join-Path $e2 'percepcion.nnet'))) { throw "No encuentro la percepción E2 en $e2" }
        Copy-Item (Join-Path $e2 'percepcion.nnet') $modelos -Force
        $extra = @('--modo=barrido')
    }
    elseif ($modo -eq '4') {
        $nueva = (Read-Host 'Carpeta con percepcion.nnet y movimiento.nnet (podés arrastrarla acá)').Trim('"', ' ')
        if (-not (Test-Path (Join-Path $nueva 'movimiento.nnet'))) { throw "No encuentro movimiento.nnet en $nueva" }
        Copy-Item (Join-Path $nueva 'percepcion.nnet') $modelos -Force
        Copy-Item (Join-Path $nueva 'movimiento.nnet') $modelos -Force
    }
    else {
        if (-not (Test-Path (Join-Path $e2 'movimiento.nnet'))) { throw "No encuentro las redes E2 en $e2" }
        Copy-Item (Join-Path $e2 'percepcion.nnet') $modelos -Force
        Copy-Item (Join-Path $e2 'movimiento.nnet') $modelos -Force
    }

    $puertos = [System.IO.Ports.SerialPort]::GetPortNames() | Sort-Object
    Write-Host ''
    Write-Host ('Puertos COM: ' + ($puertos -join ', '))
    Write-Host '(El del HC-06 es el "saliente" que aparece en Configuración > Bluetooth > Más opciones > Puertos COM.)'
    $puerto = Read-Host 'Puerto del HC-06 (por ejemplo COM5)'

    Set-Location $prueba
    Write-Host ''
    Write-Host "== Arrancando el Servidor ($puerto, umbral $Umbral). Prender la Orquita. Cortar con Ctrl+C."
    Write-Host "   Todo queda en $prueba\consola.txt"
    & java -cp $classpath orquitas.servidor.ServidorOrquita $puerto "--umbral=$Umbral" @extra 2>&1 |
        ForEach-Object { "$_" } | Tee-Object -FilePath (Join-Path $prueba 'consola.txt')
} catch {
    Write-Host "== ERROR: $_"
} finally {
    Set-Location $docs
    Write-Host "== Fin. Avisale a Claude: la consola quedó en $prueba"
}
