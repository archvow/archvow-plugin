<!-- archvow spec of the project - all components, rules, hazards and checks on one page. Generated from the vows (.archvow/spec.vow, .archvow/components/), do not edit. -->

# my_pipeline - the project's spec

Each component's own spec (its task): `.archvow/components/<component>/spec.md`, or `archvow show <component>`.

## Architecture - my_pipeline

PySpark pipeline for my_pipeline

```mermaid
flowchart TB
  subgraph my_pipeline["my_pipeline (pyspark-pipeline@1.22.0)"]
    direction TB
    subgraph my_pipeline__p0_common["p0_common"]
      my_pipeline__config["config<br/><small>foundation</small>"]
      my_pipeline__logging_setup["logging_setup<br/><small>foundation</small>"]
      my_pipeline__spark["spark<br/><small>foundation</small>"]
    end
    subgraph my_pipeline__p1_domain["p1_domain"]
      my_pipeline__clean_orders["clean_orders<br/><small>transformation - US-002</small>"]
      my_pipeline__compute_vat["compute_vat<br/><small>transformation - US-002</small>"]
      my_pipeline__orders_schema["orders_schema<br/><small>schema - US-001</small>"]
    end
    subgraph my_pipeline__p2_io["p2_io"]
      my_pipeline__orders_reader["orders_reader<br/><small>reader - US-001</small>"]
      my_pipeline__vat_writer["vat_writer<br/><small>writer - US-003</small>"]
    end
    subgraph my_pipeline__p3_jobs["p3_jobs"]
      my_pipeline__my_pipeline_job["my_pipeline_job<br/><small>job - US-003</small>"]
    end
    subgraph my_pipeline__p4_entrypoints["p4_entrypoints"]
      my_pipeline__my_pipeline_main["my_pipeline_main<br/><small>entrypoint - US-003</small>"]
    end
  end
  my_pipeline__orders_reader --> my_pipeline__orders_schema
  my_pipeline__my_pipeline_job --> my_pipeline__orders_reader
  my_pipeline__my_pipeline_job --> my_pipeline__clean_orders
  my_pipeline__my_pipeline_job --> my_pipeline__compute_vat
  my_pipeline__my_pipeline_job --> my_pipeline__vat_writer
  my_pipeline__my_pipeline_main --> my_pipeline__my_pipeline_job
  classDef empty fill:none,stroke:none
```

## Traceability - my_pipeline

What exists, where, and why. Implementation status: `archvow check` or `.archvow/status.md` (local).

| Spec | Layer | Component | Type | File | Public symbols | Invariants |
|---|---|---|---|---|---|---|
| - | p0_common | config | foundation | `src/my_pipeline/p0_common/config.py` | `JobConfig` | - |
| - | p0_common | logging_setup | foundation | `src/my_pipeline/p0_common/logging_setup.py` | `setup_logging` | - |
| - | p0_common | spark | foundation | `src/my_pipeline/p0_common/spark.py` | `get_spark_session` | - |
| US-001 | p1_domain | orders_schema | schema | `src/my_pipeline/p1_domain/orders_schema.py` | `ORDERS_SCHEMA` | - |
| US-001 | p2_io | orders_reader | reader | `src/my_pipeline/p2_io/orders_reader.py` | `read_orders` | - |
| US-002 | p1_domain | clean_orders | transformation | `src/my_pipeline/p1_domain/clean_orders.py` | `clean_orders` | clean_orders.INV1 |
| US-002 | p1_domain | compute_vat | transformation | `src/my_pipeline/p1_domain/compute_vat.py` | `compute_vat` | compute_vat.INV1 |
| US-003 | p2_io | vat_writer | writer | `src/my_pipeline/p2_io/vat_writer.py` | `write_vat` | vat_writer.INV1 |
| US-003 | p3_jobs | my_pipeline_job | job | `src/my_pipeline/p3_jobs/my_pipeline_job.py` | `run_my_pipeline` | - |
| US-003 | p4_entrypoints | my_pipeline_main | entrypoint | `src/my_pipeline/p4_entrypoints/my_pipeline_main.py` | `main` | - |

## Project rules - my_pipeline

The project's own rules, in plain words (`archvow rule add`). Each component a rule applies to implements it and has a test for it.

| Rule | Applies to | Components today | What it says |
|---|---|---|---|
| R1 | compute_vat | compute_vat | vat is rounded half-up to 2 decimals |

## Hazards - my_pipeline

What must never happen, and the invariant that prevents it (what is always true, so it cannot happen: Invariants, below). A critical or high hazard needs one; medium and low may have one.

| Hazard | Component | Severity | What must never happen | Prevented by |
|---|---|---|---|---|
| vat_writer.H1 | vat_writer | critical | Downstream reads a half-written output | vat_writer.INV1 |
| clean_orders.H1 | clean_orders | high | A valid input row is dropped | clean_orders.INV1 |
| compute_vat.H1 | compute_vat | high | vat is computed wrongly | compute_vat.INV1 |

### Not applicable

Safety questions that cannot happen here, and why.

| Component | Question | Why it cannot happen here |
|---|---|---|
| orders_schema | exposed | the input holds no personal data |

## Invariants - my_pipeline

What is always true, so a hazard cannot happen (Hazards, above), and what checks it. `archvow check` fails while a coded component's invariant is not tested.

| Invariant | Component | Always true | Prevents | Tested by |
|---|---|---|---|---|
| clean_orders.INV1 | clean_orders | every input row with an order_id and amount >= 0 appears in the output | clean_orders.H1 (high) | a Hypothesis @given test marked @pytest.mark.invariant("clean_orders.INV1") |
| compute_vat.INV1 | compute_vat | vat = amount * 0.2 for every row | compute_vat.H1 (high) | a Hypothesis @given test marked @pytest.mark.invariant("compute_vat.INV1") |
| vat_writer.INV1 | vat_writer | the output folder is complete or absent | vat_writer.H1 (critical) | a guard in the code marked `# archvow: enforces[vat_writer.INV1]` |

## Personal data - my_pipeline

Every column the spec classifies as personal or special (GDPR art. 9), and the components that read, change and write it. Input for the record of processing (GDPR art. 30).

No personal column in the spec.

4 column(s) classified as not personal.

## Briefs - my_pipeline

Where the spec comes from: each component, question and rule drawn from a brief (`--from`). `archvow check` warns when a section changes (ARCH039); after review, `archvow brief accept <brief>` records its new text.

| Brief | Drawn from it | Text recorded |
|---|---|---|
| `briefs/orders.md#cleaning` | clean_orders example US-002-negative-amount, clean_orders example US-002-zero-amount-kept | `6b25cb7402c7` |
| `briefs/orders.md#vat` | compute_vat example US-002-vat-20 | `7cd02699bae2` |

## Checks

What `archvow check` verifies on every component's code, family by family (the families the project turned on). Severity shown is the effective one (template defaults + the `rules` overrides of .archvow/spec.vow). Silence a single line only with a reason: `# archvow: ignore[CODE] -- why`.

### ARCH - Architecture: structure, layers, tests, hazards, AI policy

| Code | Severity | Rule |
|---|---|---|
| ARCH001 | error | Module not declared in the spec |
| ARCH002 | error | Public symbol not declared in the spec |
| ARCH003 | error | Declared symbol not implemented, or still a placeholder (reported with --complete) |
| ARCH004 | error | Signature/annotation differs from the spec |
| ARCH005 | error | Illegal dependency between layers |
| ARCH006 | error | Undeclared dependency between components (missing in `uses`) |
| ARCH007 | error | Import forbidden in this layer |
| ARCH008 | error | Declared module file not created (reported with --complete) |
| ARCH009 | error | Invalid entry in a vow |
| ARCH010 | error | File outside any layer (neither a layer folder nor mapped in `layer_paths`) |
| ARCH011 | error | Suppression without a justification |
| ARCH012 | warning | Project template version differs from installed template |
| ARCH013 | error | File cannot be parsed |
| ARCH014 | error | Entry of a vow differs from what its template generates (hand-edited?) |
| ARCH015 | error | Python file outside the architecture (no container, not an allowed path) |
| ARCH016 | error | Test file does not follow the test layout |
| ARCH017 | error | Implemented component without its test |
| ARCH018 | error | Component file without a correct file header |
| ARCH019 | warning | Component without the data contract its type works on (a reader without its schema) |
| ARCH020 | error | Guard weakened compared to the base branch |
| ARCH040 | error | Configuration of an AI assistant not approved for this repository |
| ARCH041 | error | Review by an AI tool not approved as a reviewer |
| ARCH042 | error | Review by the AI tool that wrote the code (self-review) |
| ARCH030 | error | Critical/high hazard that no invariant prevents |
| ARCH031 | error | An invariant of an implemented component is not tested yet |
| ARCH032 | error | An invariant's test is not of the kind it requires |
| ARCH033 | error | Implemented while a decision is still to make |
| ARCH034 | error | Implemented on an assumption nobody reviewed yet |
| ARCH035 | warning | Safety question of a component not answered (no hazard, no dismissal) |
| ARCH036 | error | Invalid hazard, dismissal, invariant or project rule entry |
| ARCH037 | error | Invariant tested too weakly for the hazard's severity |
| ARCH038 | warning | A decision or rule of an implemented component has no marked test |
| ARCH043 | warning | What the AI proposed or added (a decision, a component, a question, a hazard, a rule) is not reviewed by a person yet (an error at the release gate in mode guided) |
| ARCH044 | warning | A component's task is too long for one AI session (over max_tokens) |
| ARCH045 | warning | A test marked as evidence checks nothing (an error in mode strict and with --complete) |
| ARCH046 | error | A marked test did not run and pass in the test report (check --test-report) |
| ARCH050 | error | A file in .archvow/ that archvow does not write (nobody owns it in CODEOWNERS) |
| ARCH051 | error | An AI agent runs archvow outside a sandbox (it can write outside the project) |
| ARCH052 | error | An AI agent runs archvow in YOLO mode (it acts without asking anyone) |
| ARCH054 | error | An AI agent runs a command only a person runs (it decides or changes the spec) |
| ARCH053 | warning | Code an AI agent wrote, with no proof it ran protected (an error when it ran blocked, or in mode strict) |
| ARCH039 | warning | A brief changed after the spec was drawn from it |
| ARCH055 | error | A business example's files differ from the ones a person recorded |
| ARCH056 | warning | A business example has no marked test (an error in mode strict and with --complete) |
| ARCH057 | warning | An item recorded before its component changed is not reviewed yet (an error with --complete) |
| ARCH058 | error | The project is over its limit of components or lines of code (a warning from 90 %) |
| ARCH059 | error | Logging configured outside the logging_setup foundation (one place configures it) |
| ARCH021 | warning | New suppression compared to the base branch |
| ARCH022 | error | Change too big for one merge request (files or lines over the budget) |
| ARCH023 | error | Third-party package imported without approval |
| ARCH024 | warning | Package in requirements.txt or pyproject.toml without approval (an error in mode strict) |

### STYLE - Size, scripts and comments

| Code | Severity | Rule |
|---|---|---|
| STY001 | warning | File longer than allowed |
| STY002 | warning | Function longer than allowed |
| STY003 | warning | Shell script longer than allowed (move the logic into Python) |
| STY004 | warning | Too many comment lines for the code (say it in names and docstrings) |

PEP 8 and the Python style rules are enforced by ruff (ruff.toml).

### SPARK - PySpark style and layer purity

| Code | Severity | Rule |
|---|---|---|
| SPK001 | error | Spark/file I/O outside an I/O layer |
| SPK002 | error | Spark action or execution control in a lazy-only layer |
| SPK003 | error | SparkSession built outside the session layer |
| SPK004 | error | join() without explicit how= keyword |
| SPK005 | error | Right join (swap the DataFrames and use a left join) |
| SPK006 | warning | Python or pandas UDF used (prefer native pyspark.sql.functions) |
| SPK007 | error | Non-standard PySpark import (use functions as F, types as T, Window as W) |
| SPK008 | warning | Backslash line continuation (wrap the expression in parentheses) |
| SPK009 | warning | Method chain longer than allowed |
| SPK010 | warning | Window ordered without an explicit frame (rowsBetween/rangeBetween) |
| SPK011 | error | Empty Window.partitionBy() (moves all data to one partition) |
| SPK012 | error | Empty column filled with '' or 'NA' (use F.lit(None)) |
| SPK013 | warning | withColumnRenamed() (prefer .alias() inside a select) |
| SPK014 | warning | distinct()/dropDuplicates() (fix the cause of duplicates instead) |

### GDPR - Personal data (GDPR / RGPD): every column classified, then the code checked

| Code | Severity | Rule |
|---|---|---|
| GDPR001 | error | Column not classified in the spec (personal, special or none) |
| GDPR002 | error | Personal data processed without a decided purpose and legal basis |
| GDPR003 | error | Personal data written without a decided retention and erasure |
| GDPR004 | error | Personal data printed, shown or logged |
| GDPR005 | error | Special-category data written without a tested pseudonymisation or encryption |
| GDPR006 | error | Files partitioned or bucketed by a personal column (values end up in paths) |
| GDPR007 | error | Personal data in a URL (path or query parameter: it ends up in access logs) |
| GDPR008 | error | Personal data stored without a decided location and transfer safeguard |
| GDPR009 | warning | Classified column not found in the code |

### TST - Test data: no real data in tests

| Code | Severity | Rule |
|---|---|---|
| TST001 | error | A test reads data from outside tests/ (database, cloud storage, absolute path) |
| TST002 | error | Real-looking personal identifier in test data |
| TST003 | error | Binary or too large data file in tests/ (a real extract?) |
