"""Record every check message archvow's own test suite makes it print: contract/cli/messages.json.

    python3 contract/tools/harvest_messages.py <archvow source folder> --python <venv>/bin/python

archvow's tests break each rule one at a time (unit tests and the integration tests in real Git
repositories), so their violations are the real messages a team sees, one or more per check code. The
plugins test against them that every message gets an explanation, a "what to do" and, when the message
names an archvow command, a form prefilled with it (vscode/src/test/guidance.test.ts, GuidanceRegression).

The venv must have archvow's source installed with its test dependencies (pip install -e "<source>[dev]",
plus pyspark and Java for the Spark scenario). For the run, a .pth file in the venv wraps
archvow.checks.engine.Violation (archvow 1.25) so every violation - in pytest and in the archvow commands the tests start - is
appended to a file; the .pth file is removed afterwards. Temporary paths and dates are normalised so the
file only changes when a message does.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

CONTRACT = Path(__file__).resolve().parents[1]
HOOK = '''
import json, os
def _install():
    target = os.environ.get("ARCHVOW_PLUGIN_HARVEST")
    if not target:
        return
    try:
        from archvow.checks import engine as check
    except Exception:
        return
    original = check.Violation.__init__
    def recording(self, *args, **kwargs):
        original(self, *args, **kwargs)
        try:
            with open(target, "a", encoding="utf-8") as out:
                out.write(json.dumps({"code": self.code, "severity": self.severity, "message": self.message,
                                      "file": self.file, "line": self.line}) + "\\n")
        except OSError:
            pass
    check.Violation.__init__ = recording
_install()
'''
TEMPORARY = re.compile(r"(/tmp|/private/var/folders|[A-Z]:\\\\Users\\\\[^\\\\]+\\\\AppData\\\\Local\\\\Temp)[^\s'\"`),:]*")
DATE = re.compile(r"\b20\d\d-\d\d-\d\d(T[0-9:.+]+)?")


def normalise(text: str) -> str:
    return DATE.sub("<date>", TEMPORARY.sub("<tmp>", text))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", type=Path)
    parser.add_argument("--python", default=sys.executable)
    args = parser.parse_args()
    site = subprocess.run([args.python, "-c", "import sysconfig; print(sysconfig.get_paths()['purelib'])"],
                          capture_output=True, text=True, check=True).stdout.strip()
    module, pth = Path(site) / "archvow_plugin_harvest.py", Path(site) / "archvow_plugin_harvest.pth"
    module.write_text(HOOK, encoding="utf-8")
    pth.write_text("import archvow_plugin_harvest\n", encoding="utf-8")
    raw = Path(tempfile.mkstemp(suffix=".jsonl")[1])
    try:
        env = {**os.environ, "ARCHVOW_PLUGIN_HARVEST": str(raw)}
        run = subprocess.run([args.python, "-m", "pytest", "-q", "-p", "no:cacheprovider", "-x"], cwd=args.source, env=env)
        if run.returncode != 0:
            sys.exit("archvow's tests failed: the messages would be incomplete")
    finally:
        module.unlink(missing_ok=True)
        pth.unlink(missing_ok=True)
    seen, messages = set(), []
    for line in raw.read_text(encoding="utf-8").splitlines():
        entry = json.loads(line)
        entry = {"code": entry["code"], "severity": entry["severity"], "message": normalise(entry["message"]),
                 "file": normalise(entry["file"] or ""), "line": entry["line"]}
        key = (entry["code"], entry["message"])
        if key not in seen:
            seen.add(key)
            messages.append(entry)
    raw.unlink(missing_ok=True)
    if not messages:  # the hook did not load (archvow or its dependencies not importable at startup): say so
        sys.exit("no message recorded: archvow_plugin_harvest did not load - install archvow and its dependencies in "
                 "the venv itself (pip install -e '<archvow>[dev]'), not only on the user's site")
    messages.sort(key=lambda m: (m["code"], m["message"]))
    version = subprocess.run([args.python, "-m", "archvow", "--version"], capture_output=True, text=True).stdout.strip()
    out = CONTRACT / "cli" / "messages.json"
    out.write_text(json.dumps({"archvow": version, "messages": messages}, indent=1, ensure_ascii=False) + "\n",
                   encoding="utf-8", newline="\n")
    codes = sorted({m["code"] for m in messages})
    print(f"{len(messages)} messages, {len(codes)} codes, from {version} -> {out.relative_to(CONTRACT.parent)}")


if __name__ == "__main__":
    main()
