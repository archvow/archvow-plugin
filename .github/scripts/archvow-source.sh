#!/usr/bin/env bash
# archvow's source, cloned into $1 (default /tmp/archvow) at the version the contract was recorded from:
# contract/cli/version.txt "archvow 1.17.0" -> tag v1.17.0. ARCHVOW_REF (a tag or a branch) overrides it.
#   ARCHVOW_GIT_URL    the archvow repository (repository variable; default: archvow next to this repository on GitHub)
#   ARCHVOW_GIT_TOKEN  a read-only token for it, when it is private (repository secret): a GitLab project
#                      access token (read_repository) or a GitHub fine-grained token (Contents: read)
set -euo pipefail
target="${1:-/tmp/archvow}"
ref="${ARCHVOW_REF:-v$(awk '{print $2}' contract/cli/version.txt)}"
url="${ARCHVOW_GIT_URL:-https://github.com/${GITHUB_REPOSITORY_OWNER}/archvow.git}"
echo "archvow ${ref} from ${url}"
if [ -n "${ARCHVOW_GIT_TOKEN:-}" ]; then
  url="${url/https:\/\//https://oauth2:${ARCHVOW_GIT_TOKEN}@}"
fi
rm -rf "$target"
git -c advice.detachedHead=false clone -q --depth 1 --branch "$ref" "$url" "$target" || {
  echo "::error::cannot clone archvow at ${ref}: tag the archvow release (git tag ${ref} && git push origin ${ref}), set the variable ARCHVOW_GIT_URL and, for a private repository, the secret ARCHVOW_GIT_TOKEN"
  exit 1
}
git -C "$target" log --oneline -1
