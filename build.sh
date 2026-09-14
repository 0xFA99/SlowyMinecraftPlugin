#!/bin/bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
mvn clean package "$@"
