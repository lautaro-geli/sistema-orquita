# Neuroevolución (aprendizaje por refuerzo) de la red de movimiento, partiendo de una red ya entrenada.
# Prueba variantes de los pesos en el simulador y se queda con las que sacan más puntaje (no imita al Maestro).
# Desde E2 (sin girar y avanzar) y desde E7b (con girar y avanzar), 2 semillas cada una, 4 a la vez. Resultados en Documents\orquita-corridas\neuroevolucion_<fecha>.
$ErrorActionPreference = 'Continue'

# ---- Configuración ----
$Commit = 'bbb0172'
$Jvm = @('-Dorquita.barrido=1.91', '-Dorquita.ruidoRoi=1.51,1.73,1.94')
$Corridas_ = Join-Path $env:USERPROFILE 'Documents\orquita-corridas'
# Redes de partida (carpetas con percepcion.nnet y movimiento.nnet).
$E2 = Join-Path $Corridas_ 'experimentos2_20261116_1544\modelos_E2_repeticion_2_s3'
$E7b = Join-Path $Corridas_ 'experimentos5_20261117_1406\modelos_E7b_v2_s1'
# Cada corrida usa 1 núcleo; corta a los 120 min y hace la evaluación final (unos 5 min más).
$Base = @('--generaciones=400', '--minutos=120', '--pares=20', '--bahias=20', '--sigma=0.01', '--paso=0.01', '--validacion=200', '--cada=10', '--pruebas=300', '--hilos=1')
$Corridas = @(
    @{ Nombre = 'NE_E2_s1';  Partida = $E2;  Args = $Base + @('--semilla=1') },
    @{ Nombre = 'NE_E2_s2';  Partida = $E2;  Args = $Base + @('--semilla=2') },
    @{ Nombre = 'NE_E7b_s1'; Partida = $E7b; Args = $Base + @('--giro', '--semilla=1') },
    @{ Nombre = 'NE_E7b_s2'; Partida = $E7b; Args = $Base + @('--giro', '--semilla=2') }
)
# -----------------------

$docs = Join-Path $env:USERPROFILE 'Documents'
$repo = Join-Path $docs 'sistema-orquita'
$base = Join-Path $docs 'orquita-corridas'
$compilado = Join-Path $base 'ne-compilado'
$fecha = Get-Date -Format 'yyyyMMdd_HHmm'
$resultados = Join-Path $base "neuroevolucion_$fecha"
New-Item -ItemType Directory -Force -Path $resultados | Out-Null
Start-Transcript -Path (Join-Path $resultados 'registro_general.txt') -Force | Out-Null

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
    Paso 'Java' { java -version }
    foreach ($c in $Corridas) { if (-not (Test-Path (Join-Path $c.Partida 'movimiento.nnet'))) { throw "No encuentro la red de partida en $($c.Partida)" } }
    Set-Location $repo
    Paso 'Bajando ramas (git fetch)' { git fetch origin }
    if (Test-Path $compilado) {
        git worktree remove --force $compilado 2>&1 | Out-Null
        if (Test-Path $compilado) { Remove-Item -Recurse -Force $compilado }
        git worktree prune 2>&1 | Out-Null
    }
    Paso "Copia en $Commit" { git worktree add --detach $compilado $Commit }
    Set-Location $compilado
    Paso 'Compilando y probando (mvn clean package)' { powershell -NoProfile -ExecutionPolicy Bypass -File .\preparar.ps1 }
    $classpath = '"' + (Join-Path $compilado 'target\classes') + ';' + (Join-Path $compilado 'target\dependency\*') + '"'

    $nucleos = [Environment]::ProcessorCount
    $maximo = 4
    $mascara = [IntPtr]([long]([math]::Pow(2, $nucleos) - 2))   # todos menos el 0
    $inicio = Get-Date
    $pendientes = New-Object System.Collections.Queue
    foreach ($c in $Corridas) { $pendientes.Enqueue($c) }
    $activos = @{}
    $terminados = @{}

    while ($pendientes.Count -gt 0 -or $activos.Count -gt 0) {
        # Arrancar mientras haya núcleos libres
        while ($pendientes.Count -gt 0 -and $activos.Count -lt $maximo) {
            $c = $pendientes.Dequeue()
            $dir = Join-Path $base "ne\$($c.Nombre)"
            if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
            New-Item -ItemType Directory -Force -Path $dir | Out-Null
            $argumentos = $Jvm + @('-cp', $classpath, 'orquitas.herramientas.Neuroevolucion', ('"' + $c.Partida + '"'), 'modelos') + $c.Args
            $p = Start-Process -FilePath 'java' -ArgumentList $argumentos -WorkingDirectory $dir -PassThru -NoNewWindow `
                 -RedirectStandardOutput (Join-Path $resultados "consola_$($c.Nombre).txt") -RedirectStandardError (Join-Path $resultados "errores_$($c.Nombre).txt")
            $null = $p.Handle
            Start-Sleep -Milliseconds 500
            try { $p.ProcessorAffinity = $mascara; $p.PriorityClass = 'BelowNormal' } catch { Write-Host "Aviso: no pude fijar núcleos/prioridad de $($c.Nombre) ($_)" }
            $activos[$c.Nombre] = @{ Proceso = $p; Corrida = $c; Dir = $dir; Inicio = Get-Date }
            Write-Host "== $([int]((Get-Date) - $inicio).TotalMinutes) min: arranca $($c.Nombre) (PID $($p.Id))"
        }
        Start-Sleep -Seconds 60
        foreach ($nombre in @($activos.Keys)) {
            $a = $activos[$nombre]
            if ($a.Proceso.HasExited) {
                $min = [int]((Get-Date) - $a.Inicio).TotalMinutes
                Write-Host "== $([int]((Get-Date) - $inicio).TotalMinutes) min: terminó $nombre (código $($a.Proceso.ExitCode), $min min)"
                $reporte = Join-Path $a.Dir 'modelos\neuroevolucion.md'
                if (Test-Path $reporte) { Copy-Item $reporte (Join-Path $resultados "neuroevolucion_$nombre.md") }
                else { Write-Host "   Aviso: $nombre no dejó reporte (ver consola_$nombre.txt y errores_$nombre.txt)" }
                $modelos = Join-Path $resultados "modelos_$nombre"
                New-Item -ItemType Directory -Force -Path $modelos | Out-Null
                Copy-Item (Join-Path $a.Dir 'modelos\*.nnet') $modelos -ErrorAction SilentlyContinue
                $terminados[$nombre] = $a.Proceso.ExitCode
                $activos.Remove($nombre)
            }
        }
        $estado = ($activos.Keys | Sort-Object) -join ', '
        Write-Host "   $([int]((Get-Date) - $inicio).TotalMinutes) min  corriendo: $estado  | en espera: $($pendientes.Count)  | terminadas: $($terminados.Count)"
    }
    $total = [int]((Get-Date) - $inicio).TotalMinutes
    Write-Host "== LISTO en $total min. Resultados en $resultados"
    Write-Host "   Avisale a Claude para que revise los reportes y los suba al repo (sin los .nnet)."
} catch {
    Write-Host "== ERROR: $_"
} finally {
    Set-Location $docs
    Stop-Transcript | Out-Null
}
