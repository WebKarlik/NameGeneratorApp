#!/usr/bin/env bash
# Сборка и запуск на Linux/macOS (нужен JDK 8 или новее).
#   ./build.sh        - собрать build/jar/NameGenerator.jar
#   ./build.sh run    - собрать и запустить
set -euo pipefail
cd "$(dirname "$0")"

JSON_LIB=lib/json-simple-1.1.1.jar

rm -rf build
mkdir -p build/classes build/jar
javac -encoding UTF-8 -cp "$JSON_LIB" -d build/classes src/NameGeneratorApp.java
cp data.json packaging/icon.png build/classes/
jar cfe build/jar/NameGenerator.jar NameGeneratorApp -C build/classes .
cp "$JSON_LIB" build/jar/

echo "Готово: build/jar/NameGenerator.jar"
if [ "${1:-}" = "run" ]; then
    java -cp "build/jar/NameGenerator.jar:build/jar/json-simple-1.1.1.jar" NameGeneratorApp
fi
