# 5. Lancer archvow

> 🇬🇧 [English version](/en/05-running-archvow) · Version française

Tout ce que les plugins savent vient d'une commande archvow ; tout ce qu'ils changent passe par une
commande archvow. Cette page explique comment une commande est lancée. Fichiers : `ArchvowCli`,
`ArchvowProcessCommand`, `ArchvowInterpreter`, `ArchvowWsl` (IntelliJ) ; `vscode/src/cli.ts` (VS Code).

## Les règles

| Règle | Pourquoi | IntelliJ | VS Code |
|---|---|---|---|
| **Jamais de shell** | un texte de réponse ne doit jamais devenir une commande | `new ProcessBuilder(argv)` | `spawn(program, args, { shell: false })` |
| **Dans le dossier du projet** | archvow trouve `.archvow/` depuis là | `--root <projet>` et le dossier de travail | `cwd: root` |
| **Une commande à la fois** | deux écritures de la spec en même temps se marcheraient dessus | une seconde commande est refusée tant que la première tourne (`running`) | une file : ce qu'une personne demande passe avant les lectures de fond (`run(args, stdin, first)`) |
| **stdout et stderr à part** | un avertissement sur stderr ne doit jamais casser le JSON lu sur stdout | `Result.stdout`, `Result.stderr` | `CliResult.stdout`, `CliResult.stderr` |
| **Un délai maximal** | une commande bloquée ne doit pas bloquer l'IDE | `timeoutSeconds` (180 s par défaut), annulable depuis la barre de progression | `archvow.timeoutSeconds` |
| **Sortie bornée** | un projet énorme ne doit pas remplir la mémoire | 20 millions de caractères par flux (`MAX_OUTPUT`) | - |
| **`NO_COLOR=1`, `PYTHONIOENCODING=utf-8`** | ni couleurs ANSI dans le JSON, ni texte mal décodé | `ArchvowCli.prepare` | `env` de `spawn` |
| **`ARCHVOW_LOG=info`** (0.20.0) | ce qu'archvow a fait, pour le journal de session ; un niveau choisi par la personne est gardé | `ArchvowCli.prepare` | `env` de `spawn` |
| **Le journal et l'enveloppe d'erreur d'archvow à part** (0.20.0) | les lignes de journal d'archvow 1.23 sont sur stderr et, avec `--json`, son erreur est un objet sur stdout : ni l'une ni l'autre ne doit être lue comme sa réponse | `ArchvowOutput.split`, `ArchvowOutput.error` : `Result.log`, `Result.error` (stdout vaut alors `''`) | `splitLog()`, `errorOf()` : `CliResult.log`, `CliResult.error` |
| **Un seul message d'échec** (0.20.0) | la carte d'accueil, une notification, le refus d'un formulaire et le journal disent la même chose : le message d'archvow et son conseil (celui d'une erreur interne est le chemin de son rapport de plantage), sinon les dernières lignes de stderr | `ArchvowOutput.failure`, `ArchvowForms.refusal` | `failure()`, `alerts()` |

### Le journal de session (0.19.0)

Ce que le plugin a demandé à archvow : dans IntelliJ la fenêtre du bas *Archvow Output*, ouverte par le bouton ☷ (`ArchvowRunLog`, affichée par `ArchvowRunLogView`, et idea.log), dans VS
Code le canal de sortie *archvow* (`runlog.ts`, `archvow.showOutput`), les mêmes lignes :

- **une action d'une personne** (ce qui passe en tête de la file : `executeAction` dans IntelliJ,
  `run(args, stdin, first)` dans VS Code) : `▶` sa commande entière, son code de sortie, sa durée et la
  dernière ligne de texte d'archvow (`✔ Decision 1 of logging_setup made`) ;
- **un rafraîchissement** : une ligne pour toutes ses lectures (`Refresh  ·  9 reads  ·  2.4 s`), chacune au
  niveau debug (idea.log, ou *Developer: Set Log Level* dans VS Code) ;
- **un échec** : `✖` la commande et ce qu'archvow a dit (son erreur et son conseil, sinon les dernières
  lignes de stderr), puis les lignes de journal d'archvow pour cet appel (0.20.0 : archvow y masque déjà les
  valeurs, donc *Copier pour le support* les garde) ; `check` qui sort avec 1 parce qu'il trouve des erreurs
  est une réponse, pas un échec ; les lignes de journal d'archvow des autres appels vont au niveau debug ;
- un appel de plus d'une seconde est marqué `slow` ; une ligne d'en-tête par session : version du plugin,
  d'archvow et son contrat (0.20.0), le Python utilisé, le projet, l'IDE.

« Copier pour le support » (le bouton de la fenêtre *Archvow Output*, avec « masquer mes réponses » ; *archvow: Copy the archvow log for support* dans VS Code)
donne l'en-tête et les lignes avec leur heure, les mots d'une personne (réponses, justifications,
énoncés : `masked`) cachés si elle le demande.

Un rafraîchissement est un seul appel (0.21.0) : `archvow workspace --json` (archvow 1.25) donne ensemble
la spec, la vue de chaque composant, `check`, `next`, les briefs et le catalogue (`ArchvowProject.load`,
`ArchvowPanel.reload`). Après une action, le plugin fait le même appel (`refreshAfter` ; avant 0.21.0 un
rafraîchissement partiel lançait quatre commandes) ; le changement de vow qui suit vient de lui :
l'observateur des vows l'ignore (`ownChangeUntil`). Quand un vow change sur le disque, le plugin passe le
condensé de la dernière réponse (`--since`, gardé avec les réglages ou l'archvow qui l'a donnée) : si aucun
fichier du projet n'a changé, archvow répond `unchanged` et rien n'est redessiné. Jamais après une action
ni quand une personne rafraîchit.

## Où archvow est cherché

Dans cet ordre ; **le premier qui répond à `archvow --version`** (code 0 et « archvow » dans sa
réponse) lance les commandes. Le choix est gardé tant que les réglages et l'interpréteur de l'IDE ne
changent pas : une seule sonde par session.

1. **L'interpréteur Python de l'IDE pour le projet** : `<python> -m archvow`.
   - IntelliJ / PyCharm : le SDK Python du projet, sinon celui d'un module (`ArchvowInterpreter.projectPython`,
     par `ProjectRootManager` et `ModuleRootManager`, sans dépendre du plugin Python : le type de SDK
     est reconnu à son nom).
   - VS Code : l'interpréteur de l'extension Python (`ms-python.python`,
     `environments.getActiveEnvironmentPath`), relu quand il change.
   - Réglage *Use the project's Python interpreter first* / `archvow.useProjectInterpreter`, actif
     par défaut.
2. **Le Python des réglages** (Settings > Tools > Archvow / `archvow.python`) : `<python> -m archvow`.
3. **L'exécutable des réglages** (`archvow` par défaut, cherché dans le PATH de l'IDE).

`ArchvowProcessCommand.candidates` et `candidates()` font la liste ; `ArchvowCli.probe` et
`ArchvowCli.choose` (VS Code : la méthode `choose` d'`ArchvowCli` dans `cli.ts`) la parcourent. Quand
aucun ne répond, le message dit ce que chacun a répondu (« No module named archvow », « not found »...)
au lieu d'une erreur sans contexte.

Pourquoi l'interpréteur de l'IDE en premier : sur macOS, un IDE lancé depuis le Dock n'a pas le PATH
du terminal, donc `archvow` n'y est souvent pas ; le Python du projet, lui, est connu de l'IDE.

## Le champ Python, et `python3 -m archvow`

`ArchvowProcessCommand.build` accepte le chemin seul (`/chemin/venv/bin/python3`, la forme conseillée)
et l'ancienne forme `python3 -m archvow`, qu'il découpe en trois arguments (sinon le système
chercherait un fichier nommé « python3 -m archvow »). Un chemin avec des espaces reste un seul
argument ; des guillemets autour sont retirés. Toute autre option dans le champ est refusée.

## archvow dans WSL, l'IDE sous Windows

Un programme Linux ne se lance pas par son chemin Windows (`\\wsl.localhost\Ubuntu\home\...\python3`) :
Windows y voit un fichier, pas un programme. `ArchvowWsl` (VS Code : `wslProgram()`, `linuxPath()`
dans `cli.ts`) le lance par `wsl.exe` :

```
wsl.exe -d Ubuntu --cd /home/marc/proj --exec /home/marc/.venv/bin/python3 -m archvow --root /home/marc/proj check --json
```

| Forme reconnue | Distribution |
|---|---|
| `\\wsl.localhost\Ubuntu\...`, `\\wsl$\Ubuntu\...`, `//wsl.localhost/Ubuntu/...` | celle du chemin |
| `wsl://Ubuntu/...` | celle du chemin |
| `wsl:///...` ou un chemin Linux `/home/...` (sous Windows seulement) | celle par défaut |
| l'interpréteur WSL de PyCharm (SDK « cible ») | lue dans les données du SDK, par réflexion (`ArchvowInterpreter.wslDistribution`) |

- Le dossier du projet devient un chemin Linux (`C:\work\p` -> `/mnt/c/work/p`, `\\wsl.localhost\Ubuntu\home\p` -> `/home/p`),
  comme chaque argument qui est un chemin Windows (`ArchvowWsl.argument`).
- `--exec` : pas de shell côté Linux non plus.
- `WSLENV` transmet `NO_COLOR`, `PYTHONIOENCODING` et `ARCHVOW_TEMPLATES` (liste de dossiers, traduite
  en chemins Linux : `/l`).
- La première réponse peut attendre que WSL démarre : la sonde attend 60 s par `wsl.exe` (10 s sinon).
- Avec VS Code ouvert *dans* WSL (Remote - WSL), l'extension tourne côté Linux : rien de tout cela ne sert.

## Installer archvow (0.12.0)

Quand archvow ne démarre pas, ou ne parle pas le contrat du plugin (0.20.0, page [Le contrat](/04-contract)), les
plugins proposent **Installer archvow 1.25.0** ; rien n'est installé au démarrage ni sans un clic.
`ArchvowInstall` (IntelliJ) et `vscode/src/install.ts` (VS Code) portent les mêmes règles :

| Question | Réponse |
|---|---|
| Dans quel Python ? | le `.venv` du projet, puis `venv` (`venvPythons()`), puis l'interpréteur de l'IDE, puis le Python des réglages ; jamais un `python` nu choisi à la place de la personne (`installPython()`, `ArchvowInstall.python`) : pas de Python trouvé, la carte dit **Configurer Python** |
| Quelle version ? | `archvow==1.25.0` (`requirement()`) : exactement l'archvow avec lequel les plugins sont testés (0.20.0) ; une fois installé, tout archvow du contrat est accepté |
| D'où ? | le réglage *Installer archvow depuis* / `archvow.installFrom` (`installSource()`) : vide = la configuration de pip (pip.conf, `PIP_INDEX_URL`), une URL http(s) = `--index-url`, un fichier `.whl`, ou un dossier de wheels = `--no-index --find-links` (`pipArgs()`) |
| Comment ? | la commande est affichée (`displayCommand()`), puis lancée seulement après **Confirmer et exécuter** : sans shell, dans le dossier du projet, 10 minutes au plus, `PIP_DISABLE_PIP_VERSION_CHECK=1` ; un Python dans WSL passe par `wsl.exe`, comme archvow (`installCommand()`, `ArchvowInstall.command`) |
| Ensuite ? | réussite : les réglages gardent ce Python, la version est sondée à nouveau, l'espace de travail se recharge ; échec : la sortie de pip, rien n'est enregistré |

VS Code : la carte est `installCard()` sur la page d'accueil (et dans la Vue d'ensemble pour une autre
version) ; l'hôte garde l'offre (`offerInstall`), demande (`askInstall`) et lance pip sur le jeton
confirmé seulement (`runInstall`). IntelliJ : le bouton **Installer archvow 1.17** de la carte d'accueil
et l'action de la notification de version, `askInstall` / `runInstall` dans `ArchvowPanel`, la
confirmation un `ArchvowConfirmPanel`.

## Les commandes réservées aux personnes

archvow 1.17 refuse ses commandes de décision (décisions, dangers, règles...) quand un agent IA est
parmi les processus parents (ARCH051). Les commandes des plugins viennent de l'IDE, pas d'un agent :
elles passent. Si archvow en arrête une, le plugin affiche son message tel quel.

## Tester sans IDE

`LaunchCommandRegression` lance un faux module `archvow` avec un vrai Python ; `WslRegression` vérifie
les chemins, l'ordre, la sonde et un `wsl.exe` de substitution ; `vscode/src/test/cli.test.ts` fait de
même pour VS Code avec un faux `archvow` écrit en Node. `InstallRegression` et
`vscode/src/test/install.test.ts` vérifient l'installation : le Python choisi, la ligne pip pour chaque
source, WSL, et que pip ne se lance que depuis une confirmation.
