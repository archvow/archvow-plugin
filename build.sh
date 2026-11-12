#!/usr/bin/env bash
# Build both plugins into dist/.
#   ./build.sh            check + build IntelliJ and VS Code (what CI runs)
#   ./build.sh intellij   IntelliJ only
#   ./build.sh vscode     VS Code only
#   ./build.sh sign       sign the IntelliJ plugin, verify the signature, copy the -signed.zip to dist/ -
#                         local only, asks the passphrase.
#                         Key files: JETBRAINS_PRIVATE_KEY_FILE (default jetbrains_private_key_secret.pem) and
#                         JETBRAINS_CERTIFICATE_CHAIN_FILE (default jetbrains_certificate_chain_secret.crt).
set -euo pipefail
cd "$(dirname "$0")"
what="${1:-all}"
case "$what" in
  all|intellij|vscode|sign) ;;
  *) echo "usage: $0 [all|intellij|vscode|sign]" >&2; exit 2 ;;
esac
mkdir -p dist

if [[ "$what" == all || "$what" == intellij ]]; then
  (cd intellij && ./gradlew --quiet check buildPlugin)
  # The unsigned plugin only: a signed zip left by `./build.sh sign` must not end up in dist/.
  find dist -maxdepth 1 -name 'archvow-intellij-*.zip' ! -name '*-signed.zip' -delete
  find intellij/build/distributions -name 'archvow-intellij-*.zip' ! -name '*-signed.zip' -exec cp {} dist/ \;
fi

if [[ "$what" == all || "$what" == vscode ]]; then
  rm -f dist/archvow-*.vsix
  (cd vscode && npm ci --no-audit --no-fund && npm test && npm run package)
fi

if [[ "$what" == sign ]]; then
  key_file="${JETBRAINS_PRIVATE_KEY_FILE:-jetbrains_private_key_secret.pem}"
  chain_file="${JETBRAINS_CERTIFICATE_CHAIN_FILE:-jetbrains_certificate_chain_secret.crt}"
  for f in "$key_file" "$chain_file"; do
    if [[ ! -f "$f" ]]; then
      echo "error: $f not found (set JETBRAINS_PRIVATE_KEY_FILE / JETBRAINS_CERTIFICATE_CHAIN_FILE)" >&2
      exit 1
    fi
    if git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
      echo "error: $f is committed to Git - remove it from the repository and make a new key" >&2
      exit 1
    fi
  done

  # Always ask the passphrase, from the terminal (never from a pipe, never stored).
  if ! (exec </dev/tty) 2>/dev/null; then
    echo "error: ./build.sh sign needs a terminal to ask the passphrase" >&2
    exit 1
  fi
  read -rsp "JetBrains private key passphrase: " PRIVATE_KEY_PASSWORD </dev/tty
  echo >&2
  if [[ -z "$PRIVATE_KEY_PASSWORD" ]]; then
    echo "error: empty passphrase" >&2
    exit 1
  fi
  export PRIVATE_KEY_PASSWORD

  # A wrong passphrase gives a clear message here rather than a Gradle stack trace.
  if command -v openssl >/dev/null 2>&1 &&
     ! openssl pkey -in "$key_file" -passin env:PRIVATE_KEY_PASSWORD -noout 2>/dev/null; then
    echo "error: wrong passphrase for $key_file" >&2
    exit 1
  fi

  PRIVATE_KEY="$(cat "$key_file")"
  CERTIFICATE_CHAIN="$(cat "$chain_file")"
  export PRIVATE_KEY CERTIFICATE_CHAIN

  # Two runs: in one build, Gradle 9 rejects verifyPluginSignature reading signPlugin's output
  # unless build.gradle.kts declares the dependency. --no-daemon: no daemon keeps the key afterwards.
  (cd intellij &&
    ./gradlew --no-daemon --console=plain signPlugin &&
    ./gradlew --no-daemon --console=plain verifyPluginSignature)
  unset PRIVATE_KEY CERTIFICATE_CHAIN PRIVATE_KEY_PASSWORD

  # Replace any signed zip of an earlier version, then copy this one.
  rm -f dist/archvow-intellij-*-signed.zip
  cp intellij/build/distributions/archvow-intellij-*-signed.zip dist/
fi

ls -l dist