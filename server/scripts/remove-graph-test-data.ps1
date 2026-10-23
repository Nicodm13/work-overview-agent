[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$RemoveAllMailboxAndCalendarItems,
    [switch]$ConfirmCalendarCancellations,
    [switch]$RemoveAllChatsForListedUsers,
    [switch]$ConfirmChatDeletion,
    [switch]$RemoveAllTeamsAndChannelPosts,
    [switch]$ConfirmTeamAndGroupResourceDeletion,
    [string]$ConfirmTenantId
)

$ErrorActionPreference = 'Stop'
$expectedTenantId = 'b9ff29bd-1687-4874-83d1-273303aad419'
$dataRoot = Join-Path $PSScriptRoot '..\src\main\resources\mock-data'
$configPath = Join-Path $PSScriptRoot '..\.env'

function Import-LocalConfig {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return }
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([^#=]+?)\s*=\s*(.*?)\s*$') {
            $name = $Matches[1].Trim()
            $value = $Matches[2].Trim().Trim('"').Trim("'")
            if (-not [Environment]::GetEnvironmentVariable($name)) { [Environment]::SetEnvironmentVariable($name, $value, 'Process') }
        }
    }
}

function Get-GraphToken {
    param([string]$TenantId, [string]$ClientId, [string]$ClientSecret)
    (Invoke-RestMethod -Method Post -Uri "https://login.microsoftonline.com/$TenantId/oauth2/v2.0/token" -ContentType 'application/x-www-form-urlencoded' -Body @{
        client_id = $ClientId; client_secret = $ClientSecret; scope = 'https://graph.microsoft.com/.default'; grant_type = 'client_credentials'
    }).access_token
}

function Invoke-Graph {
    param([ValidateSet('GET', 'DELETE')][string]$Method, [string]$Uri)
    $response = Invoke-WebRequest -Method $Method -Uri $Uri -Headers @{ Authorization = "Bearer $script:graphToken" }
    $parsed = $null
    if ($response.Content) { $parsed = $response.Content | ConvertFrom-Json }
    return [pscustomobject]@{ Body = $parsed; StatusCode = [int]$response.StatusCode }
}

function Get-AllGraphItems {
    param([string]$Uri)
    $items = [System.Collections.Generic.List[object]]::new()
    $next = $Uri
    while ($next) {
        $page = (Invoke-Graph -Method GET -Uri $next).Body
        foreach ($item in $page.value) { $items.Add($item) }
        $next = $page.'@odata.nextLink'
    }
    return $items
}

Import-LocalConfig -Path $configPath
$tenantId = $env:WORK_OVERVIEW_ENTRA_TENANT_ID
$clientId = $env:WORK_OVERVIEW_ENTRA_CLIENT_ID
$clientSecret = $env:WORK_OVERVIEW_ENTRA_CLIENT_SECRET
if ($tenantId -ne $expectedTenantId) { throw "Configured tenant must be the sandbox tenant $expectedTenantId." }
if (-not $clientId -or -not $clientSecret) { throw 'Set WORK_OVERVIEW_ENTRA_CLIENT_ID and WORK_OVERVIEW_ENTRA_CLIENT_SECRET in server/.env or the process environment.' }
if ($Apply -and (
    -not $RemoveAllMailboxAndCalendarItems -or
    -not $ConfirmCalendarCancellations -or
    -not $RemoveAllChatsForListedUsers -or
    -not $ConfirmChatDeletion -or
    -not $RemoveAllTeamsAndChannelPosts -or
    -not $ConfirmTeamAndGroupResourceDeletion -or
    $ConfirmTenantId -ne $expectedTenantId
)) {
    throw "Apply requires the mailbox/calendar, chat, and team deletion switches plus -ConfirmTenantId $expectedTenantId. Team deletion also deletes the backing Microsoft 365 groups and their associated group resources."
}

$users = Import-Csv -LiteralPath (Join-Path $dataRoot 'users.csv')
Write-Output "Target tenant: $tenantId"
Write-Output "Accounts in scope: $($users.Count) (listed in mock-data/users.csv)"
if (-not $Apply) { Write-Output 'Preview only. Apply requires the explicit mailbox, chat, and team deletion switches documented in server/README.md.' }
$script:graphToken = Get-GraphToken -TenantId $tenantId -ClientId $clientId -ClientSecret $clientSecret

$messageTargets = [System.Collections.Generic.List[object]]::new()
$eventTargets = [System.Collections.Generic.List[object]]::new()
$chatTargets = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
foreach ($user in $users) {
    $upn = [uri]::EscapeDataString($user.userPrincipalName)
    $messages = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$upn/messages?`$select=id,subject&`$top=100"
    foreach ($message in $messages) { $messageTargets.Add([pscustomobject]@{ User = $user.userPrincipalName; Id = $message.id; Subject = $message.subject }) }
    $calendars = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$upn/calendars?`$select=id,name&`$top=100"
    foreach ($calendar in $calendars) {
        $calendarId = [uri]::EscapeDataString($calendar.id)
        $events = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$upn/calendars/$calendarId/events?`$select=id,subject,start&`$top=100"
        foreach ($event in $events) { $eventTargets.Add([pscustomobject]@{ User = $user.userPrincipalName; Id = $event.id; Subject = $event.subject; Calendar = $calendar.name }) }
    }
    $chats = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$upn/chats?`$select=id,chatType,topic&`$top=50"
    foreach ($chat in $chats) {
        if (-not $chatTargets.ContainsKey($chat.id)) {
            $chatTargets.Add($chat.id, [pscustomobject]@{ Id = $chat.id; Type = $chat.chatType; Topic = $chat.topic; SeenThrough = [System.Collections.Generic.List[string]]::new() })
        }
        $chatTargets[$chat.id].SeenThrough.Add($user.userPrincipalName)
    }
}
$teams = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/teams?`$select=id,displayName,description&`$top=100"

Write-Output "Mailbox messages in scope: $($messageTargets.Count)"
Write-Output "Calendar events in scope: $($eventTargets.Count)"
Write-Output "Unique chats involving the listed users: $($chatTargets.Count)"
Write-Output "Teams in tenant (all will be deleted to clear channel posts): $($teams.Count)"
Write-Output 'Calendar items to delete:'
foreach ($item in $eventTargets) { Write-Output "  $($item.User) | $($item.Calendar) | $($item.Subject)" }
Write-Output 'Chats to soft-delete:'
foreach ($chat in $chatTargets.Values) { Write-Output "  $($chat.Type) | $($chat.Topic) | $($chat.Id)" }
Write-Output 'Teams and backing Microsoft 365 groups to delete:'
foreach ($team in $teams) { Write-Output "  $($team.displayName) | $($team.id)" }
Write-Output 'This cleanup scope is all mail and calendar items in the 17 listed mailboxes, including Microsoft preloaded sample content.'
Write-Output 'Chats are scoped to chats involving at least one listed user. Teams are tenant-wide because Graph does not support application-only deletion of individual channel messages; deleting a team also deletes its backing Microsoft 365 group and associated group resources.'
if (-not $Apply) { return }

foreach ($item in $messageTargets) {
    $user = [uri]::EscapeDataString($item.User)
    $id = [uri]::EscapeDataString($item.Id)
    Invoke-Graph -Method DELETE -Uri "https://graph.microsoft.com/v1.0/users/$user/messages/$id" | Out-Null
}
foreach ($item in $eventTargets) {
    $user = [uri]::EscapeDataString($item.User)
    $id = [uri]::EscapeDataString($item.Id)
    Invoke-Graph -Method DELETE -Uri "https://graph.microsoft.com/v1.0/users/$user/events/$id" | Out-Null
}
foreach ($chat in $chatTargets.Values) {
    $id = [uri]::EscapeDataString($chat.Id)
    Invoke-Graph -Method DELETE -Uri "https://graph.microsoft.com/v1.0/chats/$id" | Out-Null
    Start-Sleep -Seconds 1
}
foreach ($team in $teams) {
    $id = [uri]::EscapeDataString($team.id)
    Invoke-Graph -Method DELETE -Uri "https://graph.microsoft.com/v1.0/groups/$id" | Out-Null
}
Write-Output 'Mailbox, calendar, chat, and team cleanup completed.'
