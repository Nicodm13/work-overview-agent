# Work Overview Agent

This repository contains a Spring Boot MCP server built with Spring AI.

## Prerequisites

- Java 26
- Gradle Wrapper included in the repository
- Node.js installed if you want to use the MCP Inspector

## Run the server

Start the application from the repository root:

```powershell
.\gradlew.bat bootRun
```

The MCP server runs on:

- `http://localhost:8080/mcp`

You can also run the project from IntelliJ using the normal Run button.

## Connect Codex as an MCP client

Add the server to Codex CLI:

```bash
codex mcp add work-overview-agent --url http://localhost:8080/mcp
codex mcp list
```

If the server is registered correctly, it will appear in the MCP list and can be used from Codex sessions.

## Use the MCP Inspector

Start the application first, then connect the inspector to the local MCP endpoint:

```bash
npx @modelcontextprotocol/inspector http://localhost:8080/mcp
```

Open the inspector in your browser and confirm that the server responds and exposes its tools.

## Build and test

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

## Notes

- This server currently uses the Spring AI MCP WebMVC starter with Streamable HTTP.
- The MCP endpoint is intended for local development unless additional security is added.
- Application services are deterministic boundaries. Source adapters fetch and normalize data, Evidence records source references and excerpts, FollowUp stores explicitly requested evidence links, Status stores user-confirmed state, and Action builds explicit drafts. AI reasoning, prioritization, grouping, and semantic interpretation do not belong in these services.

## Follow-up tracking

The prototype currently keeps follow-up state in memory only. No database or other persistence is configured.

`FollowUpService` creates a stable UUID-backed `FollowUpItem` and stores the evidence references explicitly supplied by the MCP client/AI. It does not decide whether source records are related. A follow-up item can therefore link evidence from several channels, while the individual source evidence remains independent.

User-confirmed status records, action drafts, and action audit entries reference `followUpItemId`, rather than a single source-evidence ID. The Evidence and Review services remain evidence-based: they do not infer that work has or has not been completed.
