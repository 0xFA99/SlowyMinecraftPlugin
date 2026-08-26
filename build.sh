#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="/jail/minecraft/server"

echo ""
bash "$DIR/plugin/build.sh"

echo ""
mkdir -p "$SERVER_DIR/world/datapacks/slowy"
cp -r "$DIR/datapack/"* "$SERVER_DIR/world/datapacks/slowy/"

echo ""
rm -f "$SERVER_DIR/plugins/SlowyHomes.jar"
rm -rf "$SERVER_DIR/world/datapacks/greeting"

