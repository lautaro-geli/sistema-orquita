# Analisis de la prueba de deteccion con notas por sector (lobito -15 20, obstaculo +30 15, salida -30 +15).
# Compila la ultima version de maestro-v2, pregunta que carpetas deteccion_* usar y deja analisis.md,
# analisis_sectores.csv y analisis_filtro.csv en la ultima carpeta elegida.
$ErrorActionPreference = 'Continue'
$docs = Join-Path $env:USERPROFILE 'Documents'
$repo = Join-Path $docs 'sistema-orquita'
$base = Join-Path $docs 'orquita-corridas'
$e2 = Join-Path $base 'experimentos2_20261116_1544\modelos_E2_repeticion_2_s3'
$fecha = Get-Date -Format 'yyyyMMdd_HHmm'
$compilado = Join-Path $base "analisis-compilado_$fecha"

function Paso([string]$nombre, [scriptblock]$accion) {
    Write-Host "== $nombre"
    & $accion 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Fallo: $nombre (codigo $LASTEXITCODE)" }
}

try {
    $carpetas = @(Get-ChildItem -Path $base -Directory -Filter 'deteccion_*' |
        Where-Object { Test-Path (Join-Path $_.FullName 'deteccion.csv') } | Sort-Object Name)
    if ($carpetas.Count -eq 0) { throw "No hay carpetas deteccion_* con deteccion.csv en $base" }
    Write-Host 'Carpetas con datos de deteccion:'
    for ($i = 0; $i -lt $carpetas.Count; $i++) { Write-Host ("  {0}) {1}" -f ($i + 1), $carpetas[$i].Name) }
    $eleccion = Read-Host 'Numeros separados por espacio (Enter = la ultima)'
    if ([string]::IsNullOrWhiteSpace($eleccion)) { $elegidas = @($carpetas[-1]) }
    else { $elegidas = @($eleccion.Split(' ', [StringSplitOptions]::RemoveEmptyEntries) | ForEach-Object { $carpetas[[int]$_ - 1] }) }
    $csvs = @($elegidas | ForEach-Object { Join-Path $_.FullName 'deteccion.csv' })
    $salida = $elegidas[-1].FullName

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

    $argumentos = @('-cp', $classpath, 'orquitas.herramientas.AnalisisDeteccion', $red) + $csvs + @("--salida=$salida")
    & java @argumentos 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Fallo el analisis (codigo $LASTEXITCODE)" }
    $informe = Join-Path $salida 'analisis.md'
    Write-Host "== Listo: $informe"
    Start-Process notepad.exe $informe
} catch {
    Write-Host "== ERROR: $_"
} finally {
    Set-Location $docs
    Write-Host "== Fin. Pasale a Claude la carpeta $salida (deteccion.csv y los analisis_*)."
}
