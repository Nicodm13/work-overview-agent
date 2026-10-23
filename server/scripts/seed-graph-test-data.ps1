[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$SendMail,
    [switch]$SendMeetingInvitations,
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
        [ValidateSet('GET', 'POST', 'DELETE')][string]$Method,
        [string]$Uri,
        [object]$Body
    )
    $headers = @{ Authorization = "Bearer $script:graphToken" }
    $request = @{ Method = $Method; Uri = $Uri; Headers = $headers }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json'
        $request.Body = ConvertTo-Json -InputObject $Body -Depth 30 -Compress
    }
    $response = Invoke-WebRequest @request
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

function Wait-TeamProvisioning {
    param([string]$TeamId, [string]$OperationId)
    $uri = "https://graph.microsoft.com/v1.0/teams/$TeamId/operations/$OperationId"
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $operation = (Invoke-Graph -Method GET -Uri $uri).Body
        if ($operation.status -eq 'succeeded') { return }
        if ($operation.status -eq 'failed') { throw "Team provisioning failed: $($operation.error.message)" }
        Start-Sleep -Seconds 2
    }
    throw 'Timed out waiting for Microsoft Teams to provision the project team.'
}

Import-LocalConfig -Path $configPath
$tenantId = $env:WORK_OVERVIEW_ENTRA_TENANT_ID
$clientId = $env:WORK_OVERVIEW_ENTRA_CLIENT_ID
$clientSecret = $env:WORK_OVERVIEW_ENTRA_CLIENT_SECRET
if ($tenantId -and $tenantId -ne $expectedTenantId) { throw "Configured tenant must be the sandbox tenant $expectedTenantId." }
if (-not $tenantId) { $tenantId = $expectedTenantId }
if ($Apply -and $ConfirmTenantId -ne $expectedTenantId) { throw "For apply mode pass -ConfirmTenantId $expectedTenantId." }
if ($Apply -and -not $SendMail) { throw 'Apply mode requires -SendMail because the shared dataset includes email messages.' }
if ($Apply -and -not $SendMeetingInvitations) { throw 'Apply mode requires -SendMeetingInvitations because Graph sends invitations for events with attendees.' }

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
Write-Output "Emails to send: $($emails.Count) (Graph timestamps these at send time)"
Write-Output "Calendar events: $($events.Count); event attendees receive invitations at apply time"
if (-not $Apply) {
    Write-Output 'Mail preview (date in mock data | sender | recipients | subject):'
    foreach ($mail in $emails) { Write-Output "  $($mail.occurredAt) | $($mail.senderOrOrganizer) | $($mail.participants) | $($mail.title)" }
    Write-Output 'Meeting preview (date | organizer | attendees | subject):'
    foreach ($event in $events) { Write-Output "  $($event.startsAt) | $($event.senderOrOrganizer) | $($event.participants) | $($event.title)" }
    Write-Output 'Preview only. Review the CSV content and recipients. Apply with -Apply -SendMail -SendMeetingInvitations -ConfirmTenantId <sandbox-tenant-id>.'
    return
}
if (-not $clientId -or -not $clientSecret) { throw 'Set WORK_OVERVIEW_ENTRA_CLIENT_ID and WORK_OVERVIEW_ENTRA_CLIENT_SECRET in server/.env or the process environment.' }

$script:graphToken = Get-GraphToken -TenantId $tenantId -ClientId $clientId -ClientSecret $clientSecret
$graphUsers = @{}
foreach ($user in $users) {
    $graphUser = (Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/users/$([uri]::EscapeDataString($user.userPrincipalName))?`$select=id,displayName,userPrincipalName").Body
    $graphUsers[$user.displayName] = $graphUser
}

$teamMembers = foreach ($user in $users) {
    $role = if ($user.userPrincipalName -eq 'nidm@v0lsc8f0.onmicrosoft.com') { @('owner') } else { @('member') }
    @{ '@odata.type' = '#microsoft.graph.aadUserConversationMember'; roles = $role; 'user@odata.bind' = "https://graph.microsoft.com/v1.0/users('$($graphUsers[$user.displayName].id)')" }
}
$teamBody = @{
    displayName = $team.displayName
    description = $team.description
    visibility = $team.visibility
    members = @($teamMembers)
}
$teamResponse = Invoke-Graph -Method POST -Uri 'https://graph.microsoft.com/v1.0/teams' -Body $teamBody
$contentLocation = $teamResponse.Headers['Content-Location']
if (-not $contentLocation) { $contentLocation = $teamResponse.Headers['Location'] }
if ($contentLocation -notmatch "/teams\('([^']+)'\)/operations\('([^']+)'\)") {
    throw 'Could not read the team ID and provisioning operation from the Graph response.'
}
$teamId = $Matches[1]
$operationId = $Matches[2]
Wait-TeamProvisioning -TeamId $teamId -OperationId $operationId

$channelIds = @{}
foreach ($channel in $team.channels) {
    $created = (Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels" -Body @{ displayName = $channel.displayName; description = $channel.description }).Body
    $channelIds[$channel.displayName] = $created.id
}

$conversationIds = @{}
foreach ($conversation in $conversations) {
    $members = foreach ($name in ($conversation.participants -split '\|')) {
        $graphUser = $graphUsers[$name]
        if (-not $graphUser) { throw "Unknown conversation participant '$name'." }
        @{ '@odata.type' = '#microsoft.graph.aadUserConversationMember'; roles = @('owner'); 'user@odata.bind' = "https://graph.microsoft.com/v1.0/users('$($graphUser.id)')" }
    }
    $chatBody = @{ chatType = $conversation.chatType; members = @($members) }
    if ($conversation.topic) { $chatBody.topic = $conversation.topic }
    $createdChat = (Invoke-Graph -Method POST -Uri 'https://graph.microsoft.com/v1.0/chats' -Body $chatBody).Body
    $conversationIds[$conversation.conversationId] = $createdChat.id
}

$historyStart = '2025-01-01T00:00:00Z'
$channelGroups = $messages | Where-Object { $_.team -and $_.channel } | Group-Object channel
foreach ($group in $channelGroups) {
    $channelId = $channelIds[$group.Name]
    if (-not $channelId) { throw "Dataset refers to unknown channel '$($group.Name)'." }
    Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels/$channelId/startMigration" -Body @{ conversationCreationDateTime = $historyStart } | Out-Null
    foreach ($item in ($group.Group | Sort-Object occurredAt)) {
        $author = $graphUsers[$item.senderOrOrganizer]
        $body = @{ createdDateTime = $item.occurredAt; from = @{ user = @{ id = $author.id; displayName = $author.displayName } }; body = @{ contentType = 'html'; content = [System.Net.WebUtility]::HtmlEncode($item.content) } }
        Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels/$channelId/messages" -Body $body | Out-Null
    }
    Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels/$channelId/completeMigration" | Out-Null
}

$chatGroups = $messages | Where-Object { $_.conversationId } | Group-Object conversationId
foreach ($group in $chatGroups) {
    $chatId = $conversationIds[$group.Name]
    if (-not $chatId) { throw "Dataset refers to unknown conversation '$($group.Name)'." }
    Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/chats/$chatId/startMigration" -Body @{ conversationCreationDateTime = $historyStart } | Out-Null
    foreach ($item in ($group.Group | Sort-Object occurredAt)) {
        $author = $graphUsers[$item.senderOrOrganizer]
        $body = @{ createdDateTime = $item.occurredAt; from = @{ user = @{ id = $author.id; displayName = $author.displayName } }; body = @{ contentType = 'html'; content = [System.Net.WebUtility]::HtmlEncode($item.content) } }
        Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/chats/$chatId/messages" -Body $body | Out-Null
    }
    Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/chats/$chatId/completeMigration" | Out-Null
}

foreach ($mail in $emails) {
    $sender = $userByName[$mail.senderOrOrganizer]
    $recipients = foreach ($name in ($mail.participants -split '\|')) {
        if ($name -ne $mail.senderOrOrganizer) { @{ emailAddress = @{ address = $userByName[$name].userPrincipalName } } }
    }
    $mailBody = @{ message = @{ subject = $mail.title; body = @{ contentType = 'Text'; content = $mail.content }; toRecipients = @($recipients); internetMessageHeaders = @(@{ name = 'X-WorkOverview-Seed'; value = 'atlas-developer-platform-v1' }) }; saveToSentItems = $true }
    Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/sendMail" -Body $mailBody | Out-Null
}

foreach ($event in $events) {
    $organizer = $userByName[$event.senderOrOrganizer]
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
    Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/users/$($organizer.userPrincipalName)/calendar/events" -Body $eventBody | Out-Null
}

$statePath = Join-Path $PSScriptRoot '.test-seed-state.json'
@{ tenantId = $tenantId; teamId = $teamId; chatIds = $conversationIds.Values; completedAt = (Get-Date).ToUniversalTime().ToString('o') } |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding UTF8
Write-Output "Seed completed. Team ID: $teamId. Local cleanup state: $statePath"
