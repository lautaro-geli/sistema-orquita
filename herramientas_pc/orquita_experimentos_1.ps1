# Experimentos 1 (después de las corridas A/B/C): variantes de la red A en la bahía simulada.
# Compila una sola copia del repo (rama experimentos-1) y corre las variantes de a 5 como máximo
# (una por núcleo, con prioridad baja y sin el núcleo 0). Cuando termina una, arranca la siguiente.
# Resultados en Documents\orquita-corridas\experimentos1_<fecha>.
$ErrorActionPreference = 'Continue'

# ---- Configuración ----
$Commit = '97cfd29'
# Orden de arranque: las 5 primeras salen juntas; la 7 y la 8 entran cuando se liberan los primeros núcleos.
$Corridas = @(
    @{ Nombre = '1_A_memoria_sin_orden';     Args = @('300', '80', 'modelos', '2', '--entradas=CRUDA_MEMORIA_SIN_ORDEN') },
    @{ Nombre = '2_A_4_rondas';              Args = @('300', '80', 'modelos', '4', '--entradas=CRUDA') },
    @{ Nombre = '3_A_otra_semilla';          Args = @('300', '80', 'modelos', '2', '--entradas=CRUDA', '--semilla=20261999') },
    @{ Nombre = '4_C_sin_orden';             Args = @('300', '80', 'modelos', '2', '--entradas=REPRESENTACION_MEMORIA_SIN_ORDEN') },
    @{ Nombre = '5_A_mas_14_numeros';        Args = @('300', '80', 'modelos', '2', '--entradas=CRUDA_REPRESENTACION') },
    @{ Nombre = '7_A_600_episodios';         Args = @('600', '80', 'modelos', '2', '--entradas=CRUDA') },
    @{ Nombre = '8_A_percepcion_reforzada';  Args = @('300', '80', 'modelos', '2', '--entradas=CRUDA', '--percepcion-reforzada') },
    @{ Nombre = '0_A_base_mismo_codigo';     Args = @('300', '80', 'modelos', '2', '--entradas=CRUDA') }
)
# -----------------------

$docs = Join-Path $env:USERPROFILE 'Documents'
$repo = Join-Path $docs 'sistema-orquita'
$base = Join-Path $docs 'orquita-corridas'
$compilado = Join-Path $base 'exp1-compilado'
$fecha = Get-Date -Format 'yyyyMMdd_HHmm'
$resultados = Join-Path $base "experimentos1_$fecha"
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
            $dir = Join-Path $base "exp1\$($c.Nombre)"
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
