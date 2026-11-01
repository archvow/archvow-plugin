"""The kinds of each component type (`archvow component add TYPE NAME -k KIND`), for the IDE forms.

    python -I contract/tools/export_kinds.py > contract/cli/kinds.json

Run with the Python that has archvow installed (regenerate.py does it). archvow 1.18 puts the kinds in
`archvow catalog --json` too (component_types.<type>.kinds); the plugins read them there first and fall
back on this file for an archvow that does not (1.17): a reader of files (the default), a table, a
database over JDBC... each kind with archvow's own description.
"""

import json

from archvow.templates import discover, load_template


def main() -> None:
    out: dict = {}
    for name in sorted(discover(None)):
        template = load_template(name, None)
        if template.data.get("abstract"):
            continue
        types = {}
        for key, ctype in template.component_types.items():
            names = template.kinds(key)
            if not names or not ctype.get("addable", True):
                continue
            declared = ctype.get("kinds") or {}
            # the default kind (files) is the type itself: its description is the type's
            types[key] = [{"name": kind, "description": str((declared.get(kind) or {}).get("description")
                                                            or (ctype.get("description", "") if i == 0 else "")),
                           "default": i == 0} for i, kind in enumerate(names)]
        if types:
            out[template.name] = types
    print(json.dumps({"kinds": out}, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
