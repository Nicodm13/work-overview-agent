[CmdletBinding()]
param(
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
            if (-not [Environment]::GetEnvironmentVariable($name)) {
                [Environment]::SetEnvironmentVariable($name, $value, 'Process')
            }
        }
    }
}

function Get-GraphToken {
    param([string]$TenantId, [string]$ClientId, [string]$ClientSecret)
    $token = Invoke-RestMethod -Method Post `
        -Uri "https://login.microsoftonline.com/$TenantId/oauth2/v2.0/token" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{
            client_id     = $ClientId
            client_secret = $ClientSecret
            scope         = 'https://graph.microsoft.com/.default'
            grant_type    = 'client_credentials'
        }
    return $token.access_token
}

function Invoke-Graph {
    param(
        [ValidateSet('GET', 'POST', 'PATCH', 'DELETE')][string]$Method,
        [string]$Uri,
        [object]$Body
    )
    $headers = @{ Authorization = "Bearer $script:graphToken"; Prefer = 'IdType="ImmutableId"' }
    $request = @{ Method = $Method; Uri = $Uri; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json'
        $request.Body = ConvertTo-Json -InputObject $Body -Depth 30 -Compress
    }
    try {
        $response = Invoke-WebRequest @request
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
    return [pscustomobject]@{ Body = $parsed; Headers = $response.Headers; StatusCode = [int]$response.StatusCode }
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

function Start-ChatMigration {
    param([string]$ChatId, [string]$CreationDateTime)
    $startUri = "https://graph.microsoft.com/v1.0/chats/$ChatId/startMigration"
    $startBody = @{ conversationCreationDateTime = $CreationDateTime }
    try {
        Invoke-Graph -Method POST -Uri $startUri -Body $startBody | Out-Null
        return
    }
    catch {
        if ($_ -notmatch 'SoftDeleted-Thread') { throw }
        Write-Output "Chat $ChatId is soft-deleted; restoring it before importing its messages."
    }

    try {
        Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/teamwork/deletedChats/$ChatId/undoDelete" | Out-Null
    }
    catch {
        throw "Could not restore soft-deleted chat $ChatId. Confirm the seeder app has application permission Chat.ManageDeletion.All with admin consent. $_"
    }

    for ($attempt = 1; $attempt -le 5; $attempt++) {
        try {
            Invoke-Graph -Method POST -Uri $startUri -Body $startBody | Out-Null
            return
        }
        catch {
            if ($_ -match 'SoftDeleted-Thread' -and $attempt -lt 5) {
                Start-Sleep -Seconds 2
                continue
            }
            throw
        }
    }
    throw "Restored chat $ChatId remained unavailable for message migration."
}

function Ensure-ChatFullHistory {
    param(
        [string]$ChatId,
        [object[]]$Participants,
        [DateTimeOffset]$EarliestMessage
    )

    $members = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/chats/$ChatId/members"
    foreach ($participant in $Participants) {
        $graphUser = $graphUsers[$participant]
        $member = $members | Where-Object { $_.userId -eq $graphUser.id } | Select-Object -First 1
        if (-not $member) { throw "Chat $ChatId is missing expected member '$participant'; cannot grant imported chat history." }

        $visibleFrom = $null
        if ($member.visibleHistoryStartDateTime) {
            $visibleFrom = [DateTimeOffset]::Parse($member.visibleHistoryStartDateTime).ToUniversalTime()
        }
        if ($visibleFrom -and $visibleFrom -le $EarliestMessage.ToUniversalTime()) { continue }

        $memberId = [uri]::EscapeDataString($member.id)
        Invoke-Graph -Method DELETE -Uri "https://graph.microsoft.com/v1.0/chats/$ChatId/members/$memberId" | Out-Null
        $memberBody = @{
            '@odata.type' = '#microsoft.graph.aadUserConversationMember'
            'user@odata.bind' = "https://graph.microsoft.com/v1.0/users/$($graphUser.id)"
            visibleHistoryStartDateTime = '0001-01-01T00:00:00Z'
            roles = @($member.roles)
        }
        Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/chats/$ChatId/members" -Body $memberBody | Out-Null
    }
}

function Get-RecordHash {
    param([object]$Record)
    $json = ConvertTo-Json -InputObject $Record -Depth 30 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Normalize-MailBody {
    param([string]$Content)
    $plain = [System.Net.WebUtility]::HtmlDecode(($Content -replace '<[^>]+>', ''))
    return ($plain -replace '\s+', ' ').Trim()
}

function Save-SeedState {
    $script:seedState.updatedAt = (Get-Date).ToUniversalTime().ToString('o')
    $tempPath = "$($script:statePath).tmp"
    $script:seedState | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $tempPath -Encoding UTF8
    Move-Item -LiteralPath $tempPath -Destination $script:statePath -Force
}

function Get-SeedState {
    param([string]$Path, [string]$TenantId)
    $state = @{
        schemaVersion = 2; tenantId = $TenantId; dataset = 'atlas-developer-platform-v1'; teamId = $null
        legacyChatIds = @(); channelIds = @{}; conversationIds = @{}; teamsMessages = @{}; emails = @{}; events = @{}
    }
    if (Test-Path -LiteralPath $Path) {
        $loaded = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        if ($loaded.tenantId -and $loaded.tenantId -ne $TenantId) {
            throw "Seed state belongs to tenant $($loaded.tenantId), not configured tenant $TenantId."
        }
        if ($loaded.teamId) { $state.teamId = $loaded.teamId }
        if ($loaded.chatIds) { $state.legacyChatIds = @($loaded.chatIds) }
        if ($loaded.legacyChatIds) { $state.legacyChatIds = @($loaded.legacyChatIds) }
        foreach ($mapName in @('channelIds', 'conversationIds', 'teamsMessages', 'emails', 'events')) {
            $map = $loaded.$mapName
            if ($map) {
                foreach ($property in $map.PSObject.Properties) { $state[$mapName][$property.Name] = $property.Value }
            }
        }
    }
    return $state
}

function Wait-TeamProvisioning {
    param([string]$TeamId, [string]$OperationId, [string]$OperationUri)
    $maxAttempts = 26
    $pollIntervalSeconds = 35
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $lastStatus = 'unknown'
    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        $operation = (Invoke-Graph -Method GET -Uri $OperationUri).Body
        $lastStatus = $operation.status
        Write-TaskProgress -Activity 'Provisioning Microsoft Team' -Current $attempt -Total $maxAttempts -Status "$lastStatus; elapsed $([int]$stopwatch.Elapsed.TotalSeconds)s"
        if ($operation.status -eq 'succeeded') {
            Write-Progress -Id 1 -Activity 'Provisioning Microsoft Team' -Completed
            return
        }
        if ($operation.status -eq 'failed') {
            Write-Progress -Id 1 -Activity 'Provisioning Microsoft Team' -Completed
            throw "Team provisioning failed: $($operation.error.message)"
        }
        if ($attempt -lt $maxAttempts) { Start-Sleep -Seconds $pollIntervalSeconds }
    }
    throw "Timed out waiting for Microsoft Teams after $([int]$stopwatch.Elapsed.TotalMinutes) minutes. Team ID: $TeamId; operation ID: $OperationId; last status: $lastStatus. The Team may still be provisioning."
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
if (-not $ConfirmSandboxTenant) { throw 'Pass -ConfirmSandboxTenant to confirm seeding the configured sandbox tenant.' }

$script:statePath = Join-Path $PSScriptRoot 'test-seed-state.json'
$script:seedState = Get-SeedState -Path $script:statePath -TenantId $tenantId
Save-SeedState

$users = Import-Csv -LiteralPath (Join-Path $dataRoot 'users.csv')
$emails = Import-Csv -LiteralPath (Join-Path $dataRoot 'email.csv')
$events = Import-Csv -LiteralPath (Join-Path $dataRoot 'calendar.csv')
$messages = Import-Csv -LiteralPath (Join-Path $dataRoot 'teams.csv')
$conversations = Import-Csv -LiteralPath (Join-Path $dataRoot 'teams-conversations.csv')
$team = Get-Content -LiteralPath (Join-Path $dataRoot 'team.json') -Raw | ConvertFrom-Json
$userByName = @{}
foreach ($user in $users) { $userByName[$user.displayName] = $user }

Write-Output "Target tenant: $tenantId"
Write-Output "Shared dataset: $((Resolve-Path $dataRoot).Path)"
Write-Output "Users: $($users.Count)"
Write-Output "Team: $($team.displayName), $($team.channels.Count) channels"
Write-Output "Chats: $($conversations.Count) (one-to-one and group)"
Write-Output "Historical Teams messages: $($messages.Count)"
Write-Output "Emails to reconcile: $($emails.Count) (new or corrected messages are sent; unchanged messages are skipped)"
Write-Output "Calendar events to reconcile: $($events.Count); attendees can receive invitations or update notices"
if (-not $clientId -or -not $clientSecret) { throw 'Set WORK_OVERVIEW_ENTRA_CLIENT_ID and WORK_OVERVIEW_ENTRA_CLIENT_SECRET in server/.env or the process environment.' }

$script:graphToken = Get-GraphToken -TenantId $tenantId -ClientId $clientId -ClientSecret $clientSecret
$graphUsers = @{}
$userIndex = 0
foreach ($user in $users) {
    $userIndex++
    $graphUser = (Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/users/$([uri]::EscapeDataString($user.userPrincipalName))?`$select=id,displayName,userPrincipalName").Body
    $graphUsers[$user.displayName] = $graphUser
    Write-TaskProgress -Activity 'Resolving dataset users' -Current $userIndex -Total $users.Count -Status $user.displayName
}

$teamOwner = $users | Where-Object { $_.userPrincipalName -eq 'nidm@v0lsc8f0.onmicrosoft.com' } | Select-Object -First 1
if (-not $teamOwner) { throw 'The dataset must contain nidm@v0lsc8f0.onmicrosoft.com as the project Team owner.' }
$teamOwnerMember = @{
    '@odata.type' = '#microsoft.graph.aadUserConversationMember'
    roles = @('owner')
    'user@odata.bind' = "https://graph.microsoft.com/v1.0/users('$($graphUsers[$teamOwner.displayName].id)')"
}
$teamBody = @{
    'template@odata.bind' = "https://graph.microsoft.com/v1.0/teamsTemplates('standard')"
    displayName = $team.displayName
    description = $team.description
    visibility = $team.visibility
    members = @($teamOwnerMember)
}
$teamId = $script:seedState.teamId
if ($teamId) {
    try { $existingChannels = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels?`$select=id,displayName,description" }
    catch {
        if ($_ -match 'HTTP 404') {
            Write-Output "Recorded Team $teamId no longer exists; creating a replacement."
            $teamId = $null
            $script:seedState.teamId = $null
            Save-SeedState
        }
        else { throw }
    }
}
if (-not $teamId) {
    $teamResponse = Invoke-Graph -Method POST -Uri 'https://graph.microsoft.com/v1.0/teams' -Body $teamBody
    $contentLocation = $teamResponse.Headers['Content-Location']
    $operationLocation = $teamResponse.Headers['Location']
    if ($contentLocation -notmatch "/teams\('([^']+)'\)") { throw 'Could not read the team ID from the Graph Content-Location response header.' }
    $teamId = $Matches[1]
    $script:seedState.teamId = $teamId
    Save-SeedState
    if ($operationLocation -notmatch "/teams\('[^']+'\)/operations\('([^']+)'\)") { throw 'Could not read the provisioning operation ID from the Graph Location response header.' }
    $operationId = $Matches[1]
    $operationUri = if ($operationLocation -match '^https?://') { $operationLocation } else { "https://graph.microsoft.com/v1.0$operationLocation" }
    Write-Output "Team creation accepted. Team ID: $teamId; operation ID: $operationId"
    Wait-TeamProvisioning -TeamId $teamId -OperationId $operationId -OperationUri $operationUri
    $existingChannels = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels?`$select=id,displayName,description"
}
else { Write-Output "Reusing existing project Team: $teamId" }

$membersToCheck = @($users | Where-Object { $_.userPrincipalName -ne $teamOwner.userPrincipalName })
$memberTotal = $membersToCheck.Count
$memberIndex = 0
foreach ($user in $membersToCheck) {
    $memberIndex++
    $graphUserId = $graphUsers[$user.displayName].id
    $memberBody = @{
        '@odata.type' = '#microsoft.graph.aadUserConversationMember'
        'user@odata.bind' = "https://graph.microsoft.com/v1.0/users('$graphUserId')"
    }
    try {
        Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/members" -Body $memberBody | Out-Null
        Write-TaskProgress -Activity 'Reconciling Team members' -Current $memberIndex -Total $memberTotal -Status "Added | $($user.displayName)"
    }
    catch {
        if ($_ -notmatch '(?i)already exists|already a member|duplicate|MemberAlreadyExists') { throw }
        Write-TaskProgress -Activity 'Reconciling Team members' -Current $memberIndex -Total $memberTotal -Status "Already present | $($user.displayName)"
    }
    Start-Sleep -Seconds 2
}

$channelIds = @{}
foreach ($existingChannel in $existingChannels) {
    $channelIds[$existingChannel.displayName] = $existingChannel.id
}
$channelIndex = 0
foreach ($channel in $team.channels) {
    if ($channelIds.ContainsKey($channel.displayName)) {
        $channelStatus = 'Reusing existing channel'
    }
    else {
        $created = (Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels" -Body @{ displayName = $channel.displayName; description = $channel.description }).Body
        $channelIds[$channel.displayName] = $created.id
        $script:seedState.channelIds[$channel.displayName] = $created.id
        Save-SeedState
        $channelStatus = 'Created channel'
    }
    $channelIndex++
    Write-TaskProgress -Activity 'Preparing Team channels' -Current $channelIndex -Total $team.channels.Count -Status "$channelStatus | $($channel.displayName)"
}

$conversationIds = @{}
$conversationIndex = 0
$legacyChatIdsInUse = @{}
foreach ($conversation in $conversations) {
    $members = foreach ($name in ($conversation.participants -split '\|')) {
        $graphUser = $graphUsers[$name]
        if (-not $graphUser) { throw "Unknown conversation participant '$name'." }
        @{ '@odata.type' = '#microsoft.graph.aadUserConversationMember'; roles = @('owner'); 'user@odata.bind' = "https://graph.microsoft.com/v1.0/users('$($graphUser.id)')" }
    }
    $chatBody = @{ chatType = $conversation.chatType; members = @($members) }
    if ($conversation.topic) { $chatBody.topic = $conversation.topic }
    $chatId = $script:seedState.conversationIds[$conversation.conversationId]
    if (-not $chatId) {
        $expectedMemberIds = @($conversation.participants -split '\|' | ForEach-Object { $graphUsers[$_].id } | Sort-Object -Unique)
        foreach ($legacyChatId in $script:seedState.legacyChatIds) {
            if ($legacyChatIdsInUse.ContainsKey($legacyChatId)) { continue }
            try { $legacyChat = (Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/chats/$($legacyChatId)?`$expand=members").Body }
            catch {
                if ($_ -match 'HTTP 404') { continue }
                if ($_ -match 'HTTP 403') { throw 'The existing seed-state has legacy chat IDs that must be mapped before reseeding. Temporarily grant the seeder application permission Chat.Read.All, then rerun.' }
                throw
            }
            $actualMemberIds = @($legacyChat.members | ForEach-Object { $_.userId } | Sort-Object -Unique)
            $membersMatch = ($actualMemberIds -join '|') -eq ($expectedMemberIds -join '|')
            $topicMatches = -not $conversation.topic -or $legacyChat.topic -eq $conversation.topic
            if ($membersMatch -and $topicMatches -and $legacyChat.chatType -eq $conversation.chatType) {
                $chatId = $legacyChat.id
                $legacyChatIdsInUse[$chatId] = $true
                Write-Output "Adopted existing chat for dataset conversation $($conversation.conversationId)."
                break
            }
        }
    }
    if (-not $chatId) {
        $createdChat = (Invoke-Graph -Method POST -Uri 'https://graph.microsoft.com/v1.0/chats' -Body $chatBody).Body
        $chatId = $createdChat.id
    }
    $script:seedState.conversationIds[$conversation.conversationId] = $chatId
    Save-SeedState
    $conversationIds[$conversation.conversationId] = $chatId
    $conversationIndex++
    Write-TaskProgress -Activity 'Creating chats' -Current $conversationIndex -Total $conversations.Count -Status $conversation.topic
}

$historyStart = '2025-01-01T00:00:00Z'
$channelMessages = @($messages | Where-Object { $_.team -and $_.channel })
$channelMessageTotal = $channelMessages.Count
$channelMessageIndex = 0
$channelGroups = $channelMessages | Group-Object channel
foreach ($group in $channelGroups) {
    $channelId = $channelIds[$group.Name]
    if (-not $channelId) { throw "Dataset refers to unknown channel '$($group.Name)'." }
    $needsMigration = @($group.Group | Where-Object { $saved = $script:seedState.teamsMessages[$_.id]; -not $saved -or $saved.hash -ne (Get-RecordHash $_) }).Count -gt 0
    if (-not $needsMigration) {
        foreach ($item in $group.Group) { $channelMessageIndex++; Write-TaskProgress -Activity 'Importing channel messages' -Current $channelMessageIndex -Total $channelMessageTotal -Status "Unchanged | $($item.id)" }
        continue
    }
    Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels/$channelId/startMigration" -Body @{ conversationCreationDateTime = $historyStart } | Out-Null
    foreach ($item in ($group.Group | Sort-Object occurredAt)) {
        $itemHash = Get-RecordHash $item
        $saved = $script:seedState.teamsMessages[$item.id]
        if ($saved -and $saved.hash -eq $itemHash) {
            $channelMessageIndex++
            Write-TaskProgress -Activity 'Importing channel messages' -Current $channelMessageIndex -Total $channelMessageTotal -Status "Unchanged | $($item.id)"
            continue
        }
        $author = $graphUsers[$item.senderOrOrganizer]
        $messageTime = if ($saved -and $saved.timestamp) { [DateTimeOffset]::Parse($saved.timestamp).ToUniversalTime().AddMilliseconds(1) } else { [DateTimeOffset]::Parse($item.occurredAt).ToUniversalTime() }
        if ($saved) { Write-Output "Dataset row $($item.id) changed; Teams migration messages are append-only, importing an updated version." }
        $supersededIds = @()
        if ($saved -and $saved.supersededGraphIds) { $supersededIds = @($saved.supersededGraphIds) }
        if ($saved -and $saved.graphId -and $saved.graphId -notin $supersededIds) { $supersededIds += $saved.graphId }
        $body = @{ createdDateTime = $messageTime.ToString('yyyy-MM-ddTHH:mm:ss.fffZ'); from = @{ user = @{ id = $author.id; displayName = $author.displayName } }; body = @{ contentType = 'html'; content = [System.Net.WebUtility]::HtmlEncode($item.content) } }
        try {
            $createdMessage = (Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels/$channelId/messages" -Body $body).Body
            $messageId = $createdMessage.id
        }
        catch {
            if ($_ -notmatch 'HTTP 409' -or $saved) { throw "Teams rejected the updated message timestamp for dataset row $($item.id); no new version was recorded. $_" }
            $messageId = $null
            Write-Output "Message $($item.id) already exists at its imported timestamp; recording it as applied."
        }
        $script:seedState.teamsMessages[$item.id] = @{ hash = $itemHash; graphId = $messageId; timestamp = $messageTime.ToString('yyyy-MM-ddTHH:mm:ss.fffZ'); supersededGraphIds = $supersededIds; updatedAt = (Get-Date).ToUniversalTime().ToString('o') }
        Save-SeedState
        $channelMessageIndex++
        Write-TaskProgress -Activity 'Importing channel messages' -Current $channelMessageIndex -Total $channelMessageTotal -Status $group.Name
    }
    Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels/$channelId/completeMigration" | Out-Null
}

$chatMessages = @($messages | Where-Object { $_.conversationId })
$chatMessageTotal = $chatMessages.Count
$chatMessageIndex = 0
$chatGroups = $chatMessages | Group-Object conversationId
foreach ($group in $chatGroups) {
    $chatId = $conversationIds[$group.Name]
    if (-not $chatId) { throw "Dataset refers to unknown conversation '$($group.Name)'." }
    $needsMigration = @($group.Group | Where-Object { $saved = $script:seedState.teamsMessages[$_.id]; -not $saved -or $saved.hash -ne (Get-RecordHash $_) }).Count -gt 0
    if (-not $needsMigration) {
        foreach ($item in $group.Group) { $chatMessageIndex++; Write-TaskProgress -Activity 'Importing chat messages' -Current $chatMessageIndex -Total $chatMessageTotal -Status "Unchanged | $($item.id)" }
        if ($group.Name -in @('project-room', 'platform-working-group', 'product-delivery-group', 'quality-release-group', 'data-api-group')) {
            $participants = @($conversations | Where-Object { $_.conversationId -eq $group.Name } | ForEach-Object { $_.participants -split '\|' })
            $earliestMessage = [DateTimeOffset]::Parse(($group.Group | Sort-Object occurredAt | Select-Object -First 1).occurredAt)
            Ensure-ChatFullHistory -ChatId $chatId -Participants $participants -EarliestMessage $earliestMessage
        }
        continue
    }
    Start-ChatMigration -ChatId $chatId -CreationDateTime $historyStart
    foreach ($item in ($group.Group | Sort-Object occurredAt)) {
        $itemHash = Get-RecordHash $item
        $saved = $script:seedState.teamsMessages[$item.id]
        if ($saved -and $saved.hash -eq $itemHash) {
            $chatMessageIndex++
            Write-TaskProgress -Activity 'Importing chat messages' -Current $chatMessageIndex -Total $chatMessageTotal -Status "Unchanged | $($item.id)"
            continue
        }
        $author = $graphUsers[$item.senderOrOrganizer]
        $messageTime = if ($saved -and $saved.timestamp) { [DateTimeOffset]::Parse($saved.timestamp).ToUniversalTime().AddMilliseconds(1) } else { [DateTimeOffset]::Parse($item.occurredAt).ToUniversalTime() }
        if ($saved) { Write-Output "Dataset row $($item.id) changed; Teams migration messages are append-only, importing an updated version." }
        $supersededIds = @()
        if ($saved -and $saved.supersededGraphIds) { $supersededIds = @($saved.supersededGraphIds) }
        if ($saved -and $saved.graphId -and $saved.graphId -notin $supersededIds) { $supersededIds += $saved.graphId }
        $body = @{ createdDateTime = $messageTime.ToString('yyyy-MM-ddTHH:mm:ss.fffZ'); from = @{ user = @{ id = $author.id; displayName = $author.displayName } }; body = @{ contentType = 'html'; content = [System.Net.WebUtility]::HtmlEncode($item.content) } }
        try {
            $createdMessage = (Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/chats/$chatId/messages" -Body $body).Body
            $messageId = $createdMessage.id
        }
        catch {
            if ($_ -notmatch 'HTTP 409' -or $saved) { throw "Teams rejected the updated message timestamp for dataset row $($item.id); no new version was recorded. $_" }
            $messageId = $null
            Write-Output "Message $($item.id) already exists at its imported timestamp; recording it as applied."
        }
        $script:seedState.teamsMessages[$item.id] = @{ hash = $itemHash; graphId = $messageId; timestamp = $messageTime.ToString('yyyy-MM-ddTHH:mm:ss.fffZ'); supersededGraphIds = $supersededIds; updatedAt = (Get-Date).ToUniversalTime().ToString('o') }
        Save-SeedState
        $chatMessageIndex++
        Write-TaskProgress -Activity 'Importing chat messages' -Current $chatMessageIndex -Total $chatMessageTotal -Status $group.Name
    }
    Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/chats/$chatId/completeMigration" | Out-Null
    if ($group.Name -in @('project-room', 'platform-working-group', 'product-delivery-group', 'quality-release-group', 'data-api-group')) {
        $participants = @($conversations | Where-Object { $_.conversationId -eq $group.Name } | ForEach-Object { $_.participants -split '\|' })
        $earliestMessage = [DateTimeOffset]::Parse(($group.Group | Sort-Object occurredAt | Select-Object -First 1).occurredAt)
        Ensure-ChatFullHistory -ChatId $chatId -Participants $participants -EarliestMessage $earliestMessage
    }
}

$mailIndex = 0
$sentItemsByUser = @{}
foreach ($mail in $emails) {
    $itemHash = Get-RecordHash $mail
    $saved = $script:seedState.emails[$mail.id]
    $sender = $userByName[$mail.senderOrOrganizer]
    if (-not $saved) {
        $senderUpn = $sender.userPrincipalName
        if (-not $sentItemsByUser.ContainsKey($senderUpn)) {
            $sentItemsByUser[$senderUpn] = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$senderUpn/mailFolders/sentitems/messages?`$select=id,subject,body,toRecipients,internetMessageId,internetMessageHeaders&`$top=100"
        }
        $legacy = $sentItemsByUser[$senderUpn] | Where-Object {
            $seedHeader = $_.internetMessageHeaders | Where-Object { $_.name -ieq 'X-WorkOverview-Seed' -and $_.value -eq 'atlas-developer-platform-v1' } | Select-Object -First 1
            $seedIdHeader = $_.internetMessageHeaders | Where-Object { $_.name -ieq 'X-WorkOverview-Seed-Id' -and $_.value -eq $mail.id } | Select-Object -First 1
            $seedHeader -and ($seedIdHeader -or $_.subject -eq $mail.title)
        } | Select-Object -First 1
        if ($legacy) {
            $sameContent = (Normalize-MailBody $legacy.body.content) -eq (Normalize-MailBody $mail.content)
            $savedHash = if ($sameContent) { $itemHash } else { 'legacy-unverified' }
            $legacyRecipients = @($sender.userPrincipalName) + @($legacy.toRecipients | ForEach-Object { $_.emailAddress.address }) | Sort-Object -Unique
            $saved = @{ graphId = $legacy.id; internetMessageId = $legacy.internetMessageId; hash = $savedHash; status = 'sent'; participants = $legacyRecipients; adopted = $true }
            $script:seedState.emails[$mail.id] = $saved
            Save-SeedState
            Write-Output "Adopted previously seeded email $($mail.id); no duplicate will be sent."
        }
    }
    if ($saved -and $saved.hash -eq $itemHash -and $saved.status -eq 'sent') {
        try {
            Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages/$($saved.graphId)?`$select=id,isDraft" | Out-Null
            $mailIndex++
            Write-TaskProgress -Activity 'Sending emails' -Current $mailIndex -Total $emails.Count -Status "Unchanged | $($mail.id)"
            continue
        }
        catch {
            if ($_ -notmatch 'HTTP 404') { throw }
            $script:seedState.emails[$mail.id].status = 'replacing'
            Save-SeedState
            $saved.status = 'replacing'
            Write-Output "Seeded sender copy for $($mail.id) is missing; reconciling its recipients and recreating it."
        }
    }
    $recipients = foreach ($name in ($mail.participants -split '\|')) {
        if ($name -ne $mail.senderOrOrganizer) { @{ emailAddress = @{ address = $userByName[$name].userPrincipalName } } }
    }
            $mailboxUpns = if ($saved.participants) { @($saved.participants) } else { @($sender.userPrincipalName) + @($mail.participants -split '\|' | ForEach-Object { $userByName[$_].userPrincipalName }) | Sort-Object -Unique }
    if ($saved -and $saved.status -eq 'draft' -and $saved.graphId) {
        $draftUri = "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages/$($saved.graphId)"
        $existingDraft = (Invoke-Graph -Method GET -Uri "$($draftUri)?`$select=id,isDraft").Body
        if (-not $existingDraft.isDraft) {
            $script:seedState.emails[$mail.id].status = 'sent'
            Save-SeedState
            if ($saved.hash -ne $itemHash) { throw "Email $($mail.id) was sent before an interrupted run and its dataset row has since changed. Run Microsoft Graph - Seed Data again to reconcile the changed item." }
            $mailIndex++
            Write-TaskProgress -Activity 'Sending emails' -Current $mailIndex -Total $emails.Count -Status "Recovered sent item | $($mail.id)"
            continue
        }
        Invoke-Graph -Method PATCH -Uri $draftUri -Body @{ subject = $mail.title; body = @{ contentType = 'Text'; content = $mail.content }; toRecipients = @($recipients) } | Out-Null
        $script:seedState.emails[$mail.id].hash = $itemHash
        Save-SeedState
        Invoke-Graph -Method POST -Uri "$draftUri/send" | Out-Null
        $script:seedState.emails[$mail.id].status = 'sent'
        Save-SeedState
    }
    else {
        if ($saved -and ($saved.status -eq 'sent' -or $saved.status -eq 'replacing')) {
            if (-not $saved.internetMessageId) {
                $sentCopy = (Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages/$($saved.graphId)?`$select=id,internetMessageId").Body
                $saved.internetMessageId = $sentCopy.internetMessageId
            }
            if (-not $saved.internetMessageId) { throw "Cannot reconcile changed email $($mail.id): Graph did not return its internetMessageId." }
            $script:seedState.emails[$mail.id].status = 'replacing'
            $script:seedState.emails[$mail.id].internetMessageId = $saved.internetMessageId
            Save-SeedState
            foreach ($recipientUpn in $mailboxUpns) {
                $cacheKey = "all:$recipientUpn"
                if (-not $sentItemsByUser.ContainsKey($cacheKey)) {
                    $sentItemsByUser[$cacheKey] = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$recipientUpn/messages?`$select=id,internetMessageId&`$top=100"
                }
                $copies = @($sentItemsByUser[$cacheKey] | Where-Object { $_.internetMessageId -eq $saved.internetMessageId })
                foreach ($copy in $copies) {
                    try { Invoke-Graph -Method DELETE -Uri "https://graph.microsoft.com/v1.0/users/$recipientUpn/messages/$($copy.id)" | Out-Null }
                    catch { if ($_ -notmatch 'HTTP 404') { throw } }
                }
            }
            Write-Output "Removed the prior seeded copies for $($mail.id); recreating the corrected message."
        }
        $draftBody = @{ subject = $mail.title; body = @{ contentType = 'Text'; content = $mail.content }; toRecipients = @($recipients); internetMessageHeaders = @(@{ name = 'X-WorkOverview-Seed'; value = 'atlas-developer-platform-v1' }, @{ name = 'X-WorkOverview-Seed-Id'; value = $mail.id }) }
        $draftMessage = (Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages" -Body $draftBody).Body
        $script:seedState.emails[$mail.id] = @{ hash = $itemHash; graphId = $draftMessage.id; status = 'draft'; participants = $mailboxUpns }
        Save-SeedState
        Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages/$($draftMessage.id)/send" | Out-Null
        $sentMessage = (Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages/$($draftMessage.id)?`$select=id,internetMessageId").Body
        $script:seedState.emails[$mail.id].internetMessageId = $sentMessage.internetMessageId
        $script:seedState.emails[$mail.id].status = 'sent'
        Save-SeedState
    }
    $mailIndex++
    Write-TaskProgress -Activity 'Sending emails' -Current $mailIndex -Total $emails.Count -Status $mail.title
}

$eventIndex = 0
$calendarItemsByUser = @{}
foreach ($event in $events) {
    $itemHash = Get-RecordHash $event
    $saved = $script:seedState.events[$event.id]
    $organizer = $userByName[$event.senderOrOrganizer]
    if (-not $saved) {
        $organizerUpn = $organizer.userPrincipalName
        if (-not $calendarItemsByUser.ContainsKey($organizerUpn)) {
            $calendarItemsByUser[$organizerUpn] = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$organizerUpn/calendar/events?`$select=id,subject,start,transactionId,categories&`$top=100"
        }
        $legacyEvent = $calendarItemsByUser[$organizerUpn] | Where-Object {
            $transactionMatches = $_.transactionId -eq $event.id
            $seedCategory = @($_.categories) -contains 'AtlasSeed:atlas-developer-platform-v1'
            $sameStart = $_.start.dateTime -and ([DateTimeOffset]::Parse($_.start.dateTime).ToUniversalTime() -eq [DateTimeOffset]::Parse($event.startsAt).ToUniversalTime())
            $transactionMatches -or ($seedCategory -and $_.subject -eq $event.title -and $sameStart)
        } | Select-Object -First 1
        if ($legacyEvent) {
            $saved = @{ graphId = $legacyEvent.id; hash = 'legacy-unverified'; adopted = $true }
            $script:seedState.events[$event.id] = $saved
            Save-SeedState
            Write-Output "Adopted previously seeded calendar event $($event.id); no duplicate will be created."
        }
    }
    $attendees = foreach ($name in ($event.participants -split '\|')) {
        if ($name -ne $event.senderOrOrganizer) { @{ emailAddress = @{ address = $userByName[$name].userPrincipalName }; type = 'required' } }
    }
    $eventBody = @{
        subject = $event.title
        body = @{ contentType = 'Text'; content = $event.content }
        start = @{ dateTime = $event.startsAt; timeZone = 'UTC' }
        end = @{ dateTime = $event.endsAt; timeZone = 'UTC' }
        attendees = @($attendees)
        categories = @('AtlasSeed:atlas-developer-platform-v1')
        transactionId = $event.id
    }
    if ($saved -and $saved.graphId) {
        if ($saved.hash -eq $itemHash) {
            try {
                Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/users/$($organizer.userPrincipalName)/events/$($saved.graphId)?`$select=id" | Out-Null
                $eventIndex++
                Write-TaskProgress -Activity 'Reconciling calendar events' -Current $eventIndex -Total $events.Count -Status "Unchanged | $($event.id)"
                continue
            }
            catch {
                if ($_ -notmatch 'HTTP 404') { throw }
                $script:seedState.events.Remove($event.id)
                Save-SeedState
                $saved = $null
                Write-Output "Seeded calendar event $($event.id) is missing from Graph; recreating it."
            }
        }
    }
    if ($saved -and $saved.graphId) {
        $eventPatch = @{}
        foreach ($key in $eventBody.Keys) { if ($key -ne 'transactionId') { $eventPatch[$key] = $eventBody[$key] } }
        try {
            Invoke-Graph -Method PATCH -Uri "https://graph.microsoft.com/v1.0/users/$($organizer.userPrincipalName)/events/$($saved.graphId)" -Body $eventPatch | Out-Null
            $script:seedState.events[$event.id].hash = $itemHash
            Save-SeedState
            $eventIndex++
            Write-TaskProgress -Activity 'Reconciling calendar events' -Current $eventIndex -Total $events.Count -Status "Updated | $($event.title)"
            continue
        }
        catch { if ($_ -notmatch 'HTTP 404') { throw } }
    }
    $createdEvent = (Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/users/$($organizer.userPrincipalName)/calendar/events" -Body $eventBody).Body
    $script:seedState.events[$event.id] = @{ hash = $itemHash; graphId = $createdEvent.id }
    Save-SeedState
    $eventIndex++
    Write-TaskProgress -Activity 'Creating calendar events' -Current $eventIndex -Total $events.Count -Status $event.title
}

$script:seedState.teamId = $teamId
Save-SeedState
Write-Output "Seed reconciliation completed. Team ID: $teamId. State: $script:statePath"
