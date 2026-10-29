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

With the local TLS configuration below, the MCP server runs on:

- `https://localhost:8080/mcp`

You can also run the project from IntelliJ using `Start MCP Server` for live Microsoft 365 data,
or `Start MCP Server - Mocked` for the deterministic sample data. Both configurations keep the
Entra-authenticated MCP endpoint; only the source adapters change. Start the database first.

From the command line, plain `:server:bootRun` and `:server:bootRunGraph` use live Microsoft Graph
sources. Only `:server:bootRunMocked` selects the mock adapters. Automated tests that need sample
data select mock mode explicitly.

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
- `Start MCP Server - Mocked`
- `Microsoft Graph - Seed Data`
- `Microsoft Graph - Clear Data`
- `Tests - Run`

Run `Docker - Start Database` before `Start MCP Server`. The old generic `Application` run
configuration is not shared. Use `Tests - Run` to execute the full Gradle test suite from IntelliJ. The two Microsoft
Graph configurations seed or clear data in the disposable sandbox; `Microsoft Graph - Clear Data` permanently deletes scoped data
and associated Team group resources.

The live source mode calls Microsoft Graph using the signed-in user's delegated token. It reads
Outlook messages, default-calendar events, Teams chats, channel posts and returned thread replies
from teams the user belongs to, DOCX attachments on Outlook emails as potential meeting minutes,
and tenant-directory contacts for resolving email and Teams recipients. It does not read meeting
transcripts or Teams file attachments. The client decides which attached documents are actually
minutes. Graph permissions and consent must be configured for the selected sources, and Graph
failures are reported rather than replaced with mock results. Channel messages are filtered to the
requested interval after Graph returns them; pagination limits are reported as source limitations.
Mock mode resolves contacts from the shared `mock-data/users.csv` dataset.

To smoke-test live data locally, start Docker Desktop and run `Docker - Start Database`, then use
`Start MCP Server`. Sign in through the installed MCP client and call `get_review` for a short
interval, initially selecting one source type such as `OUTLOOK`. Repeat with `CALENDAR`, `TEAMS`,
and `MEETING_NOTES` as those sources are available. `MEETING_NOTES` reads DOCX email attachments
using `Mail.Read`. An empty result means no matching source items were returned; it does not prove
the user has no outstanding work.
Use `Start MCP Server - Mocked` to demonstrate the same tools with repeatable sample data.

## Microsoft 365 test data

For the canonical mock dataset, Microsoft 365 seed/cleanup scripts, required Graph permissions, and safe execution steps,
see the **Microsoft 365 test data** section in the repository's [README](../README.md).

## Connect Codex as an MCP client

The default server configuration requires an Entra bearer token. Configure the backend `.env` file
below, then register and sign in through the client installer:

```powershell
cd client
./install.ps1
```

Do not expose the server without the Entra JWT configuration described below. The local
configuration binds only to `127.0.0.1`.

## Use the MCP Inspector

Start the application first, then connect the inspector to the local MCP endpoint:

```bash
npx @modelcontextprotocol/inspector https://localhost:8080/mcp
```

Open the inspector in your browser and confirm that the server responds and exposes its tools.

## Build and test

```powershell
.\gradlew.bat :server:test
.\gradlew.bat :server:build

# Run the local JWT and tenant-scoping tests without Docker.
.\gradlew.bat :server:authenticationTest
```

## Microsoft Entra and Microsoft 365 configuration

The MCP endpoint is a stateless OAuth 2.0 resource server. It validates Entra JWT signature, issuer,
audience, expiry, and tenant before tools can access user-scoped state. The user identity stored by the
server is the stable `<tenant-id>:<object-id>` pair from the token's `tid` and `oid` claims.

Register the MCP server as a single-tenant confidential application in Microsoft Entra ID. Expose an API
scope for the MCP client, then configure the server with the expected token values and one client
credential:

For v2 access tokens, set `WORK_OVERVIEW_ENTRA_AUDIENCE` to the app's client ID (a GUID) and
`WORK_OVERVIEW_ENTRA_SCOPE` to the full exposed scope URI, such as
`https://localhost:8080/mcp/access_as_user` for this local setup. The MCP resource URI and client
URL must both be `https://localhost:8080/mcp`. Add that exact URL to the Entra app registration's
Application ID URIs under **Expose an API** (or to `identifierUris` in its manifest). Keep the
existing `api://<app-client-id>` URI if other clients still use it. This Entra change must be made
manually; the project scripts do not modify Azure.

Before starting the server locally, generate its self-signed development certificate:

```powershell
.\server\scripts\create-local-tls.ps1
```

The script creates `server/.local-tls/localhost.p12` and exports
`server/.local-tls/localhost.cer`. Both are ignored by Git. The example `.env` uses the generated
keystore and a development-only password; do not reuse this certificate or password for deployment.
To let Codex connect without a certificate error, inspect and trust the generated certificate in
your **Current User** Windows root store:

```powershell
certutil -dump .\server\.local-tls\localhost.cer
Import-Certificate -FilePath .\server\.local-tls\localhost.cer -CertStoreLocation Cert:\CurrentUser\Root
```

Trust only the certificate you generated locally. Remove it from the Current User root store when
you no longer use this development setup. If you regenerate it, trust the new certificate and
remove the old one.

Copy `server/.env.example` to `server/.env` and fill in the values. `server/.env` is loaded automatically
when the application starts from either the repository root or the `server` directory, and is ignored by Git.
It must never be copied into `client/`.

Use exactly one backend credential. A client secret is appropriate for local development; prefer a
certificate in a deployed environment.

The MCP source adapters and action delivery use delegated Microsoft Graph permissions:

- `Mail.Read` for Outlook email and DOCX meeting-minute attachments.
- `Calendars.Read` for calendar events and shared free/busy availability with the `find_meeting_times` MCP tool.
- `Chat.Read` for the signed-in user's Teams chats.
- `Team.ReadBasic.All` for listing teams the signed-in user belongs to.
- `Channel.ReadBasic.All` for listing accessible channels in those teams.
- `ChannelMessage.Read.All` for reading channel posts and thread replies. This delegated permission requires admin consent.

The action tools also require these delegated permissions when using Graph mode:

- `Mail.Send` to send email from the signed-in user's mailbox.
- `Chat.Create` to create or retrieve a one-to-one or group chat for the recipient list.
- `ChatMessage.Send` to send a message in that chat.
- `Calendars.ReadWrite` to create a calendar event and send invitations to its attendees.
- `User.ReadBasic.All` to resolve Teams recipients to tenant user IDs. Email and calendar recipients may also be external email addresses.

Action execution uses the signed-in user's delegated authority. `Teamwork.Migrate.All` is for the separate test-data
seeding/import workflow and is not used to send normal Teams messages. In Graph mode, the action result records when
Graph accepted the request; an accepted email request is not a guarantee of final delivery. A network timeout is
reported as `OUTCOME_UNKNOWN` and must be checked in Outlook or Teams before retrying. In mock mode, action execution
only records `SIMULATED` and makes no Microsoft Graph write.

DOCX attachments larger than 5 MB are skipped. Up to 32,000 characters of extracted text are
returned in the review response for interpretation, while only a short excerpt is persisted as
evidence. Skipped or truncated attachments are reported as review limitations.

The server uses OAuth's on-behalf-of flow to call Microsoft Graph with the signed-in user's delegated
authority. It never uses application permissions. When Entra reports missing Graph consent or Graph
denies source access, the review reports source-specific guidance without exposing token details or
raw Graph error responses. Other source failures remain generic limitations.

The test-data seed script is a separate app-only client-credentials flow. It uses the same sandbox app
registration's client ID and secret, but only for the script's Graph writes and historical Teams import. The MCP server
continues to call Graph through its delegated on-behalf-of flow. Remove the temporary application permissions and
admin consent after seeding is complete.

Before importing refreshed Teams messages, the seed script also requests delegated `Chat.ReadWrite` through device-code
sign-in and soft-deletes existing messages in the signed-in dataset user's seeded chats. The chats remain active and
are reused for the new messages. Enable public client flows on the sandbox app registration for device-code sign-in,
or set `WORK_OVERVIEW_GRAPH_DELEGATED_ACCESS_TOKEN` to a current delegated token with `Chat.ReadWrite`. Sign in as
the same dataset user whose chat history the MCP client will review. Mock source timestamps are rebased into the same
14-day window when the mock source loader starts.

## Notes

- This server currently uses the Spring AI MCP WebMVC starter with Streamable HTTP.
- The MCP endpoint requires a correctly configured Entra JWT in the default configuration.
- Application services are deterministic boundaries. Source adapters fetch and normalize data, Evidence stores source
  references and excerpts, FollowUp stores explicitly requested evidence links, Status stores user-confirmed state, and
  Action stores drafts, exact-content approval references, execution state, and delivery audit entries. AI reasoning, prioritization, grouping, and semantic
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
