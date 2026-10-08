# Запуск JavaFX-приложения CyberClub (КР2) из PowerShell:
#   powershell -ExecutionPolicy Bypass -File .\run.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not $env:CYBERCLUB_DB_PASSWORD) {
    Write-Host "ВНИМАНИЕ: переменная CYBERCLUB_DB_PASSWORD не задана — будет использован пароль из database.properties." -ForegroundColor Yellow
    Write-Host 'Задать на текущий сеанс:  $env:CYBERCLUB_DB_PASSWORD = "ваш_пароль"'
}

Write-Host "CyberClub JavaFX — подключение к jdbc:postgresql://localhost:5432/cyberclub" -ForegroundColor Cyan
& .\mvnw.cmd -q javafx:run
