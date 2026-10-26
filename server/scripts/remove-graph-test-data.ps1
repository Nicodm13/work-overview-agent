[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$RemoveAllMailboxAndCalendarItems,
    [switch]$ConfirmCalendarCancellations,
    [switch]$RemoveAllChatsForListedUsers,
    [switch]$ConfirmChatDeletion,
    [switch]$RemoveAllTeamsAndChannelPosts,
    [switch]$ConfirmTeamAndGroupResourceDeletion,
    [switch]$ConfirmSandboxTenant
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
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri $Uri -Headers @{ Authorization = "Bearer $script:graphToken" }
    }
    catch {
        $statusCode = $null
        $responseBody = $_.ErrorDetails.Message
        $webResponse = $_.Exception.Response
        if ($webResponse) {
            $statusCode = [int]$webResponse.StatusCode
            if (-not $responseBody) {
                $responseStream = $webResponse.GetResponseStream()
                if ($responseStream) {
                    $reader = [System.IO.StreamReader]::new($responseStream)
                    try { $responseBody = $reader.ReadToEnd() }
                    finally { $reader.Dispose() }
                }
            }
        }
        $statusText = if ($statusCode) { "HTTP $statusCode" } else { 'HTTP request failed' }
        $detail = if ($responseBody) { $responseBody } else { $_.Exception.Message }
        throw "Microsoft Graph $Method request failed ($statusText) at $Uri. Response: $detail"
    }
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

function Write-TaskProgress {
    param([string]$Activity, [int]$Current, [int]$Total, [string]$Status, [int]$Id = 1)
    if ($Total -le 0) { return }
    $percent = [int][Math]::Floor(($Current * 100) / $Total)
    Write-Progress -Id $Id -Activity $Activity -Status "$Status ($Current/$Total)" -PercentComplete $percent
    $reportInterval = [Math]::Max(1, [Math]::Ceiling($Total / 10.0))
    if ($Current -eq 1 -or $Current -eq $Total -or $Current % $reportInterval -eq 0) {
        $filled = [int][Math]::Floor($percent / 5)
        $bar = ('#' * $filled).PadRight(20, '-')
        Write-Output ("{0} [{1}] {2,3}% ({3}/{4}) {5}" -f $Activity, $bar, $percent, $Current, $Total, $Status)
    }
    if ($Current -ge $Total) { Write-Progress -Id $Id -Activity $Activity -Completed }
}

Import-LocalConfig -Path $configPath
$tenantId = $env:WORK_OVERVIEW_ENTRA_TENANT_ID
$clientId = $env:WORK_OVERVIEW_ENTRA_CLIENT_ID
$clientSecret = $env:WORK_OVERVIEW_ENTRA_CLIENT_SECRET
if (-not $tenantId) { throw 'Set WORK_OVERVIEW_ENTRA_TENANT_ID in server/.env or the process environment.' }
if ($tenantId -ne $expectedTenantId) { throw "Configured tenant must be the sandbox tenant $expectedTenantId." }
if (-not $clientId -or -not $clientSecret) { throw 'Set WORK_OVERVIEW_ENTRA_CLIENT_ID and WORK_OVERVIEW_ENTRA_CLIENT_SECRET in server/.env or the process environment.' }
if ($Apply -and (
    -not $RemoveAllMailboxAndCalendarItems -or
    -not $ConfirmCalendarCancellations -or
    -not $RemoveAllChatsForListedUsers -or
    -not $ConfirmChatDeletion -or
    -not $RemoveAllTeamsAndChannelPosts -or
    -not $ConfirmTeamAndGroupResourceDeletion -or
    -not $ConfirmSandboxTenant
)) {
    throw 'Apply requires the mailbox/calendar, chat, and team deletion switches plus -ConfirmSandboxTenant. Team deletion also deletes the backing Microsoft 365 groups and their associated group resources.'
}

$users = Import-Csv -LiteralPath (Join-Path $dataRoot 'users.csv')
Write-Output "Target tenant: $tenantId"
Write-Output "Accounts in scope: $($users.Count) (listed in mock-data/users.csv)"
if (-not $Apply) { Write-Output 'Preview only. Apply requires the explicit mailbox, chat, and team deletion switches documented in server/README.md.' }
$script:graphToken = Get-GraphToken -TenantId $tenantId -ClientId $clientId -ClientSecret $clientSecret

$messageTargets = [System.Collections.Generic.List[object]]::new()
$eventTargets = [System.Collections.Generic.List[object]]::new()
$readOnlyCalendars = [System.Collections.Generic.List[object]]::new()
$chatTargets = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
$userIndex = 0
foreach ($user in $users) {
    $upn = [uri]::EscapeDataString($user.userPrincipalName)
    $messages = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$upn/messages?`$select=id,subject&`$top=100"
    foreach ($message in $messages) { $messageTargets.Add([pscustomobject]@{ User = $user.userPrincipalName; Id = $message.id; Subject = $message.subject }) }
    $calendars = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$upn/calendars?`$select=id,name,canEdit&`$top=100"
    foreach ($calendar in $calendars) {
        if ($calendar.canEdit -eq $false) {
            $readOnlyCalendars.Add([pscustomobject]@{ User = $user.userPrincipalName; Name = $calendar.name })
            continue
        }
        $calendarId = [uri]::EscapeDataString($calendar.id)
        $events = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$upn/calendars/$calendarId/events?`$select=id,subject,start&`$top=100"
        foreach ($event in $events) { $eventTargets.Add([pscustomobject]@{ User = $user.userPrincipalName; CalendarId = $calendar.id; Id = $event.id; Subject = $event.subject; Calendar = $calendar.name }) }
    }
    $chats = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$upn/chats?`$select=id,chatType,topic&`$top=50"
    foreach ($chat in $chats) {
        if (-not $chatTargets.ContainsKey($chat.id)) {
            $chatTargets.Add($chat.id, [pscustomobject]@{ Id = $chat.id; Type = $chat.chatType; Topic = $chat.topic; SeenThrough = [System.Collections.Generic.List[string]]::new() })
        }
        $chatTargets[$chat.id].SeenThrough.Add($user.userPrincipalName)
    }
    $userIndex++
    Write-TaskProgress -Activity 'Scanning user mailboxes and calendars' -Current $userIndex -Total $users.Count -Status $user.userPrincipalName
}
$teams = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/teams?`$select=id,displayName,description&`$top=100"

Write-Output "Mailbox messages in scope: $($messageTargets.Count)"
Write-Output "Calendar events in scope: $($eventTargets.Count)"
Write-Output "Read-only calendars skipped: $($readOnlyCalendars.Count)"
Write-Output "Unique chats involving the listed users: $($chatTargets.Count)"
Write-Output "Teams in tenant (all will be deleted to clear channel posts): $($teams.Count)"
Write-Output 'Calendar items to delete:'
foreach ($item in $eventTargets) { Write-Output "  $($item.User) | $($item.Calendar) | $($item.Subject)" }
if ($readOnlyCalendars.Count -gt 0) {
    Write-Output 'Read-only calendars skipped (Graph does not allow event deletion):'
    foreach ($calendar in $readOnlyCalendars) { Write-Output "  $($calendar.User) | $($calendar.Name)" }
}
Write-Output 'Chats to soft-delete:'
foreach ($chat in $chatTargets.Values) { Write-Output "  $($chat.Type) | $($chat.Topic) | $($chat.Id)" }
Write-Output 'Teams and backing Microsoft 365 groups to delete:'
foreach ($team in $teams) { Write-Output "  $($team.displayName) | $($team.id)" }
Write-Output 'This cleanup scope is all mail and all events in writable calendars in the 17 listed mailboxes, including Microsoft preloaded sample content. Read-only calendars are skipped.'
Write-Output 'Chats are scoped to chats involving at least one listed user. Teams are tenant-wide because Graph does not support application-only deletion of individual channel messages; deleting a team also deletes its backing Microsoft 365 group and associated group resources.'
if (-not $Apply) { return }

$messageIndex = 0
foreach ($item in $messageTargets) {
    $user = [uri]::EscapeDataString($item.User)
    $id = [uri]::EscapeDataString($item.Id)
    Invoke-Graph -Method DELETE -Uri "https://graph.microsoft.com/v1.0/users/$user/messages/$id" | Out-Null
    $messageIndex++
    Write-TaskProgress -Activity 'Deleting mailbox messages' -Current $messageIndex -Total $messageTargets.Count -Status $item.Subject
}
$eventIndex = 0
foreach ($item in $eventTargets) {
    $user = [uri]::EscapeDataString($item.User)
    $calendarId = [uri]::EscapeDataString($item.CalendarId)
    $id = [uri]::EscapeDataString($item.Id)
    Invoke-Graph -Method DELETE -Uri "https://graph.microsoft.com/v1.0/users/$user/calendars/$calendarId/events/$id" | Out-Null
    $eventIndex++
    Write-TaskProgress -Activity 'Deleting calendar events' -Current $eventIndex -Total $eventTargets.Count -Status "$($item.User) | $($item.Subject)"
}
$chatIndex = 0
foreach ($chat in $chatTargets.Values) {
    $id = [uri]::EscapeDataString($chat.Id)
    Invoke-Graph -Method DELETE -Uri "https://graph.microsoft.com/v1.0/chats/$id" | Out-Null
    $chatIndex++
    Write-TaskProgress -Activity 'Deleting chats' -Current $chatIndex -Total $chatTargets.Count -Status "$($chat.Type) | $($chat.Topic)"
    Start-Sleep -Seconds 1
}
$teamIndex = 0
foreach ($team in $teams) {
    $id = [uri]::EscapeDataString($team.id)
    Invoke-Graph -Method DELETE -Uri "https://graph.microsoft.com/v1.0/groups/$id" | Out-Null
    $teamIndex++
    Write-TaskProgress -Activity 'Deleting Teams and backing groups' -Current $teamIndex -Total $teams.Count -Status $team.displayName
}
$seedStatePath = Join-Path $PSScriptRoot 'test-seed-state.json'
if (Test-Path -LiteralPath $seedStatePath) { Remove-Item -LiteralPath $seedStatePath -Force }
Write-Output 'Mailbox, calendar, chat, and team cleanup completed.'
