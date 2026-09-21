# Work Overview Agent

The project is split into independently buildable components:

- [`server`](server/README.md) — the Spring Boot MCP server, its persistence, source adapters,
  deterministic services, and MCP tools.
- [`client`](client/README.md) — the future installable AI client. It will connect to the server
  and own AI interpretation and user interaction.

Run the existing server from the repository root:

```powershell
.\gradlew.bat :server:bootRun
```

Run its tests with:

```powershell
.\gradlew.bat :server:test
```
