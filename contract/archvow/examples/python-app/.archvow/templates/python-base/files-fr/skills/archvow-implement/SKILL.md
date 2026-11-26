---
name: archvow-implement
description: "Implémenter un composant de la spec archvow, ou tout ce qui est prêt"
---

À donner : <composant> (vide : tout ce que archvow next liste comme prêt).

Implémente ce que la personne donne (un composant, une fonctionnalité ou un fichier) comme AGENTS.md définit « Implémente <composant> ». Une fonctionnalité : lis-la en entier avec `archvow prompt --feature <fonctionnalité>` et construis ses composants un par un dans cet ordre, en lançant `archvow check` après chacun. Sans composant donné, implémente tout ce que `archvow next --base develop` liste comme prêt, un composant à la fois, et arrête-toi quand plus rien n'est prêt. Vérifie d'abord la branche, comme le dit AGENTS.md (tu ne la crées jamais).
