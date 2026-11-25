# Noche del 24 al 25/11: reentrenar percepcion y movimiento con la simulacion realista (sensor como el real).
# Fase 1 (N): maestro de siempre (conoce la geometria), 3 semillas. Usa el commit fijo de abajo.
# Fase 2 (B): maestro "barrido" (sin trampa), 3 semillas. Antes de arrancarla vuelve a bajar maestro-v2
#             (asi usa el modo barrido mas nuevo que se haya subido durante la noche).
# En las dos: barridos reales de la toma guiada mezclados en la percepcion. E2 no se toca.
# Resultados en Documents\orquita-corridas\noche_realista_<fecha>.
$ErrorActionPreference = 'Continue'

# ---- Configuracion ----
$Commit = 'd018e50'
$Jvm = @('-Dorquita.simRealista=true', '-Dorquita.barrido=1.91', '-Dorquita.ruidoRoi=1.51,1.73,1.94')
$Semillas = @('20261113', '20261999', '20262777')
# -----------------------

$docs = Join-Path $env:USERPROFILE 'Documents'
$repo = Join-Path $docs 'sistema-orquita'
$base = Join-Path $docs 'orquita-corridas'
$fecha = Get-Date -Format 'yyyyMMdd_HHmm'
$resultados = Join-Path $base "noche_realista_$fecha"
New-Item -ItemType Directory -Force -Path $resultados | Out-Null
Start-Transcript -Path (Join-Path $resultados 'registro_general.txt') -Force | Out-Null

function Paso([string]$nombre, [scriptblock]$accion) {
    Write-Host "== $nombre"
    & $accion 2>&1 | ForEach-Object { Write-Host "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Fallo: $nombre (codigo $LASTEXITCODE)" }
}

# Copia del repo en $cual (commit o rama) compilada en $carpeta. Devuelve el classpath.
function Compilar([string]$cual, [string]$carpeta) {
    Set-Location $repo
    Paso 'Bajando cambios (git fetch)' { git fetch origin }
    if (Test-Path $carpeta) {
        git worktree remove --force $carpeta 2>&1 | Out-Null
        if (Test-Path $carpeta) { Remove-Item -Recurse -Force $carpeta }
        git worktree prune 2>&1 | Out-Null
    }
    Paso "Copia en $cual" { git worktree add --detach $carpeta $cual }
    Set-Location $carpeta
    Paso 'Compilando y probando (mvn clean package)' { powershell -NoProfile -ExecutionPolicy Bypass -File .\preparar.ps1 }
    Write-Host ("   Commit: " + (git rev-parse --short HEAD))
    return '"' + (Join-Path $carpeta 'target\classes') + ';' + (Join-Path $carpeta 'target\dependency\*') + '"'
}

# Corre las corridas de a (nucleos - 1) como maximo; copia reporte y modelos al terminar cada una.
function Correr($corridas, [string]$classpath) {
    $nucleos = [Environment]::ProcessorCount
    $maximo = [Math]::Max(1, $nucleos - 1)
    $mascara = [IntPtr]([long]([math]::Pow(2, $nucleos) - 2))
    $inicio = Get-Date
    $pendientes = New-Object System.Collections.Queue
    foreach ($c in $corridas) { $pendientes.Enqueue($c) }
    $activos = @{}
    while ($pendientes.Count -gt 0 -or $activos.Count -gt 0) {
        while ($pendientes.Count -gt 0 -and $activos.Count -lt $maximo) {
            $c = $pendientes.Dequeue()
            $dir = Join-Path $base "noche\$($c.Nombre)"
            if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
            New-Item -ItemType Directory -Force -Path $dir | Out-Null
            $argumentos = $Jvm + @('-cp', $classpath, 'orquitas.herramientas.EntrenamientoSimulado') + $c.Args + @('--hilos=1')
            $p = Start-Process -FilePath 'java' -ArgumentList $argumentos -WorkingDirectory $dir -PassThru -NoNewWindow `
                 -RedirectStandardOutput (Join-Path $resultados "consola_$($c.Nombre).txt") -RedirectStandardError (Join-Path $resultados "errores_$($c.Nombre).txt")
            $null = $p.Handle
            Start-Sleep -Milliseconds 500
            try { $p.ProcessorAffinity = $mascara; $p.PriorityClass = 'BelowNormal' } catch { Write-Host "Aviso: no pude fijar nucleos/prioridad de $($c.Nombre)" }
            $activos[$c.Nombre] = @{ Proceso = $p; Dir = $dir; Inicio = Get-Date }
            Write-Host "== $([int]((Get-Date) - $inicio).TotalMinutes) min: arranca $($c.Nombre) (PID $($p.Id))"
        }
        Start-Sleep -Seconds 60
        foreach ($nombre in @($activos.Keys)) {
            $a = $activos[$nombre]
            if ($a.Proceso.HasExited) {
                Write-Host "== termino $nombre (codigo $($a.Proceso.ExitCode), $([int]((Get-Date) - $a.Inicio).TotalMinutes) min)"
                $reporte = Get-ChildItem (Join-Path $a.Dir 'docs\evidencias') -Filter 'entrenamiento_simulado_*.md' -ErrorAction SilentlyContinue |
                           Sort-Object LastWriteTime | Select-Object -Last 1
                if ($reporte) { Copy-Item $reporte.FullName (Join-Path $resultados "reporte_$nombre.md") }
                else { Write-Host "   Aviso: $nombre no dejo reporte (ver consola_$nombre.txt y errores_$nombre.txt)" }
                $modelos = Join-Path $resultados "modelos_$nombre"
                New-Item -ItemType Directory -Force -Path $modelos | Out-Null
                Copy-Item (Join-Path $a.Dir 'modelos\*.nnet') $modelos -ErrorAction SilentlyContinue
                $activos.Remove($nombre)
            }
        }
        Write-Host "   $([int]((Get-Date) - $inicio).TotalMinutes) min  corriendo: $(($activos.Keys | Sort-Object) -join ', ')  | en espera: $($pendientes.Count)"
    }
}

function Corridas([string]$prefijo, [string]$reales, [string[]]$extra) {
    $lista = @()
    $i = 1
    foreach ($s in $Semillas) {
        $lista += @{ Nombre = "$($prefijo)_s$i"; Args = @('600', '80', 'modelos', '2', '--entradas=CRUDA', '--pruebas=300', '--repeticion=2',
                     "--semilla=$s", "--reales=$reales", '--peso-reales=20') + $extra }
        $i++
    }
    return $lista
}

try {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        $mavenBin = Join-Path $docs 'herramientas\apache-maven-3.9.11\bin'
        if (-not (Test-Path (Join-Path $mavenBin 'mvn.cmd'))) { throw "No encuentro Maven en $mavenBin." }
        $env:Path = "$mavenBin;" + $env:Path
    }
    Paso 'Java' { java -version }
    $total = Get-Date

    Write-Host "=================== FASE 1: maestro de siempre ($Commit) ==================="
    $c1 = Join-Path $base 'noche-compilado-1'
    $cp1 = Compilar $Commit $c1
    Correr (Corridas 'N_maestro' (Join-Path $c1 'docs\evidencias\prueba_real\toma_guiada_20261124\etiquetado_para_entrenar.csv') @()) $cp1

    Write-Host "=================== FASE 2: maestro barrido (lo ultimo de maestro-v2) ==================="
    $c2 = Join-Path $base 'noche-compilado-2'
    $cp2 = Compilar 'origin/maestro-v2' $c2
    Correr (Corridas 'B_barrido' (Join-Path $c2 'docs\evidencias\prueba_real\toma_guiada_20261124\etiquetado_para_entrenar.csv') @('--maestro=barrido')) $cp2

    Write-Host "== LISTO en $([int]((Get-Date) - $total).TotalMinutes) min. Resultados en $resultados"
    Write-Host "   Avisale a Claude: pasale la carpeta (los reportes .md) para comparar."
} catch {
    Write-Host "== ERROR: $_"
} finally {
    Set-Location $docs
    Stop-Transcript | Out-Null
}
