#!/usr/bin/env bash
# Builds the reproducer and runs it. Settings live in repro.Main.
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out && mkdir -p out/v1 out/v2 out/logs
javac --release 21 -d out/v1 src/main/repro/*.java
# Same methods as version 1, different bodies, so redefine frees the old methods.
sed 's/return 1;/return 2;/g' src/main/repro/A.java > out/A.java
javac --release 21 -d out/v2 out/A.java
cat > out/manifest.txt <<'EOF'
Main-Class: repro.Main
Launcher-Agent-Class: repro.Main
Can-Redefine-Classes: true
EOF
jar --create --file out/repro.jar --manifest out/manifest.txt -C out/v1 .

if ! java -XX:ErrorFile="$PWD/out/logs/hs_err_pid%p.log" -XX:-CreateCoredumpOnCrash -jar out/repro.jar; then
  echo "JVM exited abnormally; see out/logs"
  exit 1
fi
