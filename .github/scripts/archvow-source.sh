#!/usr/bin/env bash
# archvow's source, cloned into $1 (default /tmp/archvow) for the archvow canary (archvow-canary.yml),
# at ARCHVOW_REF (a branch or a tag; default: the tag of contract/cli/version.txt, v1.17.0).
#   ARCHVOW_GIT_URL    the archvow repository (repository variable, required)
#   ARCHVOW_GIT_TOKEN  a read-only token for it, when it is private (repository secret): a GitLab project
#                      access token (read_repository) or a GitHub fine-grained token (Contents: read)
set -euo pipefail
target="${1:-/tmp/archvow}"
ref="${ARCHVOW_REF:-v$(awk '{print $2}' contract/cli/version.txt)}"
url="${ARCHVOW_GIT_URL:?set the repository variable ARCHVOW_GIT_URL to archvow's repository}"
echo "archvow ${ref} from ${url}"
if [ -n "${ARCHVOW_GIT_TOKEN:-}" ]; then
  url="${url/https:\/\//https://oauth2:${ARCHVOW_GIT_TOKEN}@}"
fi
rm -rf "$target"
git -c advice.detachedHead=false clone -q --depth 1 --branch "$ref" "$url" "$target" || {
  echo "::error::cannot clone ${ARCHVOW_GIT_URL} at ${ref}: check the variable ARCHVOW_GIT_URL, the branch or tag, and, for a private repository, the secret ARCHVOW_GIT_TOKEN (read-only)"
  exit 1
}
git -C "$target" log --oneline -1
