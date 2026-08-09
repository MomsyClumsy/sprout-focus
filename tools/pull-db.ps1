# Достаёт базу Sprout с телефона или эмулятора и кладёт рядом файл,
# который открывается в DBeaver как обычный SQLite.
#
#   .\tools\pull-db.ps1
#   .\tools\pull-db.ps1 -Out C:\путь\снимок.db
#
# Работает потому, что debug-сборка помечена отлаживаемой: run-as пускает
# в приватную папку приложения. С релизной сборкой из Play это не сработает —
# и это правильно, туда никто снаружи ходить не должен.

param(
    [string]$Out = "$PSScriptRoot\sprout-snapshot.db"
)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = "com.sprout.focus"

if (-not (Test-Path $adb)) { throw "Не нашёлся adb: $adb" }

$devices = (& $adb devices) | Select-String -Pattern "\sdevice$"
if (-not $devices) { throw "Не видно ни одного устройства. Проверь USB-отладку или запусти эмулятор." }

# Room пишет через WAL: часть свежих данных лежит не в самом .db, а в файле
# sprout.db-wal рядом. Без этой строчки снимок окажется без последних записей,
# причём молча — файл откроется, просто будет старым.
Write-Host "Сбрасываю WAL в основной файл…"
& $adb shell "run-as $pkg sqlite3 databases/sprout.db 'PRAGMA wal_checkpoint(TRUNCATE);'" | Out-Null

# Гасим приложение: пока оно живо, оно может дописать что-нибудь в момент копирования.
& $adb shell am force-stop $pkg | Out-Null

Write-Host "Копирую базу…"
# Через .NET, а не через '>': перенаправление PowerShell прогоняет поток
# как текст и портит двоичный файл.
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $adb
$psi.Arguments = "exec-out run-as $pkg cat databases/sprout.db"
$psi.RedirectStandardOutput = $true
$psi.UseShellExecute = $false
$proc = [System.Diagnostics.Process]::Start($psi)
$stream = New-Object System.IO.FileStream($Out, [System.IO.FileMode]::Create)
$proc.StandardOutput.BaseStream.CopyTo($stream)
$stream.Close()
$proc.WaitForExit()

$size = (Get-Item $Out).Length
if ($size -lt 4096) { throw "Файл получился подозрительно маленьким ($size байт). Похоже, копирование не удалось." }

Write-Host "Готово: $Out ($([math]::Round($size / 1KB)) КБ)"
Write-Host "Открывать в DBeaver: Новое соединение → SQLite → указать этот файл."
