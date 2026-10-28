#!/usr/bin/env bash
# Запуск MCP-сервера ИС "Киберспортивный клуб" (Linux/macOS) для внешнего ИИ:
#   Claude Desktop, opencode, GitHub Copilot и другие клиенты протокола MCP.
#
# ВНИМАНИЕ: stdout зарезервирован под JSON-RPC протокола MCP.
# Поэтому весь служебный вывод (сборка, ошибки) перенаправляется в stderr —
# если писать в stdout, клиент собьётся на посторонних строках.
#
# Пример подключения в конфиг клиента:
#   {
#     "mcpServers": {
#       "cyberclub": {
#         "command": "/полный/путь/к/mcp-server.sh",
#         "env": { "CYBERCLUB_DB_PASSWORD": "ваш_пароль" }
#       }
#     }
#   }
#
# Источник данных выбирается по окружению:
#   CYBERCLUB_API_URL=http://localhost:8080 — ходить в REST API (KR3-backend), базу не открывать;
#   без неё — прямое подключение к PostgreSQL (нужен CYBERCLUB_DB_PASSWORD).
# Оба режима работают с одной базой cyberclub: KR3-backend по умолчанию подключается
# к ней же, поэтому изменения из чата видны в окне приложения.
#
# Список доступных инструментов без запуска протокола:  ./mcp-server.sh --list-tools
set -e
cd "$(dirname "$0")"

# Нужен JDK 21+: проверяем JAVA_HOME, иначе ищем JDK 21 автоматически
# (java_home на macOS, Homebrew/linuxbrew, java в PATH).
java_is_21() {
    [ -n "$1" ] && [ -x "$1/bin/java" ] && \
        "$1/bin/java" -version 2>&1 | grep -qE 'version "(2[1-9]|[3-9][0-9])[.-]'
}
if ! java_is_21 "$JAVA_HOME"; then
    JAVA_HOME=""
    for candidate in \
        "$(/usr/libexec/java_home -v 21 2>/dev/null || true)" \
        "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
        "/home/linuxbrew/.linuxbrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"; do
        if java_is_21 "$candidate"; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi
if ! java_is_21 "$JAVA_HOME" && command -v java >/dev/null 2>&1; then
    if java -version 2>&1 | grep -qE 'version "(2[1-9]|[3-9][0-9])[.-]'; then
        export JAVA_HOME="$(cd "$(dirname "$(command -v java)")/.." && pwd)"
    fi
fi
if ! java_is_21 "$JAVA_HOME"; then
    echo "ОШИБКА: не найден JDK 21 или новее." >&2
    echo "Установите его или укажите путь: export JAVA_HOME=/путь/к/jdk-21" >&2
    exit 1
fi
JAVA_EXE="$JAVA_HOME/bin/java"

# В REST-режиме пароль БД не нужен — предупреждаем только когда он действительно требуется.
if [ -z "$CYBERCLUB_API_URL" ] && [ -z "$CYBERCLUB_DB_PASSWORD" ]; then
    echo "ВНИМАНИЕ: CYBERCLUB_DB_PASSWORD не задана — будет использован пароль из database.properties." >&2
fi

if [ ! -d target/classes/ru/mirea/cyberclub/mcp ]; then
    echo "Сборка проекта (первый запуск может занять минуты)..." >&2
    ./mvnw -q -DskipTests compile >&2
fi

# classpath пересобираем, только если изменился pom.xml
CP_FILE=target/mcp-classpath.txt
if [ ! -f "$CP_FILE" ] || [ pom.xml -nt "$CP_FILE" ]; then
    echo "Сборка classpath..." >&2
    ./mvnw -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE" >&2
fi

exec "$JAVA_EXE" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8 \
    -Dorg.slf4j.simpleLogger.logFile=System.err \
    -cp "target/classes:$(cat "$CP_FILE")" ru.mirea.cyberclub.mcp.McpMain "$@"
