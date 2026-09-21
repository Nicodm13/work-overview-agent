# Work Overview AI Client

This package is the installable agent layer for the Work Overview MCP server. It contains the
client configuration and future agent extensions; the server remains a separately installed Spring
Boot application.

## Configure the server endpoint

Copy `config/client-config.yml` into the installed client configuration directory and set
`WORK_OVERVIEW_MCP_URL` to the server's full Streamable HTTP endpoint.

```powershell
# Local server
$env:WORK_OVERVIEW_MCP_URL = 'http://localhost:8080/mcp'

# Network deployment
$env:WORK_OVERVIEW_MCP_URL = 'https://work-overview.example.internal/mcp'
```

The package does not contain credentials. Authentication configuration will be added with the
network deployment work.

## Package structure

- `skills/` contains client skills.
- `prompts/` contains reusable prompt material.
- `config/` contains client connection configuration.
