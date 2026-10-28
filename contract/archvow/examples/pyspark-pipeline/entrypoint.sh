#!/usr/bin/env bash
# Run a pipeline entry point with spark-submit (edge node, cluster or local), create its tables,
# or build its package once for deployment.
#
#   ./entrypoint.sh <entry point> [its arguments...]
#   SPARK_MASTER="local[*]" ./entrypoint.sh my_pipeline ...     # on a laptop
#   ./entrypoint.sh --ddl          # run the table scripts ddl/*.sql in order, with spark-sql
#   ./entrypoint.sh --package      # build the wheel dist/my_pipeline-<version>-py3-none-any.whl (+ dist/ddl.zip)
#
# <entry point> is the name given to `archvow component add entrypoint <name>`; the script runs
# src/my_pipeline/p4_entrypoints/<name>_main.py and ships the package to the executors.
# Table scripts are Spark SQL (CREATE TABLE IF NOT EXISTS ...), so they work with the catalog
# Spark is configured for: Hive metastore, Iceberg, Delta... The job never creates its tables.
# Settings (environment, default in brackets):
#   SPARK_SUBMIT [spark-submit]   SPARK_SQL [spark-sql]   SPARK_MASTER [yarn]   DEPLOY_MODE [client]
#   PYTHON [python3]   the Python that builds the zip and the wheel (e.g. python3.12)
#   SPARK_ARGS   extra spark-submit / spark-sql options, e.g. "--num-executors 4 --executor-memory 4g"
# The project's own settings and setup go in entrypoint.project.sh, next to this file, read first when it
# exists (e.g. export SPARK_ARGS="--queue finance", kinit -kt ...): archvow writes this file - init
# --force rewrites it - and never that one.
# Exit code: the entry point's own (0 success, 1 job failure, 2 invalid arguments, ...).
set -euo pipefail

cd "$(dirname "$0")"
if [ -f entrypoint.project.sh ]; then  # the team's own settings and setup
    # shellcheck source=/dev/null
    . ./entrypoint.project.sh
fi
package="my_pipeline"
python="${PYTHON:-python3}"

build_package() {  # manual runs: the current source as a zip (python is always there, zip may not be)
    (cd src && "$python" -m zipfile -c "$1" "${package}")
}

if [ "${1:-}" = "--package" ]; then  # the wheel deployed with the pipeline (version: pyproject.toml)
    if [ ! -d "src/${package}" ]; then
        echo "package: no code yet in src/${package}" >&2
        exit 2
    fi
    mkdir -p dist
    rm -f dist/"${package}"-*.whl dist/ddl.zip
    # the standard build (a clean, isolated environment); offline, with the setuptools installed
    if ! "$python" -m pip wheel --quiet --no-deps . -w dist 2>/dev/null; then
        "$python" -m pip wheel --quiet --no-deps --no-build-isolation . -w dist
    fi
    ls dist/"${package}"-*.whl
    if ls ddl/*.sql >/dev/null 2>&1; then  # the table scripts, for a scheduler that ships archives
        (cd ddl && "$python" -m zipfile -c ../dist/ddl.zip ./*.sql)
        echo "dist/ddl.zip"
    fi
    exit 0
fi

if [ "${1:-}" = "--ddl" ]; then
    found=0
    for script in ddl/*.sql; do
        [ -e "$script" ] || continue
        found=1
        echo "ddl: ${script}"
        # shellcheck disable=SC2086 # SPARK_ARGS is meant to be split into options
        "${SPARK_SQL:-spark-sql}" --master "${SPARK_MASTER:-yarn}" ${SPARK_ARGS:-} -f "$script"
    done
    [ "$found" = 1 ] || echo "ddl: no ddl/*.sql script"
    exit 0
fi

entry="${1:-}"
main="src/${package}/p4_entrypoints/${entry}_main.py"

if [ -z "$entry" ] || [ ! -f "$main" ]; then
    echo "usage: ./entrypoint.sh <entry point> [arguments...] | --ddl | --package" >&2
    echo "entry points:" >&2
    for found in src/"${package}"/p4_entrypoints/*_main.py; do
        [ -e "$found" ] && basename "$found" _main.py | sed 's/^/  /' >&2
    done
    exit 2
fi
shift

build="$(mktemp -d)"
trap 'rm -rf "$build"' EXIT
build_package "${build}/${package}.zip"

status=0
# shellcheck disable=SC2086 # SPARK_ARGS is meant to be split into options
"${SPARK_SUBMIT:-spark-submit}" \
    --master "${SPARK_MASTER:-yarn}" \
    --deploy-mode "${DEPLOY_MODE:-client}" \
    --name "${package}-${entry}" \
    --py-files "${build}/${package}.zip" \
    ${SPARK_ARGS:-} \
    "$main" "$@" || status=$?
exit "$status"
