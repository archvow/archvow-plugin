#!/usr/bin/env bash
# Puts the archvow the plugins are tested with into contract/archvow/: its wheel, and from its Git
# repository at the same tag, the examples and docs/rules*.md the wheel does not hold.
#   contract/tools/take_archvow.sh <archvow repository> <tag> <wheel>
set -euo pipefail
[ "$#" -eq 3 ] || { echo "usage: $0 <archvow repository> <tag> <archvow-X.Y.Z-py3-none-any.whl>"; exit 2; }
source="$1"; tag="$2"; wheel="$3"
case "$(basename "$wheel")" in archvow-*-py3-none-any.whl) ;; *) echo "not an archvow wheel: $wheel"; exit 2 ;; esac
target="$(cd "$(dirname "$0")/.." && pwd)/archvow"
find "$target" -mindepth 1 -maxdepth 1 ! -name README.md -exec rm -rf {} +
cp "$wheel" "$target/"
git -C "$source" archive "$tag" examples/python-app examples/fastapi-service examples/pyspark-pipeline \
  docs/rules.md docs/rules.fr.md | tar -x -C "$target"
echo "contract/archvow: $(basename "$wheel") and archvow's examples and rules at $tag"
echo "next: pip install the wheel, python3 contract/tools/regenerate.py contract/archvow, review git diff contract/"
