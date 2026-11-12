﻿# Запуск CyberClub (КР3) через Docker Compose одной командой.
# При первом запуске создаёт .env со случайными паролями и показывает пароль администратора.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)

function New-Secret([int]$length) {
    $chars = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'.ToCharArray()
    -join (1..$length | ForEach-Object { $chars | Get-Random })
}

$envPath = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    $text = [IO.File]::ReadAllText((Join-Path $PSScriptRoot '.env.example'), $utf8)
    $dbPassword = 'Db' + (Get-Random -Minimum 100000 -Maximum 999999) + (New-Secret 10)
    $adminPassword = 'Club' + (Get-Random -Minimum 1000 -Maximum 9999) + 'kr'
    $jwt = [Convert]::ToBase64String((1..48 | ForEach-Object { [byte](Get-Random -Maximum 256) }))
    $text = $text -replace '(?m)^POSTGRES_PASSWORD=.*$', "POSTGRES_PASSWORD=$dbPassword"
    $text = $text -replace '(?m)^BOOTSTRAP_ADMIN_PASSWORD=.*$', "BOOTSTRAP_ADMIN_PASSWORD=$adminPassword"
    $text = $text -replace '(?m)^JWT_SECRET_BASE64=.*$', "JWT_SECRET_BASE64=$jwt"
    $text = $text -replace '(?m)^PERMANENT_DELETE_ENABLED=.*$', 'PERMANENT_DELETE_ENABLED=true'
    [IO.File]::WriteAllText($envPath, $text, $utf8)
    Write-Host 'Создан .env со случайными паролями (в git и в архив он не попадает).' -ForegroundColor Green
}

# Порт сайта и данные администратора берём из .env
$settings = @{}
foreach ($line in [IO.File]::ReadAllLines($envPath, $utf8)) {
    if ($line -match '^\s*([A-Z_]+)=(.*)$') { $settings[$Matches[1]] = $Matches[2].Trim() }
}
$port = if ($settings['APP_PORT']) { $settings['APP_PORT'] } else { '8081' }

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host 'Docker не найден. Установите Docker Desktop: https://www.docker.com/products/docker-desktop/' -ForegroundColor Red
    exit 1
}
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    $desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (Test-Path -LiteralPath $desktop) {
        Write-Host 'Запускаю Docker Desktop, жду движок...'
        Start-Process -FilePath $desktop
        for ($i = 0; $i -lt 60; $i++) {
            Start-Sleep -Seconds 3
            docker info *> $null
            if ($LASTEXITCODE -eq 0) { break }
        }
    }
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Docker Desktop не запущен. Откройте его, дождитесь «Engine running» и запустите скрипт снова.' -ForegroundColor Red
        exit 1
    }
}

Write-Host 'Поднимаю PostgreSQL и backend. Первый запуск долгий (5-10 минут): Maven скачивает зависимости.'
docker compose --env-file .env up -d --wait
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Не запустилось. Логи: docker compose --env-file .env logs backend' -ForegroundColor Red
    exit 1
}

$url = "http://localhost:$port/"
Write-Host ''
Write-Host "Готово: $url" -ForegroundColor Green
Write-Host "Swagger: ${url}swagger-ui.html"
Write-Host "Администратор: $($settings['BOOTSTRAP_ADMIN_EMAIL']) / $($settings['BOOTSTRAP_ADMIN_PASSWORD'])"
Write-Host '(пароль временный: при первом входе сайт попросит задать свой)'
Write-Host 'Остановить: stop.cmd (данные сохраняются)'
Start-Process $url
