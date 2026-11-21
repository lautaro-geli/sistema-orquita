# Manejar la Orquita con el teclado por Bluetooth. Requiere ControlManual.ino cargado en el Arduino.
# Flechas o W/A/S/D: mover (manteniendo apretado sigue andando). Espacio: frenar.
# + / -: paso. J/L: servo de barrido. I/K: elevacion. B: frente. N: elevacion 0. R: medir. Esc: salir.
$puerto = Read-Host 'Puerto saliente del HC-06 (por ejemplo COM11)'
$p = New-Object System.IO.Ports.SerialPort $puerto, 9600, 'None', 8, 'One'
$p.ReadTimeout = 20; $p.NewLine = "`n"
try { $p.Open() } catch { Write-Host "No se pudo abrir $puerto : $($_.Exception.Message)"; return }
Write-Host "Conectado a $puerto. Flechas/WASD mover, espacio frenar, +/- paso, J/L barrido, I/K elevacion, R medir, Esc salir."
$p.Write('h')
try {
  while ($true) {
    while ([Console]::KeyAvailable) {
      $k = [Console]::ReadKey($true)
      $c = $null
      switch ($k.Key) {
        'UpArrow' { $c = 'w' } 'DownArrow' { $c = 's' } 'LeftArrow' { $c = 'a' } 'RightArrow' { $c = 'd' }
        'Spacebar' { $c = 'x' } 'Escape' { $p.Write('x'); $p.Close(); return }
        default { if ($k.KeyChar) { $c = [string]$k.KeyChar } }
      }
      if ($c) { $p.Write($c) }
    }
    try { while ($true) { Write-Host ('<- ' + $p.ReadLine().TrimEnd()) } } catch [System.TimeoutException] { }
  }
} finally { if ($p.IsOpen) { $p.Write('x'); $p.Close() } }
