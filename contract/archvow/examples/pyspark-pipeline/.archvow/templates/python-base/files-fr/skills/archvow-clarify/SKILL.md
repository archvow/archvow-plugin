---
name: archvow-clarify
description: "Trouver ce que la spec d'un composant, ou les composants d'une fonctionnalité entre eux, ne définissent pas encore et l'enregistrer en questions marquées comme celles de l'IA - sans rien décider"
---

À donner : <composant> (et au plus combien de questions : 5 si rien n'est dit), ou une fonctionnalité à vérifier en entier avant de coder.

Lance `archvow prompt <composant> --clarify --max <N>` pour le composant que la personne donne (pour une fonctionnalité : `archvow prompt --feature <fonctionnalité> --clarify --max <N>`) et fais la tâche qu'il affiche : enregistre toi-même chaque question avec `archvow question add <composant> "<question>" --why "<ce qui la laisse ouverte>"`. Ne propose aucune réponse et n'écris aucun code. Termine par la liste des questions enregistrées : une personne répond à chacune, la garde ou la retire.
