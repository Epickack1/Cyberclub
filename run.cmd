@echo off
chcp 65001 >nul
rem ============================================================
rem  Запуск консольной ИС "Киберспортивный клуб" (Windows)
rem  1) при необходимости собирает jar через Maven Wrapper
rem  2) консоль переключена в UTF-8 (chcp 65001), запускает приложение
rem  Аргументы передаются приложению (например: run.cmd --no-color)
rem ============================================================
setlocal
cd /d "%~dp0"

set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

if not exist "target\cyberclub.jar" (
    echo Сборка проекта, первый запуск может занять несколько минут...
    call mvnw.cmd -q -DskipTests package
    if errorlevel 1 (
        echo Сборка не удалась. Проверьте JDK 21 и подключение к интернету.
        exit /b 1
    )
)

"%JAVA_EXE%" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8 -jar "target\cyberclub.jar" %*
endlocal
