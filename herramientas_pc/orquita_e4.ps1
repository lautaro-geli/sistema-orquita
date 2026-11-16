# E4: umbral de LOBITO para el reflejo de seguridad (no entrena). Usa los modelos de E2 (3 semillas) en 300 bahías,
# las 3 a la vez en los núcleos 1..N-1. Resultados en Documents\orquita-corridas\e4_<fecha>.
$ErrorActionPreference = 'Continue'
$Commit = '85a267f'
$docs = Join-Path $env:USERPROFILE 'Documents'
$repo = Join-Path $docs 'sistema-orquita'
$base = Join-Path $docs 'orquita-corridas'
$modelos = Join-Path $base 'experimentos2_20261116_1544'
$compilado = Join-Path $base 'e4-compilado'
$resultados = Join-Path $base ("e4_" + (Get-Date -Format 'yyyyMMdd_HHmm'))
New-Item -ItemType Directory -Force -Path $resultados | Out-Null
Start-Transcript -Path (Join-Path $resultados 'registro_general.txt') -Force | Out-Null
function Paso([string]$nombre, [scriptblock]$accion) {
    Write-Host "== $nombre"
    & $accion 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Falló: $nombre (código $LASTEXITCODE)" }
}
try {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { $env:Path = (Join-Path $docs 'herramientas\apache-maven-3.9.11\bin') + ";" + $env:Path }
    Set-Location $repo
    Paso 'Bajando ramas (git fetch)' { git fetch origin }
    if (Test-Path $compilado) { git worktree remove --force $compilado 2>&1 | Out-Null; if (Test-Path $compilado) { Remove-Item -Recurse -Force $compilado }; git worktree prune 2>&1 | Out-Null }
    Paso "Copia en $Commit" { git worktree add --detach $compilado $Commit }
    Set-Location $compilado
    Paso 'Compilando y probando (mvn clean package)' { powershell -NoProfile -ExecutionPolicy Bypass -File .\preparar.ps1 }
    $cp = '"' + (Join-Path $compilado 'target\classes') + ';' + (Join-Path $compilado 'target\dependency\*') + '"'
    $mascara = [IntPtr]([long]([math]::Pow(2, [Environment]::ProcessorCount) - 2))
    $procesos = @()
    foreach ($s in 's1', 's2', 's3') {
        $m = Join-Path $modelos "modelos_E2_repeticion_2_$s"
        $rep = Join-Path $resultados "E4_E2_$s.md"
        $p = Start-Process -FilePath 'java' -ArgumentList @('-cp', $cp, 'orquitas.herramientas.EvaluacionUmbralLobito', ('"' + $m + '"'), '300', ('"' + $rep + '"')) -PassThru -NoNewWindow `
             -RedirectStandardOutput (Join-Path $resultados "consola_$s.txt") -RedirectStandardError (Join-Path $resultados "errores_$s.txt")
        $null = $p.Handle
        Start-Sleep -Milliseconds 500
        try { $p.ProcessorAffinity = $mascara; $p.PriorityClass = 'BelowNormal' } catch { }
        $procesos += $p
        Write-Host "== arranca E4 con modelos E2 $s (PID $($p.Id))"
    }
    $inicio = Get-Date
    while ($procesos | Where-Object { -not $_.HasExited }) { Start-Sleep -Seconds 20; Write-Host "   $([int]((Get-Date) - $inicio).TotalMinutes) min" }
    Get-ChildItem $resultados -Filter 'E4_*.md' | ForEach-Object { Write-Host "== $($_.Name)"; Get-Content $_.FullName | Select-String '^\|' | ForEach-Object { "$_" } }
    Write-Host "== LISTO. Resultados en $resultados. Avisale a Claude."
} catch { Write-Host "== ERROR: $_" } finally { Set-Location $docs; Stop-Transcript | Out-Null }
