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
definitions (`teams-conversations.csv`), and shared Outlook, calendar, Teams, and meeting-note records. The mock
adapters read the source CSVs directly; the seeder reads the same CSVs and creates the corresponding Microsoft 365
items. The current six-month history (April–October 2026) contains 144 emails, 34 calendar events, 195 Teams messages,
and 15 meeting notes across the 17 users. Meeting notes remain mock-only because the seeder does not import them into
Graph.

### Required Microsoft Graph permissions

Grant these **Application** permissions to the sandbox app registration and grant tenant admin consent while seeding
and cleaning up. The scripts use client-credentials authentication. These permissions are separate from the delegated
permissions used by the MCP server when Nicolai signs in.

| Permission | Used for |
| --- | --- |
| `User.Read.All` | Resolve the 17 dataset users before creating Team members, chats, and mail/calendar items. |
| `Team.Create` | Create the Atlas Developer Platform Team. |
| `Channel.Create` | Create its channels. |
| `Chat.Create` | Create project, working-group, and one-to-one chats. |
| `Teamwork.Migrate.All` | Import Teams channel and chat messages with dataset authors and historical timestamps. |
| `Mail.Send` | Send each dataset email as its listed sender. |
| `Calendars.ReadWrite` | Create the dataset calendar events. |
| `Mail.ReadWrite` | Enumerate and delete mailbox messages during cleanup. |
| `Chat.Read.All` | Enumerate chats for the 17 listed users during cleanup. |
| `Chat.ManageDeletion.All` | Soft-delete chats during cleanup. This tenant-wide permission is broader than the script's target scope, which remains chats involving the 17 listed users. |
| `Team.ReadBasic.All` | Enumerate Teams in the sandbox during cleanup. |
| `Group.ReadWrite.All` | Delete Teams' backing Microsoft 365 groups to clear channel posts. |

Some permissions are needed only during one phase. After seeding, remove seed-only application permissions and consent
that the running MCP app does not need. Temporarily grant cleanup permissions again when a reset is required. Do not
leave the broad `Group.ReadWrite.All` permission on the app longer than necessary.

Configure `WORK_OVERVIEW_ENTRA_TENANT_ID`, `WORK_OVERVIEW_ENTRA_CLIENT_ID`, and `WORK_OVERVIEW_ENTRA_CLIENT_SECRET` in
`server/.env` or the process environment. Both scripts are pinned to the sandbox tenant
`b9ff29bd-1687-4874-83d1-273303aad419` and refuse a different configured tenant. The seed preview does not need a Graph
token. The cleanup preview reads the tenant and lists its targets.

Preview both operations from the repository root:

```powershell
.\server\scripts\seed-graph-test-data.ps1
.\server\scripts\remove-graph-test-data.ps1
```

After reviewing the seed preview and CSV recipients, apply the seed:

```powershell
.\server\scripts\seed-graph-test-data.ps1 -Apply -SendMail -SendMeetingInvitations -ConfirmTenantId b9ff29bd-1687-4874-83d1-273303aad419
```

The seed imports historical Teams messages, sends the project email, and creates calendar events. Graph timestamps
emails at send time. Meeting invitations are sent when the script runs, including for events scheduled in the past.
Re-running the seed is not a reset and can create duplicates; run cleanup first for a fresh dataset.

Cleanup previews all mailbox messages and calendar events in the 17 listed mailboxes, all chats involving those users,
and every Team in the tenant. Review that list, then apply:

```powershell
.\server\scripts\remove-graph-test-data.ps1 -Apply `
  -RemoveAllMailboxAndCalendarItems -ConfirmCalendarCancellations `
  -RemoveAllChatsForListedUsers -ConfirmChatDeletion `
  -RemoveAllTeamsAndChannelPosts -ConfirmTeamAndGroupResourceDeletion `
  -ConfirmTenantId b9ff29bd-1687-4874-83d1-273303aad419
```

Cleanup is broad: it deletes all email and calendar items in the listed mailboxes, not only items created by this
seeder. Deleting organizer events can send cancellation notices. It soft-deletes every chat found for the 17 users;
Microsoft allows chat restoration for seven days. Graph does not support application-only deletion of individual
channel posts, so cleanup deletes every Team through its backing Microsoft 365 group. This also deletes each Team's
associated group resources, such as files, group mailbox, and plans; groups are generally recoverable for 30 days.
Only run cleanup against the disposable sandbox after reviewing the complete preview.
