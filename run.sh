#!/usr/bin/env bash
# Запуск консольной ИС "Киберспортивный клуб" (Linux/macOS).
# Собирает jar при первом запуске и запускает приложение; аргументы передаются приложению.
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
    # java из PATH (например, в Docker-контейнере) — берём его как JAVA_HOME
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
if [ ! -f target/cyberclub.jar ]; then
    echo "Сборка проекта (первый запуск может занять несколько минут)..."
    ./mvnw -q -DskipTests package
fi
exec "$JAVA_EXE" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8 -jar target/cyberclub.jar "$@"
