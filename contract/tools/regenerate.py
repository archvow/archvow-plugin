"""Regenerate the shared contract from a real archvow.

    python3 contract/tools/regenerate.py <archvow source folder> [--python PY] [--archvow CMD]

PY is the Python that has that archvow installed (default: python3); CMD the archvow command
(default: PY -m archvow). Each example of archvow (examples/<name>) is copied to a temporary folder, so
nothing in the archvow tree is written. Recorded:
  contract/vows/<name>/.archvow/**/*.vow   the vows archvow wrote (read by both .vow readers)
  contract/cli/<name>/<command>.json       what the CLI prints for the commands the plugins run
  contract/cli/commands.json               every command and its parameters (archvow's own parser)
  contract/cli/catalog.json                `archvow catalog --json`: the templates and their questions
  contract/cli/rules.md, rules.fr.md       archvow's docs/rules: what each check code means
  contract/cli/version.txt                 `archvow --version`
Both plugins build from and test against these files: run it after every archvow release and commit
the diff - a change here is a change of the contract the plugins rely on.
"""
import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

CONTRACT = Path(__file__).resolve().parents[1]
EXAMPLES = ("python-app", "fastapi-service", "pyspark-pipeline")


def run(cmd, cwd):
    out = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, encoding="utf-8")
    if out.returncode not in (0, 1) or not out.stdout.strip():
        sys.exit(f"{' '.join(map(str, cmd))} failed ({out.returncode}): {out.stderr}")
    return json.loads(out.stdout)


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


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
    grammar = subprocess.run([args.python, "-I", str(CONTRACT / "tools" / "export_grammar.py")],
                             capture_output=True, text=True, encoding="utf-8", check=True).stdout
    write(cli / "commands.json", json.loads(grammar))
    with tempfile.TemporaryDirectory() as neutral:  # outside any project: the installed templates only
        write(cli / "catalog.json", run([*archvow, "catalog", "--json"], neutral))
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
            for container in spec["containers"]:
                for component in container["components"]:
                    cid = component["id"]
                    write(out / "show" / f"{cid}.json", run([*archvow, "show", cid, "--json"], project))
    print(f"contract regenerated from {version}")


if __name__ == "__main__":
    main()
