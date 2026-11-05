# orders-api

<!-- archvow:begin -->
<!-- Added by archvow init: `archvow init --force` rewrites only what is between these two markers; write your own text outside them. -->

Orders

A **FastAPI web service**, specified and guarded with [archvow](.archvow/README.md)
(template `fastapi-service@1.21.0`).

## For developers

Everything you need every day, on one page. The spec of each component is written and
approved by the architects (@my-team); you write the code from it.

**Once, after cloning** - Python 3.12 or newer, FastAPI 0.143:

```bash
pip install -r requirements.txt            # FastAPI, uvicorn, archvow, ruff, pytest...
git config core.hooksPath .githooks        # the checks before every commit
archvow doctor                             # checks your setup, with the fix for each problem
make run                                   # the service on http://127.0.0.1:8000/docs, reloaded on every change
```

**Every day**

| Step | Do |
|---|---|
| What to build | `archvow next` - the planned components in build order, *ready* or what each waits for |
| Your task | the spec card `.archvow/components/<component>/spec.md`: the file, its public names and routes, the decisions made, the tests, what must never happen |
| Write it | yourself, starting the file with the card's header (`@assisted none`) - or with the AI assistant, one line in its chat: `implement <component>` |
| Something is unclear | do not guess: `archvow question add <component> "<question>"`, an architect decides |
| Commit | `git commit` - the Git hook runs ruff and `archvow check`; `make test` runs the tests |

**Good to know**

- Private helpers (`_to_response`, `_Row`) are free. Anything public must be in the spec card,
  or the build fails (`ARCH001`, `ARCH002`) - and so must every route.
- HTTP stays in `p4_api` (and the dependencies of `p0_common`): services raise domain errors,
  the app turns them into statuses.
- `async def` only with async libraries; a route that calls blocking code (directly or through
  a service) is a plain `def` (`FAPI001`).
- One test per component: `tests/<layer>/test_<component>.py`; end-to-end scenarios go in
  `tests/integration/`.
- Every error says how to fix it; the rules are in [`.archvow/spec.md`](.archvow/spec.md) (Checks)
  and explained in [`.archvow/README.md`](.archvow/README.md).
- `.archvow/` changes only through archvow commands, approved by the architects - never
  by hand.
- AI assistants approved for this project: copilot. Their rules: [`AGENTS.md`](AGENTS.md).

The rest of this README is for the architects and for reference: how the service is
specified, step by step, and how it runs.

## Architecture

Code lives in `src/orders_api/`, in five layers. A layer may only import the layers listed
next to it: the business rules never depend on HTTP or on the database, and the routes never
reach the database without a service.

| Layer | Responsibility | May import |
|---|---|---|
| `p0_common` | Shared foundations - settings, database session, the dependencies routes ask for | - |
| `p1_domain` | Business rules - Pydantic models and pure functions, no HTTP, no database | p0_common |
| `p2_io` | Repositories and clients - the only place that talks to the database and other services | p0_common, p1_domain |
| `p3_services` | Services - one business operation each, calling repositories and clients and owning the transaction | p0_common, p1_domain, p2_io |
| `p4_api` | HTTP - routers and the app, which validate input, check who may call, call a service, shape the response | p0_common, p1_domain, p3_services |

The full specification - every component, its public functions and routes, the decided edge
cases and what must never happen - is in [`.archvow/`](.archvow/README.md):

- [The project's spec](.archvow/spec.md) - the whole project on one page: diagram, components, rules, hazards, invariants, personal data, checks
- [Each component's spec](.archvow/components/) - `components/<component>/spec.md`, its task; its vow `spec.vow` next to it

## Project layout

Right after `archvow init` - the spec and the guards, no code yet:

```
orders-api/
├── .archvow/                           the spec: spec.vow, spec.md, components/<component>/, README.md
├── AGENTS.md                           rules for every AI assistant
├── .gitlab-ci.yml                      CI: guards and tests
├── .gitlab/CODEOWNERS                  the architects approve the spec and the guards
├── .githooks/pre-commit                checks before every commit
├── pyproject.toml                      the package, what it needs at run time, the test settings
├── README.md
├── requirements.txt                    development tools
├── ruff.toml                           style, security and FastAPI rules
└── .gitignore
```

When development is done - every component written from its spec card, with its test:

```
orders-api/
├── .archvow/
│   ├── spec.vow                        the project's vow
│   ├── spec.md                         the whole project on one page
│   └── components/<component>/         spec.vow (its spec) + spec.md (its task)
├── migrations/                         database schema changes, if the service has a database
├── src/
│   └── orders_api/
│       ├── p0_common/                  settings.py, get_current_user.py, ...
│       ├── p1_domain/                  order_model.py, order_not_found_error.py, rules
│       ├── p2_io/                      orders_repository.py, clients of other services
│       ├── p3_services/                create_order.py, get_order.py
│       └── p4_api/                     orders_router.py, main.py (create_app)
├── tests/
│   ├── <layer>/test_<component>.py     one test per component
│   └── integration/                    end to end, through the app
└── ...                                 the files of the first tree
```

Many components in one layer? Group them in folders: `archvow component add service create_invoice -g
billing` writes `p3_services/billing/create_invoice.py`, tested in
`tests/p3_services/billing/test_create_invoice.py`.

## Building the service, step by step

Each step below shows **one example**; do the same for every component. People write the spec
(steps 1-4); then the code is written from it, by a person or an AI assistant (step 5); the same
checks judge it (step 6). Every command and option, with examples: `archvow --help`; a
complete service, from the empty project to a run: archvow's documentation, *FastAPI template*.

**1. Create the project** - this one was created with:

```bash
archvow init orders_api -t fastapi-service -d Orders \
    -a language=en -a python_version=3.12 -a line_length=88 -a platform=gitlab -a ci=auto -a architects=@my-team -a sonar=no -a auto_approve=yes -a sandbox=yes -a briefs=briefs -a git_hooks=githooks -a branch_naming=gitflow -a fastapi_version=0.143 -a ai_tools=copilot -a mode=strict -a rules=ARCH,STYLE,FASTAPI,GDPR,TST
pip install -r requirements.txt
git add -A && git commit -m "archvow init"          # the only commit on main
git switch -c develop                            # once, from main
git switch -c feature/US-001-orders develop   # one branch per change, from develop
```

Branches follow gitflow: work on `feature/<ticket>-<short-name>` from `develop`, merged into
`develop` through a merge request; the Git hook refuses commits on `main` and `develop`, and the
hook and the CI refuse other branch names. Releases and hotfixes:
archvow's documentation, *Daily workflow*.

`init` writes the spec folder `.archvow/`, the guards (AGENTS.md, ruff.toml, the test
settings, CI, CODEOWNERS, the Git hook) and one starting component, `settings`. It never writes
source code, and runs once: developers clone the repository instead.

**2. Declare the components** - each one's type, a name, and what it must do; here the whole
chain, from the data to the entry point:

```bash
archvow component add model order -d "An order: id int, customer str, amount Decimal >= 0, status pending | paid | cancelled" -s US-001
archvow component add service create_order -d "Create a pending order for the current customer and return it" -u order_model -s US-001
archvow component add router orders -d "The order API" -u create_order,order_model -r "post_order: POST /orders" -s US-001
archvow component add app main -d "The service: the order routes under /v1" -u orders_router -s US-001
```

The type fixes the structure: the file, the public class or function, the layer, the test file;
it turns the name into the component id (`order` -> `order_model`). **The description (`-d`) is
what the AI assistant - or the developer - implements**: write the real fields, rules and
statuses. Types (model, error, dependency, repository, client, service, logic, router, app):
`archvow component add --help`. The AI assistant can propose these commands for a whole feature:
`/archvow-draft-spec <the feature in plain words>`.

The app (`main`, its `create_app() -> FastAPI`) is the entry point: it makes the service runnable
(`make run`). Until it and everything it uses are coded, `archvow next` ends with *Not ready to
run yet* and what is missing; then with *Try it locally* and the command.

**3. Answer its questions** - each type asks what must not be guessed:

```bash
archvow show order_model
archvow decision make order_model 2 "amount >= 0 with 2 decimals; status pending, paid or cancelled"
archvow rule add order_model "Given an amount with 3 decimals, when an order is built, then it is refused"
```

`show` lists everything about the component: its numbered questions (red ✖ open, green ✔
answered), its rules and its safety questions S1, S2... `decision make` answers question 2 (with no answer, it takes the one `show` suggests); `rule add`
adds a rule the questions do not cover (numbered R1, R2... for the project; `--type model` for
every model, `--all` for every component; one test each). In a terminal, `archvow specify
order_model` asks what is open, one question at a time. Nothing is decided silently - not even what FastAPI would decide by default (422, every
field in the response...): code written while a question is open fails the build.

Personal data (GDPR): every column of a model is classified - `archvow column set order_model
email,phone personal` (`special` for health, religion, biometrics..., `none` otherwise); code
with an unclassified column fails the build. A router that handles personal data then answers
P1, P2 (purpose, legal basis) and a repository P1, P2 (retention, location):
`archvow decision make <component> P1 "..."`. The result: `.archvow/spec.md` (Personal data).

**4. Say what must never happen, then commit the spec**

```bash
archvow hazard add order_model S1 high "An order is saved with a negative amount"
archvow invariant add order_model "every order has an amount of zero or more"
archvow docs --check
git add .archvow && git commit -m "US-001: specify order_model"
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
✖ settings                     foundation      blocked: 2 decisions to make  -> archvow specify settings
✖ order_model                  model           blocked: 2 decisions to make  -> archvow specify order_model
✖ create_order                 service         blocked: 5 decisions to make  -> archvow specify create_order
✖ orders_router                router          blocked: 5 decisions to make  -> archvow specify orders_router
✖ main                         app             blocked: 4 decisions to make  -> archvow specify main

Safety: 1 of 13 questions answered  -> archvow specify

! Not ready to run yet: main needs settings (planned), order_model (planned), create_order (planned), orders_router (planned), main (planned)
```

*blocked*: questions are still open; answer them as in step 3 and the component becomes
*ready*. Then, in the AI assistant's chat (agent mode, in the project folder):

```
implement order_model
implement everything archvow next lists as ready
```

`AGENTS.md` tells every approved assistant (copilot) what "implement" means:
read the spec card, write the file with its header and its tests, run the checks, fix, and
report anything it had to assume.

*Yourself*: the spec card `.archvow/components/<component>/spec.md` is your task; start the file with
its header, `@assisted none`. Commit each component; the Git hook runs the checks first. If the
assistant had to assume something, the commit is refused until a person reviews it:
`archvow assumption accept order_model 1` (or `archvow assumption reject order_model 1`).

**6. Verify and run**

```bash
archvow check --complete                             # release gate: everything planned is built
pytest --cov=src --cov-report=term-missing           # every test, with coverage (as CI does)
make run                                             # http://127.0.0.1:8000/docs
```

## One example per command

| To... | For example |
|---|---|
| declare routes | `archvow component add router orders -d "The order API" -u create_order -r "post_order: POST /orders"` |
| add a route | `archvow component edit orders_router -r "post_order: POST /orders" -r "read_order: GET /orders/{order_id}"` (the complete list) |
| change a component (description, dependencies) | `archvow component edit order_model -d "An order: id int, customer str, amount Decimal >= 0"` |
| remove a component | `archvow component remove order_model` (then delete its file and test) |
| remove a hazard, an invariant or a rule | `archvow hazard remove order_model.H1`, `archvow invariant remove order_model.INV1`, `archvow rule remove R1` (each says what goes, asks first) |
| see one hazard, invariant or rule | `archvow show order_model.INV1` |
| call another service | `archvow component add client payments -d "Charge a card through the payments API"` |
| add a domain error | `archvow component add error order_not_found -d "The order does not exist, or belongs to another customer"` |
| add a pure business rule | `archvow component add logic compute_total -d "Sum of the lines" --signature "(lines: list[Line]) -> Decimal"` |
| ask a question, as a developer | `archvow question add order_model "May an order have an amount of zero?"` |
| let the AI find what is unclear | `archvow prompt order_model --clarify` (in the chat: `/archvow-clarify order_model`) |
| add a rule for every component of a type | `archvow rule add --type model "Given an unknown field, when it is parsed, then it is refused"` |
| dismiss a safety question that does not apply | `archvow hazard dismiss order_model S2 "orders hold no personal data"` |
| see everything about a component | `archvow show order_model` |
| record a business example (files in `tests/examples/orders_router/<example>/`) | `archvow example add orders_router US-001-another-customers-order -s US-001 --from briefs/<brief>.md#<section>`; `archvow show orders_router` gives its test's verdict and where `actual/` differs from `expected/` |
| find a name everywhere (a function, a column, a ticket, an example) | `archvow show <name>`: the spec, the code, the tests, the briefs, the problems naming it |
| answer what is open, one question at a time | `archvow specify order_model` (in a terminal) |
| let the AI propose the risks of a component | in the chat: `/archvow-analyze order_model` |
| build a big component one task at a time | `archvow show orders_router` lists its tasks T1, T2...; `archvow prompt orders_router` gives the next one; mark each test: `@pytest.mark.decision(3)` |
| draw the spec from a brief (`briefs/`, Markdown; another folder: `archvow init orders_api --force -a briefs=...`) | in the chat: `/archvow-draft-spec briefs/orders.md`; after the brief changes (ARCH039): `archvow brief accept briefs/orders.md#payment` |
| classify columns (GDPR) | `archvow column set order_model email,phone personal` |
| say a step removes or pseudonymises them | `archvow column set create_order phone --removes` |
| list what a release contains | `archvow changes --from v1.0.0 --markdown` |
| adopt archvow on existing code | `archvow freeze` |
| check my setup (Python, tools, Git hook) | `archvow doctor` |
| see every command and option | `archvow --help`, then `archvow <command> --help` |

A new third-party package or AI assistant is approved by an architect, with `archvow init
orders_api --force -a dependencies=...` (or `ai_tools=...`). Details and reasons:
[`.archvow/README.md`](.archvow/README.md).

## Routes

A route is a public promise - and an attack surface - so it is declared like a public function:

```bash
archvow component edit orders_router -r "post_order: POST /orders" -r "read_order: GET /orders/{order_id}" \
    -r "list_orders: GET /orders"                     # the complete new list of routes
```

In the code, each route is a function of the router with that name, decorated with that method
and path (`@router.get("/orders/{order_id}")`). A route in the code that the spec does not
declare, or with another method or path, fails the build. Its response is declared too: a
return type or `response_model=` (`FAPI005`).

## Tests

Every component has its test, `tests/<layer>/test_<component>.py`; an implemented component
without it fails the build. Its spec card says which kind and what to cover.

- **Unit** (models, pure rules, dependencies): plain objects, fast.
- **Integration** (repositories, clients, services, routers, the app): through FastAPI's
  `TestClient`, a database in a temporary file, a fake transport (`httpx.MockTransport`) for
  other services; marked `@pytest.mark.integration`.

```bash
pytest -m "not integration"                # fast feedback while coding
pytest                                     # everything
```

Who may call a route is best proven with a property test: Hypothesis generates customers and
orders, and the test checks that no customer ever reads another one's order.

## Running the service

```bash
make run                                   # uvicorn with reload, the app of the `main` component
uvicorn --factory --ws none --app-dir src orders_api.p4_api.main:create_app --host 0.0.0.0 --port 8000
```

Settings come from the environment (`get_settings()`), never from code; a local `.env` file is
gitignored. The interactive API documentation is at `/docs`, the contract at `/openapi.json`.
`--ws none`: the service has no websocket route, so uvicorn does not load the `websockets`
package (an old one installed for another project would stop it from starting).

**A database:** an architect approves it (`archvow init orders_api --force -a
dependencies=sqlalchemy`), a `dependency` component provides the session (`get_session`), the
repositories use it and the services own the transaction. Schema changes are migrations in
`migrations/` (Alembic), never `create_all` in the app.

## Committing spec changes

The same rules apply to everyone, people and AI assistants alike:

1. **Commit `.archvow/` with the change.** Every command above updates the vows (`spec.vow`) and
   the generated specs in `.archvow/`; commit the whole folder (the local `status.md` is ignored).
   Never edit those files by hand: `archvow docs --check` fails in CI if you do.
2. **The spec and the code that follows it travel together.** Code without its spec change fails
   (`ARCH001`, `ARCH002`); a spec change without its code is simply *planned*.
3. **Spec changes are approved by the architects** (@my-team), through
   `.gitlab/CODEOWNERS`. For anything beyond a small change, prefer two
   merge requests: the spec first (approved by an architect), then the code (normal review).

## Rules for contributors and AI assistants

[`AGENTS.md`](AGENTS.md) - read it before contributing. Approved AI assistants: copilot.

Changes to the specification and its guards are approved by @my-team
(`.gitlab/CODEOWNERS`).

<!-- archvow:end -->
