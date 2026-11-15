# Corridas A/B/C del encargo (bloques 2, 5 y 6) en paralelo, en la bahía simulada nueva.
#   A = bahía nueva, red de movimiento vieja (184 entradas, sin memoria)
#   B = + representación intermedia (14 entradas, sin memoria)
#   C = + memoria (24 entradas), simulación repartida en hilos
# Cada corrida usa su propia copia del repo (git worktree) en Documents\orquita-corridas\A, B y C.
# El núcleo 0 queda libre para Windows: los tres procesos corren en los núcleos 1..N-1 y con prioridad baja.
# Resultados (reportes, modelos, registros e imágenes) en Documents\orquita-corridas\resultados_<fecha>.
$ErrorActionPreference = 'Continue'

# ---- Configuración (se cambia acá si recalibramos y hay que repetir) ----
# Las tres con la calibración medida el 15/11/2026 (200 mm/s, 212 °/s, giro sobre el eje, embudo).
# A y B son las ramas corrida-A y corrida-B (bloque 2 y bloque 5 + esa calibración); C es la rama principal.
$Corridas = [ordered]@{
    A = @{ Commit = 'd60e7aa'; Nombre = 'A_bahia_nueva';    Hilos = 0 }   # sin --hilos (esa versión no lo tiene)
    B = @{ Commit = '9d5c995'; Nombre = 'B_representacion'; Hilos = 0 }
    C = @{ Commit = '96f6be4'; Nombre = 'C_memoria';        Hilos = 3 }
}
$Episodios = 300; $Epocas = 80; $RondasDagger = 2
$SemillasVisor = 900000, 900001, 900002
# -----------------------------------------------------------------------------

$docs = Join-Path $env:USERPROFILE 'Documents'
$repo = Join-Path $docs 'sistema-orquita'
$base = Join-Path $docs 'orquita-corridas'
$fecha = Get-Date -Format 'yyyyMMdd_HHmm'
$resultados = Join-Path $base "resultados_$fecha"
New-Item -ItemType Directory -Force -Path $resultados | Out-Null
Start-Transcript -Path (Join-Path $resultados 'registro_general.txt') -Force | Out-Null

function Paso([string]$nombre, [scriptblock]$accion) {
    Write-Host "== $nombre"
    & $accion 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Falló: $nombre (código $LASTEXITCODE)" }
}

try {
    # Maven portable (el mismo que usa orquita_entrenar_v3)
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        $mavenBin = Join-Path $docs 'herramientas\apache-maven-3.9.11\bin'
        if (-not (Test-Path (Join-Path $mavenBin 'mvn.cmd'))) { throw "No encuentro Maven en $mavenBin. Correr antes orquita_entrenar_v3.bat una vez." }
        $env:Path = "$mavenBin;" + $env:Path
    }
    Paso 'Java' { java -version }
    Paso 'Maven' { mvn -v }
    if (-not (Test-Path $repo)) { throw "No encuentro el repo en $repo" }
    Set-Location $repo
    Paso 'Bajando ramas (git fetch)' { git fetch origin }

    # 1) Una copia por corrida, en su commit, compilada con sus pruebas (de a una, para no pisar Maven)
    foreach ($k in $Corridas.Keys) {
        $c = $Corridas[$k]; $dir = Join-Path $base $k
        Set-Location $repo
        if (Test-Path $dir) {
            git worktree remove --force $dir 2>&1 | Out-Null
            if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
            git worktree prune 2>&1 | Out-Null
        }
        Paso "Copia $k en $($c.Commit)" { git worktree add --detach $dir $c.Commit }
        Set-Location $dir
        Paso "Compilando y probando $k (mvn clean package)" { powershell -NoProfile -ExecutionPolicy Bypass -File .\preparar.ps1 }
    }

    # 2) Lanzar las tres en paralelo, fuera del núcleo 0 y con prioridad baja
    $nucleos = [Environment]::ProcessorCount
    $mascara = [IntPtr]([long]([math]::Pow(2, $nucleos) - 2))   # todos menos el 0
    $inicio = Get-Date
    $procesos = @{}
    foreach ($k in $Corridas.Keys) {
        $c = $Corridas[$k]; $dir = Join-Path $base $k
        $argumentos = @('-cp', '"target/classes;target/dependency/*"', 'orquitas.herramientas.EntrenamientoSimulado',
                        $Episodios, $Epocas, 'modelos', $RondasDagger)
        if ($c.Hilos -gt 0) { $argumentos += "--hilos=$($c.Hilos)" }
        $p = Start-Process -FilePath 'java' -ArgumentList $argumentos -WorkingDirectory $dir -PassThru -NoNewWindow `
             -RedirectStandardOutput (Join-Path $resultados "consola_$k.txt") -RedirectStandardError (Join-Path $resultados "errores_$k.txt")
        $null = $p.Handle   # necesario para poder leer ExitCode al final
        Start-Sleep -Milliseconds 500
        try { $p.ProcessorAffinity = $mascara; $p.PriorityClass = 'BelowNormal' } catch { Write-Host "Aviso: no pude fijar núcleos/prioridad de $k ($_)" }
        $procesos[$k] = $p
        Write-Host "== Corrida $k lanzada (PID $($p.Id), núcleos 1..$($nucleos - 1))"
    }
    Write-Host "== Entrenando A, B y C en paralelo. Se puede minimizar esta ventana; NO cerrarla."
    while ($procesos.Values | Where-Object { -not $_.HasExited }) {
        Start-Sleep -Seconds 60
        $min = [int]((Get-Date) - $inicio).TotalMinutes
        $estado = ($Corridas.Keys | ForEach-Object { "$_=" + $(if ($procesos[$_].HasExited) { 'terminó' } else { 'corriendo' }) }) -join '  '
        Write-Host "   $min min  $estado"
    }

    # 3) Juntar reportes y modelos
    foreach ($k in $Corridas.Keys) {
        $c = $Corridas[$k]; $dir = Join-Path $base $k
        $codigo = $procesos[$k].ExitCode
        Write-Host "== $k terminó con código $codigo"
        $reporte = Get-ChildItem (Join-Path $dir 'docs\evidencias') -Filter 'entrenamiento_simulado_*.md' |
                   Where-Object { $_.LastWriteTime -ge $inicio } | Sort-Object LastWriteTime | Select-Object -Last 1
        if ($reporte) { Copy-Item $reporte.FullName (Join-Path $resultados "entrenamiento_simulado_$($fecha)_$($c.Nombre).md") }
        else { Write-Host "   Aviso: $k no dejó reporte (ver consola_$k.txt y errores_$k.txt)" }
        $modelos = Join-Path $resultados "modelos_$k"
        New-Item -ItemType Directory -Force -Path $modelos | Out-Null
        Copy-Item (Join-Path $dir 'modelos\*.nnet') $modelos -ErrorAction SilentlyContinue
    }

    # 4) Imágenes del visor: maestro y redes de C en las mismas semillas
    $dirC = Join-Path $base 'C'
    $imagenes = Join-Path $resultados 'imagenes'
    New-Item -ItemType Directory -Force -Path $imagenes | Out-Null
    Set-Location $dirC
    foreach ($s in $SemillasVisor) {
        & java -cp 'target/classes;target/dependency/*' orquitas.simulacion.bahia.VisorBahia $s --maestro "--png=$imagenes\maestro_$s.png" 2>&1 | ForEach-Object { "$_" }
        & java -cp 'target/classes;target/dependency/*' orquitas.simulacion.bahia.VisorBahia $s '--percepcion=modelos/percepcion.nnet' '--movimiento=modelos/movimiento.nnet' "--png=$imagenes\redesC_$s.png" 2>&1 | ForEach-Object { "$_" }
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
