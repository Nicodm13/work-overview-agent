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

## Local database

The repository includes a Docker Compose configuration for the local PostgreSQL database. It uses
the same database name, username, password, and port as the application's local defaults.

```powershell
# Start PostgreSQL
.\scripts\database.ps1 start

# Show the database container state
.\scripts\database.ps1 status

# Delete all local database data and start with a clean database
.\scripts\database.ps1 reset

# Stop PostgreSQL and delete all local database data
.\scripts\database.ps1 delete
```

`start` and `reset` start PostgreSQL and build then run Flyway in a short-lived Docker container.
The build copies the repository's migrations into the container, so new migrations are included.
`reset` and `delete` remove only the named Docker volume created by this project's Compose file.
The schema is therefore ready before `MCP Server` starts.

## IntelliJ IDEA run configurations

The shared configurations are committed under `.run` and are available to everyone who clones the
repository:

- `Docker - Start Database`
- `Docker - Reset Database`
- `Docker - Delete Database`
- `MCP Server`

Run `Docker - Start Database` before `MCP Server`. The old generic `Application` run configuration
is not shared; use `MCP Server` for the Spring Boot MCP server.

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
- Application services are deterministic boundaries. Source adapters fetch and normalize data, Evidence stores source
  references and excerpts, FollowUp stores explicitly requested evidence links, Status stores user-confirmed state, and
  Action stores explicit drafts, approvals, and audit entries. AI reasoning, prioritization, grouping, and semantic
  interpretation do not belong in these services.

## Follow-up tracking

The prototype persists minimal user-scoped state in PostgreSQL. Configure it with
`WORK_OVERVIEW_DATABASE_URL`, `WORK_OVERVIEW_DATABASE_USERNAME`, and `WORK_OVERVIEW_DATABASE_PASSWORD`; local defaults
target `jdbc:postgresql://localhost:5432/work_overview_agent` with the user and password
`work_overview_agent`. Flyway creates the schema on application startup.

`EvidenceService` persists independent, minimized `EvidenceReference` records: source type and id, timestamp, author,
title, excerpt, and confidence. It does not store complete channel histories. `FollowUpService` creates a stable
UUID-backed `FollowUpItem` and records the explicitly supplied links in `FOLLOW_UP_EVIDENCE_REFERENCE`. It does not
decide whether source records are related. One evidence reference can therefore support multiple follow-up items, and a
follow-up item can link evidence from several channels.

User-confirmed status records, action drafts, approvals, and action audit entries reference `followUpItemId`, rather
than a single source-evidence ID. The Evidence and Review services remain evidence-based: they do not infer that work
has or has not been completed.
