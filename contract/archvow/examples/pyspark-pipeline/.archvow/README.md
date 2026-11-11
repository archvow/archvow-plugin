<!-- archvow README - how this folder specifies and guards the project, for every developer and architect. Written by archvow init, do not edit. -->

# .archvow - how this project is specified and guarded

This folder is the project's specification and the rules that guard it. It is written by
`archvow` and protected by CODEOWNERS: change it with `archvow` commands, in a merge
request an architect approves - never by hand, never to make a check pass.

One rule to read it: **`spec.vow` is the spec** (what must be; archvow commands change it),
**`spec.md` is to read** (generated from the `spec.vow` next to it).

| File | What it is |
|---|---|
| `spec.vow` | The project's vow: mode, the families of checks that run, approved AI assistants, reviewers and packages, the project's own rules R1, R2... (`archvow rule add`), the third-party register |
| `spec.md` | The whole project on one page: architecture diagram, components, rules, hazards, invariants, personal data (each privacy regime's register), third parties (DORA), and every check of the families that are on |
| `components/<component>/spec.vow` | One component's vow: its file and public names, decisions, assumptions, columns, hazards (H1, H2...) and invariants (INV1...) |
| `components/<component>/spec.md` | Its spec card = the task given to a developer or an AI (`archvow prompt <component>`) |
| `proposals.vow` | Decisions the AI proposed, waiting for a person (modes guided and free; only while some wait) |
| `frozen.json` | Violations that existed before archvow was adopted (may only shrink) |
| `templates/` | The company template the project uses (when it is not a built-in one) |
| `status.md` | Local implementation status (not committed; `archvow docs`) |
| `README.md` | This file: how the rules work and why |

Everyday commands: `archvow show <component>` (everything about it), `archvow specify` (answer
what is open), `archvow next` (what to build, in order), `archvow prompt <component>`
(the task), `archvow check` (does the code follow the spec), `archvow diff --base develop`
(what a branch changes in the spec).

## The rules and where they come from

Four sources, each enforced by a tool rather than by memory:

| Source | Enforced by |
|---|---|
| PEP 8 - Style Guide for Python Code | ruff (`E`, `W`, `N`, `I`) + `ruff format` |
| PEP 20 - The Zen of Python | ruff rule families and archvow rules (mapping below) |
| Palantir PySpark Style Guide | archvow `SPK*` / `STY*` rules |
| This project's architecture and safety spec | archvow `ARCH*` rules + `.archvow/` vows |

## The Zen of Python, made checkable

| Aphorism | What it means here | Enforced by |
|---|---|---|
| Beautiful is better than ugly. | One formatting, no debates. | `ruff format`, PEP 8 (`E`, `W`) |
| Explicit is better than implicit. | Typed signatures declared in the vow; `how=` on joins; explicit window frames; explicit schemas. | ruff `ANN`, `ARCH004`, `SPK004`, `SPK010`, `schema` components |
| Simple is better than complex. | Native functions over UDFs; small functions. | ruff `C90`, `PLR`; `SPK006`, `STY002` |
| Complex is better than complicated. | Split multi-source transforms into named sub-steps. | `SPK009`, `STY001` |
| Flat is better than nested. | Few branches, short chains. | ruff `SIM`, `PLR0912`; `SPK009` |
| Sparse is better than dense. | One component per module; chains of at most 5 calls. | archvow scaffolding; `SPK009` |
| Readability counts. | PEP 8 naming; parentheses instead of `\`. | ruff `N`; `SPK008` |
| Errors should never pass silently. | No blind `except`; no `''`/`'NA'` placeholders hiding nulls. | ruff `BLE`, `E722`; `SPK012` |
| Unless explicitly silenced. | Suppressions must carry a reason. | `ARCH011` |
| In the face of ambiguity, refuse the temptation to guess. | Undeclared modules or symbols fail the build. | `ARCH001`, `ARCH002` |
| There should be one obvious way to do it. | Structure only via `archvow component add`; I/O only in `p2_io`; sessions only in `p0_common`. | `ARCH001`, `SPK001`, `SPK003` |
| Now is better than never. | Planned components are listed in build order; a release needs all of them. | `archvow next`, `check --complete` |
| If the implementation is hard to explain, it's a bad idea. | Every component needs a description; every public symbol a docstring. | `ARCH009`, ruff `D` |
| Namespaces are one honking great idea. | Layers are packages; `F.`, `T.`, `W.` prefixes; no star imports. | `ARCH005`, `SPK007`, ruff `F403` |

## PySpark rules (from the Palantir PySpark Style Guide)

- **Imports**: `from pyspark.sql import functions as F, types as T, Window as W`. Never import
  names directly from `pyspark.sql.functions` (shadows `sum`, `max`, ...). `SPK007`
- **Columns**: prefer `F.col("a")` or plain strings over `df.a` / `df["a"]`, except to
  disambiguate joins. (Review rule; not statically detectable.)
- **Schema contracts**: start or end a transformation with a `select` that states the output
  columns; prefer `F.col(...).alias(...)` over `withColumnRenamed`. `SPK013`
- **Empty columns**: `F.lit(None)`, never `''` or `'NA'`. `SPK012`
- **Joins**: always `how=` as a keyword, even for inner joins; never right joins; do not use
  `distinct()` / `dropDuplicates()` to hide a join explosion. `SPK004`, `SPK005`, `SPK014`
- **Windows**: always an explicit frame (`rowsBetween` / `rangeBetween`); never an empty
  `partitionBy()`. `SPK010`, `SPK011`
- **Chaining**: at most 5 chained calls, one kind of operation per block, parentheses for
  multi-line expressions. `SPK008`, `SPK009`
- **UDFs**: avoid; native functions are much faster. `SPK006`
- **Size**: files at most 250 lines, functions at most 70 lines. `STY001`, `STY002`
- **Comments**: explain *why*, not *what*; no commented-out code. ruff `ERA`
- **Literals**: name magic values as constants. ruff `PLR2004`

## Layer rules

| Layer | May import | I/O | Actions | Notes |
|---|---|---|---|---|
| `p0_common` | - | no | - | Only place that builds a SparkSession |
| `p1_domain` | `p0_common` | no | no (lazy only) | No `os`, `pathlib`, `pandas`, `requests`, ... |
| `p2_io` | `p0_common`, `p1_domain` | yes | allowed | Enforce schemas when reading |
| `p3_jobs` | `p0_common`, `p1_domain`, `p2_io` | no | allowed | Wiring only |
| `p4_entrypoints` | `p0_common`, `p3_jobs` | no | - | Argument parsing, exit codes |

## File header

Every component file starts with a header in its module docstring, given pre-filled by the
spec card. It records the component, spec and project, and who wrote the file with which AI
assistant (or `none`):

```python
"""Keep only the valid input rows.

@component  clean_sales (transformation, p1_domain)
@spec       US-002
@project    my_pipeline
@author     A. Developer
@assisted   claude-code
@date       2026-10-06
"""
```

`archvow check` fails (`ARCH018`) if it is missing, still contains a `<...>` placeholder, names
another component, spec or project, or names an AI assistant that is not approved.

## Tests

Every component type declares the test it needs; an implemented component without
`tests/<layer>/[<group>/]test_<component>.py` fails the build (`ARCH017`). The spec card lists what to cover:
one test per decision, and the test of each invariant.

| Kind | For | What it does |
|---|---|---|
| unit | schemas, transformations, helpers, entry points | small DataFrames in memory; no files, tables or network; fast |
| integration | readers, writers, jobs | real files or tables in a temporary folder; `@pytest.mark.integration` |
| end-to-end | scenarios spanning several components | `tests/integration/test_<scenario>.py`, any name |

Layer rules (no I/O, no actions) apply to the code, not to tests: tests may read, write and
create tables. Full tests against a **local Hive metastore** (Derby) are welcome, kept safe:

- only in integration tests, so `pytest -m "not integration"` stays fast;
- everything in a temporary folder: `spark.sql.warehouse.dir` and Derby's home
  (`-Dderby.system.home=...` via `spark.driver.extraJavaOptions`), so nothing leaks into the
  repository (`metastore_db/`, `derby.log` and `spark-warehouse/` are gitignored anyway);
- one SparkSession for the whole test run (a session-scoped fixture): embedded Derby accepts a
  single connection, so do not run these tests in parallel processes;
- never a real metastore, bucket or database: an integration test must be repeatable on any
  laptop and in CI.

The test settings (`[tool.pytest.ini_options]` in `pyproject.toml`, or `pytest.ini`) register
the markers strictly (`--strict-markers`): a mistyped `@pytest.mark.invarient(...)` fails
instead of silently giving no evidence. Changing them is a guard change (`ARCH020`).

## What must never happen (safety)

The spec is most useful where it says what must **not** happen. archvow follows the
practice of critical systems: find the hazards systematically, keep the essential
properties few, and verify those without compromise.

1. **Ask the safety questions.** `archvow specify <component>` asks, as plain questions
   numbered S1, S2..., what its type calls for (input missing, too many rows, wrong values...). Each one
   ends as a **hazard** - what must never happen, recorded high by `specify`, with how it is
   prevented, its invariant (another severity: `archvow hazard add <component> S<number>
   <severity>`, the suggestion is taken when no text is given) - or "not applicable" with a
   reason (`archvow hazard dismiss <component> S<number> "..."`). Unanswered questions are
   warnings (`ARCH035`). Severities: critical = data lost or wrong results that cannot be undone;
   high = wrong results reach users; medium = a delay or a re-run; low = noticed and fixed
   without harm.
2. **Prevent each critical/high hazard with an invariant** - what is always true, so the hazard
   cannot happen, e.g. "order_id is never duplicated in the output" (`archvow invariant add`, `ARCH030`). Medium and low
   hazards may have one. Both are listed in `spec.md` (Hazards, Invariants).
3. **Test it.** Every invariant is tested by a test marked `@pytest.mark.invariant("clean_sales.INV1")`;
   for a critical hazard, a Hypothesis `@given` test that searches for counterexamples
   (`ARCH031`, `ARCH037`). Experts can ask for another check instead: a guard in the code marked
   `# archvow: enforces[clean_sales.INV1]` (it fails the job), an archvow rule, a model or a proof checked
   in CI.
4. **No silent decisions.** Each component starts with edge-case questions from its type
   (empty input, nulls, duplicates, partial writes, re-runs...). Code for a component with a
   decision still to make fails (`ARCH033`); so does code relying on an assumption nobody
   reviewed yet (`ARCH034`); a rejected one no longer blocks, the code must just not rely on it.

Removing a hazard, lowering its severity, removing an invariant or weakening its test
counts as weakening a guard (`ARCH020` with `check --base`).

## Personal data, test data, DORA (compliance)

The `rules` answer of `init` chooses the rule families: GDPR and TST are on by default; DORA and the
US privacy regimes (US-PRIVACY, GLBA) on request (`archvow init <package> --force -a rules=all`, or a list
such as `-a rules=ARCH,GDPR,US-PRIVACY,DORA`).

- **Personal data (PRIV, with every privacy regime)** - every column of a schema or model is classified:
  `archvow column set <component> <columns> personal|special|none` (special = health, religion,
  biometrics...: GDPR art. 9, US sensitive personal information), with tags a regime may need
  (`--tag financial`, `health`, `biometric`, `children`, `precise-location`). Code with an unclassified
  column fails (`PRIV001`). archvow follows the columns through the `uses` of the spec: a component
  that handles personal data never shows, prints or logs rows (`PRIV002`), never partitions files by
  them (`PRIV004`), never puts them in a URL (`PRIV005`). Special data written in clear needs a
  critical `exposed` hazard and its tested invariant, unless a step pseudonymises it (`archvow column
  set <step> <columns> --pseudonymises`, `PRIV003`).
- **The privacy regimes** - each asks its own questions where personal data is processed, stored or
  sent, and fails while one is open on coded code: GDPR P1, P2... (purpose and legal basis `GDPR002`,
  retention `GDPR003`, location and recipients `GDPR008`), US-PRIVACY U1, U2..., GLBA G1, G2... (the
  columns tagged `financial`): `archvow decision make <component> P1 "..."`. `spec.md` sums up each
  regime's register (GDPR: the record of processing, art. 30).
- **TST** - tests use invented data: nothing read outside `tests/` (`TST001`), no real-looking
  e-mail, IBAN, card, social security or phone number (`TST002`), no binary extract and no big
  data file (`TST003`).
- **DORA** - every component has a ticket and every commit names one (`DORA001`, commit-msg
  hook); two people at least can approve guard changes (`DORA002`); every package of
  requirements.txt is in the third-party register, critical ones pinned (`DORA003`,
  `archvow dependency add`); the tests of invariants cannot be skipped and no CI job may fail
  silently (`DORA004`; that they ran and passed: `ARCH046`); jobs answer O1, O2 - failure detection, recovery (`DORA005`).
  `archvow changes --from <last release>` lists what a release contains, merge by merge.

A company template adds its own rules as data (`custom_rules`: forbidden imports, calls, text,
files, or a question per component type); each family gets its section in `spec.md` (Checks).

## AI assistants: a policy, not a lock

`archvow init -a ai_tools=...` records which assistants are approved (`ai_tools` in `spec.vow`,
protected). The list is the project's **policy**: decided by the architects, visible in the
repository, traced in every file header, and checked at merge time. It is not a lock - no
repository can see which tool typed the code. It works the same in every editor (VS Code,
JetBrains IDEs, others). Three levels, from strongest to weakest:

| Level | Mechanism | Strength |
|---|---|---|
| Restrict | `.claude/settings.json` denies Claude Code edits to guard files and `git commit --no-verify` | real for that tool's file-edit tools; not a sandbox |
| Detect | `ARCH040`: configuration files of unapproved assistants (`.cursor/`, `CLAUDE.md`, `.junie/`, `GEMINI.md`...) fail the build; approving a new tool is `ARCH020` with `--base` | after the fact |
| Ask | `AGENTS.md` tells any assistant that reads it and is not approved to stop | relies on the tool |

What a repository cannot do: stop someone from opening it with any assistant installed on
their machine, in any editor. Company-wide blocking happens outside the repository - managed IDE
installations with a plugin allow-list, the AI provider's organisation policies (which
models, which seats, excluded paths), and network or proxy rules that only allow the approved
AI endpoints. The repository then makes the policy visible, enforced at merge time, and
audited.

## Working is not enough: what fails the build

| What the AI (or anyone) did | Rule |
|---|---|
| Created a file or public symbol the spec does not declare | `ARCH001`, `ARCH002` |
| Used other names or signatures than the spec | `ARCH004`, `ARCH014` |
| Put a file outside `src/<package>/<layer>/`, `tests/` or `docs/` | `ARCH010`, `ARCH015` |
| Named a test file anything but `tests/<layer>/test_<component>.py` (or `tests/integration/`) | `ARCH016` |
| Implemented a component without its test | `ARCH017` |
| Left out the file header, a placeholder, or named an unapproved AI assistant in it | `ARCH018` |
| Imported across layers or used an undeclared component | `ARCH005`, `ARCH006` |
| Implemented while a decision was still to make, or on an assumption nobody reviewed | `ARCH033`, `ARCH034` |
| Implemented while the component's invariants are not tested | `ARCH031`, `ARCH032` |
| Lowered a rule, relaxed ruff, deleted a test, edited a guard file, removed a hazard or invariant (vs `main`) | `ARCH020` |
| Added `# noqa`, `# type: ignore` or `# archvow: ignore` (vs `main`) | `ARCH021` |

Planned components that do not exist yet are not errors; `archvow check --complete` makes
them errors for a release gate.

## Silencing a rule

Only on one line, only with a reason (at least a few words):

```python
df = df.dropDuplicates(["id"])  # archvow: ignore[SPK014] -- by design
```

Project-wide changes go in `spec.vow`, under `rules`, and are reviewed like any other architecture
change:

```vow
rules
  SPK009
    max_chain 4
```
