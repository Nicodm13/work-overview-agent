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

## Microsoft 365 test data

`server/src/main/resources/mock-data/` is the canonical dataset for both the mock source adapters and the Microsoft 365
sandbox scripts. It contains the 17 project users (`users.csv`), Team and channel definitions (`team.json`), chat
definitions (`teams-conversations.csv`), shared Outlook, calendar, and Teams records, and 15 synthetic meeting-minutes
DOCX files. The documents contain their own meeting metadata and notes; mock mode reads them directly, and the Graph
seeder sends each document as an attachment to its meeting participants. The current six-month history (April–October
2026) contains 144 project emails, 15 meeting-minutes emails with DOCX attachments, 34 calendar events, 219 Teams
messages, 33 chats (including 28 one-to-one chats across the team), and 15 meeting notes. Graph timestamps the minutes
emails when the seed script runs.
### Microsoft Graph permissions

The running AI and the test-data scripts use different permission types and flows. Configure only the delegated
permissions on the app for the AI to read the signed-in user's work data. The seed and cleanup scripts instead use an
app-only client-credentials flow and need temporary application permissions. Do not leave those broad script
permissions consented when the scripts are not in use.

#### AI runtime — Delegated permissions

These are used by the MCP server when the user signs in. They grant access in the context of that signed-in user:

| Permission | Used for |
| --- | --- |
| `Mail.Read` | Read Outlook email and DOCX meeting-minute attachments. |
| `Calendars.Read` | Read calendar events and check shared free/busy availability for meeting attendees. |
| `Chat.Read` | Read the signed-in user's Teams chats. |
| `User.ReadBasic.All` | Search tenant directory contacts by name or email for email and Teams recipient resolution. |
| `Mail.Send` | Send an approved email as the signed-in user. |
| `Chat.Create` | Create or retrieve one-to-one and group chats for approved Teams messages. |
| `ChatMessage.Send` | Send an approved message in a Teams chat. |
| `Calendars.ReadWrite` | Create approved calendar events and invite their attendees. |
| `Team.ReadBasic.All` | List Teams the signed-in user belongs to. |
| `Channel.ReadBasic.All` | List accessible channels in those Teams. |
| `ChannelMessage.Read.All` | Read channel posts and thread replies. Requires admin consent. |
| `Chat.ReadWrite` | Reopen existing sandbox one-to-one chats for participants when needed and delete test messages during cleanup. |

#### Test-data seeding — Application permissions

Grant these to the sandbox app registration and grant tenant admin consent only while running
`seed-graph-test-data.ps1`:

| Permission | Used for |
| --- | --- |
| `User.Read.All` | Resolve the 17 dataset users before creating Team members, chats, and mail/calendar items. |
| `Team.Create` | Create the Atlas Developer Platform Team. |
| `TeamMember.ReadWriteNonOwnerRole.All` | Add missing users to the Team after creating it with Nicolai as its initial owner. |
| `Channel.Create` | Create its channels. |
| `Channel.ReadBasic.All` | Find the template-created General channel and reuse it when importing messages. |
| `Chat.Create` | Create project, working-group, and one-to-one chats. |
| `Chat.Read.All` | Discover existing chats and messages before importing missing dataset messages. |
| `ChannelMessage.Read.All` | Discover existing channel messages before importing missing dataset messages. |
| `ChatMember.ReadWrite.All` | Re-share full imported history with group-chat members after migration by removing and re-adding members with an earlier `visibleHistoryStartDateTime`. |
| `Chat.ManageDeletion.All` | Restore a dataset chat if Graph reports it is soft-deleted. |
| `Teamwork.Migrate.All` | Import Teams channel and chat messages with dataset authors and historical timestamps. |
| `Mail.ReadWrite` | Create resumable email drafts with meeting-note attachments and adopt earlier seeded messages. |
| `Mail.Send` | Send each completed dataset draft as its listed sender. |
| `Calendars.ReadWrite` | Create the dataset calendar events. |

#### Test-data cleanup — Application permissions

These permissions are used by `remove-graph-test-data.ps1`. Grant tenant admin consent only while running the relevant operation:

| Permission | Used for |
| --- | --- |
| `Mail.ReadWrite` | Enumerate and delete mailbox messages during cleanup. |
| `Chat.Read.All` | Enumerate chats for the 17 listed users during cleanup. |
| `Team.ReadBasic.All` | Enumerate Teams in the sandbox during cleanup. |
| `Group.ReadWrite.All` | Delete Teams' backing Microsoft 365 groups to clear channel posts and associated group resources. |

After seeding, remove the seeding and cleanup application permissions and their admin consent; the MCP server does not
use application permissions. Temporarily grant cleanup permissions again only when a reset is required. In particular,
do not leave the broad `Group.ReadWrite.All` permission consented longer than necessary.

Configure `WORK_OVERVIEW_ENTRA_TENANT_ID`, `WORK_OVERVIEW_ENTRA_CLIENT_ID`, and `WORK_OVERVIEW_ENTRA_CLIENT_SECRET` in
`server/.env` or the process environment. Both scripts read `WORK_OVERVIEW_ENTRA_TENANT_ID` from that configuration
and refuse to run outside the sandbox tenant `b9ff29bd-1687-4874-83d1-273303aad419`. Seeding writes data immediately
and requires explicit tenant confirmation. Cleanup has a separate preview mode.

Seed from IntelliJ by launching the `Microsoft Graph - Seed Data` Gradle run configuration, or run the equivalent
Gradle task from the repository root:

```powershell
.\gradlew.bat :server:seedGraphTestData
```

The script can also be run directly from the repository root:

```powershell
.\server\scripts\seed-graph-test-data.ps1 -ConfirmSandboxTenant
```

Both scripts use at most four concurrent workers by default. To adjust this between 1 and 16, pass `-MaxConcurrency 2` when invoking a script directly, or `-PgraphMaxConcurrency=2` to either Gradle task. The seeder processes separate chats concurrently while keeping each chat's messages ordered. Cleanup processes separate Teams conversations and mailboxes concurrently, and retains the calendar-before-mailbox cleanup order. Graph throttling can limit the benefit of increasing the worker count. Do not run seed and cleanup at the same time.

The seeder stores no local seed-state file. Each invocation reads Graph to find existing Teams, chats, messages, sent emails, drafts and events, and keeps lookup information in memory only while it runs. Existing matching items are skipped; an existing item is not refreshed merely because the rolling dataset dates or content have changed. Teams messages are matched by author and normalized content, so identical real messages can also match. For ambiguous group chats or multiple drafts, the affected items are skipped and a warning is printed. Run cleanup before reseeding if a fresh rolling timeline is needed.

The seeder signs in as each sandbox test user to check their chat list. If a one-to-one chat is missing, it reopens the existing chat with delegated `Chat.ReadWrite` and verifies it is listed for both participants. Set `WORK_OVERVIEW_TEST_USER_PASSWORD` in `server/.env` to the shared test-user password, and grant the app delegated `Chat.ReadWrite` and `User.Read` consent. The password grant requires a compatible test-tenant sign-in policy and does not support MFA. No chat message is sent by the reopening step.

Dataset timestamps use workday-relative tokens such as `WORKDAY-0T15:03:00Z`, resolved against the last completed UTC workday. Meeting-note DOCX metadata uses the same tokens.

Teams migration messages are immutable through the application-only migration API. The seeder resumes in-progress imports and imports messages it cannot find; it does not clear existing messages. Graph timestamps emails at send time, and creating calendar events sends meeting invitations, including for events scheduled in the past.

Cleanup preview reports counts of mailbox messages, events in writable calendars, and chats involving the listed users. Apply either by launching the `Microsoft Graph - Clear Data` Gradle run
configuration or by running the script directly with its required confirmation switches:

```powershell
.\server\scripts\remove-graph-test-data.ps1 -Apply `
  -RemoveAllMailboxAndCalendarItems -ConfirmCalendarCancellations `
  -ConfirmTeamsMessageDeletion -ConfirmSandboxTenant
```

Cleanup is broad: it deletes all email and events in writable calendars in the listed mailboxes, not only items created by this seeder. Events in read-only calendars cannot be deleted and are skipped. Deleting organizer events can send cancellation notices. Cleanup deletes readable messages in the discovered chats and Teams as their authors, but preserves chat containers so they remain available for reseeding. It deletes the dataset Team through its backing Microsoft 365 group, including its associated group resources, such as files, group mailbox, and plans; groups are generally recoverable for 30 days.
Only run cleanup against the disposable sandbox after reviewing the complete preview.
