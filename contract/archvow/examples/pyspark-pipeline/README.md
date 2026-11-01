# my-pipeline

<!-- archvow:begin -->
<!-- Added by archvow init: `archvow init --force` rewrites only what is between these two markers; write your own text outside them. -->

PySpark pipeline for my_pipeline

A batch **PySpark data pipeline**, specified and guarded with
[archvow](.archvow/README.md) (template `pyspark-pipeline@1.18.0`).

## For developers

Everything you need every day, on one page. The spec of each component is written and
approved by the architects (@my-team); you write the code from it.

**Once, after cloning** - Python 3.10 to 3.12, Java 17,
PySpark 3.5:

```bash
pip install -r requirements.txt            # archvow, ruff, pytest, pyspark (local only)...
git config core.hooksPath .githooks        # the checks before every commit
archvow doctor                             # checks your setup, with the fix for each problem
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

- Private helpers (`_parse_date`, `_Row`) are free. Anything public must be in the spec card,
  or the build fails (`ARCH001`, `ARCH002`).
- One test per component: `tests/<layer>/test_<component>.py`; end-to-end scenarios go in
  `tests/integration/`.
- Every error says how to fix it; the rules are in [`.archvow/spec.md`](.archvow/spec.md) (Checks)
  and explained in [`.archvow/README.md`](.archvow/README.md).
- `.archvow/` changes only through archvow commands, approved by the architects - never
  by hand.
- AI assistants approved for this project: claude-code. Their rules: [`AGENTS.md`](AGENTS.md).

The rest of this README is for the architects and for reference: how the pipeline is
specified, step by step, and how it runs.

## Architecture

Code lives in `src/my_pipeline/`, in five layers. A layer may only import the layers listed
next to it, so business logic never depends on storage or orchestration:

| Layer | Responsibility | May import |
|---|---|---|
| `p0_common` | Shared foundations - SparkSession factory, configuration, constants | - |
| `p1_domain` | Schemas and pure, lazy DataFrame -> DataFrame transformations | p0_common |
| `p2_io` | Readers and writers - the only place that touches storage | p0_common, p1_domain |
| `p3_jobs` | Jobs wiring readers, transformations and writers together | p0_common, p1_domain, p2_io |
| `p4_entrypoints` | Process entry points (CLI, scheduler, Databricks/EMR job) | p0_common, p3_jobs |

The full specification - every component, its public functions, the decisions made on edge cases and
what must never happen - is in [`.archvow/`](.archvow/README.md):

- [The project's spec](.archvow/spec.md) - the whole project on one page: diagram, components, rules, hazards, invariants, personal data, checks
- [Each component's spec](.archvow/components/) - `components/<component>/spec.md`, its task; its vow `spec.vow` next to it

## Project layout

Right after `archvow init` - the spec and the guards, no code yet:

```
my-pipeline/
├── .archvow/                           the spec: spec.vow, spec.md, components/<component>/, README.md
├── AGENTS.md                           rules for every AI assistant
├── .gitlab-ci.yml                      CI: guards and tests
├── .gitlab/CODEOWNERS                  the architects approve the spec and the guards
├── .githooks/pre-commit                checks before every commit
├── entrypoint.sh                       run an entry point, the tables (--ddl), build (--package)
├── pyproject.toml                      the package, its version (the wheel), the test settings
├── README.md
├── requirements.txt                    development tools
├── ruff.toml                           style and security rules
└── .gitignore
```

When development is done - every component written from its spec card, with its test:

```
my-pipeline/
├── .archvow/
│   ├── spec.vow                        the project's vow
│   ├── spec.md                         the whole project on one page
│   └── components/<component>/         spec.vow (its spec) + spec.md (its task)
├── ddl/                                tables, created before the job (--ddl)
│   ├── 001_<table>.sql                 CREATE TABLE IF NOT EXISTS ...
│   └── 002_<table>_<change>.sql        ALTER TABLE ... ADD COLUMNS (additive)
├── src/
│   └── my_pipeline/
│       ├── p0_common/                  spark.py, config.py, shared helpers
│       ├── p1_domain/                  schemas, transformations
│       ├── p2_io/                      readers, writers
│       ├── p3_jobs/                    jobs: read, transform, write
│       └── p4_entrypoints/             command lines, exit codes
├── tests/
│   ├── conftest.py                     Spark session, local test catalog
│   ├── <layer>/test_<component>.py     one test per component
│   └── integration/                    end to end, with golden files
│       ├── actuals/                    input files
│       ├── expecteds/                  the output they must produce
│       └── test_<name>_end_to_end.py
├── dist/                               built by --package, not committed
│   ├── my_pipeline-<version>-py3-none-any.whl  the code
│   └── ddl.zip                         the table scripts
└── ...                                 the files of the first tree
```

Deployment files of your company (RPM, Ansible, shared scripts...) can sit next to these:
they need nothing from archvow, which only checks the Python code.

## Building the pipeline, step by step

Each step below shows **one example**; do the same for every component. People write the spec
(steps 1-4); then the code is written from it, by a person or an AI assistant (step 5); the same
checks judge it (step 6). Every command and option, with examples: `archvow --help`; a
complete pipeline, from the empty project to a run: archvow's documentation, *Getting started*.

**1. Create the project** - this one was created with:

```bash
archvow init my_pipeline -t pyspark-pipeline -d 'PySpark pipeline for my_pipeline' \
    -a language=en -a python_version=3.10 -a line_length=88 -a platform=gitlab -a ci=auto -a architects=@my-team -a sonar=no -a auto_approve=yes -a sandbox=yes -a briefs=briefs -a git_hooks=githooks -a branch_naming=gitflow -a spark_version=3.5 -a scheduler=none -a ai_tools=claude-code -a mode=strict -a rules=ARCH,STYLE,SPARK,GDPR,TST
pip install -r requirements.txt
git add -A && git commit -m "archvow init"          # the only commit on main
git switch -c develop                            # once, from main
git switch -c feature/US-001-sales-reader develop   # one branch per change, from develop
```

Branches follow gitflow: work on `feature/<ticket>-<short-name>` from `develop`, merged into
`develop` through a merge request; the Git hook refuses commits on `main` and `develop`, and the
hook and the CI refuse other branch names. Releases and hotfixes:
archvow's documentation, *Daily workflow*.

`init` writes the spec folder `.archvow/`, the guards (AGENTS.md, ruff.toml, the test
settings, CI, CODEOWNERS, the Git hook) and two starting components, `spark` and `config`. It
never writes source code, and runs once: developers clone the repository instead.

**2. Declare the components** - each one's type, a name, and what it must do; here the whole
chain, from the input to the entry point:

```bash
archvow component add reader sales -d "Read the sales CSV files at the given path, with a header" -s US-001
archvow component add schema sales -d "id string not null, amount double >= 0, effective_date date not null, comment string" -s US-001
archvow component edit sales_reader -u sales_schema
archvow component add transformation clean_sales -d "Keep the rows with amount > 0, then sum amount per effective_date as revenue" -s US-001
archvow component add schema revenue -d "effective_date date not null, revenue double not null" -s US-001
archvow component add writer revenue -d "Write the revenue per day as parquet, replacing the output" -u revenue_schema -s US-001
archvow component add job my_pipeline -d "Read the sales, clean them, write the revenue" -u sales_reader,clean_sales,revenue_writer -s US-001
archvow component add entrypoint my_pipeline -d "Command line: --input PATH (the sales folder), --output PATH (the revenue folder)" -u my_pipeline_job -s US-001
archvow show sales_schema                # its decisions: nullable columns, personal data...
```

The type fixes the structure: the file, the public function and its signature, the layer, the
test file; it turns the name into the component id (`sales` -> `sales_reader`). **The
description (`-d`) is what the AI assistant - or the developer - implements**: write the real
columns, conditions and formulas. Types: `archvow component add --help`. The AI assistant can propose
these commands for a whole feature: `/draft-spec <the feature in plain words>`. The columns a
reader or a writer works on are a `schema` component it uses; without one, `archvow check` warns
(`ARCH019`).

The entry point (`my_pipeline_main`, its `main(argv) -> int`) makes the program runnable: it
reads the arguments and runs the job. Until it and everything it uses are coded, `archvow next`
ends with *Not ready to run yet* and what is missing; then with *Try it locally* and the command.

**3. Answer its questions** - each type asks what must not be guessed:

```bash
archvow show sales_reader
archvow decision make sales_reader 3 "Every file under the given path, no partitions"
archvow rule add sales_reader "Given a file with a malformed line, when it is read, then the job fails"
```

`show` lists everything about the component: its numbered questions (red ✖ open, green ✔
answered), its rules and its safety questions S1, S2... `decision make` answers question 3 (with no answer, it takes the one `show` suggests); `rule add`
adds a rule the questions do not cover (numbered R1, R2... for the project; `--type reader` for
every reader, `--all` for every component; one test each). In a terminal, `archvow specify
sales_reader` asks what is open, one question at a time. Code written while a question is open fails the build.

Personal data (GDPR): every column of a schema is classified - `archvow column set sales_schema
email,phone personal` (`special` for health, religion, biometrics..., `none` otherwise); code
with an unclassified column fails the build. A job that handles personal data then answers
P1, P2 (purpose, legal basis) and a writer P1, P2 (retention, location):
`archvow decision make <component> P1 "..."`. The result: `.archvow/spec.md` (Personal data).

**4. Say what must never happen, then commit the spec**

```bash
archvow hazard add sales_reader S1 high "An empty input is processed as if it were complete"
archvow invariant add sales_reader "an empty or missing input fails the job"
archvow docs --check
git add .archvow && git commit -m "US-001: specify sales_reader"
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
✖ spark                        foundation      blocked: 1 decision to make  -> archvow specify spark
✖ clean_sales                  transformation  blocked: 9 decisions to make  -> archvow specify clean_sales
✖ revenue_schema               schema          blocked: 2 decisions to make  -> archvow specify revenue_schema
✖ sales_schema                 schema          blocked: 2 decisions to make  -> archvow specify sales_schema
✖ revenue_writer               writer          blocked: 5 decisions to make  -> archvow specify revenue_writer
✖ sales_reader                 reader          blocked: 3 decisions to make  -> archvow specify sales_reader
✖ my_pipeline_job              job             blocked: 3 decisions to make  -> archvow specify my_pipeline_job
✖ my_pipeline_main             entrypoint      blocked: 2 decisions to make  -> archvow specify my_pipeline_main

Safety: 1 of 20 questions answered  -> archvow specify

! Not ready to run yet: my_pipeline_main needs config (planned), spark (planned), sales_schema (planned), sales_reader (planned), clean_sales (planned), revenue_schema (planned), revenue_writer (planned), my_pipeline_job (planned), my_pipeline_main (planned)
```

*blocked*: questions are still open; answer them as in step 3 and the component becomes
*ready*. Then, in the AI assistant's chat (agent mode, in the project folder):

```
implement sales_reader
implement everything archvow next lists as ready
```

`AGENTS.md` tells every approved assistant (claude-code) what "implement" means:
read the spec card, write the file with its header and its tests, run the checks, fix, and
report anything it had to assume.

*Yourself*: the spec card `.archvow/components/<component>/spec.md` is your task; start the file with
its header, `@assisted none`. Commit each component; the Git hook runs the checks first. If the
assistant had to assume something, the commit is refused until a person reviews it:
`archvow assumption accept sales_reader 1` (or `archvow assumption reject sales_reader 1`).

**6. Verify and run**

```bash
archvow check --complete                             # release gate: everything planned is built
pytest --cov=src --cov-report=term-missing           # every test, with coverage (as CI does)
archvow next                                         # last line: Try it locally: ... (or what is not ready yet)
SPARK_MASTER="local[*]" ./entrypoint.sh my_pipeline <its arguments>   # that line, with real values
```

## One example per command

| To... | For example |
|---|---|
| change a component (description, dependencies, signature) | `archvow component edit sales_reader -d "Read the sales CSV files at the given path, in UTF-8"` |
| remove a component | `archvow component remove sales_reader` (then delete its file and test) |
| remove a hazard, an invariant or a rule | `archvow hazard remove sales_reader.H1`, `archvow invariant remove sales_reader.INV1`, `archvow rule remove R1` (each says what goes, asks first) |
| see one hazard, invariant or rule | `archvow show sales_reader.INV1` |
| add a rule for every component of a type | `archvow rule add --type reader "Given an empty input, when it runs, then it fails"` |
| say what a transformation computes | answer its questions 1-4: `archvow decision make clean_sales 1 "Rows with an id and an amount >= 0"` |
| join two datasets | `archvow component add transformation add_reference -d "..." --signature "(df: DataFrame, reference: DataFrame) -> DataFrame"` |
| ask a question, as a developer | `archvow question add clean_sales "May amount be negative for refunds?"` |
| let the AI find what is unclear | `archvow prompt clean_sales --clarify` (in the chat: `/clarify clean_sales`) |
| dismiss a safety question that does not apply | `archvow hazard dismiss sales_reader S4 "the job reads only the file it is given"` |
| see everything about a component | `archvow show sales_reader` |
| answer what is open, one question at a time | `archvow specify sales_reader` (in a terminal) |
| let the AI propose the risks of a component | in the chat: `/analyze sales_reader` |
| build a big component one task at a time | `archvow show clean_sales` lists its tasks T1, T2...; `archvow prompt clean_sales` gives the next one; mark each test: `@pytest.mark.decision(3)` |
| draw the spec from a brief (`briefs/`, Markdown; another folder: `archvow init my_pipeline --force -a briefs=...`) | in the chat: `/draft-spec briefs/sales.md`; after the brief changes (ARCH039): `archvow brief accept briefs/sales.md#cleaning` |
| classify columns (GDPR) | `archvow column set sales_schema email,phone personal` |
| say a step removes or pseudonymises them | `archvow column set clean_sales phone --removes` |
| list what a release contains | `archvow changes --from v1.0.0 --markdown` |
| adopt archvow on existing code | `archvow freeze` |
| check my setup (Python, Java, tools, Git hook) | `archvow doctor` |
| see every command and option | `archvow --help`, then `archvow <command> --help` |

Structure is declared first, then coded: a public function, class or module the spec does not
declare fails the build, whoever wrote it. Private helpers (`_parse`, `_Row`) are free.

## Tests

Every component has its test, `tests/<layer>/test_<component>.py`; an implemented component
without it fails the build. Its spec card says which kind and what to cover.

- **Unit** (schemas, transformations, helpers, entry points): small DataFrames in memory, fast.
- **Integration** (readers, writers, jobs): real files or tables in a temporary folder, marked
  `@pytest.mark.integration`. End-to-end scenarios across components go in `tests/integration/`.

```bash
pytest -m "not integration"                # fast feedback while coding
pytest                                     # everything
pytest --cov=src --cov-report=term-missing   # with coverage, as CI does
```

The development requirements (`pip install -r requirements.txt`) are for development only: on the edge
node / cluster, Spark is already installed. Keep their `pyspark` version equal to the cluster's.

**End to end, with golden files:** put input files in `tests/integration/actuals/` and the
output they must produce in `tests/integration/expecteds/`; a test in `tests/integration/`
runs the job on the actuals and compares the result with the expecteds (sorted, same columns
and types). The job's spec card asks for it.

## Tables

The job never creates its tables: they exist before it runs. Their scripts are versioned with
the code, in **Spark SQL**, so they work with the catalog Spark is configured for (Hive
metastore, Iceberg, Delta...):

```sql
-- ddl/001_<table>.sql
CREATE TABLE IF NOT EXISTS <database>.<table> (id STRING, total DOUBLE, run_date DATE)
USING parquet PARTITIONED BY (run_date);          -- or USING iceberg / delta, or STORED AS ...
-- ddl/002_<table>_add_source.sql: changes are additive only, never drop or rename in place
ALTER TABLE <database>.<table> ADD COLUMNS (source STRING);
```

- `./entrypoint.sh --ddl` runs them in order with `spark-sql` (idempotent: `IF NOT EXISTS`);
  deployment or the scheduler runs it before the job.
- The writer checks that the table matches the schema, selects the table's column order and
  writes with `insertInto(<table>, overwrite=True)` and
  `spark.sql.sources.partitionOverwriteMode=dynamic` - a re-run replaces only its partitions.
  Never `saveAsTable` on an existing table.
- The writer's test runs the scripts on the local test catalog and asserts that
  `spark.table(<table>).schema` equals the schema: the script and the code cannot drift apart.

The writer's spec card asks which table and which script.

## Running the pipeline

`entrypoint.sh` runs an entry point with `spark-submit`, shipping the package to the executors:

```bash
./entrypoint.sh                                              # lists the entry points
./entrypoint.sh my_pipeline <its arguments>                 # on the edge node (YARN, client mode)
SPARK_MASTER="local[*]" ./entrypoint.sh my_pipeline ...    # on a laptop
SPARK_ARGS="--num-executors 4 --executor-memory 4g" ./entrypoint.sh my_pipeline ...
```

Its exit code is the entry point's own, so schedulers see failures. A run by hand ships the
current source (zipped on the fly), so it always runs your latest code.

**Packaging for deployment:** `./entrypoint.sh --package` builds the wheel
`dist/my_pipeline-<version>-py3-none-any.whl` (version in `pyproject.toml`: raise it for
every release) and `dist/ddl.zip` with the table scripts. The wheel is the versioned artifact
you deploy - publish it to your internal package index, or put it in your RPM - and Spark
ships it to the executors: `spark-submit --py-files dist/my_pipeline-<version>-py3-none-any.whl
src/my_pipeline/p4_entrypoints/<name>_main.py ...`. PySpark itself comes with the
cluster. A third-party package that the cluster does not have (approved with
`-a dependencies=`) is listed in `pyproject.toml` and shipped as a packed environment
(`venv-pack`, then `--archives env.tar.gz#env` with `PYSPARK_PYTHON=./env/bin/python`).

**Scheduling:** run `entrypoint.sh` from your scheduler (cron, Control-M, ...): tables first
(`--ddl`), then the entry point. To generate the files for Airflow or Oozie:
`archvow init my_pipeline --force -a scheduler=airflow` (or `oozie`).

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

```bash
archvow component edit sales_reader -d "Read the sales CSV files at the given path, with a header, in UTF-8"
git add .archvow && git commit -m "US-005: the input is UTF-8"
```

## Rules for contributors and AI assistants

[`AGENTS.md`](AGENTS.md) - read it before contributing. Approved AI assistants: claude-code.

Changes to the specification and its guards are approved by @my-team
(`.gitlab/CODEOWNERS`).

<!-- archvow:end -->
