[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$RemoveAllMailboxAndCalendarItems,
    [switch]$ConfirmCalendarCancellations,
    [switch]$PurgeAllTeamsMessages,
    [switch]$ConfirmPermanentTeamsPurge,
    [switch]$SkipTeamsMessagePurge,
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
    param([ValidateSet('GET', 'POST', 'DELETE')][string]$Method, [string]$Uri, [object]$Body)
    $requestParameters = @{ UseBasicParsing = $true; Method = $Method; Uri = $Uri; Headers = @{ Authorization = "Bearer $script:graphToken" } }
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
        throw "Microsoft Graph $Method request failed ($statusText) at $Uri. Response: $detail"
    }
    $parsed = $null
    if ($response.Content) { $parsed = $response.Content | ConvertFrom-Json }
    return [pscustomobject]@{ Body = $parsed; StatusCode = [int]$response.StatusCode; Headers = $response.Headers }
}

function Wait-PurviewOperation {
    param([string]$OperationUri, [string]$Label)
    if ($OperationUri.StartsWith('/')) { $OperationUri = "https://graph.microsoft.com$OperationUri" }
    $deadline = (Get-Date).AddMinutes(45)
    $pollFailures = 0
    do {
        Start-Sleep -Seconds 10
        try {
            $operation = (Invoke-Graph -Method GET -Uri $OperationUri).Body
            $pollFailures = 0
        }
        catch {
            if ($_ -match 'HTTP (429|500|502|503|504)') {
                $pollFailures++
                [Console]::Out.WriteLine("$Label status check returned a temporary Graph error; retrying ($pollFailures). $_")
                [Console]::Out.Flush()
                continue
            }
            throw
        }
        $status = [string]$operation.status
        [Console]::Out.WriteLine("$Label status: $status ($($operation.percentProgress)%)")
        [Console]::Out.Flush()
        if ($status -in @('succeeded', 'partiallySucceeded', 'failed', 'submissionFailed')) { return $operation }
    } while ((Get-Date) -lt $deadline)
    throw "$Label did not finish within 45 minutes. Check the Purview operation before rerunning Clear Data."
}

function Invoke-TeamsMessagePurge {
    param()
    $stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
    $createdCase = Invoke-Graph -Method POST -Uri 'https://graph.microsoft.com/v1.0/security/cases/ediscoveryCases' -Body @{
        displayName = "WorkOverviewSandboxCleanup-$stamp"
        description = 'Teams-message purge for the disposable Work Overview test sandbox.'
    }
    $caseId = $createdCase.Body.id
    if (-not $caseId) { throw 'Microsoft Purview did not return an eDiscovery case ID.' }

    $search = Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/security/cases/ediscoveryCases/$caseId/searches" -Body @{
        displayName = "WorkOverviewTeamsCleanup-$stamp"
        description = 'Find Teams instant messages across sandbox mailboxes before permanent purge.'
        contentQuery = 'kind:microsoftteams'
        dataSourceScopes = 'allTenantMailboxes'
    }
    $searchId = $search.Body.id
    if (-not $searchId) { throw 'Microsoft Purview did not return an eDiscovery search ID.' }
    Write-Output "Purview case: $caseId"
    Write-Output "Purview Teams-message search: $searchId (kind:microsoftteams; all tenant mailboxes)"

    $estimate = Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/security/cases/ediscoveryCases/$caseId/searches/$searchId/estimateStatistics" -Body @{
        statisticsOptions = 'includeRefiners, includeQueryStats'
    }
    $estimateLocation = [string]$estimate.Headers['Location']
    if (-not $estimateLocation) { throw 'Microsoft Purview did not return the estimate operation Location header.' }
    $estimateResult = Wait-PurviewOperation -OperationUri $estimateLocation -Label 'Purview Teams-message search estimate'
    if ($estimateResult.status -ne 'succeeded') { throw "Purview search estimate ended with status '$($estimateResult.status)'. No Teams purge was submitted." }
    Write-Output "Estimated matching items: $($estimateResult.indexedItemCount); mailboxes with hits: $($estimateResult.mailboxCount)"

    $purge = Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/security/cases/ediscoveryCases/$caseId/searches/$searchId/purgeData" -Body @{
        purgeType = 'permanentlyDelete'
        purgeAreas = 'teamsMessages'
    }
    $purgeLocation = [string]$purge.Headers['Location']
    if (-not $purgeLocation) { throw 'Microsoft Purview accepted no purge operation Location header; check the case before rerunning.' }
    $purgeResult = Wait-PurviewOperation -OperationUri $purgeLocation -Label 'Purview Teams-message purge'
    if ($purgeResult.status -ne 'succeeded') {
        throw "Purview Teams-message purge ended with status '$($purgeResult.status)'. Review its operation report before rerunning; Graph purges at most 100 items per mailbox per request."
    }
    Write-Output 'Purview Teams-message purge operation succeeded. The immediate search estimate is not used as a deletion check because indexed search results can remain after the purge completes.'
    Write-Output 'Chat, team, and channel containers were preserved.'
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

function Test-ActiveTeamsMessages {
    param([object[]]$Users)

    $checkedChats = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($user in $Users) {
        $upn = [uri]::EscapeDataString($user.userPrincipalName)
        $chats = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$upn/chats?`$select=id&`$top=50"
        foreach ($chat in $chats) {
            if (-not $checkedChats.Add([string]$chat.id)) { continue }
            $chatId = [uri]::EscapeDataString([string]$chat.id)
            $messages = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/chats/$chatId/messages?`$select=id,deletedDateTime,messageType&`$top=50"
            foreach ($message in $messages) {
                if ($message.messageType -eq 'message' -and -not $message.deletedDateTime) {
                    [Console]::Out.WriteLine("Active Teams chat messages found in chat $($chat.id). Purview cleanup is required.")
                    [Console]::Out.Flush()
                    return $true
                }
            }
        }
    }

    $teams = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/teams?`$select=id&`$top=100"
    foreach ($team in $teams) {
        $teamId = [uri]::EscapeDataString([string]$team.id)
        $channels = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels?`$select=id"
        foreach ($channel in $channels) {
            $channelId = [uri]::EscapeDataString([string]$channel.id)
            $messages = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels/$channelId/messages?`$select=id,deletedDateTime,messageType&`$top=50"
            foreach ($message in $messages) {
                if ($message.messageType -eq 'message' -and -not $message.deletedDateTime) {
                    [Console]::Out.WriteLine("Active Teams channel messages found in channel $($channel.id). Purview cleanup is required.")
                    [Console]::Out.Flush()
                    return $true
                }
            }
        }
    }

    return $false
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
    (-not $SkipTeamsMessagePurge -and (-not $PurgeAllTeamsMessages -or -not $ConfirmPermanentTeamsPurge)) -or
    -not $ConfirmSandboxTenant
)) {
    throw 'Apply requires the mailbox/calendar switches and -ConfirmSandboxTenant. Teams purge additionally requires -PurgeAllTeamsMessages and -ConfirmPermanentTeamsPurge unless -SkipTeamsMessagePurge is specified.'
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
Write-Output "Mailbox messages in scope: $($messageTargets.Count)"
Write-Output "Calendar events in scope: $($eventTargets.Count)"
Write-Output "Read-only calendars skipped: $($readOnlyCalendars.Count)"
Write-Output "Unique active chats involving the listed users (will be preserved): $($chatTargets.Count)"
Write-Output 'Calendar items to delete:'
foreach ($item in $eventTargets) { Write-Output "  $($item.User) | $($item.Calendar) | $($item.Subject)" }
if ($readOnlyCalendars.Count -gt 0) {
    Write-Output 'Read-only calendars skipped (Graph does not allow event deletion):'
    foreach ($calendar in $readOnlyCalendars) { Write-Output "  $($calendar.User) | $($calendar.Name)" }
}
Write-Output 'Chat containers to preserve:'
foreach ($chat in $chatTargets.Values) { Write-Output "  $($chat.Type) | $($chat.Topic) | $($chat.Id)" }
Write-Output 'This cleanup scope is all mail and all events in writable calendars in the 17 listed mailboxes, including Microsoft preloaded sample content. Read-only calendars are skipped.'
Write-Output 'Teams-message purge scope is all tenant mailboxes in the disposable sandbox. It permanently purges matching Teams messages and keeps chat, team, and channel containers so test chats can be reused.'
Write-Output 'Microsoft Purview app-only setup requires eDiscovery.Read.All and eDiscovery.ReadWrite.All application permissions, an eDiscovery Premium-enabled case, and the app service principal registered in Purview eDiscovery roles.'
Write-Output 'The purge API processes at most 100 Teams messages per mailbox per request.'
if (-not $Apply) { return }

if ($SkipTeamsMessagePurge) {
    Write-Output 'Skipping Teams-message purge because it was already completed and verified separately.'
}
elseif (-not (Test-ActiveTeamsMessages -Users $users)) {
    Write-Output 'No active Teams chat or channel messages were found through Microsoft Graph; skipping the Purview search and purge.'
}
else {
    Invoke-TeamsMessagePurge
}

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
$seedStatePath = Join-Path $PSScriptRoot 'test-seed-state.json'
if (Test-Path -LiteralPath $seedStatePath) {
    $loadedState = Get-Content -LiteralPath $seedStatePath -Raw | ConvertFrom-Json
    $datasetName = if ($loadedState.dataset) { $loadedState.dataset } else { 'atlas-developer-platform-v1' }
    $savedLegacyChatIds = if ($loadedState.legacyChatIds) { @($loadedState.legacyChatIds) } elseif ($loadedState.chatIds) { @($loadedState.chatIds) } else { @() }
    $preservedState = @{
        schemaVersion = 2
        tenantId = $tenantId
        dataset = $datasetName
        teamId = $loadedState.teamId
        teamProvisioning = $loadedState.teamProvisioning
        legacyChatIds = $savedLegacyChatIds
        channelIds = @{}
        conversationIds = @{}
        teamsMessages = @{}
        emails = @{}
        events = @{}
    }
    foreach ($mapName in @('channelIds', 'conversationIds')) {
        $map = $loadedState.$mapName
        if ($map) { foreach ($property in $map.PSObject.Properties) { $preservedState[$mapName][$property.Name] = $property.Value } }
    }
    $messageMap = $loadedState.teamsMessages
    if ($messageMap) {
        foreach ($property in $messageMap.PSObject.Properties) {
            $messageState = @{}
            foreach ($messageProperty in $property.Value.PSObject.Properties) { $messageState[$messageProperty.Name] = $messageProperty.Value }
            $messageState.purged = $true
            $preservedState.teamsMessages[$property.Name] = $messageState
        }
    }
    $preservedState.updatedAt = (Get-Date).ToUniversalTime().ToString('o')
    $tempStatePath = "$seedStatePath.tmp"
    $preservedState | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $tempStatePath -Encoding UTF8
    Move-Item -LiteralPath $tempStatePath -Destination $seedStatePath -Force
    Write-Output 'Seed state reset: existing Team, channel, and chat IDs retained; old message, email, and event IDs cleared.'
}
Write-Output 'Mailbox, calendar, and Teams-message cleanup completed. User accounts and chat/team/channel containers were preserved.'
