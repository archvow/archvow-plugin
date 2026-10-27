#!/usr/bin/env bash
# Builds archvow's IDE plugins into dist/, each after its tests:
#   dist/archvow-intellij-<version>.zip   IntelliJ IDEA, PyCharm... (JDK 21 needed)
#   dist/archvow-<version>.vsix           VS Code (Node 20+ needed)
# ./build.sh            both
# ./build.sh intellij   only the IntelliJ plugin
# ./build.sh vscode     only the VS Code extension
set -euo pipefail
cd "$(dirname "$0")"
what="${1:-all}"
mkdir -p dist
if [[ "$what" == all || "$what" == intellij ]]; then
  (cd intellij && ./gradlew --quiet check buildPlugin)
  cp intellij/build/distributions/archvow-intellij-*.zip dist/
fi
if [[ "$what" == all || "$what" == vscode ]]; then
  (cd vscode && npm ci --no-audit --no-fund && npm test && npm run package)
fi
ls -l dist
