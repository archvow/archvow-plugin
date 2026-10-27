"""Prints the command grammar of the installed archvow, read from its own argparse parser, as JSON.

Run with the Python that has archvow: <python> -I contract/tools/export_grammar.py. regenerate.py runs it
and writes contract/cli/commands.json, from which both plugins build their command forms.
"""
import argparse
import json
from archvow.cli import build_parser


def walk(parser, path=()):
    for action in parser._actions:
        if isinstance(action, argparse._SubParsersAction):
            for name, nested in action.choices.items():
                yield from walk(nested, (*path, name))
            return
    if not path:
        return
    params = []
    for action in parser._actions:
        if isinstance(action, (argparse._HelpAction, argparse._SubParsersAction)) or action.dest in {"func", "help", "json"}:
            continue
        params.append({
            "name": action.dest,
            "flags": action.option_strings,
            "required": bool(action.required),
            "boolean": isinstance(action, (argparse._StoreTrueAction, argparse._StoreFalseAction)),
            "append": isinstance(action, argparse._AppendAction),
            "multiToken": action.nargs in ("+", "*"),
            "optionalValue": action.nargs == "?",
            "choices": [str(v) for v in action.choices] if action.choices else [],
            "help": action.help or "",
            "metavar": (str(action.metavar) if action.metavar is not None else action.dest),
        })
    yield {"path": list(path), "description": parser.description or "", "json": any("--json" in a.option_strings for a in parser._actions), "parameters": params}


if __name__ == "__main__":
    print(json.dumps({"commands": list(walk(build_parser()))}, indent=2, ensure_ascii=False))
