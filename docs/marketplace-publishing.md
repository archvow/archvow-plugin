# ArchVow Marketplace CI/CD

The **CI** GitHub Actions workflow (`.github/workflows/ci.yml`) tests the CLI contract, the VS Code extension,
the IntelliJ plugin and the installable packages on every push and pull request. **It publishes only from a tag**
(1.1.0; before, every push to `main` or `beta` published, with a version computed from the commit count):

- a tag **`vMAJOR.MINOR.PATCH`** (`v1.1.0`) publishes the **stable** release of both plugins - the tag must be a
  commit of `main`;
- a tag **`vMAJOR.MINOR.PATCH-beta`** (`v1.2.0-beta`) publishes a **pre-release** (VS Code's pre-release flag,
  JetBrains' `beta` channel) with the version `1.2.0`;
- a push to a branch, a pull request or a manual run only tests.

Before anything is uploaded, `.github/scripts/release-version.py --check-tag <tag>` checks that the tag names the
version every release file says - `vscode/package.json`, `intellij/gradle.properties` and the newest entry of both
changelogs - and refuses otherwise: nothing is computed and nothing is rewritten in CI.

## Make a release

```bash
# on a branch: set the version (vscode/package.json and its lock, intellij/gradle.properties, the README's
# Versions, both CHANGELOG.md), with what changed; npm test checks they all say it
git switch main && git pull --ff-only            # once the release is merged
git tag v1.1.0 && git push origin v1.1.0          # CI tests, packages and publishes 1.1.0
```

A pre-release: the same, with a version no stable release uses, tagged `v1.2.0-beta` (from `main` or from a
branch). The stable release that follows uses a higher version (`v1.2.0` after `v1.2.0-beta` is refused by VS
Code's Marketplace: the numbers are already taken; publish `v1.2.1` or `v1.3.0`).

For JetBrains, Gradle publishes a pre-release into the Marketplace `beta` channel. Users must add
`https://plugins.jetbrains.com/plugins/beta/list` under **Settings → Plugins → Manage Plugin Repositories** to
receive it; stable users need nothing.

## One-time setup

1. In the [Visual Studio Marketplace publisher portal](https://marketplace.visualstudio.com/manage),
   create (or select) publisher `archvow` matching `vscode/package.json`. Create a **Trusted
   Publishing** policy for your GitHub repository and `.github/workflows/ci.yml`. This enables
   `vsce publish --oidc` without a Microsoft publishing token. CI runs the vsce locked in
   `vscode/package-lock.json` (`@vscode/vsce` 4.1.0, the first release with `--oidc`; it needs Node 22) with
   `npx --no-install vsce ...`: nothing is downloaded at publish time.
2. [Upload the first IntelliJ plugin version manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)
   to the JetBrains Marketplace under the existing plugin ID `org.archvow`. Automatic
   `publishPlugin` updates only work **after** that first manual upload.
3. Create a JetBrains Marketplace publishing token and a plugin signing key/certificate using
   the [JetBrains signing instructions](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).
   Add four **GitHub Actions repository secrets**:

   | Secret | Value |
   | --- | --- |
   | `JETBRAINS_PUBLISH_TOKEN` | JetBrains Marketplace publishing token |
   | `JETBRAINS_PRIVATE_KEY` | Plugin signing private key (PEM) |
   | `JETBRAINS_PRIVATE_KEY_PASSWORD` | Signing key password |
   | `JETBRAINS_CERTIFICATE_CHAIN` | Signing certificate chain (PEM) |

   These map to the standard `PUBLISH_TOKEN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, and
   `CERTIFICATE_CHAIN` values that IntelliJ Platform Gradle Plugin 2.19 already supports.
   Do **not** commit these secrets or the certificate private key.
4. Review Marketplace listing details and the existing `UNLICENSED` licensing declaration;
   decide the appropriate distribution license/terms before submitting. Check that the
   JetBrains plugin ID, publisher ID and listing ownership belong to your accounts.

## Versions

The plugins have their own versioning (1.0.0): MAJOR.MINOR.PATCH, chosen by people, the same for both plugins. A
release says the archvow versions it works with (`contract/archvow-range.txt`, `>=3.2,<4` for 1.2.0). Both
Marketplaces need a unique numeric version for each upload, higher than the one installed for the update to reach
people; the tag check and the release test keep the version in one place per file.

## Failures and retries

- Both publication commands run **after** the existing CI jobs and release-specific package tests.
- Beta and stable upload jobs share a GitHub Actions concurrency group to avoid simultaneous uploads.
- JetBrains credentials are checked **before** either plugin is published.
- VS Code publishes first with `--skip-duplicate`; if JetBrains fails, rerunning the failed
  workflow can skip the already-published VS Code release and retry JetBrains.
- If JetBrains has already accepted a version, manually verify that publication before
  rerunning `publishPlugin`, because JetBrains rejects duplicate versions.
- Each Marketplace may independently review or delay its public listing even after the upload succeeds.
- A tag's run is never cancelled by a later push, and GitHub queues the jobs that hold the global Marketplace
  publishing lock. Do not force-cancel an upload. To retry, re-run the failed jobs of the tag's workflow run - never
  move a published tag.

This workflow **does not** publish the separate ArchVow Python CLI package to PyPI; it publishes
only the two IDE integrations.
