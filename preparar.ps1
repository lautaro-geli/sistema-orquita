$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$mavenOrquita = Get-Command mvn -ErrorAction SilentlyContinue
if ($mavenOrquita) { $mavenOrquita = $mavenOrquita.Source }
else { $mavenOrquita = Join-Path $env:ProgramFiles 'Apache NetBeans/java/maven/bin/mvn.cmd' }
if (-not (Test-Path -LiteralPath $mavenOrquita)) { throw 'Instalar Maven o agregarlo al PATH.' }
& $mavenOrquita clean package dependency:copy-dependencies '-DincludeScope=runtime'
if ($LASTEXITCODE -ne 0) { throw 'Fallo la construccion. Revisar la salida de Maven.' }
