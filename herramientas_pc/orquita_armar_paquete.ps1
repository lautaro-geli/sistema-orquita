# Arma una carpeta portátil del Servidor para llevar a otra PC (solo necesita Java 17 o más nuevo).
# Compila la rama maestro-v2 en una copia aparte y junta: clases, librerías (Neuroph, jSerialComm...),
# las redes E2, la red de reglas (plan B), el sketch de la Orquita y servidor.bat para arrancar.
# Resultado: Documents\OrquitaPortable y Documents\OrquitaPortable.zip
$ErrorActionPreference = 'Continue'
$Commit = '1b78d39'

$docs = Join-Path $env:USERPROFILE 'Documents'
$repo = Join-Path $docs 'sistema-orquita'
$base = Join-Path $docs 'orquita-corridas'
$compilado = Join-Path $base 'paquete-compilado'
$e2 = Join-Path $base 'experimentos2_20261116_1544\modelos_E2_repeticion_2_s3'
$destino = Join-Path $docs 'OrquitaPortable'

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
    if (-not (Test-Path (Join-Path $e2 'movimiento.nnet'))) { throw "No encuentro las redes E2 en $e2" }
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

    Write-Host "== Armando $destino"
    if (Test-Path $destino) { Remove-Item -Recurse -Force $destino }
    New-Item -ItemType Directory -Force -Path (Join-Path $destino 'lib'), (Join-Path $destino 'modelos'), (Join-Path $destino 'reglas\modelos'), (Join-Path $destino 'OrquitaServidor') | Out-Null
    Copy-Item -Recurse (Join-Path $compilado 'target\classes') (Join-Path $destino 'classes')
    Copy-Item (Join-Path $compilado 'target\dependency\*.jar') (Join-Path $destino 'lib')
    Copy-Item (Join-Path $e2 'percepcion.nnet'), (Join-Path $e2 'movimiento.nnet') (Join-Path $destino 'modelos')
    Copy-Item (Join-Path $compilado 'modelos\entorno.nnet') (Join-Path $destino 'reglas\modelos')
    Copy-Item (Join-Path $compilado 'arduino\Orquita\Orquita.ino') (Join-Path $destino 'OrquitaServidor\OrquitaServidor.ino')
    # servidor.bat: el que Claude dejó en Documentos (con finales de línea de Windows).
    $bat = Join-Path $docs 'servidor_portable.bat'
    if (-not (Test-Path $bat)) { $bat = Join-Path $compilado 'herramientas_pc\servidor_portable.bat' }
    Copy-Item $bat (Join-Path $destino 'servidor.bat')
    $zip = "$destino.zip"
    if (Test-Path $zip) { Remove-Item -Force $zip }
    Compress-Archive -Path (Join-Path $destino '*') -DestinationPath $zip
    Write-Host "== LISTO: $destino y $zip"
    Write-Host "   En la otra PC: descomprimir, instalar Java 17+ si no está, y doble clic en servidor.bat"
} catch {
    Write-Host "== ERROR: $_"
} finally {
    Set-Location $docs
}
