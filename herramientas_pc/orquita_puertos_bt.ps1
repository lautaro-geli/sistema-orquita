# Lista los puertos COM de Bluetooth y dice cual es el SALIENTE (el que hay que usar en el Servidor).
# Los salientes tienen la direccion del modulo (HC-06) en el ID; los entrantes tienen 000000000000.
$disp = Get-CimInstance Win32_PnPEntity | Where-Object { $_.Name -match '\(COM\d+\)' }
foreach ($d in $disp) {
  $com = [regex]::Match($d.Name, 'COM\d+').Value
  $tipo = 'otro (USB u otro)'
  if ($d.PNPDeviceID -match '^BTHENUM') {
    $m = [regex]::Match($d.PNPDeviceID, '&([0-9A-F]{12})_')
    if ($m.Success -and $m.Groups[1].Value -ne '000000000000') { $tipo = 'BLUETOOTH SALIENTE  <- usar este (modulo ' + $m.Groups[1].Value + ')' }
    else { $tipo = 'Bluetooth entrante (no sirve)' }
  }
  '{0,-6} {1}' -f $com, $tipo
}
''
'Dispositivos Bluetooth vinculados:'
Get-PnpDevice -Class Bluetooth -ErrorAction SilentlyContinue | Where-Object { $_.FriendlyName -match 'HC|Orquita' } |
  ForEach-Object { '  ' + $_.FriendlyName + '  (' + $_.Status + ')' }
