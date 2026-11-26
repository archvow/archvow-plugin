---
name: archvow-draft-spec
description: "Rédiger la spec archvow d'une fonctionnalité décrite en mots simples, ou d'un brief (un fichier d'un dossier de briefs : briefs/ par défaut) : proposée en mode strict, enregistrée comme celle de l'IA en modes guided et free"
---

À donner : <la fonctionnalité, en mots simples> | briefs/<fichier>.md | briefs/<fichier>.md#<section> | specs/<fonctionnalité>/plan.md.

Rédige la spec de cette fonctionnalité, comme AGENTS.md définit « Rédige la spec de <fonctionnalité> » - ou, quand c'est un fichier d'un dossier de briefs (briefs/, ou un autre dossier que le projet déclare), « Rédige la spec depuis <brief> » : en mode strict, propose les commandes et n'en lance aucune ; en modes guided et free, lance-les toi-même avec `--from` et `--why` (chaque élément reste celui de l'IA jusqu'à ce qu'une personne le garde). Les composants vont dans la fonctionnalité qu'ils servent (`archvow feature add <fonctionnalité>` quand elle est nouvelle, puis `component add ... --feature <fonctionnalité>`). N'écris aucun code.
