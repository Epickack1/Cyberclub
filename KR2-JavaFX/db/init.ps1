# =====================================================================
#  Создание базы данных cyberclub и загрузка схемы с тестовыми данными.
#  Запуск:  powershell -ExecutionPolicy Bypass -File db\init.ps1
#  Параметры (необязательно):
#    -PgBin "C:\Program Files\PostgreSQL\18\bin" -User postgres -DbHost localhost -Port 5432
# =====================================================================
param(
    [string]$PgBin  = "C:\Program Files\PostgreSQL\18\bin",
    [string]$User   = "postgres",
    [string]$DbHost = "localhost",
    [int]   $Port   = 5432,
    [string]$DbName = "cyberclub"
)

$ErrorActionPreference = "Stop"
$psql = Join-Path $PgBin "psql.exe"
if (-not (Test-Path $psql)) {
    Write-Host "psql.exe не найден: $psql. Укажите путь параметром -PgBin." -ForegroundColor Red
    exit 1
}

$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:PGCLIENTENCODING = "UTF8"

Write-Host "1/3 Создание базы данных $DbName (если её ещё нет)..."
$exists = & $psql -U $User -h $DbHost -p $Port -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$DbName'"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($exists -ne "1") {
    & $psql -U $User -h $DbHost -p $Port -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $DbName ENCODING 'UTF8'"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} else {
    Write-Host "    база уже существует, пересоздаём таблицы"
}

Write-Host "2/3 Схема: 01_schema.sql"
& $psql -U $User -h $DbHost -p $Port -d $DbName -v ON_ERROR_STOP=1 -q -f (Join-Path $dir "01_schema.sql")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "3/3 Тестовые данные: 02_seed.sql"
& $psql -U $User -h $DbHost -p $Port -d $DbName -v ON_ERROR_STOP=1 -q -f (Join-Path $dir "02_seed.sql")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Готово. Проверка:" -ForegroundColor Green
& $psql -U $User -h $DbHost -p $Port -d $DbName -c "SELECT (SELECT COUNT(*) FROM clients) AS clients, (SELECT COUNT(*) FROM stations) AS stations, (SELECT COUNT(*) FROM bookings) AS bookings"
