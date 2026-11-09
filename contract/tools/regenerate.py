"""Regenerate the shared contract from a real archvow.

    python3 contract/tools/regenerate.py <archvow source folder> [--python PY] [--archvow CMD]

PY is the Python that has that archvow installed (default: python3); CMD the archvow command
(default: PY -m archvow). Each example of archvow (examples/<name>) is copied to a temporary folder, so
nothing in the archvow tree is written. Recorded:
  contract/vows/<name>/.archvow/**/*.vow   the vows archvow wrote (read by both .vow readers)
  contract/cli/<name>/<command>.json       what the CLI prints for the commands the plugins run
  contract/cli/commands.json               every command and its parameters (archvow's own parser)
  contract/cli/catalog.json                `archvow catalog --json`: the templates and their questions
  contract/cli/kinds.json                  the kinds of each component type (component add -k), for an archvow
                                           whose catalog does not list them (1.17; tools/export_kinds.py)
  contract/cli/rules.md, rules.fr.md       archvow's docs/rules: what each check code means
  contract/cli/version.txt                 `archvow --version`: the archvow the plugins are tested with and install
  contract/cli/contract.txt                `archvow version --json`'s "contract" (MAJOR.MINOR, 1.23): an installed
                                           archvow of the same MAJOR and a MINOR at least this one is accepted
  contract/cli/<name>/workspace.json       `archvow workspace --json` (1.25, contract 1.1): all of the above in one
                                           process - what the plugins load; its digest (file times) as <digest>
  contract/cli/<name>/search/<name>.json   `archvow show <name> --json` (1.21): a name found, and one found nowhere
  contract/cli/python-app/examples/        python-app's business example in each state (1.21: the test's verdict in a
                                           test-results.xml written here, actual/ against expected/): passing.json,
                                           failing.json, no-test.json (show invoices_cli --json)
  contract/cli/errors/<case>.json          a command that fails, as the plugins get it (ARCHVOW_LOG=info, 1.23): exit,
                                           stdout (the error envelope with --json) and stderr (its message and the log
                                           lines), times and paths normalised: no-spec, refused, usage (an argparse
                                           refusal: no envelope)
  contract/cli/briefs/                     python-app with a Spec Kit `specs` folder declared as a briefs folder
                                           (-a briefs=briefs,specs), recorded like an example: refused.txt (the
                                           --from refusal before), mark_paid drawn from specs/ (show `source`),
                                           ARCH039 once that section changed
Both plugins build from and test against these files: run it after every archvow release and commit
the diff - a change here is a change of the contract the plugins rely on.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime
from pathlib import Path

CONTRACT = Path(__file__).resolve().parents[1]
EXAMPLES = ("python-app", "fastapi-service", "pyspark-pipeline")


def run(cmd, cwd, codes=(0, 1)):
    out = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, encoding="utf-8")
    if out.returncode not in codes or not out.stdout.strip():
        sys.exit(f"{' '.join(map(str, cmd))} failed ({out.returncode}): {out.stderr}")
    return json.loads(out.stdout)


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def portable(catalog):
    """A built-in template's `source` is where archvow is installed on this machine: recorded as
    <archvow>/packs/<name>, so the contract is the same wherever it is regenerated (the CI job
    `contract` regenerates it and fails on any difference). The plugins do not read it."""
    for template in catalog.get("templates", []):
        source = Path(str(template.get("source") or ""))
        if source.parent.name == "packs" and source.parent.parent.name == "archvow":
            template["source"] = f"<archvow>/packs/{source.name}"
    return catalog


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", type=Path)
    parser.add_argument("--python", default="python3")
    parser.add_argument("--archvow", nargs="+")
    args = parser.parse_args()
    source = args.source.resolve()
    archvow = args.archvow or [args.python, "-m", "archvow"]
    cli = CONTRACT / "cli"
    cli.mkdir(parents=True, exist_ok=True)
    version = subprocess.run([*archvow, "--version"], capture_output=True, text=True).stdout.strip()
    (cli / "version.txt").write_text(version + "\n", encoding="utf-8")
    said = json.loads(subprocess.run([*archvow, "version", "--json"], capture_output=True, text=True, check=True).stdout)
    (cli / "contract.txt").write_text(said["contract"] + "\n", encoding="utf-8")
    grammar = subprocess.run([args.python, "-I", str(CONTRACT / "tools" / "export_grammar.py")],
                             capture_output=True, text=True, encoding="utf-8", check=True).stdout
    write(cli / "commands.json", json.loads(grammar))
    kinds = subprocess.run([args.python, "-I", str(CONTRACT / "tools" / "export_kinds.py")],
                           capture_output=True, text=True, encoding="utf-8", check=True).stdout
    write(cli / "kinds.json", json.loads(kinds))
    with tempfile.TemporaryDirectory() as neutral:  # outside any project: the installed templates only
        write(cli / "catalog.json", portable(run([*archvow, "catalog", "--json"], neutral)))
    for name in ("rules.md", "rules.fr.md"):
        shutil.copyfile(source / "docs" / name, cli / name)
    for name in EXAMPLES:
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / name
            shutil.copytree(source / "examples" / name, project)
            vows = CONTRACT / "vows" / name
            shutil.rmtree(vows, ignore_errors=True)
            for vow in sorted((project / ".archvow").rglob("*.vow")):
                target = vows / vow.relative_to(project)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(vow, target)
            out = cli / name
            shutil.rmtree(out, ignore_errors=True)
            spec = run([*archvow, "show", "--json"], project)
            write(out / "show.json", spec)
            write(out / "next.json", run([*archvow, "next", "--json"], project))
            write(out / "check.json", run([*archvow, "check", "--json"], project))
            whole = run([*archvow, "workspace", "--json"], project)
            whole["digest"] = "<digest>"  # file times: different at every copy
            write(out / "workspace.json", whole)
            for container in spec["containers"]:
                for component in container["components"]:
                    cid = component["id"]
                    write(out / "show" / f"{cid}.json", run([*archvow, "show", cid, "--json"], project))
            for wanted in SEARCHES[name]:  # a name found nowhere exits 2, its close names on stdout
                write(out / "search" / f"{wanted}.json", run([*archvow, "show", wanted, "--json"], project, (0, 2)))
    errors(source, archvow, cli / "errors")
    examples(source, archvow, cli / "python-app" / "examples")
    briefs(source, archvow, cli / "briefs")
    print(f"contract regenerated from {version}")


ERRORS = {"no-spec": (None, ["show", "--json"]),
          "refused": ("python-app", ["decision", "make", "invoices_cli", "99", "x", "--json"]),
          "usage": ("python-app", ["component", "add", "bad-id", "--json"])}


def errors(source, archvow, out):
    """What the plugins get from a command that fails (archvow 1.23): the error envelope on stdout with --json,
    the message and archvow's log lines on stderr. Read by both plugins' output tests (cli.test.ts, OutputRegression)."""
    shutil.rmtree(out, ignore_errors=True)
    env = {**os.environ, "ARCHVOW_LOG": "info", "NO_COLOR": "1"}
    for case, (example, args) in ERRORS.items():
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / (example or "empty")
            if example:
                shutil.copytree(source / "examples" / example, project)
            else:
                project.mkdir()
            done = subprocess.run([*archvow, *args], cwd=project, capture_output=True, text=True, encoding="utf-8", env=env)
            if done.returncode == 0:
                sys.exit(f"{case}: {' '.join(args)} did not fail")
            said = {"args": args, "exit": done.returncode, "stdout": done.stdout, "stderr": done.stderr}
            for key in ("stdout", "stderr"):
                text = said[key].replace(str(project.resolve()), "<project>").replace(str(project), "<project>")
                text = re.sub(r"(?m)^(archvow )\d\d:\d\d:\d\d\.\d{3}", r"\g<1>00:00:00.000", text)
                text = re.sub(r"Python \d+\.\d+\.\d+", "Python <python>", text)
                said[key] = re.sub(r"\(\d+ ms\)", "(<ms>)", text)
            write(out / f"{case}.json", said)


SEARCHES = {"python-app": ("InvoiceNotFoundError", "US-001", "mark_payd"),
            "fastapi-service": ("get_current_user", "amount"), "pyspark-pipeline": ("US-002-zero-amount-kept",)}
EXAMPLE = "tests/examples/invoices_cli/US-001-pay-a-sent-invoice"
TEST = ("tests.p4_entrypoints.test_invoices_cli", "test_example_pay_a_sent_invoice")


def examples(source, archvow, out):
    """python-app's business example in each state (archvow 1.21): the verdict comes from a test-results.xml
    written here (no pytest needed), what the code gave from an actual/ written here."""
    shutil.rmtree(out, ignore_errors=True)
    with tempfile.TemporaryDirectory() as tmp:
        project = Path(tmp) / "python-app"
        shutil.copytree(source / "examples" / "python-app", project)
        folder = project / EXAMPLE
        report = project / "test-results.xml"

        def verdict(failed):
            body = '<failure message="assert"/>' if failed else ""
            report.write_text(f'<testsuites><testsuite><testcase classname="{TEST[0]}" name="{TEST[1]}">{body}'
                              '</testcase></testsuite></testsuites>\n', encoding="utf-8", newline="\n")

        actual = folder / "actual"

        def pinned():
            # archvow shows when the report and actual/ were written (run_on, actual_on): a fixed time, so the
            # contract is the same at every run (the CI job `contract` regenerates it and fails on any difference)
            stamp = datetime(2026, 11, 5, 10, 12).timestamp()  # local time, as archvow reads it back
            for path in [report, *actual.rglob("*")]:
                if path.exists():
                    os.utime(path, (stamp, stamp))

        (actual / "invoices").mkdir(parents=True)
        (actual / "stdout.txt").write_text("F-7 Ada 9.90 paid\n", encoding="utf-8", newline="\n")
        (actual / "invoices" / "F-7.json").write_text('{"number": "F-7", "customer": "Ada", "amount": "9.90", '
                                                     '"status": "paid"}', encoding="utf-8")
        verdict(False)
        pinned()
        write(out / "passing.json", run([*archvow, "show", "invoices_cli", "--json"], project))
        (actual / "stdout.txt").write_text("F-7 Ada 9.90 sent\n", encoding="utf-8", newline="\n")
        (actual / "invoices" / "F-7.json").write_text('{"number": "F-7", "customer": "Ada", "amount": "9.90", '
                                                     '"status": "sent"}', encoding="utf-8")
        verdict(True)
        pinned()
        write(out / "failing.json", run([*archvow, "show", "invoices_cli", "--json"], project))
        shutil.rmtree(actual)
        report.unlink()
        test = project / "tests" / "p4_entrypoints" / "test_invoices_cli.py"
        test.write_text(test.read_text(encoding="utf-8").replace('@pytest.mark.example("US-001-pay-a-sent-invoice")\n', ""),
                        encoding="utf-8")
        write(out / "no-test.json", run([*archvow, "show", "invoices_cli", "--json"], project))


BRIEF = "# Orders plan\n\n## Data model\n\nAn invoice has a number and an amount.\n\n## Paying an invoice\n\nMark it paid once.\n"


def briefs(source, archvow, out):
    """The briefs folders of archvow 1.17 (init answer `briefs`), recorded on a copy of python-app."""
    import tomllib
    shutil.rmtree(out, ignore_errors=True)
    with tempfile.TemporaryDirectory() as tmp:
        project = Path(tmp) / "python-app"
        shutil.copytree(source / "examples" / "python-app", project)
        package = tomllib.loads((project / "pyproject.toml").read_text(encoding="utf-8"))["project"]["name"]
        git = ["git", "-c", "user.email=contract@archvow", "-c", "user.name=contract"]

        def commit(message):
            subprocess.run([*git, "add", "-A"], cwd=project, check=True, capture_output=True)
            subprocess.run([*git, "commit", "-qm", message], cwd=project, check=True, capture_output=True)

        subprocess.run(["git", "init", "-q"], cwd=project, check=True)
        plan = project / "specs" / "001-orders" / "plan.md"
        plan.parent.mkdir(parents=True)
        plan.write_text(BRIEF, encoding="utf-8", newline="\n")
        commit("example")
        drawn = ["component", "edit", "mark_paid", "--from", "specs/001-orders/plan.md#paying-an-invoice"]
        refused = subprocess.run([*archvow, *drawn], cwd=project, capture_output=True, text=True, encoding="utf-8")
        out.mkdir(parents=True, exist_ok=True)
        (out / "refused.txt").write_text((refused.stderr or refused.stdout).strip() + "\n", encoding="utf-8", newline="\n")
        subprocess.run([*archvow, "init", package, "--force", "-a", "briefs=briefs,specs", "--no-input"], cwd=project,
                       check=True, capture_output=True)
        commit("briefs: briefs, specs")
        subprocess.run([*archvow, *drawn], cwd=project, check=True, capture_output=True)
        commit("mark_paid drawn from specs/")
        plan.write_text(BRIEF.replace("Mark it paid once.", "Mark it paid once, never twice."), encoding="utf-8", newline="\n")
        spec = run([*archvow, "show", "--json"], project)
        write(out / "show.json", spec)
        write(out / "next.json", run([*archvow, "next", "--json"], project))
        write(out / "check.json", run([*archvow, "check", "--json"], project))
        for container in spec["containers"]:
            for component in container["components"]:
                cid = component["id"]
                write(out / "show" / f"{cid}.json", run([*archvow, "show", cid, "--json"], project))


if __name__ == "__main__":
    main()
