# Work Overview AI Client

This package is the installable agent layer for the Work Overview MCP server. It contains the
client configuration and future agent extensions; the server remains a separately installed Spring
Boot application.

## Install in Codex

Run the installer from this directory. It registers the server in Codex using `codex mcp add`.
Codex CLI, the Codex IDE extension, and the ChatGPT desktop app share MCP configuration on the
same host.

In IntelliJ, run the shared `Install MCP Client` configuration to use the default local endpoint.

```powershell
# Local server (the default)
.\install.ps1

# Network server
$env:WORK_OVERVIEW_MCP_URL = 'https://work-overview.example.internal/mcp'
.\install.ps1
```

Pass `-ServerUrl` to override the environment variable for one installation, or use `-DryRun` to
show the exact Codex command without changing the local configuration:

```powershell
.\install.ps1 -ServerUrl http://localhost:8080/mcp -DryRun
```

The installed endpoint can be inspected with `codex mcp list`. In this repository, the installer
reads only the public client ID and audience from `server/.env` when it is available. For a separately
installed client package, set the existing Work Overview app registration's client ID before running
the installer, then sign in through the browser:

```powershell
$env:WORK_OVERVIEW_ENTRA_CLIENT_ID = '<Work Overview app client ID>'
./install.ps1
```

The client ID is public. Do not put the backend client secret in this package or on a client
machine. The installer registers the MCP server and opens the Entra sign-in flow.

## Package structure

- `AGENTS.md` contains the client-wide operating instructions.
- `.agents/skills/` contains tool-specific Codex skills.
- `config/` contains client connection configuration.
