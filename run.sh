#!/usr/bin/env bash
# Запуск консольной ИС "Киберспортивный клуб" (Linux/macOS).
# Собирает jar при первом запуске и запускает приложение; аргументы передаются приложению.
set -e
cd "$(dirname "$0")"
JAVA_EXE="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if [ ! -f target/cyberclub.jar ]; then
    echo "Сборка проекта (первый запуск может занять несколько минут)..."
    ./mvnw -q -DskipTests package
fi
exec "$JAVA_EXE" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8 -jar target/cyberclub.jar "$@"
