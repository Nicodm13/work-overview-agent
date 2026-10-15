# Work Overview AI Client

This package is the installable agent layer for the Work Overview MCP server. It contains the
client configuration and future agent extensions; the server remains a separately installed Spring
Boot application.

## Install in Codex

Run the installer from this directory. It writes the server registration to Codex configuration,
then starts `codex mcp login` with the configured scope.
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

## Package structure

- `AGENTS.md` contains the client-wide operating instructions.
- `.agents/skills/` contains tool-specific Codex skills.
- `config/` contains client connection configuration.
