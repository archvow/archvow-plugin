---
name: archvow-clarify
description: "Find what the spec of a component, or where a whole feature's components disagree, does not define yet and record it as questions marked as the AI's - decide nothing"
---

What to give: <component> (and at most how many questions: 5 when not said), or a feature to check whole before coding.

Run `archvow prompt <component> --clarify --max <N>` for the component the person gives (for a feature: `archvow prompt --feature <feature> --clarify --max <N>`) and do the task it prints: record each question yourself with `archvow question add <component> "<question>" --why "<what leaves it open>"`. Propose no answer and write no code. End with the list of the questions you recorded: a person answers, keeps or removes each one.
