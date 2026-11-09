# invoicing

<!-- archvow:begin -->
<!-- Added by archvow init: `archvow init --force` rewrites only what is between these two markers; write your own text outside them. -->

Marks invoices paid, from the command line

A **Python application**, specified and guarded with [archvow](.archvow/README.md)
(template `python-app@1.25.0`).

## For developers

Everything you need every day, on one page. The spec of each component is written and
approved by the architects (@acme/architects); you write the code from it.

**Once, after cloning** - Python 3.12 or newer:

```bash
pip install -r requirements.txt            # the package (editable), archvow, ruff, mypy, pytest...
git config core.hooksPath .githooks        # the checks before every commit
archvow doctor                             # checks your setup, with the fix for each problem
make help                                  # the usual commands: make check, make test, make run...
```

**Every day**

| Step | Do |
|---|---|
| What to build | `archvow next` - the planned components in build order, *ready* or what each waits for |
| Your task | the spec card `.archvow/components/<component>/spec.md`: the file, its public names, the decisions made, the tests, what must never happen |
| Write it | yourself, starting the file with the card's header (`@assisted none`) - or with the AI assistant, one line in its chat: `implement <component>` |
| Something is unclear | do not guess: `archvow question add <component> "<question>"`, an architect decides |
| Commit | `git commit` - the Git hook runs ruff and `archvow check`; `make test` runs the tests |

**Good to know**

- Private helpers (`_parse_line`, `_Row`) are free. Anything public must be in the spec card,
  or the build fails (`ARCH001`, `ARCH002`).
- Files, databases and other services are reached only from `p2_io`; the domain is pure. The
  domain and the services raise domain errors; the command line turns them into a message and
  an exit code.
- Every function is typed: `make check` runs mypy (settings in `pyproject.toml`).
- One test per component: `tests/<layer>/test_<component>.py`; end-to-end scenarios go in
  `tests/integration/`.
- Every error says how to fix it; the rules are in [`.archvow/spec.md`](.archvow/spec.md) (Checks)
  and explained in [`.archvow/README.md`](.archvow/README.md).
- `.archvow/` changes only through archvow commands, approved by the architects - never
  by hand.
- AI assistants approved for this project: claude-code. Their rules: [`AGENTS.md`](AGENTS.md).

The rest of this README is for the architects and for reference: how the service is
specified, step by step, and how it runs.

## Architecture

Code lives in `src/invoicing/`, in five layers (the *src layout*: the tests always import
the installed package). A layer may only import the layers listed next to it: the business rules
never depend on files, databases or the command line, and the command line never reaches a file
or a database without a service.

| Layer | Responsibility | May import |
|---|---|---|
| `p0_common` | Shared foundations - settings, logging, small helpers every layer may use | - |
| `p1_domain` | Business rules - data classes, domain errors and pure functions, no I/O | p0_common |
| `p2_io` | Repositories and clients - the only place that reads and writes files, databases and other services | p0_common, p1_domain |
| `p3_services` | Services - one operation each, calling repositories and clients and applying the domain's rules | p0_common, p1_domain, p2_io |
| `p4_entrypoints` | Entry points - the command line, which parses arguments, calls a service and sets the exit code | p0_common, p1_domain, p3_services |

The full specification - every component, its public functions, the decided edge
cases and what must never happen - is in [`.archvow/`](.archvow/README.md):

- [The project's spec](.archvow/spec.md) - the whole project on one page: diagram, components, rules, hazards, invariants, personal data, checks
- [Each component's spec](.archvow/components/) - `components/<component>/spec.md`, its task; its vow `spec.vow` next to it

## Project layout

Right after `archvow init` - the spec and the guards, no code yet:

```
invoicing/
├── .archvow/                           the spec: spec.vow, spec.md, components/<component>/, README.md
├── AGENTS.md                           rules for every AI assistant
├── .gitlab-ci.yml                      CI: guards and tests
├── .gitlab/CODEOWNERS                  the architects approve the spec and the guards
├── .githooks/pre-commit                checks before every commit
├── pyproject.toml                      the package, what it needs at run time, pytest and mypy settings
├── README.md
├── requirements.txt                    development tools
├── ruff.toml                           style and security rules
└── .gitignore
```

When development is done - every component written from its spec card, with its test:

```
invoicing/
├── .archvow/
│   ├── spec.vow                        the project's vow
│   ├── spec.md                         the whole project on one page
│   └── components/<component>/         spec.vow (its spec) + spec.md (its task)
├── src/
│   └── invoicing/
│       ├── p0_common/                  config.py, logging_setup.py, small helpers
│       ├── p1_domain/                  invoice_model.py, invoice_not_found_error.py, compute_total.py
│       ├── p2_io/                      invoices_repository.py, mailer_client.py
│       ├── p3_services/                send_invoice.py, list_invoices.py
│       └── p4_entrypoints/             invoices_cli.py (main)
├── tests/
│   ├── <layer>/test_<component>.py     one test per component
│   └── integration/                    end to end, through the command line
└── ...                                 the files of the first tree
```

Many components in one layer? Group them in folders: `archvow component add service create_invoice -g
billing` writes `p3_services/billing/create_invoice.py`, tested in
`tests/p3_services/billing/test_create_invoice.py`.

## Building the application, step by step

Each step below shows **one example**; do the same for every component. People write the spec
(steps 1-4); then the code is written from it, by a person or an AI assistant (step 5); the same
checks judge it (step 6). Every command and option, with examples: `archvow --help`; a
complete application, from the empty project to a run: archvow's documentation, *Python template*.

**1. Create the project** - this one was created with:

```bash
archvow init invoicing -t python-app -d 'Marks invoices paid, from the command line' \
    -a language=en -a python_version=3.12 -a line_length=88 -a platform=gitlab -a ci=auto -a architects=@acme/architects -a sonar=no -a auto_approve=yes -a sandbox=yes -a briefs=briefs -a git_hooks=githooks -a branch_naming=gitflow -a ai_tools=claude-code -a mode=strict -a rules=ARCH,STYLE,GDPR,TST
pip install -r requirements.txt
git add -A && git commit -m "archvow init"          # the only commit on main
git switch -c develop                            # once, from main
git switch -c feature/US-001-invoices develop   # one branch per change, from develop
```

Branches follow gitflow: work on `feature/<ticket>-<short-name>` from `develop`, merged into
`develop` through a merge request; the Git hook refuses commits on `main` and `develop`, and the
hook and the CI refuse other branch names. Releases and hotfixes:
archvow's documentation, *Daily workflow*.

`init` writes the spec folder `.archvow/`, the guards (AGENTS.md, ruff.toml, the test
settings, CI, CODEOWNERS, the Git hook) and two starting components, `config` and `logging_setup`.
It never writes
source code, and runs once: developers clone the repository instead.

**2. Declare the components** - each one's type, a name, and what it must do; here the whole
chain, from the data to the entry point:

```bash
archvow component add model invoice -d "An invoice: number str, customer str, amount Decimal >= 0, status draft | sent | paid" -s US-001
archvow component add service send_invoice -d "Refuse an invoice that is not a draft, mark it sent, return it" -u invoice_model -s US-001
archvow component add cli invoices -d "Command line: send NUMBER - sends the invoice and prints it" -u send_invoice -s US-001
```

The type fixes the structure: the file, the public class or function, the layer, the test file;
it turns the name into the component id (`invoice` -> `invoice_model`). **The description (`-d`) is
what the AI assistant - or the developer - implements**: write the real fields, rules and
statuses. Types (utility, model, error, logic, repository, client, service, cli):
`archvow component add --help`. The AI assistant can propose these commands for a whole feature:
`/archvow-draft-spec <the feature in plain words>`.

The command line (`invoices_cli`, its `main(argv) -> int`) is the entry point: it makes the
program runnable (`python -m invoicing.p4_entrypoints.invoices_cli`). Until it and everything
it uses are coded, `archvow next` ends with *Not ready to run yet* and what is missing; then with
*Try it locally* and the command.

**3. Answer its questions** - each type asks what must not be guessed:

```bash
archvow show invoice_model
archvow decision make invoice_model 2 "amount >= 0 with 2 decimals; status draft, sent or paid"
archvow rule add invoice_model "Given an amount with 3 decimals, when an invoice is built, then it is refused"
```

`show` lists everything about the component: its numbered questions (red ✖ open, green ✔
answered), its rules and its safety questions S1, S2... `decision make` answers question 2 (with no answer, it takes the one `show` suggests); `rule add`
adds a rule the questions do not cover (numbered R1, R2... for the project; `--type model` for
every model, `--all` for every component; one test each). In a terminal, `archvow specify
invoice_model` asks what is open, one question at a time. Nothing is decided silently: code
written while a question is open fails the build.

Personal data (GDPR): every column of a model is classified - `archvow column set invoice_model
email,phone personal` (`special` for health, religion, biometrics..., `none` otherwise); code
with an unclassified column fails the build. A service that handles personal data then answers
P1, P2 (purpose, legal basis) and a repository P1, P2 (retention, location):
`archvow decision make <component> P1 "..."`. The result: `.archvow/spec.md` (Personal data).

**4. Say what must never happen, then commit the spec**

```bash
archvow hazard add invoice_model S1 high "An invoice is saved with a negative amount"
archvow invariant add invoice_model "every invoice has an amount of zero or more"
archvow docs --check
git add .archvow && git commit -m "US-001: specify invoice_model"
```

Each safety question (S1, S2... in `archvow show <component>`) ends as a hazard, given by its number, or a dismissal with its reason
(`archvow hazard dismiss <component> S<number> "..."`). The invariant says what is always true
so the hazard cannot happen; by default it guards the component's hazard and is proven by a test.

**5. Write the code** - `archvow next` gives the order, and what each component waits for:

```bash
archvow next
```

```
  component                    type            status
✖ config                       foundation      blocked: 2 decisions to make  -> archvow specify config
✖ logging_setup                foundation      blocked: 1 decision to make  -> archvow specify logging_setup
✖ invoice_model                model           blocked: 3 decisions to make  -> archvow specify invoice_model
✖ send_invoice                 service         blocked: 4 decisions to make  -> archvow specify send_invoice
✖ invoices_cli                 cli             blocked: 3 decisions to make  -> archvow specify invoices_cli

Safety: 1 of 9 questions answered  -> archvow specify

! Not ready to run yet: invoices_cli needs config (planned), logging_setup (planned), invoice_model (planned), send_invoice (planned), invoices_cli (planned)
```

*blocked*: questions are still open; answer them as in step 3 and the component becomes
*ready*. Then, in the AI assistant's chat (agent mode, in the project folder):

```
implement invoice_model
implement everything archvow next lists as ready
```

`AGENTS.md` tells every approved assistant (claude-code) what "implement" means:
read the spec card, write the file with its header and its tests, run the checks, fix, and
report anything it had to assume.

*Yourself*: the spec card `.archvow/components/<component>/spec.md` is your task; start the file with
its header, `@assisted none`. Commit each component; the Git hook runs the checks first. If the
assistant had to assume something, the commit is refused until a person reviews it:
`archvow assumption accept invoice_model 1` (or `archvow assumption reject invoice_model 1`).

**6. Verify and run**

```bash
archvow check --complete                             # release gate: everything planned is built
pytest --cov=src --cov-report=term-missing           # every test, with coverage (as CI does)
make run CLI=invoices ARGS="--help"                   # the command line of the invoices_cli component
```

## One example per command

| To... | For example |
|---|---|
| declare the command line | `archvow component add cli invoices -d "send NUMBER, list [--status]" -u send_invoice` |
| change a component (description, dependencies) | `archvow component edit invoice_model -d "An invoice: number str, customer str, amount Decimal >= 0"` |
| remove a component | `archvow component remove invoice_model` (then delete its file and test) |
| remove a hazard, an invariant or a rule | `archvow hazard remove invoice_model.H1`, `archvow invariant remove invoice_model.INV1`, `archvow rule remove R1` (each says what goes, asks first) |
| see one hazard, invariant or rule | `archvow show invoice_model.INV1` |
| talk to another system | `archvow component add client mailer -d "Send an e-mail through the company's SMTP relay"` |
| add a domain error | `archvow component add error invoice_not_found -d "No invoice with this number"` |
| add a pure business rule | `archvow component add logic compute_total -d "Sum of the lines" --signature "(lines: list[Line]) -> Decimal"` |
| ask a question, as a developer | `archvow question add invoice_model "May an invoice have an amount of zero?"` |
| let the AI find what is unclear | `archvow prompt invoice_model --clarify` (in the chat: `/archvow-clarify invoice_model`) |
| add a rule for every component of a type | `archvow rule add --type model "Given an unknown field, when it is parsed, then it is refused"` |
| dismiss a safety question that does not apply | `archvow hazard dismiss invoice_model S2 "invoices hold no personal data"` |
| see everything about a component | `archvow show invoice_model` |
| record a business example (files in `tests/examples/invoices_cli/<example>/`) | `archvow example add invoices_cli US-001-pay-a-sent-invoice -s US-001 --from briefs/<brief>.md#<section>`; `archvow show invoices_cli` gives its test's verdict and where `actual/` differs from `expected/` |
| find a name everywhere (a function, a column, a ticket, an example) | `archvow show <name>`: the spec, the code, the tests, the briefs, the problems naming it |
| answer what is open, one question at a time | `archvow specify invoice_model` (in a terminal) |
| let the AI propose the risks of a component | in the chat: `/archvow-analyze invoice_model` |
| build a big component one task at a time | `archvow show send_invoice` lists its tasks T1, T2...; `archvow prompt send_invoice` gives the next one; mark each test: `@pytest.mark.decision(3)` |
| draw the spec from a brief (`briefs/`, Markdown; another folder: `archvow init invoicing --force -a briefs=...`) | in the chat: `/archvow-draft-spec briefs/invoices.md`; after the brief changes (ARCH039): `archvow brief accept briefs/invoices.md#sending` |
| classify columns (GDPR) | `archvow column set invoice_model email,phone personal` |
| say a step removes or pseudonymises them | `archvow column set send_invoice phone --removes` |
| list what a release contains | `archvow changes --from v1.0.0 --markdown` |
| adopt archvow on existing code | `archvow freeze` |
| check my setup (Python, tools, Git hook) | `archvow doctor` |
| see every command and option | `archvow --help`, then `archvow <command> --help` |

A new third-party package or AI assistant is approved by an architect, with `archvow init
invoicing --force -a dependencies=...` (or `ai_tools=...`). Details and reasons:
[`.archvow/README.md`](.archvow/README.md).



## Tests

Every component has its test, `tests/<layer>/test_<component>.py`; an implemented component
without it fails the build. Its spec card says which kind and what to cover.

- **Unit** (models, errors, pure rules, helpers, the command line): plain objects, fast; the
  command line is tested by calling `main([...])` and checking its exit code and output.
- **Integration** (repositories, clients, services): real files in pytest's `tmp_path`, a database
  in a temporary file, a fake transport for other systems; marked `@pytest.mark.integration`.

```bash
pytest -m "not integration"                # fast feedback while coding
pytest                                     # everything
make check                                 # ruff, mypy, archvow check, specs up to date
```

A rule that must hold for every input is best proven with a property test: Hypothesis generates
the inputs (`@given`), and the test checks the invariant on each one.

## Running the application

```bash
make run CLI=invoices ARGS="send F-42"      # python -m invoicing.p4_entrypoints.invoices_cli send F-42
python -m invoicing.p4_entrypoints.invoices_cli --help
```

Settings come from the environment (`load_config()`), never from code; a local `.env` file is
gitignored. To give the program a command name of its own once it is installed, add it to
`pyproject.toml` (an architect approves the change):

```toml
[project.scripts]
invoices = "invoicing.p4_entrypoints.invoices_cli:main"
```

uv works as well as pip: `uv venv && uv pip install -r requirements.txt`.

**A database:** an architect approves the driver (`archvow init invoicing --force -a
dependencies=sqlalchemy`), a repository uses it and the services decide what is saved together.

## Committing spec changes

The same rules apply to everyone, people and AI assistants alike:

1. **Commit `.archvow/` with the change.** Every command above updates the vows (`spec.vow`) and
   the generated specs in `.archvow/`; commit the whole folder (the local `status.md` is ignored).
   Never edit those files by hand: `archvow docs --check` fails in CI if you do.
2. **The spec and the code that follows it travel together.** Code without its spec change fails
   (`ARCH001`, `ARCH002`); a spec change without its code is simply *planned*.
3. **Spec changes are approved by the architects** (@acme/architects), through
   `.gitlab/CODEOWNERS`. For anything beyond a small change, prefer two
   merge requests: the spec first (approved by an architect), then the code (normal review).

## Rules for contributors and AI assistants

[`AGENTS.md`](AGENTS.md) - read it before contributing. Approved AI assistants: claude-code.

Changes to the specification and its guards are approved by @acme/architects
(`.gitlab/CODEOWNERS`).

<!-- archvow:end -->
