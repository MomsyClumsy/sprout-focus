# Подкладывает в базу Sprout правдоподобную историю за месяц, чтобы было
# на чём проверить экран «Я». Наблюдения показываются только при достаточном
# объёме данных — на живой базе первых дней проверять там просто нечего.
#
#   .\tools\seed-history.ps1            # записать историю (со снимком «до»)
#   .\tools\seed-history.ps1 -Restore   # вернуть базу, какой она была до записи
#
# Перед записью снимается копия базы в tools\sprout-before-seed.db.
# Это не роскошь: данные Sprout живут только на телефоне, восстановить
# их неоткуда, а скрипт дописывает в ту же базу, где лежат настоящие задачи.

param(
    [switch]$Restore
)

$ErrorActionPreference = "Stop"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = "com.sprout.focus"
$backup = "$PSScriptRoot\sprout-before-seed.db"

if (-not (Test-Path $adb)) { throw "Не нашёлся adb: $adb" }
if (-not ((& $adb devices) | Select-String -Pattern "\sdevice$")) {
    throw "Не видно ни одного устройства. Проверь USB-отладку или запусти эмулятор."
}

# Приложение гасим ДО любой работы с базой: живое оно держит блокировку,
# и запись падает с database is locked.
& $adb shell am force-stop $pkg | Out-Null

function Copy-DbFromDevice($path) {
    & $adb shell "run-as $pkg sqlite3 databases/sprout.db 'PRAGMA wal_checkpoint(TRUNCATE);'" | Out-Null
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $adb
    $psi.Arguments = "exec-out run-as $pkg cat databases/sprout.db"
    $psi.RedirectStandardOutput = $true
    $psi.UseShellExecute = $false
    $proc = [System.Diagnostics.Process]::Start($psi)
    $stream = New-Object System.IO.FileStream($path, [System.IO.FileMode]::Create)
    $proc.StandardOutput.BaseStream.CopyTo($stream)
    $stream.Close()
    $proc.WaitForExit()
    if ((Get-Item $path).Length -lt 4096) {
        throw "Снимок базы получился пустым. Открывалось ли приложение хоть раз?"
    }
}

if ($Restore) {
    if (-not (Test-Path $backup)) { throw "Нет файла $backup — восстанавливать не из чего." }
    & $adb push $backup /data/local/tmp/restore.db | Out-Null
    # Журналы WAL старше подложенного файла и переписали бы его обратно
    & $adb shell "run-as $pkg sh -c 'rm -f databases/sprout.db-wal databases/sprout.db-shm; cat /data/local/tmp/restore.db > databases/sprout.db'"
    & $adb shell rm -f /data/local/tmp/restore.db | Out-Null
    & $adb shell am start -n "$pkg/.MainActivity" | Out-Null
    Write-Host "База возвращена к состоянию до подкладывания."
    exit 0
}

Write-Host "Снимаю копию базы «до»…"
Copy-DbFromDevice $backup

# --- сама история -------------------------------------------------------
#
# Числа подобраны так, чтобы обе карточки появились и их можно было
# сверить глазом, а пороги молчания при этом проверялись:
#   причины  — тревога 11 из 18, явный лидер;
#   длина    — 20 минут доводятся заметно чаще, чем 45;
#   25 минут запускались трижды — в сравнение не попадут, их слишком мало.

$now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$day = 24 * 60 * 60 * 1000L
$sql = New-Object System.Text.StringBuilder

function Add-Line($text) { [void]$sql.AppendLine($text) }

function Sql-Text($value) {
    if ($null -eq $value) { return "NULL" }
    return "'" + $value.Replace("'", "''") + "'"
}

Add-Line "BEGIN;"

# Задачи. Две с планом «если — то», две без: пригодится этапу, где план
# начнут сравнивать с его отсутствием.
$tasks = @(
    @{ title = "Дописать главу диплома"; step = "Открыть файл и перечитать последний абзац";
       ifT = "после того как налью кофе"; then = "сажусь за главу на 20 минут"; ago = 29; done = $false }
    @{ title = "Разобрать почту за неделю"; step = "Открыть ящик и удалить рассылки";
       ifT = $null; then = $null; ago = 24; done = $true }
    @{ title = "Отчёт по проекту"; step = "Выписать три пункта, о чём он вообще";
       ifT = "как только сяду за стол утром"; then = "пишу отчёт 20 минут"; ago = 18; done = $false }
    @{ title = "Записаться к врачу"; step = "Найти номер регистратуры";
       ifT = $null; then = $null; ago = 9; done = $true }
)

$taskIds = @()
foreach ($t in $tasks) {
    $created = $now - $t.ago * $day
    $completedAt = if ($t.done) { $created + 3 * $day } else { "NULL" }
    $status = if ($t.done) { "done" } else { "active" }
    Add-Line ("INSERT INTO tasks (title, firstStep, ifTrigger, thenAction, status, isCurrent, createdAt, completedAt, postponeCount, remindDaysMask) VALUES ({0}, {1}, {2}, {3}, '{4}', 0, {5}, {6}, 0, 0);" -f
        (Sql-Text $t.title), (Sql-Text $t.step), (Sql-Text $t.ifT), (Sql-Text $t.then), $status, $created, $completedAt)
    Add-Line "INSERT INTO events (type, at, taskId, payload) VALUES ('task_created', $created, last_insert_rowid(), '{""seed"":true}');"
    if ($t.done) {
        $at = $created + 3 * $day
        Add-Line "INSERT INTO events (type, at, taskId, payload) VALUES ('task_completed', $at, (SELECT MAX(id) FROM tasks), '{""seed"":true}');"
    }
    $taskIds += 1
}

# Сессии. Каждая строка: минут по плану, доведена ли, сколько таких.
$sessions = @(
    @{ minutes = 20; completed = $true;  count = 12 }
    @{ minutes = 20; completed = $false; count = 2 }
    @{ minutes = 45; completed = $true;  count = 3 }
    @{ minutes = 45; completed = $false; count = 5 }
    @{ minutes = 25; completed = $true;  count = 3 }
)

$slot = 0
foreach ($s in $sessions) {
    for ($i = 0; $i -lt $s.count; $i++) {
        # Раскладываем по дням окна: сессии должны быть разными днями,
        # иначе они выглядят как один запойный вечер.
        $started = $now - (28 - ($slot % 27)) * $day + ($slot % 6) * 60 * 60 * 1000L
        $planned = $s.minutes * 60
        # Брошенная сессия обрывается примерно на трети — так и бывает
        $actual = if ($s.completed) { $planned } else { [int]($planned / 3) }
        $completed = if ($s.completed) { 1 } else { 0 }
        $ended = $started + $actual * 1000L
        Add-Line "INSERT INTO sessions (taskId, mode, plannedSeconds, startedAt, endedAt, pausedTotal, actualSeconds, completed) VALUES ((SELECT MIN(id) FROM tasks), 'pomodoro', $planned, $started, $ended, 0, $actual, $completed);"
        Add-Line "INSERT INTO events (type, at, taskId, payload) VALUES ('session_started', $started, (SELECT MIN(id) FROM tasks), '{""seed"":true,""plannedSec"":$planned}');"
        Add-Line "INSERT INTO events (type, at, taskId, payload) VALUES ('session_ended', $ended, (SELECT MIN(id) FROM tasks), '{""seed"":true,""completed"":$($s.completed.ToString().ToLower())}');"
        $slot++
    }
}

# Отказы начать. Тревога — явный лидер, остальное вокруг неё.
$reasons = @(
    @{ reason = "anxiety";    count = 11 }
    @{ reason = "no_energy";  count = 3 }
    @{ reason = "boredom";    count = 2 }
    @{ reason = "too_big";    count = 1 }
    @{ reason = "distracted"; count = 1 }
)

$slot = 0
foreach ($r in $reasons) {
    for ($i = 0; $i -lt $r.count; $i++) {
        $at = $now - (27 - ($slot % 26)) * $day + 11 * 60 * 60 * 1000L
        Add-Line "INSERT INTO events (type, at, taskId, payload) VALUES ('task_postponed', $at, (SELECT MIN(id) FROM tasks), '{""seed"":true,""reason"":""$($r.reason)""}');"
        $slot++
    }
}

Add-Line "COMMIT;"

# Файлом, а не аргументом команды: строк много, а кириллицу в длинной
# командной строке adb легко потерять. Файл в UTF-8 без BOM — sqlite3
# читает его как есть.
$sqlPath = Join-Path $env:TEMP "sprout-seed.sql"
[System.IO.File]::WriteAllText($sqlPath, $sql.ToString(), (New-Object System.Text.UTF8Encoding($false)))

Write-Host "Записываю историю…"
& $adb push $sqlPath /data/local/tmp/seed.sql | Out-Null
$out = & $adb shell "cat /data/local/tmp/seed.sql | run-as $pkg sqlite3 databases/sprout.db"
if ($out) { Write-Host $out }
& $adb shell rm -f /data/local/tmp/seed.sql | Out-Null
Remove-Item $sqlPath -ErrorAction SilentlyContinue

# Проверочный запрос тоже уходит файлом. Через adb shell кавычки внутри
# запроса съедаются шеллом, и sqlite видит имя колонки вместо строки.
$check = "select (select count(*) from sessions), " +
    "(select count(*) from events where type='task_postponed'), " +
    "(select count(*) from tasks);"
[System.IO.File]::WriteAllText($sqlPath, $check, (New-Object System.Text.UTF8Encoding($false)))
& $adb push $sqlPath /data/local/tmp/check.sql | Out-Null
$counts = & $adb shell "cat /data/local/tmp/check.sql | run-as $pkg sqlite3 databases/sprout.db"
& $adb shell rm -f /data/local/tmp/check.sql | Out-Null
Remove-Item $sqlPath -ErrorAction SilentlyContinue
Write-Host "В базе теперь (сессии | отказы | задачи): $counts"

# Room узнаёт об изменениях только через свой трекер, а он видит записи
# изнутри приложения. Правку снаружи он пропустит — нужен перезапуск.
& $adb shell am start -n "$pkg/.MainActivity" | Out-Null
Write-Host "Приложение перезапущено. Вкладка «Я» — там должны появиться две карточки."
Write-Host "Вернуть базу как было: .\tools\seed-history.ps1 -Restore"
