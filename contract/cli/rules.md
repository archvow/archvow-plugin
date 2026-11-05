# Rules

`archvow check` prints `file:line:col: CODE message`, and every message says how to fix it.
This page lists every rule. A project's own list, with the severities in force, is in its
`.archvow/spec.md` (Checks), one section per family: ARCH, STYLE, the template's (SPARK,
FASTAPI), GDPR, TST and DORA when on, a company's own. The project's own rules, written with
`archvow rule add`, are in the same page (Rules).

Severity **error** fails the check (and the commit, and the CI); **warning** is reported only.
Rules whose fix depends on a decision by the architects say so.

The rules about where code sits need the file's layer (its folder, or `layer_paths` for code
adopted where it is - [Adopting an existing project](adopting-an-existing-project.md)): `ARCH005`
to `ARCH007`, `SPK001` to `SPK003`, `FAPI002`, `FAPI003`. Every other code rule runs on every
Python file of the package, in a layer or not. `archvow check` ends with the count per rule; a
hit is reported once per file, line and message (an import of nine names is one import).

## The code follows the spec

| Code | Fails when | Fix |
|---|---|---|
| ARCH001 | a Python module in a layer is not declared in the spec | `archvow component add` the component, or make it a private helper inside a declared file |
| ARCH002 | a public name (no leading `_`), or a route even on a private function, is not declared | rename it with a leading `_`, or declare it (`archvow component edit --signature`, or a new component) |
| ARCH003 | a declared symbol is missing, or still a placeholder - a function with only `pass`, `...`, a docstring or `raise NotImplementedError`, an empty class other than an exception (with `--complete`) | write it, as the spec card names it |
| ARCH004 | a signature, an annotation or a route's method and path differs from the spec | match the spec card, or change the spec (`archvow component edit --signature`, when the type allows it) |
| ARCH005 | a layer imports a layer it may not | move the code to the right layer; the layer table is in the spec card and `.archvow/README.md` |
| ARCH006 | a component imports another one not listed in its `uses` | `archvow component edit <component> -u a,b` (architect review) |
| ARCH007 | an import forbidden in this layer (`os`, `pathlib`, `requests`... in `p1_domain`) | move the I/O to a reader or writer |
| ARCH008 | a declared file does not exist (with `--complete`) | write the component, or `archvow component remove` it |
| ARCH009 | an invalid entry in a vow (`spec.vow`), or an unknown template | the message names the entry; for a company template, set `ARCHVOW_TEMPLATES` |
| ARCH010 | a file in the package outside every layer (a module there is `ARCH001`, which says how to map it) | move it into a layer folder, or map it in `layer_paths` |
| ARCH011 | a suppression without a reason | `# archvow: ignore[CODE] -- why` |
| ARCH012 | (warning) the installed template version differs from the project's | `archvow init <package> --force`, review, commit ([upgrading](project-management.md#upgrading-archvow-in-a-project)) |
| ARCH013 | a file cannot be parsed | fix the syntax error, or save the file as UTF-8 |
| ARCH014 | an entry of a vow differs from what its template generates (edited by hand) | revert the hand edit; change the spec with the commands |
| ARCH015 | a Python file outside the architecture (no layer, not an allowed path) | make it a component, move it under `tests/`, or `archvow freeze` legacy code |
| ARCH016 | a test outside the test layout | `tests/<layer>/[<group>/]test_<component>.py`, or `tests/integration/` for end-to-end scenarios |
| ARCH017 | an implemented component without its test | write `tests/<layer>/test_<component>.py` |
| ARCH018 | the file header is missing, incomplete, still has a `<placeholder>`, or names an AI assistant that is not approved | copy the header from the spec card and fill it: `@author`, `@assisted` (an approved assistant, or `none`), `@date` |
| ARCH023 | an import of a third-party package not approved | use the standard library or an approved package; an architect approves it with `archvow init <package> --force -a dependencies=a,b`. Imported outside the folder it is approved for (`dependencies_by_path`, optional): call it from that folder ([Shared libraries](shared-libraries.md)) |
| ARCH024 | a package in `requirements.txt` or `pyproject.toml` (`[project]` dependencies) nobody approved - an error in mode strict, an error at `check --complete` in mode guided, a warning in mode free | an architect approves it with `archvow init <package> --force -a dependencies=a,b` (the requirements name or the import name); the template's own packages are approved |
| ARCH040 | the files of an AI assistant not approved (`.cursor/`, `GEMINI.md`, `.continue/`...) | delete them, or an architect approves the assistant (`-a ai_tools=...`) |
| ARCH041 | with AI reviewers approved: a review report in `reviews/` by a tool that is not an approved reviewer, or without `@reviewer` | ask an approved reviewer, or an architect approves it (`-a reviewers=...`); see [AI review](ai-assistants.md#ai-review-optional) |
| ARCH042 | with AI reviewers approved: a self-review: the reviewer is named in the `@assisted` header of a file it reviewed | ask another approved reviewer, or a person reviews |

## Decisions and safety

| Code | Fails when | Fix |
|---|---|---|
| ARCH030 | a critical or high hazard is prevented by no invariant | the message gives the command: `archvow invariant add <component> "..." --prevents H<n>` |
| ARCH031 | an invariant of an implemented component is not tested yet | write the test marked `@pytest.mark.invariant("<component>.INV<n>")` - for an invariant that is a decision (`from_decision N`, recorded when the decision covers the safety question), the test marked `@pytest.mark.decision(N)` proves it (the message says which kind and in which file, and names a mark that almost matches - `INV1` for `clean_orders.INV1`, and a test file that is not UTF-8, whose marks cannot be read) |
| ARCH032 | the invariant's test is not of the kind it needs (a plain test where a Hypothesis test is needed...) | make it a Hypothesis `@given` test (or, for an expert check, the guard / rule / file it names) |
| ARCH033 | code exists while a decision is still to make | `archvow decision make <component> <n> "..."` (a person) |
| ARCH034 | code relies on an assumption nobody reviewed yet | `archvow assumption accept <component> <n>` or `reject` (a person; a rejected assumption no longer blocks, the code must not rely on it) |
| ARCH035 | (warning) a safety question of a component is not answered; `check` counts them on one line, `check --all` lists them | `archvow specify <component>`, or `hazard add <component> S<number> ...` / `hazard dismiss <component> S<number> "..."` (`archvow show <component>` numbers them) |
| ARCH036 | an invalid hazard, dismissal, invariant or project rule (unknown component or type, no text) | the message names the entry |
| ARCH037 | the invariant is tested too weakly for the hazard's severity (a critical hazard needs a Hypothesis test or stronger) | the message gives the command for a stronger invariant |
| ARCH038 | (warning) a decision made or a rule of a coded component has no test marked with it (`check` counts them on one line; `--all` lists them) | mark the test: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (one test may carry several) |
| ARCH039 | (warning) a brief section changed (or disappeared) after components, questions, rules or business examples were drawn from it with `--from` | review what the message names, then `archvow brief accept <brief>#<section>` |
| ARCH043 | (warning; an error at `check --complete` in mode guided) a decision the AI proposed (`archvow decision propose`, kept in `.archvow/proposals.vow`) that no person reviewed yet, a component the AI created (`archvow component add` run by an agent in mode guided or free: marked `origin ai` in its vow) that no person accepted yet, or a question, a hazard, an invariant or a rule an agent added (`question add` in any mode; `hazard add`, `invariant add`, `rule add` in mode guided or free: marked `added_by <agent>`) that no person kept yet | a person runs `archvow decision accept <component> <n>`, or `archvow decision make <component> <n> "..."` to replace it; for a component, `archvow component accept <component>` (or `component remove`); for a question, `archvow decision make` (answering keeps it) or `archvow question accept <component> <n>` (or `question remove`); for a hazard and the invariants the AI added to prevent it, `archvow hazard accept <id>`; for a rule, `archvow rule accept <id>` (an edit by a person keeps the item too) |
| ARCH044 | (warning) the task an AI reads for a component - its `spec.md` - is over 4000 tokens (about 16 000 characters; `archvow next` and `archvow prompt` show each size): a long task gets followed less well | split the component into smaller ones, or let the AI take one task at a time (`archvow prompt <component> --task 1`) |
| ARCH045 | (warning; an error in mode strict and with `check --complete`) a test marked as the evidence of an invariant, a decision or a rule cannot fail: no assert, `assert True`, `assert x == x`, an empty or `pass` body | assert on what the component returns, or use `pytest.raises`. Counted as checks: an assert that can fail, `pytest.raises` / `pytest.warns`, a call named `assert...` (`assertDataFrameEqual`, `self.assertEqual`...), `pytest.fail`, a helper of the same test file that does one of these, a helper imported from the tests folder |
| ARCH046 | with `check --test-report test-results.xml` (pytest's JUnit report): a marked test - of an invariant, a decision, a rule or a business example - did not run, was skipped or failed; matched by its file and its name, every case of a parametrized test included | make it run and pass; the generated CI runs `archvow check --test-report test-results.xml` after pytest |
| ARCH055 | (always on) a business example's files in `tests/examples/<component>/<example>/` (its `input/`, `expected/` and `scenario.feature`; never `actual/`, which the test writes) differ from the ones a person recorded with `archvow example add`, or are gone - their fingerprint changed (line endings do not count; a CSV saved again by Excel does) | put them back (`git checkout -- tests/examples/...`); when the business team really changed the expected result, a person replaces the files and runs `archvow example add <component> <example>` again |
| ARCH056 | (warning; an error in mode strict and with `check --complete`) a business example of a coded component has no test marked `@pytest.mark.example("<example>")` | write the test that reads its `input/`, runs the component, writes what it gave to `actual/` and compares with its `expected/`; when the message names a test file that is not UTF-8, save it as UTF-8 (its marks cannot be read) |
| ARCH057 | (warning; an error with `check --complete`) a component's description, signature or routes changed (`archvow component edit`) after decisions, hazards, invariants, "not applicable" answers, accepted assumptions or business examples were recorded for it: each may no longer hold ("amount > 0" when the description now says "> 1"). archvow deletes nothing; `archvow show` lists them, the spec card stops the AI, `archvow next` says the component is blocked | a person looks at each one: one that no longer holds is recorded again (`archvow decision make <component> <n> "..."`, `archvow hazard edit`...), which takes it off the list; when every one still holds, `archvow component accept <component>` |
| ARCH058 | (an error; a warning from 90 %) the project is over its limit: more components than `max_components`, or more lines of code under the package's folder than `max_code_lines` (init answers, off by default; lines that are not blank, comments or docstrings; tests not counted). `archvow component add` refuses the component over the limit | split the project, or an architect raises the limit: `archvow init <package> --force -a max_components=<n>` (a raised limit is a weakening, ARCH020) |
| ARCH050 | a file in `.archvow/` that archvow does not write: CODEOWNERS lists the folder path by path, so nobody would own it (always on) | move it out of `.archvow/` |
| ARCH051 | an AI agent runs archvow outside a sandbox: it can write outside the project (archvow tries an empty file in the home folder). Only when the project's `sandbox` answer is yes (the default in mode strict). archvow stops before the command, always on | start the agent in its sandbox - [Agent security](agent-security.md) |
| ARCH052 | an AI agent runs archvow in YOLO mode: its command line has an option that lets it act without asking (`--dangerously-skip-permissions`, `--yolo`, `--allow-all`...). archvow stops before the command, always on | start it without that option - [Agent security](agent-security.md) |
| ARCH053 | (a warning; an error when the agent ran blocked, or in mode strict when it never ran archvow) a file an AI agent wrote (`@assisted <tool>`) is committed and archvow cannot say that agent last ran protected in this project; with `check --base`, a commit of such a file has no `Archvow-Agent:` line (made without the Git hooks). Always on | let the agent run `archvow check` protected, then commit again with the hooks on |
| ARCH054 | an AI agent runs a command only a person runs: one that decides or changes the spec (`component`, `decision make`, `decision add`, `decision accept`, `question remove`, `assumption accept`/`reject`, `hazard`, `invariant`, `rule`, `column`, `dependency`, `brief`, `specify`, `freeze`, `init`). archvow stops before the command, always on. The AI may still run `question add`, `assumption add` and, in modes guided and free, `decision propose` | the agent gives the command; the person runs it in their own terminal - [Agent security](agent-security.md) |

## Merge requests (`check --base <branch>`)

| Code | Fails when | Fix |
|---|---|---|
| ARCH019 | (warning) a component works on columns written nowhere: a reader or writer without its schema, a repository without its model | `archvow component add schema <name> -d "..."`, then `archvow component edit <component> -u <name>_schema` |
| ARCH020 | a guard is weakened: a lower severity or verification, a removed hazard or invariant, a relaxed ruff rule or test setting, a deleted test, a spec item whose marked test is gone, unmarked or skipped on the branch, a business example taken out of the spec, an edited guard file, more frozen violations, a new AI assistant or dependency | undo it, or the architects approve it (label `architecture-approved`, `ARCHITECTURE_APPROVED=true`, or `--accept-weakening`) |
| ARCH021 | (warning) new suppressions | check each reason in the review |
| ARCH022 | the change is too big to review (default: over 30 files, 1000 lines added or 300 deleted, outside `.archvow/`) | split the merge request, or the architects approve it. To stay under it: `archvow next --base develop` lists nothing as ready once the change is full (AI assistants run it before each component) |

## PySpark (template `pyspark-pipeline`)

| Code | Fails when | Fix |
|---|---|---|
| SPK001 | Spark or file I/O outside `p2_io`: `spark.read`/`write`, `spark.table`, RDD files (`textFile`, `saveAsTextFile`...), streams (`socketTextStream`), MLlib (`MLUtils.load*`, `model.save(sc, path)`, `Model.load(sc, path)`), `spark.sql("CREATE/INSERT/LOAD/DROP ...")`, `open()`, a log file (`logging.FileHandler` and the rotating ones, `logging.basicConfig(filename=...)`) | a reader or a writer; logs go to stderr, the cluster keeps them |
| SPK002 | an action (`collect`, `count`, `show`, `toPandas`, `cache`...) in `p1_domain` | keep transformations lazy; act in the job or the writer |
| SPK003 | a SparkSession built outside `p0_common` | `get_spark_session` from `p0_common.spark` |
| SPK004 | `join()` without `how=` | `df.join(other, on=..., how="left")` |
| SPK005 | a right join | swap the DataFrames, left join |
| SPK006 | (warning) a Python UDF or UDTF; a pandas UDF or `applyInPandas`/`mapInPandas` (vectorised: acceptable when native functions cannot do it) | a native `pyspark.sql.functions` expression |
| SPK007 | a non-standard PySpark import | `from pyspark.sql import functions as F, types as T, Window as W` |
| SPK008 | (warning) a backslash line continuation | wrap the expression in parentheses |
| SPK009 | (warning) a method chain longer than 5 | name the intermediate steps |
| SPK010 | (warning) an ordered window without an explicit frame | `.rowsBetween(...)` or `.rangeBetween(...)` |
| SPK011 | an empty `Window.partitionBy()` | partition by a key (all rows in one partition otherwise) |
| SPK012 | an empty column filled with `''` or `'NA'` | `F.lit(None)` |
| SPK013 | (warning) `withColumnRenamed()` | `.alias()` inside a `select` |
| SPK014 | (warning) `distinct()` / `dropDuplicates()` after a join (same chain, or a name assigned from a join in the function) | fix the join keys, or decide it in the spec |

## FastAPI (template `fastapi-service`)

| Code | Fails when | Fix |
|---|---|---|
| FAPI001 | a known blocking call (`requests.*`, `httpx.get`..., `time.sleep`, `subprocess.run`, `urlopen`, `open()`) directly inside `async def` | an async library (`httpx.AsyncClient`, `asyncio.sleep`), or make the function a plain `def` |
| FAPI002 | `HTTPException` outside `p4_api` and the `p0_common` dependencies | raise a domain error (an `error` component); the app maps it to a status |
| FAPI003 | `FastAPI()` created outside the app component | only `create_app()` of the app component creates it |
| FAPI004 | an `on_event` startup or shutdown handler | the app's `lifespan` |
| FAPI005 | (warning) a route without `response_model=` or a return type (one with `response_class=`, or returning a `...Response(...)`, has no model to check) | declare what it returns: a Pydantic model |

## Size

| Code | Fails when | Fix |
|---|---|---|
| STY001 | (warning) a file over 250 lines | split the component |
| STY002 | (warning) a function body over 70 lines (docstring and signature not counted) | extract private helpers |
| STY003 | (warning) a shell script (`*.sh`, outside hidden folders) over 150 lines | move the logic into a tested Python component; keep the script a launcher |
| STY004 | (warning) a Python file with more comment lines than 30% of its code lines (from 20 code lines; the comment block at the top - licence, copyright - is not counted) | rename, extract a function, move the explanation into the docstring; ruff's `ERA` already refuses commented-out code |

## Personal data - GDPR (`rules` answer GDPR, on by default)

The rules read the classification of each column (`archvow column set`) and follow it through the
`uses` of the spec - see [Compliance](compliance.md).

| Code | Fails when | Fix |
|---|---|---|
| GDPR001 | a column defined in the code of a schema or model (`StructField("x")`, a model field) is not classified - from day one, it blocks the code | `archvow column set <component> x personal\|special\|none` |
| GDPR002 | a coded job (or router) handling personal data has no answer to P1, P2 (purpose, legal basis) - or to the art. 9 condition for special data | `archvow decision make <component> P1 "..."` |
| GDPR003 | a coded writer (or repository) of personal data has no answer on retention and erasure | `archvow decision make <component> P1 "..."` |
| GDPR004 | a component handling personal data calls `.show()`, `.display()` or `.explain()`, or prints or logs collected rows (`collect`, `take`, `head`, `toPandas`...) | log counts, check rows in a test |
| GDPR005 | special-category data reaches a writer without a step that pseudonymises it, and without a critical `exposed` hazard prevented by a tested invariant (that question cannot be "not applicable") | `archvow column set <step> <columns> --pseudonymises`, or the hazard and its invariant |
| GDPR006 | `partitionBy` or `bucketBy` on a personal column (its values become folder names) | partition by a non-personal column |
| GDPR007 | a FastAPI route has a personal field in its path or as a query parameter (it ends up in access logs) | use an id, send it in the body |
| GDPR008 | a coded writer, repository or client of personal data has no answer on its location (country or region) and transfer safeguard (art. 46) | `archvow decision make <component> P2 "..."` |
| GDPR009 | (warning) a classified column is not in the code any more | rename the classification, or remove it with the column |

## Test data (`rules` answer TST, on by default)

| Code | Fails when | Fix |
|---|---|---|
| TST001 | a test reads a real table (`spark.table`, `.jdbc`), cloud storage (`s3://`, `abfss://`, `gs://`, `hdfs://`, `dbfs:`...), a database URL that is not local, or an absolute path | build the rows in the test, or a small file under `tests/`; a local or in-memory database |
| TST002 | a test or a data file under `tests/` holds a real-looking identifier: an e-mail outside example.com/.org/.net, an IBAN with a valid checksum, a card number passing the Luhn check, a French social security number with a valid key, a phone number | invented values: name@example.com, FR76 3000 6000 0112 3456 7890 189 |
| TST003 | a binary data file under `tests/` (Parquet, Avro, ORC, Excel, a database), or a text data file over 200 KB or 1000 lines (`max_kb`, `max_rows`) | a few invented rows |

## DORA (`rules` answer DORA, on request)

| Code | Fails when | Fix |
|---|---|---|
| DORA001 | a component has no ticket (the template's foundations excepted); the commit-msg hook also puts the branch's ticket in every commit message | `archvow component edit <component> -s <ticket>` |
| DORA002 | fewer than two people can approve guard changes (CODEOWNERS has one user and no group) | `init --force -a architects="@first @second"` (or a group) |
| DORA003 | a package of requirements.txt is not in the third-party register, or a critical one is not pinned with `==` | `archvow dependency add <package> --supplier ... --licence ... --criticality ...` |
| DORA004 | the test of an invariant can be skipped (`skip`, `skipif`, `xfail`, `pytest.skip()`), or a CI job is allowed to fail (`allow_failure`, `continue-on-error`). That the tests ran and passed is ARCH046, for every project | let it run; let a failing job stop the merge |
| DORA005 | a coded job, entry point or app has no answer to O1, O2 (failure detection and alerting, recovery with its RTO and RPO) | `archvow decision make <component> O1 "..."` |
| DORA006 | (warning) the licence in the register differs from the installed package's metadata | correct the register |

## A company's own rules (`custom_rules`)

A company template declares rules as data, without Python - forbidden imports, calls, text or
files, or a question asked of a component type (C1, C2...). Their codes are the company's (for
example `ACME001`), their section is ACME in `.archvow/spec.md` (Checks). See
[Company templates](company-templates.md#your-own-rules).

## Ruff

The generated `ruff.toml` enforces PEP 8 (`E`, `W`, `N`), imports (`I`), docstrings (`D`),
annotations (`ANN`), complexity (`C90`, `PL`), likely bugs (`B`, `BLE`), no commented-out code
(`ERA`) and the security rules (`S`): a hard-coded password or secret fails ruff, so the Git
hook and the CI refuse it.

## Suppressing a finding

When a finding is wrong for one line, say why on that line:

```python
rows = df.distinct()  # archvow: ignore[SPK014] -- the source re-sends whole files, decided in US-12
```

Several codes: `ignore[SPK006, SPK009]`. A suppression without a reason is itself an error
(`ARCH011`), and each new one is listed in the merge request (`ARCH021`).

## Expert checks for an invariant

By default an invariant is tested by a test (a Hypothesis test for a critical hazard). For the
rare cases where a test is not the right check, `invariant add` takes a hidden option,
`--verify`:

| `--verify` | What `archvow check` looks for |
|---|---|
| `example_test` | a test marked `@pytest.mark.invariant("clean_orders.INV1")` (the default) |
| `property_test` | the same marker on a Hypothesis `@given` test (the default for a critical hazard) |
| `runtime` | `# archvow: enforces[vat_writer.INV1]` at the guard, in the component's own file |
| `static` | an archvow rule add at error severity (`--rule SPK001`) |
| `model_check`, `proof` | a model or proof file checked by its own tool in CI (`--evidence path`) |

The template sets the weakest check each severity accepts (`verification_floor`).

## Changing a severity or a limit

For every project of a template, in its `rules:` ([company templates](company-templates.md)).
For one project, in the `rules` group of its `.archvow/spec.vow` (by hand, the only part of a
vow that is; [the .vow format](vow-format.md)) - a guard change, approved by the architects:

```vow
rules
  SPK009
    max_chain 4
  STY001
    max_lines 300
  STY003
    max_lines 80
  STY004
    max_percent 20
    min_code_lines 30
  ARCH022
    max_files 50
    max_added_lines 2000
  ARCH044
    max_tokens 6000
  SPK006
    severity error
```

`severity` is `error`, `warning` or `off`.
