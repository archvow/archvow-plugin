# Règles

`archvow check` affiche `fichier:ligne:colonne: CODE message`, et chaque message dit comment
corriger. Cette page liste toutes les règles. La liste propre à un projet, avec les gravités en
vigueur, est dans son `.archvow/spec.md` (Contrôles), une section par famille : ARCH, STYLE,
celle du template (SPARK, FASTAPI), GDPR, TST et DORA quand elles sont activées, celles d'une
entreprise. Les règles propres au projet, écrites avec `archvow rule add`, sont sur la même page
(Règles).

La gravité **error** fait échouer le contrôle (et le commit, et la CI) ; **warning** est
seulement signalée. Les règles dont la correction dépend d'une décision des architectes le
disent.

Les règles sur l'endroit où vit le code ont besoin de la couche du fichier (son dossier, ou
`layer_paths` pour le code adopté là où il est - [Adopter un projet existant](adopting-an-existing-project.md)) :
`ARCH005` à `ARCH007`, `SPK001` à `SPK003`, `FAPI002`, `FAPI003`. Toutes les autres règles de code
s'appliquent à tout fichier Python du paquet, dans une couche ou non. `archvow check` finit par le
compte par règle ; un constat est signalé une fois par fichier, ligne et message (un import de
neuf noms est un seul import).

## Le code suit la spec

| Code | Échoue quand | Correction |
|---|---|---|
| ARCH001 | un module Python d'une couche n'est pas déclaré dans la spec | `archvow component add` le composant, ou en faire une fonction privée dans un fichier déclaré |
| ARCH002 | un nom public (sans `_` au début), ou une route même sur une fonction privée, n'est pas déclaré | le renommer avec un `_` au début, ou le déclarer (`archvow component edit --signature`, ou un nouveau composant) |
| ARCH003 | un symbole déclaré manque, ou n'est encore qu'un bouche-trou - une fonction avec seulement `pass`, `...`, une docstring ou `raise NotImplementedError`, une classe vide autre qu'une exception (avec `--complete`) | l'écrire, comme la fiche de spec le nomme |
| ARCH004 | une signature, une annotation ou la méthode et le chemin d'une route diffèrent de la spec | suivre la fiche de spec, ou changer la spec (`archvow component edit --signature`, quand le type le permet) |
| ARCH005 | une couche importe une couche qui lui est interdite | déplacer le code dans la bonne couche ; le tableau des couches est dans la fiche de spec et `.archvow/README.md` |
| ARCH006 | un composant en importe un autre absent de ses `uses` | `archvow component edit <composant> -u a,b` (revue d'un architecte) |
| ARCH007 | un import interdit dans cette couche (`os`, `pathlib`, `requests`... dans `p1_domain`) | déplacer les E/S dans un lecteur ou un écrivain |
| ARCH008 | un fichier déclaré n'existe pas (avec `--complete`) | écrire le composant, ou le supprimer avec `archvow component remove` |
| ARCH009 | une entrée invalide dans un engagement (`spec.vow`), ou un template inconnu | le message nomme l'entrée ; pour un template d'entreprise, définir `ARCHVOW_TEMPLATES` |
| ARCH010 | un fichier du paquet hors de toute couche (un module y est un `ARCH001`, qui dit comment le rattacher) | le déplacer dans le dossier d'une couche, ou le rattacher dans `layer_paths` |
| ARCH011 | une exemption (`ignore`) sans raison | `# archvow: ignore[CODE] -- pourquoi` |
| ARCH012 | (warning) la version du template installé diffère de celle du projet | `archvow init <package> --force`, relire, commiter ([montée de version](project-management.md#nouvelle-version-darchvow-dans-un-projet)) |
| ARCH013 | un fichier est illisible | corriger l'erreur de syntaxe |
| ARCH014 | une entrée d'un engagement diffère de ce que génère son template (modifiée à la main) | annuler la modification manuelle ; changer la spec avec les commandes |
| ARCH015 | un fichier Python hors de l'architecture (aucune couche, chemin non autorisé) | en faire un composant, le déplacer sous `tests/`, ou geler l'existant avec `archvow freeze` |
| ARCH016 | un test hors de l'organisation des tests | `tests/<couche>/[<groupe>/]test_<composant>.py`, ou `tests/integration/` pour les scénarios de bout en bout |
| ARCH017 | un composant implémenté sans son test | écrire `tests/<couche>/test_<composant>.py` |
| ARCH018 | l'en-tête du fichier manque, est incomplet, contient encore un `<...>`, ou nomme un assistant IA non approuvé | copier l'en-tête de la fiche de spec et le remplir : `@author`, `@assisted` (un assistant approuvé, ou `none`), `@date` |
| ARCH023 | l'import d'un paquet tiers non approuvé | utiliser la bibliothèque standard ou un paquet approuvé ; un architecte l'approuve avec `archvow init <package> --force -a dependencies=a,b`. Importé hors du dossier pour lequel il est approuvé (`dependencies_by_path`, facultatif) : l'appeler depuis ce dossier ([Bibliothèques partagées](shared-libraries.md)) |
| ARCH024 | un paquet de `requirements.txt` ou de `pyproject.toml` (dépendances `[project]`) que personne n'a approuvé - une erreur en mode strict, une erreur à `check --complete` en mode guided, un avertissement en mode free | un architecte l'approuve avec `archvow init <package> --force -a dependencies=a,b` (le nom de requirements ou le nom importé) ; les paquets du template lui-même sont approuvés |
| ARCH040 | les fichiers d'un assistant IA non approuvé (`.cursor/`, `GEMINI.md`, `.continue/`...) | les supprimer, ou un architecte approuve l'assistant (`-a ai_tools=...`) |
| ARCH041 | avec des relecteurs IA approuvés : un rapport de relecture dans `reviews/` par un outil qui n'est pas un relecteur approuvé, ou sans `@reviewer` | demander à un relecteur approuvé, ou un architecte l'approuve (`-a reviewers=...`) ; voir [Relecture IA](ai-assistants.md#relecture-ia-optionnelle) |
| ARCH042 | avec des relecteurs IA approuvés : une auto-relecture : le relecteur est nommé dans l'en-tête `@assisted` d'un fichier qu'il a relu | demander à un autre relecteur approuvé, ou une personne relit |

## Décisions et sûreté

| Code | Échoue quand | Correction |
|---|---|---|
| ARCH030 | un danger critique ou élevé n'est empêché par aucun invariant | le message donne la commande : `archvow invariant add <composant> "..." --prevents H<n>` |
| ARCH031 | un invariant d'un composant implémenté n'est pas encore testé | écrire le test marqué `@pytest.mark.invariant("<composant>.INV<n>")` (le message dit de quel type et dans quel fichier, et nomme une marque presque juste - `INV1` pour `clean_orders.INV1`) |
| ARCH032 | le test de l'invariant n'est pas du type qu'il exige (un test simple là où il faut un test Hypothesis...) | en faire un test Hypothesis `@given` (ou, pour une vérification d'expert, la garde / règle / le fichier qu'il nomme) |
| ARCH033 | du code existe alors qu'une décision reste à prendre | `archvow decision make <composant> <n> "..."` (une personne) |
| ARCH034 | du code repose sur une hypothèse que personne n'a encore relue | `archvow assumption accept <composant> <n>` ou `reject` (une personne ; une hypothèse rejetée ne bloque plus, le code ne doit pas s'y fier) |
| ARCH035 | (warning) une question de sûreté d'un composant n'a pas de réponse ; `check` les compte sur une ligne, `check --all` les liste | `archvow specify <composant>`, ou `hazard add <composant> S<numéro> ...` / `hazard dismiss <composant> S<numéro> "..."` (`archvow show <composant>` les numérote) |
| ARCH036 | un danger, une mise à l'écart, un invariant ou une règle du projet invalide (composant ou type inconnu, pas de texte) | le message nomme l'entrée |
| ARCH037 | l'invariant est testé trop faiblement pour la gravité du danger (un danger critique exige un test Hypothesis ou plus fort) | le message donne la commande pour un invariant plus fort |
| ARCH038 | (avertissement) une décision prise ou une règle d'un composant codé n'a aucun test marqué avec elle (`check` les compte sur une ligne ; `--all` les liste) | marquer le test : `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (un test peut en porter plusieurs) |
| ARCH039 | (avertissement) une section d'un brief a changé (ou disparu) après qu'on en a tiré des composants, questions ou règles avec `--from` | relire ce que le message nomme, puis `archvow brief accept <brief>#<section>` |
| ARCH043 | (avertissement ; une erreur à `check --complete` en mode guided) une décision proposée par l'IA (`archvow decision propose`, gardée dans `.archvow/proposals.vow`) qu'aucune personne n'a encore relue | une personne lance `archvow decision accept <composant> <n>`, ou `archvow decision make <composant> <n> "..."` pour la remplacer |
| ARCH044 | (avertissement) la tâche qu'une IA lit pour un composant - son `spec.md` - dépasse 4000 tokens (environ 16 000 caractères ; `archvow next` et `archvow prompt` affichent chaque taille) : une tâche longue est moins bien suivie | découper le composant en plus petits, ou donner à l'IA une tâche à la fois (`archvow prompt <composant> --task 1`) |
| ARCH045 | (avertissement ; une erreur en mode strict et avec `check --complete`) un test marqué comme preuve d'un invariant, d'une décision ou d'une règle ne peut pas échouer : aucun assert, `assert True`, `assert x == x`, un corps vide ou `pass` | faire un assert sur ce que le composant renvoie, ou utiliser `pytest.raises`. Comptent comme vérifications : un assert qui peut échouer, `pytest.raises` / `pytest.warns`, un appel nommé `assert...` (`assertDataFrameEqual`, `self.assertEqual`...), `pytest.fail`, une fonction du même fichier de test qui fait l'un de ceux-là, une fonction importée du dossier tests |
| ARCH046 | avec `check --test-report test-results.xml` (le rapport JUnit de pytest) : un test marqué - d'un invariant, d'une décision, d'une règle ou d'un exemple métier - n'a pas tourné, a été sauté ou a échoué ; reconnu par son fichier et son nom, chaque cas d'un test paramétré compris | le faire tourner et réussir ; la CI générée lance `archvow check --test-report test-results.xml` après pytest |
| ARCH055 | (toujours active) les fichiers d'un exemple métier dans `tests/examples/<composant>/<exemple>/` (son `input/` et son `expected/`) diffèrent de ceux qu'une personne a enregistrés avec `archvow example add`, ou ont disparu - leur empreinte a changé (les fins de ligne ne comptent pas) | les remettre (`git checkout -- tests/examples/...`) ; quand le métier a vraiment changé le résultat attendu, une personne remplace les fichiers et relance `archvow example add <composant> <exemple>` |
| ARCH056 | (avertissement ; une erreur en mode strict et avec `check --complete`) un exemple métier d'un composant codé n'a pas de test marqué `@pytest.mark.example("<exemple>")` | écrire le test qui lit son `input/`, lance le composant et compare avec son `expected/` |
| ARCH057 | (avertissement ; une erreur avec `check --complete`) la description, la signature ou les routes d'un composant ont changé (`archvow component edit`) après que des décisions, dangers, invariants, réponses « sans objet », hypothèses acceptées ou exemples métier ont été enregistrés pour lui : chacun peut ne plus tenir (« montant > 0 » quand la description dit maintenant « > 1 »). archvow ne supprime rien ; `archvow show` les liste, la fiche de spec arrête l'IA, `archvow next` dit que le composant est bloqué | une personne regarde chacun : celui qui ne tient plus est enregistré à nouveau (`archvow decision make <composant> <n> "..."`, un danger supprimé puis ajouté à nouveau...), ce qui le retire de la liste ; quand tous tiennent encore, `archvow component accept <composant>` |
| ARCH050 | un fichier dans `.archvow/` qu'archvow n'écrit pas : CODEOWNERS liste le dossier chemin par chemin, donc personne n'en serait propriétaire (toujours actif) | le sortir de `.archvow/` |
| ARCH051 | un agent IA lance archvow hors d'un sandbox : il peut écrire hors du projet (archvow essaie un fichier vide dans le dossier personnel). Seulement quand la réponse `sandbox` du projet est yes (le défaut en mode strict). archvow s'arrête avant la commande, toujours actif | lancer l'agent dans son sandbox - [Sécurité de l'agent](agent-security.md) |
| ARCH052 | un agent IA lance archvow en mode YOLO : sa ligne de commande a une option qui le laisse agir sans demander (`--dangerously-skip-permissions`, `--yolo`, `--allow-all`...). archvow s'arrête avant la commande, toujours actif | le lancer sans cette option - [Sécurité de l'agent](agent-security.md) |
| ARCH053 | (un avertissement ; une erreur quand archvow a arrêté l'agent, ou en mode strict quand l'agent n'a jamais lancé archvow) un fichier écrit par un agent IA (`@assisted <outil>`) est commité et archvow ne peut pas dire que cet agent tournait protégé lors de son dernier passage dans ce projet ; avec `check --base`, un commit d'un tel fichier n'a pas de ligne `Archvow-Agent:` (fait sans les hooks Git). Toujours actif | laisser l'agent lancer `archvow check` protégé, puis commiter de nouveau avec les hooks actifs |
| ARCH054 | un agent IA lance une commande que seule une personne lance : une commande qui décide ou change la spec (`component`, `decision make`, `decision add`, `decision accept`, `question remove`, `assumption accept`/`reject`, `hazard`, `invariant`, `rule`, `column`, `dependency`, `brief`, `specify`, `freeze`, `init`). archvow s'arrête avant la commande, toujours actif. L'IA peut toujours lancer `question add`, `assumption add` et, en modes guided et free, `decision propose` | l'agent donne la commande ; la personne la lance dans son propre terminal - [Sécurité de l'agent](agent-security.md) |

## Merge requests (`check --base <branche>`)

| Code | Échoue quand | Correction |
|---|---|---|
| ARCH019 | (avertissement) un composant travaille sur des colonnes écrites nulle part : un lecteur ou un écrivain sans son schéma, un repository sans son modèle | `archvow component add schema <nom> -d "..."`, puis `archvow component edit <composant> -u <nom>_schema` |
| ARCH020 | une garde est affaiblie : une gravité ou une vérification plus faible, un danger ou un invariant supprimé, une règle ruff ou un réglage des tests assouplis, un test supprimé, un élément de la spec dont le test marqué a disparu, perdu sa marque ou est sauté sur la branche, un exemple métier retiré de la spec, un fichier de garde modifié, davantage de violations gelées, un nouvel assistant IA ou une nouvelle dépendance | l'annuler, ou les architectes l'approuvent (label `architecture-approved`, `ARCHITECTURE_APPROVED=true`, ou `--accept-weakening`) |
| ARCH021 | (warning) de nouvelles exemptions | vérifier chaque raison pendant la revue |
| ARCH022 | le changement est trop gros pour être relu (par défaut : plus de 30 fichiers, 1000 lignes ajoutées ou 300 supprimées, hors `.archvow/`) | découper la merge request, ou les architectes l'approuvent. Pour rester en dessous : `archvow next --base develop` n'indique plus rien comme ready une fois le changement plein (les assistants IA le lancent avant chaque composant) |

## PySpark (template `pyspark-pipeline`)

| Code | Échoue quand | Correction |
|---|---|---|
| SPK001 | des E/S Spark ou fichier hors de `p2_io` : `spark.read`/`write`, `spark.table`, fichiers RDD (`textFile`, `saveAsTextFile`...), flux (`socketTextStream`), MLlib (`MLUtils.load*`, `model.save(sc, path)`, `Model.load(sc, path)`), `spark.sql("CREATE/INSERT/LOAD/DROP ...")`, `open()` | un lecteur ou un écrivain |
| SPK002 | une action (`collect`, `count`, `show`, `toPandas`, `cache`...) dans `p1_domain` | garder les transformations paresseuses ; agir dans le job ou l'écrivain |
| SPK003 | une SparkSession créée hors de `p0_common` | `get_spark_session` de `p0_common.spark` |
| SPK004 | `join()` sans `how=` | `df.join(other, on=..., how="left")` |
| SPK005 | une jointure à droite | inverser les DataFrames, jointure à gauche |
| SPK006 | (warning) une UDF ou UDTF Python ; une UDF pandas ou `applyInPandas`/`mapInPandas` (vectorisées : acceptables quand les fonctions natives ne suffisent pas) | une expression native de `pyspark.sql.functions` |
| SPK007 | un import PySpark non standard | `from pyspark.sql import functions as F, types as T, Window as W` |
| SPK008 | (warning) une continuation de ligne par barre oblique inverse | mettre l'expression entre parenthèses |
| SPK009 | (warning) une chaîne de plus de 5 méthodes | nommer les étapes intermédiaires |
| SPK010 | (warning) une fenêtre ordonnée sans cadre explicite | `.rowsBetween(...)` ou `.rangeBetween(...)` |
| SPK011 | un `Window.partitionBy()` vide | partitionner par une clé (sinon toutes les lignes dans une seule partition) |
| SPK012 | une colonne vide remplie avec `''` ou `'NA'` | `F.lit(None)` |
| SPK013 | (warning) `withColumnRenamed()` | `.alias()` dans un `select` |
| SPK014 | (warning) `distinct()` / `dropDuplicates()` après une jointure (même chaîne, ou un nom affecté depuis une jointure dans la fonction) | corriger les clés de jointure, ou le décider dans la spec |

## FastAPI (template `fastapi-service`)

| Code | Échoue quand | Correction |
|---|---|---|
| FAPI001 | un appel bloquant connu (`requests.*`, `httpx.get`..., `time.sleep`, `subprocess.run`, `urlopen`, `open()`) directement dans un `async def` | une bibliothèque asynchrone (`httpx.AsyncClient`, `asyncio.sleep`), ou faire de la fonction un simple `def` |
| FAPI002 | `HTTPException` hors de `p4_api` et des dépendances de `p0_common` | lever une erreur métier (un composant `error`) ; l'app la traduit en statut |
| FAPI003 | `FastAPI()` créé hors du composant app | seul `create_app()` du composant app le crée |
| FAPI004 | un gestionnaire `on_event` de démarrage ou d'arrêt | le `lifespan` de l'app |
| FAPI005 | (warning) une route sans `response_model=` ni type de retour (une route avec `response_class=`, ou qui renvoie un `...Response(...)`, n'a pas de modèle à vérifier) | déclarer ce qu'elle renvoie : un modèle Pydantic |

## Taille

| Code | Échoue quand | Correction |
|---|---|---|
| STY001 | (warning) un fichier de plus de 250 lignes | découper le composant |
| STY002 | (warning) un corps de fonction de plus de 70 lignes (docstring et signature non comptées) | extraire des fonctions privées |
| STY003 | (warning) un script shell (`*.sh`, hors dossiers cachés) de plus de 150 lignes | mettre la logique dans un composant Python testé ; le script reste un lanceur |
| STY004 | (warning) un fichier Python avec plus de lignes de commentaire que 30 % de ses lignes de code (à partir de 20 lignes de code ; le bloc de commentaires en tête - licence, copyright - ne compte pas) | renommer, extraire une fonction, mettre l'explication dans la docstring ; `ERA` de ruff refuse déjà le code commenté |

## Données personnelles - RGPD (réponse `rules` : GDPR, activée par défaut)

Les règles lisent la classe de chaque colonne (`archvow column set`) et la suivent à travers les
`uses` de la spec - voir [Conformité](compliance.md).

| Code | Échoue quand | Correction |
|---|---|---|
| GDPR001 | une colonne définie dans le code d'un schéma ou d'un modèle (`StructField("x")`, un champ de modèle) n'est pas classée - dès le premier jour, elle bloque le code | `archvow column set <composant> x personal\|special\|none` |
| GDPR002 | un job (ou router) codé qui manipule des données personnelles n'a pas de réponse à P1, P2 (finalité, base légale) - ni à la condition de l'art. 9 pour les données particulières | `archvow decision make <composant> P1 "..."` |
| GDPR003 | un writer (ou repository) codé de données personnelles n'a pas de réponse sur la conservation et l'effacement | `archvow decision make <composant> P1 "..."` |
| GDPR004 | un composant qui manipule des données personnelles appelle `.show()`, `.display()` ou `.explain()`, ou imprime ou journalise des lignes collectées (`collect`, `take`, `head`, `toPandas`...) | journaliser des comptes, vérifier les lignes dans un test |
| GDPR005 | des données de catégorie particulière arrivent à un writer sans étape qui les pseudonymise, et sans danger `exposed` critique empêché par un invariant testé (cette question ne peut pas être « sans objet ») | `archvow column set <étape> <colonnes> --pseudonymises`, ou le danger et son invariant |
| GDPR006 | `partitionBy` ou `bucketBy` sur une colonne personnelle (ses valeurs deviennent des noms de dossiers) | partitionner selon une colonne non personnelle |
| GDPR007 | une route FastAPI a un champ personnel dans son chemin ou en paramètre de requête (il finit dans les journaux d'accès) | utiliser un id, l'envoyer dans le corps |
| GDPR008 | un writer, repository ou client codé de données personnelles n'a pas de réponse sur sa localisation (pays ou région) et la garantie de transfert (art. 46) | `archvow decision make <composant> P2 "..."` |
| GDPR009 | (warning) une colonne classée n'est plus dans le code | renommer la classification, ou la retirer avec la colonne |

## Données de test (réponse `rules` : TST, activée par défaut)

| Code | Échoue quand | Correction |
|---|---|---|
| TST001 | un test lit une vraie table (`spark.table`, `.jdbc`), un stockage cloud (`s3://`, `abfss://`, `gs://`, `hdfs://`, `dbfs:`...), une URL de base de données non locale, ou un chemin absolu | construire les lignes dans le test, ou un petit fichier sous `tests/` ; une base locale ou en mémoire |
| TST002 | un test ou un fichier de données sous `tests/` contient un identifiant d'apparence réelle : un e-mail hors example.com/.org/.net, un IBAN à clé valide, un numéro de carte qui passe le contrôle de Luhn, un numéro de sécurité sociale à clé valide, un numéro de téléphone | des valeurs inventées : nom@example.com, FR76 3000 6000 0112 3456 7890 189 |
| TST003 | un fichier de données binaire sous `tests/` (Parquet, Avro, ORC, Excel, une base), ou un fichier texte de plus de 200 Ko ou 1000 lignes (`max_kb`, `max_rows`) | quelques lignes inventées |

## DORA (réponse `rules` : DORA, sur demande)

| Code | Échoue quand | Correction |
|---|---|---|
| DORA001 | un composant n'a pas de ticket (sauf les fondations du template) ; le hook commit-msg met aussi le ticket de la branche dans chaque message de commit | `archvow component edit <composant> -s <ticket>` |
| DORA002 | moins de deux personnes peuvent approuver les changements de garde (CODEOWNERS n'a qu'un utilisateur et aucun groupe) | `init --force -a architects="@premier @second"` (ou un groupe) |
| DORA003 | un paquet de requirements.txt n'est pas dans le registre des tiers, ou un paquet critique n'a pas de version figée par `==` | `archvow dependency add <paquet> --supplier ... --licence ... --criticality ...` |
| DORA004 | le test d'un invariant peut être sauté (`skip`, `skipif`, `xfail`, `pytest.skip()`), ou un job de CI peut échouer sans bloquer (`allow_failure`, `continue-on-error`). Que les tests ont tourné et réussi, c'est ARCH046, pour tout projet | le laisser tourner ; qu'un job en échec bloque la fusion |
| DORA005 | un job, un point d'entrée ou une app codé n'a pas de réponse à O1, O2 (détection des pannes et alerte, reprise avec son RTO et son RPO) | `archvow decision make <composant> O1 "..."` |
| DORA006 | (warning) la licence du registre diffère de celle des métadonnées du paquet installé | corriger le registre |

## Les règles propres à une entreprise (`custom_rules`)

Un template d'entreprise déclare des règles comme des données, sans Python - imports, appels,
textes ou fichiers interdits, ou une question posée à un type de composant (C1, C2...). Leurs
codes sont ceux de l'entreprise (par exemple `ACME001`), leur section est ACME dans `.archvow/spec.md` (Contrôles).
Voir [Templates d'entreprise](company-templates.md#vos-propres-interdits-et-questions).

## Ruff

Le `ruff.toml` généré applique PEP 8 (`E`, `W`, `N`), les imports (`I`), les docstrings (`D`),
les annotations (`ANN`), la complexité (`C90`, `PL`), les bugs probables (`B`, `BLE`), l'absence
de code commenté (`ERA`) et les règles de sécurité (`S`) : un mot de passe ou un secret écrit en
dur fait échouer ruff, donc le hook Git et la CI le refusent.

## Faire taire un signalement

Quand un signalement est faux pour une ligne, dites pourquoi sur cette ligne :

```python
rows = df.distinct()  # archvow: ignore[SPK014] -- la source renvoie des fichiers entiers, décidé dans US-12
```

Plusieurs codes : `ignore[SPK006, SPK009]`. Une exemption sans raison est elle-même une erreur
(`ARCH011`), et chaque nouvelle exemption est listée dans la merge request (`ARCH021`).

## Vérifications d'expert pour un invariant

Par défaut, un invariant est vérifié par un test (un test Hypothesis pour un danger critique).
Pour les rares cas où un test n'est pas la bonne vérification, `invariant add` accepte une
option cachée, `--verify` :

| `--verify` | Ce que cherche `archvow check` |
|---|---|
| `example_test` | un test marqué `@pytest.mark.invariant("clean_orders.INV1")` (par défaut) |
| `property_test` | le même marqueur sur un test Hypothesis `@given` (par défaut pour un danger critique) |
| `runtime` | `# archvow: enforces[vat_writer.INV1]` à l'endroit de la garde, dans le fichier du composant |
| `static` | une règle archvow en gravité error (`--rule SPK001`) |
| `model_check`, `proof` | un fichier de modèle ou de démonstration vérifié par son propre outil en CI (`--evidence chemin`) |

Le template fixe la vérification la plus faible que chaque gravité accepte (`verification_floor`).

## Changer un niveau ou une limite

Pour tous les projets d'un template, dans ses `rules:` ([templates d'entreprise](company-templates.md)).
Pour un seul projet, dans le groupe `rules` de son `.archvow/spec.vow` (à la main, la seule
partie d'un engagement qui l'est ; [le format .vow](vow-format.md)) - un changement de garde, approuvé
par les architectes :

```vow
rules
  SPK009
    max_chain 4
  STY001
    max_lines 300
  STY003
    max_lines 80
  STY004
    max_percent 20
    min_code_lines 30
  ARCH022
    max_files 50
    max_added_lines 2000
  ARCH044
    max_tokens 6000
  SPK006
    severity error
```

`severity` vaut `error`, `warning` ou `off`.
