# Experimentos 2 (análisis del 15/11): red A sin repetir las clases raras (E1), con repetición máxima 2 (E2)
# y sin repetición con el doble de épocas (E3); 3 semillas cada uno, evaluación en 300 bahías.
# Compila una sola copia del repo (rama experimentos-2) y corre las variantes de a 5 como máximo
# (una por núcleo, con prioridad baja y sin el núcleo 0). Cuando termina una, arranca la siguiente.
# Resultados en Documents\orquita-corridas\experimentos2_<fecha>.
$ErrorActionPreference = 'Continue'

# ---- Configuración ----
$Commit = '8a04d8f'
# Las 5 primeras salen juntas; las otras entran cuando se liberan núcleos.
# Todas: red A (184 entradas), 600 recorridos, 2 rondas, evaluación en 300 bahías (semillas 900000+).
$Corridas = @(
    @{ Nombre = 'E1_sin_repeticion_s1';   Args = @('600', '80',  'modelos', '2', '--entradas=CRUDA', '--pruebas=300', '--repeticion=1', '--semilla=20261113') },
    @{ Nombre = 'E1_sin_repeticion_s2';   Args = @('600', '80',  'modelos', '2', '--entradas=CRUDA', '--pruebas=300', '--repeticion=1', '--semilla=20261999') },
    @{ Nombre = 'E1_sin_repeticion_s3';   Args = @('600', '80',  'modelos', '2', '--entradas=CRUDA', '--pruebas=300', '--repeticion=1', '--semilla=20262777') },
    @{ Nombre = 'E2_repeticion_2_s1';     Args = @('600', '80',  'modelos', '2', '--entradas=CRUDA', '--pruebas=300', '--repeticion=2', '--semilla=20261113') },
    @{ Nombre = 'E2_repeticion_2_s2';     Args = @('600', '80',  'modelos', '2', '--entradas=CRUDA', '--pruebas=300', '--repeticion=2', '--semilla=20261999') },
    @{ Nombre = 'E2_repeticion_2_s3';     Args = @('600', '80',  'modelos', '2', '--entradas=CRUDA', '--pruebas=300', '--repeticion=2', '--semilla=20262777') },
    @{ Nombre = 'E3_sin_rep_160ep_s1';    Args = @('600', '160', 'modelos', '2', '--entradas=CRUDA', '--pruebas=300', '--repeticion=1', '--semilla=20261113') },
    @{ Nombre = 'E3_sin_rep_160ep_s2';    Args = @('600', '160', 'modelos', '2', '--entradas=CRUDA', '--pruebas=300', '--repeticion=1', '--semilla=20261999') },
    @{ Nombre = 'E3_sin_rep_160ep_s3';    Args = @('600', '160', 'modelos', '2', '--entradas=CRUDA', '--pruebas=300', '--repeticion=1', '--semilla=20262777') }
)
# -----------------------

$docs = Join-Path $env:USERPROFILE 'Documents'
$repo = Join-Path $docs 'sistema-orquita'
$base = Join-Path $docs 'orquita-corridas'
$compilado = Join-Path $base 'exp2-compilado'
$fecha = Get-Date -Format 'yyyyMMdd_HHmm'
$resultados = Join-Path $base "experimentos2_$fecha"
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
    $maximo = [Math]::Max(1, $nucleos - 1)
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
            $dir = Join-Path $base "exp2\$($c.Nombre)"
            if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
            New-Item -ItemType Directory -Force -Path $dir | Out-Null
            $argumentos = @('-cp', $classpath, 'orquitas.herramientas.EntrenamientoSimulado') + $c.Args + @('--hilos=1')
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
                $reporte = Get-ChildItem (Join-Path $a.Dir 'docs\evidencias') -Filter 'entrenamiento_simulado_*.md' -ErrorAction SilentlyContinue |
                           Sort-Object LastWriteTime | Select-Object -Last 1
                if ($reporte) { Copy-Item $reporte.FullName (Join-Path $resultados "entrenamiento_simulado_$($fecha)_$nombre.md") }
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
