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
| `ChatMember.ReadWrite.All` | Re-share full imported history with group-chat members after migration by removing and re-adding members with an earlier `visibleHistoryStartDateTime`. |
| `Chat.Read.All` | One-time adoption of existing chat IDs from the earlier seed-state format. It is no longer needed after the new state ledger records conversation-to-chat mappings. |
| `Chat.ManageDeletion.All` | Restore an adopted chat if Graph reports it is soft-deleted. Needed only when a chat from an earlier cleanup is being reused. |
| `Teamwork.Migrate.All` | Import Teams channel and chat messages with dataset authors and historical timestamps. |
| `Mail.ReadWrite` | Create resumable email drafts with meeting-note attachments and adopt earlier seeded messages. |
| `Mail.Send` | Send each completed dataset draft as its listed sender. |
| `Calendars.ReadWrite` | Create the dataset calendar events. |

#### Test-data cleanup — Application permissions

These permissions are used by `remove-graph-test-data.ps1`. `Chat.Read.All` is also needed for one-time legacy chat adoption, and `Chat.ManageDeletion.All` is also needed if the seeder must restore an adopted soft-deleted chat. Grant tenant admin consent only while running the relevant operation:

| Permission | Used for |
| --- | --- |
| `Mail.ReadWrite` | Enumerate and delete mailbox messages during cleanup. |
| `Chat.Read.All` | Enumerate chats for the 17 listed users during cleanup. |
| `Chat.ManageDeletion.All` | Soft-delete chats during cleanup. This tenant-wide permission is broader than the script's target scope, which remains chats involving the 17 listed users. |
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

The seed keeps a local `server/scripts/test-seed-state.json` ledger, ignored by Git. It records stable dataset row IDs, content hashes, and Graph IDs after each successful operation, so interrupted runs resume and unchanged rows are skipped. On first run after upgrading, it adopts matching prior seeded messages, events, chats, and the existing Team where Graph exposes their seed marker, roster, or transaction ID. Calendar events are patched in place when a CSV row changes. Since Graph allows changing a message's body and subject only while it is a draft, changed sent test emails are removed from the listed participants' mailboxes and recreated from a resumable draft. This applies only to messages identified as seeded by the ledger or seed header.

Teams migration messages are immutable after import through the application-only migration API. Unchanged rows are skipped; changed rows are imported as a new version with a distinct timestamp. The old message remains stored in Teams, and its known Graph ID is marked superseded in the ledger so the live Teams adapter excludes that old version from reviews. For a legacy imported message whose Graph ID was never recorded, a timestamp conflict is adopted without an ID; that message cannot be hidden if its dataset row is later changed. If you need a completely clean Teams history, the cleanup procedure remains separate, and soft-deleted one-to-one chats cannot be reused until restored or until Microsoft's recovery window expires.

Graph timestamps emails at send time. Meeting invitations are sent when the script runs, including for events scheduled in the past. The seeder reuses its recorded Team, channels, and chats; it does not clear existing tenant data. Keep the state file so later runs can reconcile the same Graph objects. Deleting the state file loses those mappings and can lead to duplicate data.

Cleanup previews all mailbox messages and events in writable calendars in the 17 listed mailboxes, all chats involving those users,
and every Team in the tenant. Review that list, then apply either by launching the `Microsoft Graph - Clear Data` Gradle run
configuration or by running the script directly with its required confirmation switches:

```powershell
.\server\scripts\remove-graph-test-data.ps1 -Apply `
  -RemoveAllMailboxAndCalendarItems -ConfirmCalendarCancellations `
  -RemoveAllChatsForListedUsers -ConfirmChatDeletion `
  -RemoveAllTeamsAndChannelPosts -ConfirmTeamAndGroupResourceDeletion `
  -ConfirmSandboxTenant
```

Cleanup is broad: it deletes all email and events in writable calendars in the listed mailboxes, not only items created by this
seeder. Events in read-only calendars cannot be deleted and are skipped. Deleting organizer events can send cancellation notices. It soft-deletes every chat found for the 17 users;
Microsoft allows chat restoration for seven days. Graph does not support application-only deletion of individual
channel posts, so cleanup deletes every Team through its backing Microsoft 365 group. This also deletes each Team's
associated group resources, such as files, group mailbox, and plans; groups are generally recoverable for 30 days.
Only run cleanup against the disposable sandbox after reviewing the complete preview.
