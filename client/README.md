# Work Overview AI Client

This package is the installable agent layer for the Work Overview MCP server. It contains the
client configuration and future agent extensions; the server remains a separately installed Spring
Boot application.

## Install in Codex

Run the installer from this directory. It installs the packaged workflow and tool guidance in
Codex's user-level instructions, writes the server registration to Codex configuration, then
starts `codex mcp login` with the configured scope.
Codex CLI, the Codex IDE extension, and the ChatGPT desktop app share MCP configuration on the
same host.
Each run signs out and replaces any existing registration with the same name so that a changed
tenant or client ID does not leave stale Codex OAuth settings behind.

In IntelliJ, run the shared `Install MCP Client` configuration to use the default local HTTPS
endpoint. First generate and trust the local development certificate as described in
`server/README.md`, and add the exact HTTPS MCP URL as an Application ID URI in Entra.

```powershell
# Local server (the default)
.\install.ps1

# Network server
$env:WORK_OVERVIEW_MCP_URL = 'https://work-overview.example.internal/mcp'
.\install.ps1
```

Pass `-ServerUrl` to override the environment variable for one installation, or use `-DryRun` to
preview the Codex commands without changing the local configuration:

```powershell
.\install.ps1 -ServerUrl https://localhost:8080/mcp -DryRun
```

The installed endpoint can be inspected with `codex mcp list`. In this repository, the installer
reads the public client ID, server URL, and OAuth scope from `server/.env` when it is available. For a separately
installed client package, set the existing Work Overview app registration's client ID before running
the installer, then sign in through the browser:

```powershell
$env:WORK_OVERVIEW_ENTRA_CLIENT_ID = '<Work Overview app client ID>'
./install.ps1
```

The client ID is public. Do not put the backend client secret in this package or on a client
machine. Every installer run replaces the MCP registration and opens the Entra sign-in flow.
The server advertises its MCP URL as the OAuth resource, matching the app's Application ID URI.
Register the exact OAuth callback URL for this server under the app's mobile and desktop
redirect URIs. The installer opens the browser for sign-in and waits for the local callback.
If the browser cannot open, Codex prints the authorization URL as a fallback. The backend client
secret stays in `server/.env`.

## Automatic instructions from every directory

The installer adds a managed Work Overview block to `$CODEX_HOME/AGENTS.md`, defaulting to
`~/.codex/AGENTS.md`. Codex reads this global file at the start of every CLI session, independent
of the current working directory. No profile flag or repository checkout is required after
installation. See [Codex instruction discovery](https://learn.chatgpt.com/docs/agent-configuration/agents-md).

The block includes the workflow from this package's `AGENTS.md`. The 15 tool-specific skills
are installed separately under `$CODEX_HOME/skills/work-overview-<skill-folder>/SKILL.md`,
with their original metadata intact, so they appear in `/skills` and can load on demand.
Neither the workflow nor the installed skills depend on the repository's location.
Their scope is limited to Work Overview requests and tools; unrelated sessions retain their
normal behavior. The MCP connection and Entra authentication are still required to access data.

Existing personal instructions outside the marked block are preserved. Reinstallation replaces
only the block and installer-owned skill files; it does not duplicate them. Unmanaged skill
directories are not overwritten. Existing instruction files are backed up once alongside the original
with the suffix `.work-overview.bak`. If a non-empty global `AGENTS.override.md` exists, the
installer updates its managed block too because Codex gives that file precedence over `AGENTS.md`.

For an already registered and authenticated client, update the workflow and skills without signing
out or changing the MCP registration:

```powershell
.\install.ps1 -InstructionsOnly -DryRun
.\install.ps1 -InstructionsOnly
```

Close the old CLI session and start a new one from any directory:

```powershell
codex
```

Installation applies to the current user's Codex home. A different account, machine, or
`CODEX_HOME` needs its own installation. Codex's configured instruction byte limit and later
project-specific instructions still apply. Editing the package sources requires rerunning the
instruction installer; already open sessions do not reload them automatically.

To remove the installed workflow, remove only the block between `BEGIN WORK OVERVIEW CLIENT`
and `END WORK OVERVIEW CLIENT` from the global files. Leave other personal instructions intact.
The installed skill directories use the `work-overview-` prefix and contain a
`.work-overview-client` ownership marker; remove those directories too when uninstalling the skills.

## Package structure

- `AGENTS.md` contains the client-wide operating instructions.
- `.agents/skills/` contains tool-specific Codex skills.
- `install.ps1` installs the global Work Overview instruction block and configures the client connection.
