# Security policy

## Supported versions

Only the latest release of the plugins receives security fixes. Each plugin release is made for one archvow release and its contract.

| Plugins | For archvow (contract) | Supported |
| ------- | ---------------------- | --------- |
| 0.26.x  | 2.6.x (2.4)            | yes       |
| < 0.26  | earlier                | no: update the plugin and archvow together |

## Reporting a vulnerability

Please report a vulnerability **privately**, not in a public issue, pull request or discussion:

- on GitHub, use this repository's **Security** tab → **Report a vulnerability** (private vulnerability reporting);
- if your organization received the plugins from an internal share, report it to the team that distributed them, who forward it here.

Include the plugin (VS Code or IntelliJ) and its version, the archvow version (`archvow --version`), the IDE and its version, the operating system, and the steps that show the problem.

What happens next:

- you get an acknowledgement within **5 working days**;
- we confirm the problem, or explain why it is not one, within **15 working days**;
- a confirmed vulnerability is fixed in a patch release (0.26.x). Its changelog entry names the problem once the release is out, and credits you unless you ask us not to.

## What the plugins do on your machine

These points help you judge a report and deploy the plugins:

- The plugins run archvow and pip only. They never use a shell; each command is an argument list.
- They run nothing in a project you have not trusted: VS Code's Restricted Mode, or IntelliJ's safe mode ("Preview in Safe Mode").
- Python is started with `-P`, so a file or folder of the project named `archvow` or `pip` is never run in place of the real one.
- Installing archvow is never automatic. The exact pip command is shown first and runs only after you confirm it.
- The plugins send nothing over the network. Only pip, when you confirm an install, reaches your package index.
