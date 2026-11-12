# ArchVow Marketplace CI/CD

The existing **CI** GitHub Actions workflow (`.github/workflows/ci.yml`) tests the CLI contract,
VS Code extension, IntelliJ plugin, and installable packages. After **all jobs pass**:

- A **push to `main`** publishes **stable** versions of both plugins.
- A **push to `beta`** publishes **beta/pre-release** versions of both plugins.
- A manual **Actions → CI → Run workflow** on the `beta` branch also publishes a beta
  (useful when retrying). A manual run on `main` or another branch only runs tests.
- Pull requests and other branches run CI but **never publish**.

Protect `main` with required PR reviews and CI checks. A direct push to `main` also publishes
if direct pushes are permitted. Keep `beta` synchronized with `main` before starting a beta
release series so it contains the version anchor commit. The beta branch is optional: create
it only when you want prerelease publishing.

## Start a beta release

```bash
git switch main
git pull --ff-only
git switch -c beta
git push -u origin beta
```

Subsequent pushes to `beta` automatically test, package and publish both plugins to their beta
channels. To promote tested changes, **merge the beta branch into `main` with a merge commit**
(do not squash that history). This ensures stable versions are higher than the beta versions
already built from the same commits. A stable publish runs automatically on the merge.

For VS Code, `vsce package --pre-release` sets pre-release metadata inside the VSIX and
`vsce publish --pre-release --oidc` publishes it as a pre-release. VS Code users select
**Switch to Pre-Release Version** from the extension's Manage menu.
For JetBrains, Gradle publishes into the Marketplace `beta` release channel. Users must add
`https://plugins.jetbrains.com/plugins/beta/list` under **Settings → Plugins → Manage Plugin
Repositories** to receive beta updates. Stable JetBrains users need not add a repository: a stable
release goes to the `default` channel (`intellij/build.gradle.kts`; an empty channel list would upload to
no channel and publish nothing, which `vscode/src/test/release.test.ts` refuses).

Both beta and stable publications use the same plugin identities, tests, source files and
CI signing/publishing secrets. No extra publisher account is required.

## One-time setup

1. In the [Visual Studio Marketplace publisher portal](https://marketplace.visualstudio.com/manage),
   create (or select) publisher `archvow` matching `vscode/package.json`. Create a **Trusted
   Publishing** policy for your GitHub repository and `.github/workflows/ci.yml`. This enables
   `vsce publish --oidc` without a Microsoft publishing token. CI runs the vsce locked in
   `vscode/package-lock.json` (`@vscode/vsce` 4.1.0, the first release with `--oidc`; it needs Node 22) with
   `npx --no-install vsce ...`: nothing is downloaded at publish time.
2. [Upload the first IntelliJ plugin version manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)
   to the JetBrains Marketplace under the existing plugin ID `org.archvow`. Automatic
   `publishPlugin` updates only work **after** that first manual upload. If 0.19.0 was never
   published, manually upload it first, or coordinate the initial version with the automated
   version used by the workflow.
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
5. Ensure the repository history is preserved from the attached Git bundle. Publishing uses
   `git rev-list` against the existing 0.19.0 commit; squashing/reinitializing the repository
   without that history requires updating the release version strategy.

## Versions

The source release in this bundle is **0.19.2**. Both marketplaces require a unique numeric
`major.minor.patch` version for each upload. VS Code does **not** support a `-beta` prerelease
suffix for Marketplace extension versions. The release script computes versions from Git
ancestry, reserving **odd patch versions for stable** and **even patch versions for beta**:

| Commits after ArchVow 0.19.0 | Stable (`main`) | Beta (`beta`) |
|---:|---:|---:|
| 1 | `0.19.1` (existing release) | `0.19.2` |
| 2 | `0.19.3` | `0.19.4` |
| 3 | `0.19.5` | `0.19.6` |
| 4 | `0.19.7` | `0.19.8` |

The count is **all commits reachable from the current release commit since the original
0.19.0 anchor** (`f87b75a522f019d58db4bed21270b88b079184f5`), not just first-parent
commits. A beta change based on a previous `main` commit can therefore publish a higher
beta version, and **merging that beta history into main using a merge commit** advances the
stable version past that beta. Squashing or cherry-picking instead of merging beta history,
rewriting release history, independently updating both release branches, or rapidly
publishing different refs can violate that ordering. If your release model needs squash
merges or independent concurrent releases, switch to a centralized, transactional version
allocator instead of this Git-history scheme before enabling those paths.

`.github/scripts/release-version.py --compute --channel stable|beta` computes the version;
`--apply VERSION --channel stable|beta` synchronizes `vscode/package.json`,
`vscode/package-lock.json`, `intellij/gradle.properties`, root README version references
and both changelog headings. **Only the CI checkout is modified**. No version commits or
tags are pushed to protected branches.

To preview versions locally from a checkout with full history:

```bash
python3 .github/scripts/release-version.py --compute --channel stable
python3 .github/scripts/release-version.py --compute --channel beta
```

To test the exact release from a disposable checkout:

```bash
CHANNEL=beta # or stable
VERSION=$(python3 .github/scripts/release-version.py --compute --channel "$CHANNEL")
python3 .github/scripts/release-version.py --apply "$VERSION" --channel "$CHANNEL"
./build.sh
```

For a local beta VSIX, repackage with `vsce package --pre-release` after `./build.sh` as CI
does. The JetBrains distribution is published to beta by the environment variable
`ARCHVOW_MARKETPLACE_CHANNEL=beta` during the Gradle publish task.

## Failures and retries

- Both publication commands run **after** the existing CI jobs and release-specific package tests.
- Beta and stable upload jobs share a GitHub Actions concurrency group to avoid simultaneous uploads.
- JetBrains credentials are checked **before** either plugin is published.
- VS Code publishes first with `--skip-duplicate`; if JetBrains fails, rerunning the failed
  workflow can skip the already-published VS Code release and retry JetBrains.
- If JetBrains has already accepted a version, manually verify that publication before
  rerunning `publishPlugin`, because JetBrains rejects duplicate versions.
- Each Marketplace may independently review or delay its public listing even after the upload succeeds.
- Updates to `main` or `beta` are not automatically cancelled during publication, but GitHub
  queues jobs that hold the global Marketplace publishing lock. Do not force-cancel an upload.

This workflow **does not** publish the separate ArchVow Python CLI package to PyPI; it publishes
only the two IDE integrations.
