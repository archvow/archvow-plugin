---
name: archvow-implement
description: "Implement a component of the archvow spec, or everything that is ready"
---

What to give: <component> (empty: everything archvow next lists as ready).

Implement what the person gives (a component, a feature or a file) as AGENTS.md defines "Implement <component>". A feature: read it whole with `archvow prompt --feature <feature>` and build its components one at a time in that order, running `archvow check` after each. With no component given, implement everything `archvow next --base develop` lists as ready, one component at a time, and stop when none is ready. Check the branch first, as AGENTS.md says (you never create it).
