@echo off
chcp 65001 >nul
rem ============================================================
rem  Запуск JavaFX-приложения CyberClub (КР2) на Windows.
rem  Нужны: JDK 21, запущенный PostgreSQL с базой cyberclub (db\init.ps1)
rem  и переменная окружения CYBERCLUB_DB_PASSWORD с паролем пользователя postgres.
rem ============================================================
setlocal
cd /d "%~dp0"

if "%CYBERCLUB_DB_PASSWORD%"=="" (
    echo ВНИМАНИЕ: переменная CYBERCLUB_DB_PASSWORD не задана — будет использован пароль из database.properties.
    echo Задать на текущий сеанс:  set CYBERCLUB_DB_PASSWORD=ваш_пароль
)

call "%~dp0mvnw.cmd" -q javafx:run
endlocal
