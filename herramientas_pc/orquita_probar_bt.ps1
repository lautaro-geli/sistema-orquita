# Prueba solo el Bluetooth, sin el Servidor: abre el COM del HC-06, manda LATIDO cada 1 s durante 40 s
# y muestra todo lo que llega de la Orquita. Si anda, en el Monitor Serie del Arduino aparecen los LATIDO
# y aca aparece TELEMETRIA|... cada pocos segundos.
$puerto = Read-Host 'Puerto saliente del HC-06 (por ejemplo COM11)'
$p = New-Object System.IO.Ports.SerialPort $puerto, 9600, 'None', 8, 'One'
$p.ReadTimeout = 200; $p.NewLine = "`n"
Write-Host "Abriendo $puerto (el LED del HC-06 tendria que quedar fijo)..."
try { $p.Open() } catch { Write-Host "NO SE PUDO ABRIR $puerto : $($_.Exception.Message)"; return }
Write-Host 'Abierto. Mandando LATIDO cada 1 s durante 40 s...'
$fin = (Get-Date).AddSeconds(40); $proximo = Get-Date; $recibidos = 0
while ((Get-Date) -lt $fin) {
  if ((Get-Date) -ge $proximo) { $p.Write("LATIDO`n"); $proximo = (Get-Date).AddSeconds(1); Write-Host -NoNewline '.' }
  try { $l = $p.ReadLine(); $recibidos++; Write-Host ''; Write-Host "<- $l"; if ($l -like 'TELEMETRIA*') { $p.Write("RECHAZO`n") } } catch [System.TimeoutException] { }
}
$p.Close()
Write-Host ''
if ($recibidos -gt 0) { Write-Host "OK: llegaron $recibidos lineas de la Orquita. El Bluetooth anda." }
else { Write-Host 'No llego nada de la Orquita. Mirar el Monitor Serie: si aparecen LATIDO, falla el cable TX de la Orquita (A2 -> RX del HC-06); si no aparecen, el enlace no conecta o falla TX del HC-06 -> D10.' }
