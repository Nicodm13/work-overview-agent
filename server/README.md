# Work Overview Agent

This repository contains a Spring Boot MCP server built with Spring AI.

## Prerequisites

- Java 26
- Gradle Wrapper included in the repository
- Node.js installed if you want to use the MCP Inspector

## Run the server

Start the application from the repository root:

```powershell
.\gradlew.bat :server:bootRun
```

The MCP server runs on:

- `http://localhost:8080/mcp`

You can also run the project from IntelliJ using the normal Run button.

## Local database

The repository includes a Docker Compose configuration for the local PostgreSQL database. It uses
the same database name, username, password, and port as the application's local defaults.

```powershell
# Start PostgreSQL
.\server\scripts\database.ps1 start

# Show the database container state
.\server\scripts\database.ps1 status

# Delete all local database data and start with a clean database
.\server\scripts\database.ps1 reset

# Stop PostgreSQL and delete all local database data
.\server\scripts\database.ps1 delete
```

`start` and `reset` start PostgreSQL and build then run Flyway in a short-lived Docker container.
The build copies the repository's migrations into the container, so new migrations are included.
`reset` and `delete` remove only the named Docker volume created by this project's Compose file.
The schema is therefore ready before `Start MCP Server` starts.

## IntelliJ IDEA run configurations

The shared configurations are committed under `.run` and are available to everyone who clones the
repository:

- `Docker - Start Database`
- `Docker - Reset Database`
- `Docker - Delete Database`
- `Start MCP Server`
- `Run Tests`

Run `Docker - Start Database` before `Start MCP Server`. The old generic `Application` run
configuration is not shared; use `Start MCP Server` for the Spring Boot MCP server. Use `Run Tests`
to execute the full Gradle test suite from IntelliJ.

## Connect Codex as an MCP client

The default server configuration requires an Entra bearer token. Configure the backend `.env` file
below, then register and sign in through the client installer:

```powershell
cd client
./install.ps1
```

Do not expose the server without the Entra JWT configuration described below.

## Use the MCP Inspector

Start the application first, then connect the inspector to the local MCP endpoint:

```bash
npx @modelcontextprotocol/inspector http://localhost:8080/mcp
```

Open the inspector in your browser and confirm that the server responds and exposes its tools.

## Build and test

```powershell
.\gradlew.bat :server:test
.\gradlew.bat :server:build

# Run the local JWT, tenant-scoping, and consent tests without Docker.
.\gradlew.bat :server:authenticationTest
```

## Microsoft Entra and Microsoft 365 configuration

The MCP endpoint is a stateless OAuth 2.0 resource server. It validates Entra JWT signature, issuer,
audience, expiry, and tenant before tools can access user-scoped state. The user identity stored by the
server is the stable `<tenant-id>:<object-id>` pair from the token's `tid` and `oid` claims.

Register the MCP server as a single-tenant confidential application in Microsoft Entra ID. Expose an API
scope for the MCP client, then configure the server with the expected token values and one client
credential:

Copy `server/.env.example` to `server/.env` and fill in the values. `server/.env` is loaded automatically
when the application starts from either the repository root or the `server` directory, and is ignored by Git.
It must never be copied into `client/`.

Use exactly one backend credential. A client secret is appropriate for local development; prefer a
certificate in a deployed environment.

Grant only these delegated Microsoft Graph permissions to the Entra app registration:

- `Mail.Read` for Outlook email.
- `Calendars.Read` for calendar events.
- `Chat.Read` for the signed-in user's Teams chats.
- `Notes.Read` for an initial OneNote-backed meeting-note adapter.

`ChannelMessage.Read.All` is intentionally deferred until the initial integrations have been verified.

The server uses OAuth's on-behalf-of flow to call Microsoft Graph with the signed-in user's delegated
authority. It never uses application permissions. If Entra reports missing or declined consent, the server
returns a consent-required error containing this exact permission set; it does not silently request a
broader permission.

## Notes

- This server currently uses the Spring AI MCP WebMVC starter with Streamable HTTP.
- The MCP endpoint requires a correctly configured Entra JWT in the default configuration.
- Application services are deterministic boundaries. Source adapters fetch and normalize data, Evidence stores source
  references and excerpts, FollowUp stores explicitly requested evidence links, Status stores user-confirmed state, and
  Action stores explicit drafts, approvals, and audit entries. AI reasoning, prioritization, grouping, and semantic
  interpretation do not belong in
  these services.

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
than a single
source-evidence ID. The Evidence and Review services remain evidence-based: they do not infer that work
has or has not
been completed.
