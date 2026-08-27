#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="/jail/minecraft/server"

echo "========================================="
echo "   BUILD & DEPLOY SLOWY (PLUGIN + DATAPACK)   "
echo "========================================="

echo ""
echo "1. Membangun Plugin Slowy (Slowy.jar)..."
bash "$DIR/plugin/build.sh"

echo ""
echo "2. Memperbarui Datapack ke Server..."
mkdir -p "$SERVER_DIR/world/datapacks/slowy"
cp -r "$DIR/datapack/"* "$SERVER_DIR/world/datapacks/slowy/"

echo ""
echo "3. Membersihkan plugin & datapack lama..."
rm -f "$SERVER_DIR/plugins/SlowyHomes.jar"
rm -rf "$SERVER_DIR/world/datapacks/greeting"

echo ""
echo "========================================="
echo "  SUKSES! Slowy.jar & Datapack Slowy Siap!"
echo "========================================="
