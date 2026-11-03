[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$RemoveAllMailboxAndCalendarItems,
    [switch]$ConfirmCalendarCancellations,
    [switch]$ConfirmTeamsMessageDeletion,
    [switch]$ConfirmSandboxTenant,
    [ValidateRange(1, 16)][int]$MaxConcurrency = 4
)

$ErrorActionPreference = 'Stop'
$expectedTenantId = 'b9ff29bd-1687-4874-83d1-273303aad419'
$dataRoot = Join-Path $PSScriptRoot '..\src\main\resources\mock-data'
$configPath = Join-Path $PSScriptRoot '..\.env'
. (Join-Path $PSScriptRoot 'graph-parallel.ps1')

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
    param([ValidateSet('GET', 'POST', 'DELETE')][string]$Method, [string]$Uri, [object]$Body, [string]$AccessToken = $script:graphToken)
    $requestParameters = @{ UseBasicParsing = $true; Method = $Method; Uri = $Uri; Headers = @{ Authorization = "Bearer $AccessToken" } }
    if ($PSBoundParameters.ContainsKey('Body')) {
        $requestParameters.ContentType = 'application/json'
        $requestParameters.Body = ConvertTo-Json -InputObject $Body -Depth 20 -Compress
    }
    try {
        $response = Invoke-WebRequest @requestParameters
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
        $retryAfter = if ($webResponse) { [string]$webResponse.Headers['Retry-After'] } else { $null }
        if ($retryAfter) { $detail = "$detail Retry-After: $retryAfter" }
        throw "Microsoft Graph $Method request failed ($statusText) at $Uri. Response: $detail"
    }
    $parsed = $null
    if ($response.Content) { $parsed = $response.Content | ConvertFrom-Json }
    return [pscustomobject]@{ Body = $parsed; StatusCode = [int]$response.StatusCode; Headers = $response.Headers }
}

function Get-GraphItemIfPresent {
    param([string]$Uri, [string]$AccessToken = $script:graphToken)
    try { return (Invoke-Graph -Method GET -Uri $Uri -AccessToken $AccessToken).Body }
    catch {
        if ($_ -match 'HTTP 404') { return $null }
        throw
    }
}

function Invoke-GraphCleanupMutation {
    param([ValidateSet('POST', 'DELETE')][string]$Method, [string]$Uri, [object]$Body, [string]$AccessToken = $script:graphToken)
    $maxAttempts = 5
    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        try {
            if ($PSBoundParameters.ContainsKey('Body')) {
                return Invoke-Graph -Method $Method -Uri $Uri -Body $Body -AccessToken $AccessToken
            }
            return Invoke-Graph -Method $Method -Uri $Uri -AccessToken $AccessToken
        }
        catch {
            $errorText = [string]$_
            if ($errorText -match 'HTTP 404') { return $null }
            if ($errorText -notmatch 'HTTP (429|500|502|503|504)' -or $attempt -ge $maxAttempts) { throw }
            $delaySeconds = if ($errorText -match 'Retry-After:\s*(\d+)') { [int]$Matches[1] } else { [int][Math]::Min(30, [Math]::Pow(2, $attempt)) }
            Write-Output "Graph $Method returned a transient error; retrying in $delaySeconds seconds (attempt $($attempt + 1) of $maxAttempts)."
            Start-Sleep -Seconds $delaySeconds
        }
    }
}

function Get-AllGraphItems {
    param([string]$Uri, [string]$AccessToken = $script:graphToken)
    $items = [System.Collections.Generic.List[object]]::new()
    $next = $Uri
    while ($next) {
        $page = (Invoke-Graph -Method GET -Uri $next -AccessToken $AccessToken).Body
        foreach ($item in $page.value) { $items.Add($item) }
        $next = $page.'@odata.nextLink'
    }
    return $items
}

function Test-AdminDeletedTeamsMessage {
    param([object]$Message)

    $content = [string]$Message.body.content
    if (-not $content) { return $false }

    $plainText = [System.Net.WebUtility]::HtmlDecode(($content -replace '<[^>]+>', ' '))
    $plainText = [regex]::Replace($plainText, '\s+', ' ').Trim().TrimEnd('.')
    return $plainText -match '^(?i:this message was deleted by an admin|this message was deleted by an administrator|an admin deleted this message|an administrator deleted this message|denne meddelelse blev slettet af en administrator|denne besked blev slettet af en administrator)$'
}

function Test-ReadableTeamsMessage {
    param([object]$Message)

    return $Message.messageType -eq 'message' -and
        -not $Message.deletedDateTime -and
        -not (Test-AdminDeletedTeamsMessage -Message $Message)
}

function Get-TeamsMessageTargets {
    param([object[]]$Chats, [hashtable]$ChatSessions)
    $targets = [Collections.Generic.List[object]]::new()
    foreach ($chat in $Chats) {
        $chatId = [uri]::EscapeDataString([string]$chat.Id)
        $uri = "https://graph.microsoft.com/v1.0/chats/$chatId/messages"
        $session = $ChatSessions[[string]$chat.Id]
        if (-not $session) { throw "No delegated inventory session was recorded for chat $($chat.Id)." }
        foreach ($message in (Get-AllGraphItems -Uri "$uri`?`$top=50&`$orderby=createdDateTime%20desc" -AccessToken $session.AccessToken)) {
            if (-not (Test-ReadableTeamsMessage -Message $message)) { continue }
            $messageId = [uri]::EscapeDataString([string]$message.id)
            $authorId = [string]$message.from.user.id
            $targets.Add(@{ AuthorId = $authorId; Source = 'chat'; ReadUri = "$uri/$messageId"; DeleteUri = "https://graph.microsoft.com/v1.0/users/$authorId/chats/$chatId/messages/$messageId/softDelete" })
        }
    }
    foreach ($team in (Get-AllGraphItems -Uri 'https://graph.microsoft.com/v1.0/teams?$select=id&$top=100')) {
        $teamId = [uri]::EscapeDataString([string]$team.id)
        foreach ($channel in (Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels?`$select=id")) {
            $channelId = [uri]::EscapeDataString([string]$channel.id)
            $uri = "https://graph.microsoft.com/v1.0/teams/$teamId/channels/$channelId/messages"
            foreach ($message in (Get-AllGraphItems -Uri "$uri`?`$top=50")) {
                $messageId = [uri]::EscapeDataString([string]$message.id)
                $repliesUri = "$uri/$messageId/replies"
                foreach ($reply in (Get-AllGraphItems -Uri "$repliesUri`?`$top=50")) {
                    if (-not (Test-ReadableTeamsMessage -Message $reply)) { continue }
                    $replyId = [uri]::EscapeDataString([string]$reply.id)
                    $targets.Add(@{ AuthorId = [string]$reply.from.user.id; Source = 'channel'; ReadUri = "$repliesUri/$replyId"; DeleteUri = "$repliesUri/$replyId/softDelete" })
                }
                if (-not (Test-ReadableTeamsMessage -Message $message)) { continue }
                $targets.Add(@{ AuthorId = [string]$message.from.user.id; Source = 'channel'; ReadUri = "$uri/$messageId"; DeleteUri = "$uri/$messageId/softDelete" })
            }
        }
    }
    return $targets
}

function Get-DelegatedGraphSession {
    param([object]$User)
    $password = [Environment]::GetEnvironmentVariable('WORK_OVERVIEW_TEST_USER_PASSWORD')
    if (-not $password) { throw 'Set WORK_OVERVIEW_TEST_USER_PASSWORD in server/.env to the shared sandbox test-account password.' }
    try {
        $response = Invoke-RestMethod -Method Post -Uri "https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token" -ContentType 'application/x-www-form-urlencoded' -Body @{
            client_id = $clientId; client_secret = $clientSecret; grant_type = 'password'
            username = $User.userPrincipalName; password = $password
            scope = 'https://graph.microsoft.com/Chat.ReadWrite https://graph.microsoft.com/ChannelMessage.ReadWrite https://graph.microsoft.com/User.Read'
        }
    }
    catch { throw "Delegated Graph sign-in failed for $($User.userPrincipalName). Check the password, consent for Chat.ReadWrite and ChannelMessage.ReadWrite, and the account's sign-in policy." }
    finally { $password = $null }
    if (-not $response.access_token) { throw "Delegated Graph sign-in returned no token for $($User.userPrincipalName)." }
    $me = (Invoke-Graph -Method GET -Uri 'https://graph.microsoft.com/v1.0/me?$select=id' -AccessToken $response.access_token).Body
    if ([string]$me.id -ne [string]$User.id) { throw "Delegated token identity differs from test user $($User.userPrincipalName)." }
    return @{ AccessToken = $response.access_token; ExpiresAt = (Get-Date).AddSeconds([int]$response.expires_in) }
}

function Remove-TeamsMessages {
    param([object[]]$Targets, [object[]]$Users)
    if ($Targets.Count -eq 0) { Write-Output 'Teams messages: none found.'; return }
    $usersById = @{}
    foreach ($user in $Users) {
        $graphUser = (Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/users/$([uri]::EscapeDataString($user.userPrincipalName))?`$select=id").Body
        $usersById[[string]$graphUser.id] = @{ id = $graphUser.id; userPrincipalName = $user.userPrincipalName }
    }
    $sessions = @{}
    foreach ($authorId in @($Targets | ForEach-Object { $_.AuthorId } | Sort-Object -Unique)) {
        if (-not $usersById.ContainsKey($authorId)) { throw "Teams message author $authorId is outside the configured test users; cleanup stopped before deletion." }
        $sessions[$authorId] = Get-DelegatedGraphSession -User $usersById[$authorId]
    }
    Write-Output "Teams messages: deleting $($Targets.Count) messages as their authors."
    $groups = @($Targets | Group-Object { [regex]::Replace($_.ReadUri, '/messages/.*$', '/messages') })
    $work = @($groups | ForEach-Object { @{ Targets = @($_.Group); Users = $usersById; Sessions = $sessions; TenantId = $tenantId; ClientId = $clientId; ClientSecret = $clientSecret } })
    $completed = @(Invoke-GraphParallel -Items $work -GraphToken $script:graphToken -MaxConcurrency $MaxConcurrency -Functions @('Invoke-Graph', 'Get-GraphItemIfPresent', 'Invoke-GraphCleanupMutation', 'Get-DelegatedGraphSession', 'Test-AdminDeletedTeamsMessage', 'Test-ReadableTeamsMessage') -Worker {
        param($group)
        $script:tenantId = $group.TenantId
        $script:clientId = $group.ClientId
        $script:clientSecret = $group.ClientSecret
        $localSessions = @{}
        foreach ($target in $group.Targets) {
            if (-not $localSessions.ContainsKey($target.AuthorId)) { $localSessions[$target.AuthorId] = $group.Sessions[$target.AuthorId] }
            $session = $localSessions[$target.AuthorId]
            if ($session.ExpiresAt -lt (Get-Date).AddMinutes(5)) {
                $session = Get-DelegatedGraphSession -User $group.Users[$target.AuthorId]
                $localSessions[$target.AuthorId] = $session
            }
            $current = Get-GraphItemIfPresent -Uri $target.ReadUri -AccessToken $session.AccessToken
            if ($current -and (Test-ReadableTeamsMessage -Message $current)) {
                Invoke-GraphCleanupMutation -Method POST -Uri $target.DeleteUri -AccessToken $session.AccessToken | Out-Null
            }
        }
        foreach ($target in $group.Targets) {
            $session = $localSessions[$target.AuthorId]
            if ($session.ExpiresAt -lt (Get-Date).AddMinutes(5)) {
                $session = Get-DelegatedGraphSession -User $group.Users[$target.AuthorId]
                $localSessions[$target.AuthorId] = $session
            }
            $remaining = Get-GraphItemIfPresent -Uri $target.ReadUri -AccessToken $session.AccessToken
            if ($remaining -and (Test-ReadableTeamsMessage -Message $remaining)) { throw "Teams message $($target.ReadUri) remains readable after delegated deletion." }
        }
        return $group.Targets.Count
    })
    Write-Output "Teams messages: processed $((($completed | Measure-Object -Sum).Sum)) messages across $($groups.Count) conversations."
    Write-Output 'Teams messages: verified deleted.'
}

function Remove-DatasetTeams {
    $teamConfigPath = Join-Path $dataRoot 'team.json'
    $datasetTeam = Get-Content -LiteralPath $teamConfigPath -Raw | ConvertFrom-Json
    $teams = @(Get-AllGraphItems -Uri 'https://graph.microsoft.com/v1.0/teams?$select=id,displayName&$top=100' | Where-Object { $_.displayName -ceq $datasetTeam.displayName })
    $script:deletedDatasetTeams = @($teams)
    if ($teams.Count -eq 0) { Write-Output "Teams: no active '$($datasetTeam.displayName)' Team found."; return }

    Write-Output "Teams: soft-deleting $($teams.Count) active '$($datasetTeam.displayName)' Team container(s)."
    $index = 0
    foreach ($team in $teams) {
        $teamId = [uri]::EscapeDataString([string]$team.id)
        Invoke-GraphCleanupMutation -Method DELETE -Uri "https://graph.microsoft.com/v1.0/groups/$teamId" | Out-Null
        $index++
        Write-TaskProgress -Activity 'Deleting project Teams' -Current $index -Total $teams.Count -Status $team.displayName
    }

    foreach ($team in $teams) {
        $teamId = [uri]::EscapeDataString([string]$team.id)
        $groupUri = "https://graph.microsoft.com/v1.0/groups/$teamId`?`$select=id"
        $remaining = $null
        for ($attempt = 1; $attempt -le 30; $attempt++) {
            $remaining = Get-GraphItemIfPresent -Uri $groupUri
            if (-not $remaining) { break }
            if ($attempt -lt 30) { Start-Sleep -Seconds 2 }
        }
        if ($remaining) { throw "The backing group for Team '$($team.displayName)' ($($team.id)) is still active after deletion; stopping before mailbox and calendar cleanup." }
    }
    Write-Output "Teams: verified $($teams.Count) backing group(s) are deleted; Teams removal may still be propagating."
}

function Confirm-DatasetTeamsRemoved {
    param([object[]]$Teams)
    foreach ($team in $Teams) {
        $teamId = [uri]::EscapeDataString([string]$team.id)
        $teamUri = "https://graph.microsoft.com/v1.0/teams/$teamId`?`$select=id"
        $remaining = $null
        for ($attempt = 1; $attempt -le 37; $attempt++) {
            $remaining = Get-GraphItemIfPresent -Uri $teamUri
            if (-not $remaining) { break }
            if ($attempt -lt 37) { Start-Sleep -Seconds 5 }
        }
        if ($remaining) { throw "Team '$($team.displayName)' ($($team.id)) is still visible in Graph after its backing group was deleted. Mailbox and calendar cleanup completed; wait for propagation before reseeding." }
    }
    Write-Output "Teams: verified $($Teams.Count) project Team container(s) are no longer visible."
}

function Get-MailboxMessageTargets {
    param([object[]]$Users)
    $inventory = @(Invoke-GraphParallel -Items @($Users) -GraphToken $script:graphToken -MaxConcurrency $MaxConcurrency -Functions @('Invoke-Graph', 'Get-AllGraphItems') -Worker {
        param($user)
        $upn = [uri]::EscapeDataString($user.userPrincipalName)
        $messages = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$upn/messages?`$select=id,subject&`$top=100"
        foreach ($message in $messages) {
            [pscustomobject]@{ User = $user.userPrincipalName; Id = $message.id; Subject = $message.subject }
        }
    })
    return $inventory
}

function Remove-MailboxMessages {
    param([object[]]$Users, [object[]]$Targets)
    if ($Targets.Count -eq 0) { Write-Output 'Mailbox messages: none found.'; return }

    $pending = @($Targets)
    $maxPasses = 3
    for ($pass = 1; $pass -le $maxPasses; $pass++) {
        Write-Output "Mailbox messages: permanently deleting $($pending.Count) visible items (pass $pass of $maxPasses)."
        $mailboxes = @($pending | Group-Object User)
        $deleted = @(Invoke-GraphParallel -Items $mailboxes -GraphToken $script:graphToken -MaxConcurrency $MaxConcurrency -Functions @('Invoke-Graph', 'Invoke-GraphCleanupMutation') -Worker {
            param($mailbox)
            foreach ($item in $mailbox.Group) {
                $user = [uri]::EscapeDataString($item.User)
                $id = [uri]::EscapeDataString($item.Id)
                Invoke-GraphCleanupMutation -Method POST -Uri "https://graph.microsoft.com/v1.0/users/$user/messages/$id/permanentDelete" | Out-Null
            }
            return $mailbox.Count
        })
        Write-Output "Mailbox messages: processed $((($deleted | Measure-Object -Sum).Sum)) items across $($mailboxes.Count) mailboxes."

        Start-Sleep -Seconds 3
        $pending = @(Get-MailboxMessageTargets -Users $Users)
        if ($pending.Count -eq 0) {
            Write-Output 'Mailbox messages: verified no visible messages remain.'
            return
        }
        if ($pass -lt $maxPasses) {
            Write-Output "Mailbox verification still found $($pending.Count) messages; retrying from the current Graph inventory."
        }
    }
    throw "Mailbox cleanup stopped after $maxPasses passes; Graph still returns $($pending.Count) visible messages."
}

function Write-TaskProgress {
    param([string]$Activity, [int]$Current, [int]$Total, [string]$Status, [int]$Id = 1)
    if ($Total -le 0) { return }
    $percent = [int][Math]::Floor(($Current * 100) / $Total)
    Write-Progress -Id $Id -Activity $Activity -Status "$percent%" -PercentComplete $percent
    $reportInterval = [Math]::Max(1, [Math]::Ceiling($Total / 10.0))
    if ($Current -eq 1 -or $Current -eq $Total -or $Current % $reportInterval -eq 0) {
        Write-Output ("{0}: {1}%" -f $Activity, $percent)
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
    -not $ConfirmTeamsMessageDeletion -or
    -not $ConfirmSandboxTenant
)) {
    throw 'Apply requires the mailbox/calendar switches and -ConfirmSandboxTenant. Teams deletion additionally requires -ConfirmTeamsMessageDeletion.'
}

$users = Import-Csv -LiteralPath (Join-Path $dataRoot 'users.csv')
Write-Output "Cleanup: $($users.Count) test accounts"
if (-not $Apply) { Write-Output 'Preview only; no data was changed.' }
$script:graphToken = Get-GraphToken -TenantId $tenantId -ClientId $clientId -ClientSecret $clientSecret

$messageTargets = [System.Collections.Generic.List[object]]::new()
$eventTargets = [System.Collections.Generic.List[object]]::new()
$readOnlyCalendars = [System.Collections.Generic.List[object]]::new()
$chatTargets = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
$chatSessions = @{}
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
    $graphUser = (Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/users/${upn}?`$select=id,userPrincipalName").Body
    $session = Get-DelegatedGraphSession -User $graphUser
    $chats = Get-AllGraphItems -Uri 'https://graph.microsoft.com/v1.0/me/chats?$select=id,chatType,topic&$top=50' -AccessToken $session.AccessToken
    foreach ($chat in $chats) {
        if (-not $chatTargets.ContainsKey($chat.id)) {
            $chatTargets.Add($chat.id, [pscustomobject]@{ Id = $chat.id; Type = $chat.chatType; Topic = $chat.topic; SeenThrough = [System.Collections.Generic.List[string]]::new() })
            $chatSessions[[string]$chat.id] = $session
        }
        $chatTargets[$chat.id].SeenThrough.Add($user.userPrincipalName)
    }
    $userIndex++
    Write-TaskProgress -Activity 'Scanning user mailboxes and calendars' -Current $userIndex -Total $users.Count -Status $user.userPrincipalName
}
if ($Apply) {
    $teamsTargets = Get-TeamsMessageTargets -Chats $chatTargets.Values -ChatSessions $chatSessions
    $chatMessageCount = @($teamsTargets | Where-Object { $_.Source -eq 'chat' }).Count
    $channelMessageCount = @($teamsTargets | Where-Object { $_.Source -eq 'channel' }).Count
    Write-Output "Teams inventory: $chatMessageCount chat messages across $($chatTargets.Count) live conversations; $channelMessageCount channel messages."
}
Write-Output "Found $($messageTargets.Count) mailbox messages, $($eventTargets.Count) calendar events, and $($chatTargets.Count) live chats."
if ($readOnlyCalendars.Count -gt 0) { Write-Output "Skipping $($readOnlyCalendars.Count) read-only calendars." }
if (-not $Apply) { return }

Write-Output 'Teams messages: deleting messages found in the live Graph inventory.'
Remove-TeamsMessages -Targets $teamsTargets -Users $users
Write-Output 'Chats: preserving conversation containers for reseeding.'
Remove-DatasetTeams

$eventIndex = 0
foreach ($item in $eventTargets) {
    $user = [uri]::EscapeDataString($item.User)
    $calendarId = [uri]::EscapeDataString($item.CalendarId)
    $id = [uri]::EscapeDataString($item.Id)
    Invoke-GraphCleanupMutation -Method DELETE -Uri "https://graph.microsoft.com/v1.0/users/$user/calendars/$calendarId/events/$id" | Out-Null
    $eventIndex++
    Write-TaskProgress -Activity 'Deleting calendar events' -Current $eventIndex -Total $eventTargets.Count -Status "$($item.User) | $($item.Subject)"
}
# Organizer deletions can generate cancellation messages. Re-inventory after
# calendar cleanup so those messages are included in mailbox cleanup.
$messageTargets = @(Get-MailboxMessageTargets -Users $users)
Remove-MailboxMessages -Users $users -Targets $messageTargets
Confirm-DatasetTeamsRemoved -Teams $script:deletedDatasetTeams
Write-Output 'Cleanup complete.'
