# Manana 25/11: compara en la simulacion realista (900 bahias, mismas semillas) E2, el modo barrido y las
# redes nuevas de la noche (cada carpeta modelos_* de la ultima noche_realista_*). Deja comparacion.md en esa
# carpeta y la abre. Compila lo ultimo de maestro-v2.
$ErrorActionPreference = 'Continue'
$docs = Join-Path $env:USERPROFILE 'Documents'
$repo = Join-Path $docs 'sistema-orquita'
$base = Join-Path $docs 'orquita-corridas'
$e2 = Join-Path $base 'experimentos2_20261116_1544\modelos_E2_repeticion_2_s3'
$compilado = Join-Path $base 'comparar-compilado'

function Paso([string]$nombre, [scriptblock]$accion) {
    Write-Host "== $nombre"
    & $accion 2>&1 | ForEach-Object { Write-Host "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Fallo: $nombre (codigo $LASTEXITCODE)" }
}

try {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        $mavenBin = Join-Path $docs 'herramientas\apache-maven-3.9.11\bin'
        if (-not (Test-Path (Join-Path $mavenBin 'mvn.cmd'))) { throw "No encuentro Maven en $mavenBin." }
        $env:Path = "$mavenBin;" + $env:Path
    }
    $noche = Get-ChildItem $base -Directory -Filter 'noche_realista_*' | Sort-Object Name | Select-Object -Last 1
    if (-not $noche) { throw "No hay ninguna carpeta noche_realista_* en $base" }
    Write-Host "== Redes de: $($noche.FullName)"
    Set-Location $repo
    Paso 'Bajando cambios (git fetch)' { git fetch origin }
    if (Test-Path $compilado) {
        git worktree remove --force $compilado 2>&1 | Out-Null
        if (Test-Path $compilado) { Remove-Item -Recurse -Force $compilado }
        git worktree prune 2>&1 | Out-Null
    }
    Paso 'Copia de maestro-v2' { git worktree add --detach $compilado origin/maestro-v2 }
    Set-Location $compilado
    Paso 'Compilando y probando' { powershell -NoProfile -ExecutionPolicy Bypass -File .\preparar.ps1 }
    $classpath = (Join-Path $compilado 'target\classes') + ';' + (Join-Path $compilado 'target\dependency\*')
    $pilotos = @()
    foreach ($m in (Get-ChildItem $noche.FullName -Directory -Filter 'modelos_*' | Sort-Object Name)) {
        if (Test-Path (Join-Path $m.FullName 'movimiento.nnet')) { $pilotos += ($m.Name.Substring(8) + '=' + $m.FullName) }
    }
    Write-Host "== Pilotos: E2, barrido y $($pilotos.Count) redes nuevas. Tarda unos minutos."
    $salida = Join-Path $noche.FullName 'comparacion.md'
    & java '-Dorquita.simRealista=true' '-Dorquita.barrido=1.91' '-Dorquita.ruidoRoi=1.51,1.73,1.94' -cp $classpath `
        orquitas.herramientas.ComparacionPilotos $e2 $salida '--bahias=900' @pilotos 2>&1 | ForEach-Object { Write-Host "$_" }
    if (Test-Path $salida) { Start-Process notepad.exe $salida }
    Write-Host "== Listo: $salida. Pasasela a Claude."
} catch {
    Write-Host "== ERROR: $_"
} finally {
    Set-Location $docs
}
