# Запуск MCP-сервера ИС "Киберспортивный клуб" (Windows) для внешнего ИИ:
#   powershell -ExecutionPolicy Bypass -File .\mcp-server.ps1
#
# ВНИМАНИЕ: stdout зарезервирован под JSON-RPC протокола MCP. Весь служебный вывод
# перенаправляется в stderr, иначе клиент собьётся на посторонних строках.
#
# Пример подключения в конфиг клиента (путь — абсолютный):
#   {
#     "mcpServers": {
#       "cyberclub": {
#         "command": "powershell",
#         "args": ["-ExecutionPolicy", "Bypass", "-File", "C:\\путь\\к\\mcp-server.ps1"],
#         "env": { "CYBERCLUB_DB_PASSWORD": "ваш_пароль" }
#       }
#     }
#   }
#
# Список инструментов без запуска протокола:  .\mcp-server.ps1 -ListTools
param(
    [switch]$ListTools
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Команды сборки пишут в stdout — перехватываем его в переменную, чтобы протокол не
# засорился, а stderr пусть идёт в консоль напрямую (MCP-клиент читает его как stderr).
# Намеренно без 2>&1: на Windows PowerShell 5.1 вместе с $ErrorActionPreference = "Stop"
# перенаправленный stderr native-команды превращается в терминирующую ошибку и роняет скрипт.
function Invoke-Tool {
    param([string]$Exe, [string[]]$CmdArgs)
    $output = & $Exe @CmdArgs
    if ($LASTEXITCODE -ne 0) {
        foreach ($line in $output) { [Console]::Error.WriteLine($line) }
        exit $LASTEXITCODE
    }
}

if (-not $env:CYBERCLUB_DB_PASSWORD) {
    [Console]::Error.WriteLine("ВНИМАНИЕ: CYBERCLUB_DB_PASSWORD не задана — будет использован пароль из database.properties.")
}

if (-not (Test-Path "target\classes\ru\mirea\cyberclub\mcp")) {
    [Console]::Error.WriteLine("Сборка проекта (первый запуск может занять минуты)...")
    Invoke-Tool ".\mvnw.cmd" @("-q", "-DskipTests", "compile")
}

$cpFile = "target\mcp-classpath.txt"
if (-not (Test-Path $cpFile) -or
    ((Get-Item "pom.xml").LastWriteTime -gt (Get-Item $cpFile).LastWriteTime)) {
    [Console]::Error.WriteLine("Сборка classpath...")
    Invoke-Tool ".\mvnw.cmd" @("-q", "dependency:build-classpath", "-Dmdep.outputFile=target/mcp-classpath.txt")
}

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }
$classpath = "target\classes;" + (Get-Content $cpFile -Raw).Trim()

# Не используем имя $args — это автоматическая переменная PowerShell.
$javaArgs = @(
    "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstdin.encoding=UTF-8",
    "-Dorg.slf4j.simpleLogger.logFile=System.err",
    "-cp", $classpath, "ru.mirea.cyberclub.mcp.McpMain"
)
if ($ListTools) { $javaArgs += "--list-tools" }

& $java @javaArgs
exit $LASTEXITCODE
