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
| FastAPI's own guidance (async, dependencies, response models, lifespan) | archvow `FAPI*` rules + ruff `FAST`, `ASYNC` |
| This project's architecture and safety spec | archvow `ARCH*` rules + `.archvow/` vows |

## The Zen of Python, made checkable

| Aphorism | What it means here | Enforced by |
|---|---|---|
| Beautiful is better than ugly. | One formatting, no debates. | `ruff format`, PEP 8 (`E`, `W`) |
| Explicit is better than implicit. | Declared routes; typed responses; Pydantic models for every input and output. | ruff `ANN`, `ARCH004`, `FAPI005`, `model` components |
| Simple is better than complex. | One operation per service; small functions. | ruff `C90`, `PLR`; `STY002` |
| Complex is better than complicated. | Split big operations into services and pure rules. | `STY001` |
| Flat is better than nested. | Few branches. | ruff `SIM`, `PLR0912` |
| Sparse is better than dense. | One component per module, one resource per router. | archvow structure |
| Readability counts. | PEP 8 naming. | ruff `N` |
| Errors should never pass silently. | No blind `except`; domain errors mapped to explicit HTTP statuses. | ruff `BLE`, `E722`; `FAPI002` |
| Unless explicitly silenced. | Suppressions must carry a reason. | `ARCH011` |
| In the face of ambiguity, refuse the temptation to guess. | Undeclared modules or symbols fail the build. | `ARCH001`, `ARCH002` |
| There should be one obvious way to do it. | Structure only via `archvow component add`; the database and other services only in `p2_io`; HTTP only in `p4_api` (and the dependencies of `p0_common`); one app. | `ARCH001`, `ARCH005`, `FAPI002`, `FAPI003` |
| Now is better than never. | Planned components are listed in build order; a release needs all of them. | `archvow next`, `check --complete` |
| If the implementation is hard to explain, it's a bad idea. | Every component needs a description; every public symbol a docstring. | `ARCH009`, ruff `D` |
| Namespaces are one honking great idea. | Layers are packages; no star imports. | `ARCH005`, ruff `F403` |

## FastAPI rules

- **The event loop**: `async def` only with async libraries (`httpx.AsyncClient`, an async
  database driver). A blocking call - `requests`, a synchronous driver, `time.sleep`, `open()` -
  stops every other request. A route (or dependency) that calls blocking code, directly or
  through a service, is itself a plain `def`: FastAPI runs those in a thread pool. A plain `def`
  helper called from an `async def` route still blocks. `FAPI001`, ruff `ASYNC`
- **HTTP lives in `p4_api`**: routers validate input, check who may call, call one service and
  shape the response; the `p0_common` dependencies may refuse a request too (401). Services and
  the domain raise domain errors (`OrderNotFoundError`); the app maps them to statuses with
  `app.add_exception_handler`. `FAPI002`
- **One app**: only the app component creates `FastAPI()`, includes the routers under a prefix
  (`/v1`) and owns startup and shutdown in its `lifespan` - never `on_event`. `FAPI003`, `FAPI004`
- **Declared routes**: every route is in the spec (`archvow component add router ... -r "name: METHOD
  /path"`); a route in the code that the spec does not declare, or with another method or path,
  fails. Each route declares what it returns (`response_model` or a return type). `ARCH002`,
  `ARCH004`, `FAPI005`
- **Dependencies**: `Annotated[Session, Depends(get_session)]`, declared in `p0_common`. ruff `FAST002`
- **Responses**: only the declared Pydantic models, never a database object; fields that must
  never leave the service are not in the response model.
- **Settings**: `get_settings()` (pydantic-settings) reads the environment; no secret in code,
  `.env` is gitignored. ruff `S`
- **Database**: the schema changes only through migrations in `migrations/`; the service owns the
  transaction, repositories never commit.
- **Size**: files at most 250 lines, functions at most 70 lines. `STY001`, `STY002`

## Layer rules

| Layer | May import | Database, network | HTTP | Notes |
|---|---|---|---|---|
| `p0_common` | - | session factory only | `Depends` providers | Settings, the database session, the current user |
| `p1_domain` | `p0_common` | no | no | Pydantic models and pure rules; no `fastapi`, `sqlalchemy`, `httpx`, `os` |
| `p2_io` | `p0_common`, `p1_domain` | yes | no | Repositories and clients; no business rules |
| `p3_services` | `p0_common`, `p1_domain`, `p2_io` | through `p2_io` | no | One operation each; owns the transaction |
| `p4_api` | `p0_common`, `p1_domain`, `p3_services` | no | yes | Routers and the app; never a repository directly |

## File header

Every component file starts with a header in its module docstring, given pre-filled by the
spec card. It records the component, spec and project, and who wrote the file with which AI
assistant (or `none`):

```python
"""Create an order and return it.

@component  create_order (service, p3_services)
@spec       US-002
@project    orders_api
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
| unit | models, pure rules, dependencies | plain objects in memory; no database, network or app; fast |
| integration | repositories, clients, services, routers, the app | the app through `TestClient`, a database in a temporary file, a fake transport; `@pytest.mark.integration` |
| end-to-end | scenarios spanning several components | `tests/integration/test_<scenario>.py`, any name |

Layer rules apply to the code, not to tests. Routes are tested through FastAPI's
`TestClient` with the app from the app component; repositories against a real database in a
temporary file (SQLite), never a shared one; clients against a fake transport
(`httpx.MockTransport`), never the real service. An integration test must be repeatable on any
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
   cannot happen, e.g. "a customer only ever reads their own orders" (`archvow invariant add`, `ARCH030`). Medium and low
   hazards may have one. Both are listed in `spec.md` (Hazards, Invariants).
3. **Test it.** Every invariant is tested by a test marked `@pytest.mark.invariant("create_order_service.INV1")`;
   for a critical hazard, a Hypothesis `@given` test that searches for counterexamples
   (`ARCH031`, `ARCH037`). Experts can ask for another check instead: a guard in the code marked
   `# archvow: enforces[create_order_service.INV1]` (it fails the request), an archvow rule, a model or a proof checked
   in CI.
4. **No silent decisions.** Each component starts with edge-case questions from its type
   (not found, invalid input, retries, permissions, partial failures...). Code for a component with a
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
| Put a file outside `src/<package>/<layer>/`, `tests/`, `docs/` or `migrations/` | `ARCH010`, `ARCH015` |
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
time.sleep(1)  # archvow: ignore[FAPI001] -- test helper, never on a request path
```

Project-wide changes go in `spec.vow`, under `rules`, and are reviewed like any other architecture
change:

```vow
rules
  STY001
    max_lines 300
```
