[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$RemoveAllMailboxAndCalendarItems,
    [switch]$ConfirmCalendarCancellations,
    [switch]$ConfirmTeamsMessageDeletion,
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
        throw "Microsoft Graph $Method request failed ($statusText) at $Uri. Response: $detail"
    }
    $parsed = $null
    if ($response.Content) { $parsed = $response.Content | ConvertFrom-Json }
    return [pscustomobject]@{ Body = $parsed; StatusCode = [int]$response.StatusCode; Headers = $response.Headers }
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
    param([object[]]$Chats)
    $targets = [Collections.Generic.List[object]]::new()
    foreach ($chat in $Chats) {
        $chatId = [uri]::EscapeDataString([string]$chat.Id)
        $uri = "https://graph.microsoft.com/v1.0/chats/$chatId/messages"
        foreach ($message in (Get-AllGraphItems -Uri "$uri`?`$top=50")) {
            if (-not (Test-ReadableTeamsMessage -Message $message)) { continue }
            $messageId = [uri]::EscapeDataString([string]$message.id)
            $authorId = [string]$message.from.user.id
            $targets.Add(@{ AuthorId = $authorId; ReadUri = "$uri/$messageId"; DeleteUri = "https://graph.microsoft.com/v1.0/users/$authorId/chats/$chatId/messages/$messageId/softDelete" })
        }
    }
    foreach ($team in (Get-AllGraphItems -Uri 'https://graph.microsoft.com/v1.0/teams?$select=id&$top=100')) {
        $teamId = [uri]::EscapeDataString([string]$team.id)
        foreach ($channel in (Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels?`$select=id")) {
            $channelId = [uri]::EscapeDataString([string]$channel.id)
            $uri = "https://graph.microsoft.com/v1.0/teams/$teamId/channels/$channelId/messages"
            foreach ($message in (Get-AllGraphItems -Uri "$uri`?`$top=50")) {
                if (-not (Test-ReadableTeamsMessage -Message $message)) { continue }
                $messageId = [uri]::EscapeDataString([string]$message.id)
                $targets.Add(@{ AuthorId = [string]$message.from.user.id; ReadUri = "$uri/$messageId"; DeleteUri = "$uri/$messageId/softDelete" })
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
    $index = 0
    foreach ($target in $Targets) {
        $session = $sessions[$target.AuthorId]
        if ($session.ExpiresAt -lt (Get-Date).AddMinutes(5)) {
            $session = Get-DelegatedGraphSession -User $usersById[$target.AuthorId]
            $sessions[$target.AuthorId] = $session
        }
        $current = (Invoke-Graph -Method GET -Uri $target.ReadUri).Body
        if (Test-ReadableTeamsMessage -Message $current) {
            Invoke-Graph -Method POST -Uri $target.DeleteUri -AccessToken $session.AccessToken | Out-Null
        }
        $index++
        Write-TaskProgress -Activity 'Deleting Teams messages' -Current $index -Total $Targets.Count -Status $target.AuthorId
    }
    foreach ($target in $Targets) {
        $remaining = (Invoke-Graph -Method GET -Uri $target.ReadUri).Body
        if (Test-ReadableTeamsMessage -Message $remaining) { throw "Teams message $($target.ReadUri) remains readable after delegated deletion. Mailbox and calendar cleanup was stopped." }
    }
    Write-Output 'Teams messages: verified deleted.'
}

function Remove-SeededChats {
    $statePath = Join-Path $PSScriptRoot 'test-seed-state.json'
    if (-not (Test-Path -LiteralPath $statePath)) { throw 'Seed state is missing; refusing to delete chats that cannot be tied to the seeded dataset.' }
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($state.tenantId -and $state.tenantId -ne $tenantId) { throw 'Seed state belongs to a different tenant; refusing to delete chats.' }
    $chatIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($property in $state.conversationIds.PSObject.Properties) {
        $id = [string]$property.Value
        if (-not [string]::IsNullOrWhiteSpace($id)) { [void]$chatIds.Add($id) }
    }
    $legacyChatIds = @()
    if ($state.legacyChatIds -is [System.Management.Automation.PSCustomObject]) {
        $legacyChatIds = @($state.legacyChatIds.PSObject.Properties | ForEach-Object { $_.Value })
    }
    elseif ($null -ne $state.legacyChatIds) {
        $legacyChatIds = @($state.legacyChatIds)
    }
    foreach ($id in $legacyChatIds) {
        if ($id -is [string] -and -not [string]::IsNullOrWhiteSpace($id)) { [void]$chatIds.Add($id) }
    }
    if ($chatIds.Count -eq 0) { Write-Output 'Seeded chats: none recorded.'; return }

    Write-Output "Seeded chats: soft-deleting $($chatIds.Count) conversations."
    $index = 0
    foreach ($chatId in $chatIds) {
        $encodedId = [uri]::EscapeDataString($chatId)
        $chatUri = "https://graph.microsoft.com/v1.0/chats/$encodedId"
        $exists = $true
        try { Invoke-Graph -Method GET -Uri "$chatUri`?`$select=id" | Out-Null }
        catch { if ($_ -match 'HTTP 404') { $exists = $false } else { throw } }
        if ($exists) {
            Invoke-Graph -Method DELETE -Uri $chatUri | Out-Null
            Start-Sleep -Milliseconds 1100
        }
        try {
            Invoke-Graph -Method GET -Uri "$chatUri`?`$select=id" | Out-Null
            throw "Seeded chat $chatId is still active after deletion; stopping before mailbox and calendar cleanup."
        }
        catch { if ($_ -notmatch 'HTTP 404') { throw } }
        $index++
        Write-TaskProgress -Activity 'Deleting seeded chats' -Current $index -Total $chatIds.Count -Status $chatId
    }
    Write-Output "Seeded chats: verified $($chatIds.Count) are soft-deleted."
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
Write-Output "Found $($messageTargets.Count) emails, $($eventTargets.Count) calendar events, and $($chatTargets.Count) chats."
if ($readOnlyCalendars.Count -gt 0) { Write-Output "Skipping $($readOnlyCalendars.Count) read-only calendars." }
if (-not $Apply) { return }

Write-Output 'Teams messages: delegated deletion will run after test-user sign-in preflight.'
$teamsTargets = Get-TeamsMessageTargets -Chats $chatTargets.Values
Remove-TeamsMessages -Targets $teamsTargets -Users $users
Remove-SeededChats

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
    $legacyChatSource = $loadedState.legacyChatIds
    if ($null -eq $legacyChatSource) { $legacyChatSource = $loadedState.chatIds }
    if ($legacyChatSource -is [System.Management.Automation.PSCustomObject]) {
        $legacyChatSource = @($legacyChatSource.PSObject.Properties | ForEach-Object { $_.Value })
    }
    $savedLegacyChatIds = @($legacyChatSource | Where-Object {
        $_ -is [string] -and -not [string]::IsNullOrWhiteSpace($_)
    })
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
    Write-Output 'Seed state reset.'
}
Write-Output 'Cleanup complete.'
