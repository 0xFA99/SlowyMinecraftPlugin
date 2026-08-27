#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="/jail/minecraft/server"

echo "==> Membersihkan build lama..."
rm -rf "$DIR/build"
mkdir -p "$DIR/build/classes"

echo "==> Mengumpulkan libraries classpath..."
CP=$(find "$SERVER_DIR/libraries" -name "*.jar" | tr '\n' ':'):"$SERVER_DIR/plugins/UltimateDonutSmp-1.4.1.jar":"$SERVER_DIR/plugins/EssentialsX-2.22.0.jar":"$SERVER_DIR/plugins/VaultUnlocked-2.20.2.jar":"$SERVER_DIR/plugins/GSit-3.5.1.jar":"$SERVER_DIR/plugins/Citizens-2.0.43-b4232.jar"

echo "==> Mengompilasi kode Java..."
find "$DIR/src/main/java" -name "*.java" > "$DIR/sources.txt"
/usr/lib/jvm/java-1.25.0-openjdk-amd64/bin/javac -cp "$CP" -d "$DIR/build/classes" @"$DIR/sources.txt"
rm -f "$DIR/sources.txt"

echo "==> Menyalin plugin.yml..."
cp "$DIR/src/main/resources/plugin.yml" "$DIR/build/classes/"

echo "==> Membuat file JAR ke folder plugins server..."
jar -cvf "$SERVER_DIR/plugins/Slowy.jar" -C "$DIR/build/classes" .

echo "==> Selesai! Plugin berhasil dibuat di: $SERVER_DIR/plugins/Slowy.jar"
