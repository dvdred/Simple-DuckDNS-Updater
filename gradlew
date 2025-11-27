#!/bin/bash
# Gradle wrapper per Android Studio su Ubuntu

# Determina la directory dove si trova lo script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Usa il gradle wrapper se esiste
if [ -f "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
    java -jar "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
else
    # Usa il gradle di sistema
    gradle "$@"
fi
