[CmdletBinding()]
param(
    [switch]$ConfirmSandboxTenant,
    [ValidateRange(1, 16)][int]$MaxConcurrency = 4
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
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
        [object]$Body,
        [string]$BodyContentType = 'application/json',
        [string]$AccessToken = $script:graphToken
    )
    $headers = @{ Authorization = "Bearer $AccessToken"; Prefer = 'IdType="ImmutableId"' }
    $request = @{ Method = $Method; Uri = $Uri; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $request.ContentType = $BodyContentType
        $request.Body = if ($BodyContentType -eq 'application/json') {
            ConvertTo-Json -InputObject $Body -Depth 30 -Compress
        }
        else {
            [string]$Body
        }
    }
    for ($attempt = 1; $attempt -le 5; $attempt++) {
      try {
        $response = Invoke-WebRequest @request
        break
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
        if (($statusCode -eq 429 -or ($Method -eq 'GET' -and $statusCode -in @(500, 502, 503, 504))) -and $attempt -lt 5) {
            $retryAfter = [string]$webResponse.Headers['Retry-After']
            $delay = if ($retryAfter -match '^\d+$') { [int]$retryAfter } else { [int][Math]::Min(30, [Math]::Pow(2, $attempt)) }
            Start-Sleep -Seconds $delay
            continue
        }
        $statusText = if ($statusCode) { "HTTP $statusCode" } else { 'HTTP request failed' }
        $detail = if ($responseBody) { $responseBody } else { $_.Exception.Message }
        throw "Microsoft Graph $Method request failed ($statusText) at $Uri. Response: $detail"
      }
    }
    $parsed = $null
    if ($response.Content) { $parsed = $response.Content | ConvertFrom-Json }
    return [pscustomobject]@{ Body = $parsed; Headers = $response.Headers; StatusCode = [int]$response.StatusCode }
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

function Get-SeedDelegatedSession {
    param([object]$User)
    $password = [Environment]::GetEnvironmentVariable('WORK_OVERVIEW_TEST_USER_PASSWORD')
    if (-not $password) { throw 'Set WORK_OVERVIEW_TEST_USER_PASSWORD in server/.env to reopen hidden sandbox chats.' }
    try {
        $response = Invoke-RestMethod -Method Post -Uri "https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token" -ContentType 'application/x-www-form-urlencoded' -Body @{
            client_id = $clientId; client_secret = $clientSecret; grant_type = 'password'
            username = $User.userPrincipalName; password = $password
            scope = 'https://graph.microsoft.com/Chat.ReadWrite https://graph.microsoft.com/User.Read'
        }
    }
    catch { throw "Delegated Graph sign-in failed for $($User.userPrincipalName). Check the shared test password, consent, and sign-in policy." }
    finally { $password = $null }
    if (-not $response.access_token) { throw "Delegated Graph sign-in returned no token for $($User.userPrincipalName)." }
    $me = (Invoke-Graph -Method GET -Uri 'https://graph.microsoft.com/v1.0/me?$select=id' -AccessToken $response.access_token).Body
    if ([string]$me.id -ne [string]$User.id) { throw "Delegated token identity differs from test user $($User.userPrincipalName)." }
    return @{ AccessToken = $response.access_token; ExpiresAt = (Get-Date).AddSeconds([int]$response.expires_in) }
}

function Ensure-SeedOneOnOneListed {
    param([string]$ChatId, [string[]]$Participants, [hashtable]$GraphUsers, [hashtable]$Listings, [hashtable]$Sessions)
    $members = @($Participants | ForEach-Object {
        $memberId = $GraphUsers[$_].id
        @{ '@odata.type' = '#microsoft.graph.aadUserConversationMember'; roles = @('owner'); 'user@odata.bind' = "https://graph.microsoft.com/v1.0/users('$memberId')" }
    })
    foreach ($name in $Participants) {
        $user = $GraphUsers[$name]
        $userId = [string]$user.id
        if (-not $Listings.ContainsKey($userId)) {
            if (-not $Sessions.ContainsKey($userId) -or $Sessions[$userId].ExpiresAt -lt (Get-Date).AddMinutes(5)) {
                $Sessions[$userId] = Get-SeedDelegatedSession -User $user
            }
            $Listings[$userId] = @(Get-AllGraphItems -Uri 'https://graph.microsoft.com/v1.0/me/chats?$select=id&$top=50' -AccessToken $Sessions[$userId].AccessToken)
        }
        if (@($Listings[$userId] | Where-Object { $_.id -eq $ChatId }).Count -gt 0) { continue }
        if (-not $Sessions.ContainsKey($userId) -or $Sessions[$userId].ExpiresAt -lt (Get-Date).AddMinutes(5)) {
            $Sessions[$userId] = Get-SeedDelegatedSession -User $user
        }
        $opened = (Invoke-Graph -Method POST -Uri 'https://graph.microsoft.com/v1.0/chats' -Body @{ chatType = 'oneOnOne'; members = $members } -AccessToken $Sessions[$userId].AccessToken).Body
        if ([string]$opened.id -ne $ChatId) { throw "Graph returned an unexpected chat while reopening $ChatId for $name." }
        foreach ($participant in $Participants) {
            $participantUser = $GraphUsers[$participant]
            $participantId = [string]$participantUser.id
            if (-not $Sessions.ContainsKey($participantId) -or $Sessions[$participantId].ExpiresAt -lt (Get-Date).AddMinutes(5)) {
                $Sessions[$participantId] = Get-SeedDelegatedSession -User $participantUser
            }
            $Listings[$participantId] = @(Get-AllGraphItems -Uri 'https://graph.microsoft.com/v1.0/me/chats?$select=id&$top=50' -AccessToken $Sessions[$participantId].AccessToken)
        }
        Write-Output "Reopened one-to-one chat for $name."
    }
    foreach ($name in $Participants) {
        $userId = [string]$GraphUsers[$name].id
        if (@($Listings[$userId] | Where-Object { $_.id -eq $ChatId }).Count -eq 0) {
            throw "Chat $ChatId is not listed for $name after reopening; no messages were imported into this chat."
        }
    }
}

function Get-SeedMessageInventory {
    param([string]$MessagesUri)
    $query = if ($MessagesUri -match '/chats/[^/]+/messages$') { '?$top=50&$orderby=createdDateTime%20desc' } else { '?$top=50' }
    $restored = $false
    for ($attempt = 1; $attempt -le 6; $attempt++) {
        try { return @(Get-AllGraphItems -Uri "${MessagesUri}${query}") }
        catch {
            $errorText = [string]$_
            if ($errorText -notmatch 'SoftDeleted-Thread' -or $MessagesUri -notmatch '/chats/([^/]+)/messages$') { throw }
            $chatId = [uri]::EscapeDataString([uri]::UnescapeDataString($Matches[1]))
            if (-not $restored) {
                Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/teamwork/deletedChats/$chatId/undoDelete" | Out-Null
                $restored = $true
            }
            if ($attempt -ge 6) { throw "Restored chat $chatId is still unavailable for message inventory. $errorText" }
            Start-Sleep -Seconds 2
        }
    }
}

function Wait-SentMessage {
    param([string]$Uri)
    for ($attempt = 1; $attempt -le 15; $attempt++) {
        try {
            $message = (Invoke-Graph -Method GET -Uri "$Uri`?`$select=id,isDraft,internetMessageId").Body
            if ($message -and -not $message.isDraft -and $message.internetMessageId) { return $message }
        }
        catch { if ($_ -notmatch 'HTTP 404') { throw } }
        if ($attempt -lt 15) { Start-Sleep -Seconds 2 }
    }
    throw "Graph has not confirmed the sent message at $Uri. Check the sender's mailbox before retrying; the send may still complete."
}

function Ensure-SeedChatActive {
    param([string]$ChatId)
    $encodedChatId = [uri]::EscapeDataString($ChatId)
    try {
        Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/chats/${encodedChatId}?`$select=id" | Out-Null
    }
    catch {
        if ($_ -notmatch 'HTTP 404') { throw }
        try {
            Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/teamwork/deletedChats/$encodedChatId/undoDelete" | Out-Null
        }
        catch {
            throw "Could not restore seed chat $ChatId before clearing its messages. $_"
        }
    }
}

function Get-MigrationStartTime {
    param([string]$ThreadUri, [string]$RequestedStart)

    $thread = (Invoke-Graph -Method GET -Uri "${ThreadUri}?`$select=id,createdDateTime").Body
    if (-not $thread.createdDateTime) { throw "Graph returned no createdDateTime for $ThreadUri; migration was not started." }
    $created = [DateTimeOffset]::Parse($thread.createdDateTime).ToUniversalTime()
    $requested = [DateTimeOffset]::Parse($RequestedStart).ToUniversalTime()
    # Migration moves the thread's creation date backwards. Equality is also
    # rejected, including when resuming a previously completed import.
    $latestAllowed = $created.AddSeconds(-1)
    if ($requested -gt $latestAllowed) { $requested = $latestAllowed }
    return $requested.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
}

function Start-ChatMigration {
    param([string]$ChatId, [string]$CreationDateTime)
    $startUri = "https://graph.microsoft.com/v1.0/chats/$ChatId/startMigration"
    $chatUri = "https://graph.microsoft.com/v1.0/chats/$ChatId"
    $chat = (Invoke-Graph -Method GET -Uri "$chatUri`?`$select=id,migrationMode").Body
    if ($chat.migrationMode -eq 'inProgress') {
        Write-Verbose "Chat $ChatId is already in migration mode; resuming its import."
        return
    }
    $startBody = @{ conversationCreationDateTime = (Get-MigrationStartTime -ThreadUri "https://graph.microsoft.com/v1.0/chats/$ChatId" -RequestedStart $CreationDateTime) }
    try {
        Invoke-Graph -Method POST -Uri $startUri -Body $startBody | Out-Null
        return
    }
    catch {
        if ($_ -notmatch 'SoftDeleted-Thread') { throw }
        Write-Verbose "Chat $ChatId is soft-deleted; restoring it before importing its messages."
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

function Start-ChannelMigration {
    param([string]$ChannelUri, [string]$CreationDateTime, [string]$PreviousMode)

    $body = @{ conversationCreationDateTime = $CreationDateTime }
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        try {
            Invoke-Graph -Method POST -Uri "$ChannelUri/startMigration" -Body $body | Out-Null
            return
        }
        catch {
            $errorText = [string]$_
            if ($errorText -notmatch 'HTTP (500|502|503|504)|HTTP request failed') { throw }
            $delay = [int][Math]::Min(30, [Math]::Pow(2, $attempt))
            Start-Sleep -Seconds $delay
            $state = (Invoke-Graph -Method GET -Uri "$ChannelUri`?`$select=id,migrationMode").Body
            if ($state.migrationMode -eq 'inProgress') { return }
            if ([string]$state.migrationMode -ne $PreviousMode) {
                throw "Cannot safely retry starting migration for ${ChannelUri}: Graph reports '$($state.migrationMode)'. $errorText"
            }
            if ($attempt -ge 5) { throw $errorText }
            Write-Output "Graph could not start channel migration; retrying (attempt $($attempt + 1) of 5)."
        }
    }
}

function Complete-SeedMigration {
    param([string]$ThreadUri)
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        try {
            Invoke-Graph -Method POST -Uri "$ThreadUri/completeMigration" | Out-Null
            return
        }
        catch {
            $errorText = [string]$_
            if ($errorText -notmatch 'HTTP (429|500|502|503|504)|HTTP request failed' -or $attempt -ge 5) { throw }
            $state = (Invoke-Graph -Method GET -Uri "$ThreadUri`?`$select=id,migrationMode").Body
            if ($state.migrationMode -eq 'completed') { return }
            if ($state.migrationMode -ne 'inProgress') { throw "Cannot safely retry migration completion for ${ThreadUri}: Graph reports '$($state.migrationMode)'. $errorText" }
            Start-Sleep -Seconds ([int][Math]::Min(30, [Math]::Pow(2, $attempt)))
        }
    }
}

function Ensure-ChatFullHistory {
    param(
        [string]$ChatId,
        [object[]]$Participants,
        [DateTimeOffset]$EarliestMessage,
        [string]$ChatType
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

        if ($ChatType -eq 'oneOnOne') {
            throw "Chat $ChatId does not expose the imported history to $participant. One-to-one chat members cannot be removed and re-added to change history visibility."
        }

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
    $hashInput = $Record
    if ($Record.attachments) {
        $hashInput = @{
            record = $Record
            attachmentHashes = @(
                foreach ($attachment in ($Record.attachments -split '\|')) {
                    $attachmentPath = Resolve-DatasetAttachment -RelativePath $attachment
                    "$attachment=$((Get-FileHash -LiteralPath $attachmentPath -Algorithm SHA256).Hash.ToLowerInvariant())"
                }
            )
        }
    }
    $json = ConvertTo-Json -InputObject $hashInput -Depth 30 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Resolve-SeedMessageTime {
    param([string]$DatasetTimestamp, [object]$Saved)
    $messageTime = [DateTimeOffset]::Parse($DatasetTimestamp).ToUniversalTime()
    if ($Saved -and $Saved.timestamp) {
        $previousTime = [DateTimeOffset]::Parse($Saved.timestamp).ToUniversalTime()
        if ($previousTime -ge $messageTime) { return $previousTime.AddMilliseconds(1) }
    }
    return $messageTime
}

function Import-TeamsMigrationMessage {
    param([string]$Uri, [hashtable]$Body, [DateTimeOffset]$MessageTime, [string]$DatasetRowId)
    $maxConflictRetries = 10
    for ($attempt = 0; $attempt -le $maxConflictRetries; $attempt++) {
        $Body.createdDateTime = $MessageTime.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
        try {
            $message = (Invoke-Graph -Method POST -Uri $Uri -Body $Body).Body
            return [pscustomobject]@{ Message = $message; Timestamp = $MessageTime }
        }
        catch {
            if ($_ -notmatch 'HTTP 409' -or $attempt -ge $maxConflictRetries) {
                throw "Teams could not import dataset row $DatasetRowId after $($attempt + 1) timestamp attempt(s). $_"
            }
            Write-Verbose "Dataset row $DatasetRowId collided at $($MessageTime.ToString('o')); trying the next millisecond."
            $MessageTime = $MessageTime.AddMilliseconds(1)
        }
    }
}

function Resolve-DatasetAttachment {
    param([string]$RelativePath)
    if ([string]::IsNullOrWhiteSpace($RelativePath)) { throw 'Dataset attachment path must not be blank.' }
    $rootPath = [System.IO.Path]::GetFullPath($dataRoot).TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    $attachmentPath = [System.IO.Path]::GetFullPath((Join-Path $rootPath $RelativePath))
    if (-not $attachmentPath.StartsWith($rootPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Dataset attachment path must stay within mock-data: $RelativePath"
    }
    if (-not (Test-Path -LiteralPath $attachmentPath -PathType Leaf)) {
        throw "Dataset attachment does not exist: $RelativePath"
    }
    return $attachmentPath
}

function Get-MeetingNoteIdFromFileName {
    param([string]$FileName)
    $title = [IO.Path]::GetFileNameWithoutExtension($FileName) -replace '^Minutes of Meeting - ', ''
    $slug = $title.ToLowerInvariant() -replace '[^a-z0-9]+', '-'
    return "notes-$($slug.Trim('-'))"
}

function Read-MeetingNoteDocument {
    param([string]$RelativePath)
    $documentPath = Resolve-DatasetAttachment -RelativePath $RelativePath
    $archive = [System.IO.Compression.ZipFile]::OpenRead($documentPath)
    try {
        $entry = $archive.GetEntry('word/document.xml')
        if (-not $entry) { throw "DOCX has no Word document body: $RelativePath" }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { [xml]$xml = $reader.ReadToEnd() }
        finally { $reader.Dispose() }
    }
    finally { $archive.Dispose() }

    $namespace = [System.Xml.XmlNamespaceManager]::new($xml.NameTable)
    $namespace.AddNamespace('w', 'http://schemas.openxmlformats.org/wordprocessingml/2006/main')
    $paragraphs = foreach ($paragraph in $xml.SelectNodes('//w:body/w:p', $namespace)) {
        (($paragraph.SelectNodes('.//w:t', $namespace) | ForEach-Object { $_.InnerText }) -join '')
    }
    $metadata = @{}
    $notesStarted = $false
    foreach ($paragraph in $paragraphs) {
        if ($paragraph -eq 'Meeting notes') { $notesStarted = $true; continue }
        if ($notesStarted) { continue }
        if ($paragraph -match '^Meeting:\s*(.+)$') { $metadata.meetingTitle = $Matches[1]; continue }
        if ($paragraph -match '^Date and time:\s*(.+)$') { $metadata.occurredAt = $Matches[1]; continue }
        if ($paragraph -match '^Organizer:\s*(.+)$') { $metadata.organizer = $Matches[1]; continue }
        if ($paragraph -match '^Participants:\s*(.+)$') { $metadata.participants = $Matches[1]; continue }
        if ($paragraph -match '^Topic:\s*(.+)$') { $metadata.topicId = $Matches[1]; continue }
    }
    foreach ($field in @('meetingTitle', 'occurredAt', 'organizer', 'participants', 'topicId')) {
        if ([string]::IsNullOrWhiteSpace($metadata[$field])) {
            throw "DOCX meeting note is missing '$field' metadata: $RelativePath"
        }
    }
    $metadata.dateToken = $metadata.occurredAt
    $metadata.occurredAt = Resolve-DatasetTimestamp -Value $metadata.dateToken
    $metadata.id = Get-MeetingNoteIdFromFileName -FileName $RelativePath
    $metadata.participantNames = @($metadata.participants -split ';' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    return [pscustomobject]$metadata
}

function Get-DatasetEmailAttachments {
    param([object]$Mail)
    if ([string]::IsNullOrWhiteSpace($Mail.attachments)) { return @() }
    $attachments = @(
        foreach ($relativePath in ($Mail.attachments -split '\|')) {
            $attachmentPath = Resolve-DatasetAttachment -RelativePath $relativePath
            $bytes = [System.IO.File]::ReadAllBytes($attachmentPath)
            $attachmentName = [System.IO.Path]::GetFileName($attachmentPath)
            if ($relativePath -like 'meeting-notes/*.docx') {
                $note = Read-MeetingNoteDocument -RelativePath $relativePath
                $rebasedTimestamp = $script:meetingNoteTimestampMap[$note.dateToken]
                if ($rebasedTimestamp) {
                    $bytes = Set-MeetingNoteDocumentDate -Bytes $bytes -OldTimestamp $note.dateToken -NewTimestamp $rebasedTimestamp
                }
            }
            if ($bytes.Length -gt 3MB) {
                throw "Graph simple file attachments must be smaller than 3 MB: $relativePath"
            }
            @{
                '@odata.type' = '#microsoft.graph.fileAttachment'
                name = $attachmentName
                contentType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
                contentBytes = [Convert]::ToBase64String($bytes)
            }
        }
    )
    return $attachments
}

function New-MimeReplyDraftContent {
    param(
        [string]$FromAddress,
        [string[]]$ToAddresses,
        [string]$Subject,
        [string]$Content,
        [string]$ParentInternetMessageId,
        [string[]]$References,
        [string]$DatasetRowId
    )
    if ($ParentInternetMessageId -notmatch '^<[^<>]+>$') {
        throw "Reply parent for dataset email $DatasetRowId has an invalid Internet Message ID."
    }
    $encodedSubject = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($Subject))
    $encodedBody = [Convert]::ToBase64String(
        [System.Text.Encoding]::UTF8.GetBytes($Content),
        [Base64FormattingOptions]::InsertLineBreaks
    )
    $toHeader = @($ToAddresses | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object { "<$_>" }) -join ', '
    if ([string]::IsNullOrWhiteSpace($toHeader)) {
        throw "Reply email $DatasetRowId has no recipients."
    }
    $mimeContent = @(
        "From: <$FromAddress>"
        "To: $toHeader"
        "Subject: =?UTF-8?B?${encodedSubject}?="
        "Date: $([DateTimeOffset]::UtcNow.ToString('r', [System.Globalization.CultureInfo]::InvariantCulture))"
        "In-Reply-To: $ParentInternetMessageId"
        "References: $($References -join ' ')"
        'X-WorkOverview-Seed: atlas-developer-platform-v1'
        "X-WorkOverview-Seed-Id: $DatasetRowId"
        'MIME-Version: 1.0'
        'Content-Type: text/plain; charset=utf-8'
        'Content-Transfer-Encoding: base64'
        ''
        $encodedBody
    ) -join "`r`n"
    return [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($mimeContent))
}

function Set-MeetingNoteDocumentDate {
    param([byte[]]$Bytes, [string]$OldTimestamp, [string]$NewTimestamp)
    $stream = [System.IO.MemoryStream]::new()
    try {
        $stream.Write($Bytes, 0, $Bytes.Length)
        $stream.Position = 0
        $archive = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Update, $true)
        try {
            $entry = $archive.GetEntry('word/document.xml')
            if (-not $entry) { throw 'Meeting-note DOCX has no Word document body.' }
            $reader = [System.IO.StreamReader]::new($entry.Open())
            try { $xml = $reader.ReadToEnd() }
            finally { $reader.Dispose() }
            $entry.Delete()
            $updatedEntry = $archive.CreateEntry('word/document.xml', [System.IO.Compression.CompressionLevel]::Optimal)
            $writer = [System.IO.StreamWriter]::new($updatedEntry.Open(), [System.Text.UTF8Encoding]::new($false))
            try { $writer.Write($xml.Replace($OldTimestamp, $NewTimestamp)) }
            finally { $writer.Dispose() }
        }
        finally { $archive.Dispose() }
        return ,$stream.ToArray()
    }
    finally { $stream.Dispose() }
}

function Normalize-MailBody {
    param([string]$Content)
    $plain = [System.Net.WebUtility]::HtmlDecode(($Content -replace '<[^>]+>', ''))
    return ($plain -replace '\s+', ' ').Trim()
}

function Test-SeedMailMatch {
    param([object]$Message, [object]$Record)
    $headers = @($Message.internetMessageHeaders)
    $dataset = @($headers | Where-Object { $_.name -ieq 'X-WorkOverview-Seed' -and $_.value -eq 'atlas-developer-platform-v1' }).Count -gt 0
    $ids = @($headers | Where-Object { $_.name -ieq 'X-WorkOverview-Seed-Id' })
    return $dataset -and ((@($ids | Where-Object { $_.value -eq $Record.id }).Count -gt 0) -or ($ids.Count -eq 0 -and $Message.subject -eq $Record.title))
}

function Test-SeedEventMatch {
    param([object]$Existing, [object]$Record)
    if ($Existing.transactionId -and $Existing.transactionId -eq $Record.id) { return $true }
    if (@($Existing.categories) -notcontains 'AtlasSeed:atlas-developer-platform-v1' -or $Existing.subject -ne $Record.title -or -not $Existing.start.dateTime) { return $false }
    $utcStyle = [Globalization.DateTimeStyles]::AssumeUniversal
    return [DateTimeOffset]::Parse($Existing.start.dateTime, [Globalization.CultureInfo]::InvariantCulture, $utcStyle).ToUniversalTime() -eq
        [DateTimeOffset]::Parse($Record.startsAt, [Globalization.CultureInfo]::InvariantCulture, $utcStyle).ToUniversalTime()
}

function Find-ExistingSeedMessages {
    param([string]$MessagesUri, [object[]]$Records, [hashtable]$GraphUsers, [object[]]$ExistingMessages)
    $query = if ($MessagesUri -match '/chats/[^/]+/messages$') { '?$top=50&$orderby=createdDateTime%20desc' } else { '?$top=50' }
    $existing = if ($PSBoundParameters.ContainsKey('ExistingMessages')) { @($ExistingMessages) } else { @(Get-AllGraphItems -Uri "${MessagesUri}${query}") }
    foreach ($record in $Records) {
        $authorId = [string]$GraphUsers[$record.senderOrOrganizer].id
        $matches = @($existing | Where-Object {
            -not $_.deletedDateTime -and $_.messageType -eq 'message' -and
            [string]$_.from.user.id -eq $authorId -and
            (Normalize-MailBody $_.body.content) -ceq (Normalize-MailBody $record.content)
        })
        if ($matches.Count -gt 1) { Write-Warning "Multiple Teams messages match $($record.id); skipping this dataset item." }
        if ($matches.Count -ge 1) {
            $script:seedState.teamsMessages[$record.id] = @{
                hash = Get-RecordHash $record
                graphId = $matches[0].id
                timestamp = $matches[0].createdDateTime
            }
        }
    }
}

function Confirm-SeedChatMessages {
    param([string]$MessagesUri, [object[]]$Records, [hashtable]$GraphUsers, [string]$AccessToken = $script:graphToken)
    for ($attempt = 1; $attempt -le 6; $attempt++) {
        $messages = @(Get-AllGraphItems -Uri "${MessagesUri}?`$top=50&`$orderby=createdDateTime%20desc" -AccessToken $AccessToken)
        $missing = @($Records | Where-Object {
            $record = $_
            $authorId = [string]$GraphUsers[$record.senderOrOrganizer].id
            @($messages | Where-Object {
                -not $_.deletedDateTime -and $_.messageType -eq 'message' -and
                [string]$_.from.user.id -eq $authorId -and
                (Normalize-MailBody $_.body.content) -ceq (Normalize-MailBody $record.content)
            }).Count -eq 0
        })
        if ($missing.Count -eq 0) { return }
        if ($attempt -lt 6) { Start-Sleep -Seconds 2 }
    }
    throw "Graph did not return expected messages for $MessagesUri after import: $(@($missing | ForEach-Object { $_.id }) -join ', ')."
}

function Resolve-DatasetTimestamp {
    param([string]$Value)
    if ($Value -match '^WORKDAY-(\d+)T(\d{2}:\d{2}:\d{2})Z$') {
        $workdayOffset = [int]$Matches[1]
        $clockTime = $Matches[2]
        $date = $script:lastCompletedWorkday
        for ($index = 0; $index -lt $workdayOffset; $index++) {
            do { $date = $date.AddDays(-1) }
            while ($date.DayOfWeek -eq [DayOfWeek]::Saturday -or $date.DayOfWeek -eq [DayOfWeek]::Sunday)
        }
        return "$($date.ToString('yyyy-MM-dd'))T${clockTime}Z"
    }
    throw "Dataset timestamp must use a relative WORKDAY-<offset>T<time>Z token, received '$Value'."
}

function Get-SeedState {
    param([string]$TenantId)
    $state = @{
        tenantId = $TenantId; dataset = 'atlas-developer-platform-v1'; teamId = $null
        teamProvisioning = $null
        channelIds = @{}; conversationIds = @{}; teamsMessages = @{}; emails = @{}; events = @{}
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
        # Graph says to wait more than 30 seconds between operation status checks.
        # The first check also needs to wait because the operation can lag behind the 202 response.
        Start-Sleep -Seconds $pollIntervalSeconds
        try { $operation = (Invoke-Graph -Method GET -Uri $OperationUri).Body }
        catch {
            if ($_ -notmatch 'HTTP 404') { throw }
            $lastStatus = 'operation not visible yet (HTTP 404)'
            Write-TaskProgress -Activity 'Provisioning Microsoft Team' -Current $attempt -Total $maxAttempts -Status "$lastStatus; elapsed $([int]$stopwatch.Elapsed.TotalSeconds)s"
            try {
                $availableChannels = @(Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/teams/$TeamId/channels?`$select=id,displayName,description")
                if ($availableChannels.Count -gt 0) {
                    Write-Verbose "Team $TeamId is ready; its operation record is not available, but its channels are."
                    Write-Progress -Id 1 -Activity 'Provisioning Microsoft Team' -Completed
                    $script:seedState.teamProvisioning = $null
                    return
                }
            }
            catch {
                if ($_ -notmatch 'HTTP 404') { throw }
            }
            continue
        }
        $lastStatus = $operation.status
        Write-TaskProgress -Activity 'Provisioning Microsoft Team' -Current $attempt -Total $maxAttempts -Status "$lastStatus; elapsed $([int]$stopwatch.Elapsed.TotalSeconds)s"
        if ($operation.status -eq 'succeeded') {
            Write-Progress -Id 1 -Activity 'Provisioning Microsoft Team' -Completed
            $script:seedState.teamProvisioning = $null
            return
        }
        if ($operation.status -eq 'failed') {
            Write-Progress -Id 1 -Activity 'Provisioning Microsoft Team' -Completed
            throw "Team provisioning failed: $($operation.error.message)"
        }
    }
    throw "Timed out waiting for Microsoft Teams after $([int]$stopwatch.Elapsed.TotalMinutes) minutes. Team ID: $TeamId; operation ID: $OperationId; last status: $lastStatus. The Team may still be provisioning. Confirm the Team's status before running the seeder again."
}

function Get-TeamChannelsWhenAvailable {
    param([string]$TeamId)
    $maxAttempts = 26
    $pollIntervalSeconds = 35
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $lastStatus = 'unknown'
    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        try {
            $channels = @(Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/teams/$TeamId/channels?`$select=id,displayName,description")
            if ($channels.Count -gt 0) { return [pscustomobject]@{ Channels = $channels } }
            $lastStatus = 'no channels visible yet'
        }
        catch {
            if ($_ -notmatch 'HTTP 404') { throw }
            $lastStatus = 'Team channels not visible yet (HTTP 404)'
        }
        Write-TaskProgress -Activity 'Checking Microsoft Team readiness' -Current $attempt -Total $maxAttempts -Status "$lastStatus; elapsed $([int]$stopwatch.Elapsed.TotalSeconds)s"
        if ($attempt -lt $maxAttempts) { Start-Sleep -Seconds $pollIntervalSeconds }
    }
    throw "Team $TeamId is still unavailable after $([int]$stopwatch.Elapsed.TotalMinutes) minutes. Confirm the Team's status before running the seeder again. Last status: $lastStatus."
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
if (-not $ConfirmSandboxTenant) { throw 'Pass -ConfirmSandboxTenant to confirm seeding the configured sandbox tenant.' }

$script:seedState = Get-SeedState -TenantId $tenantId

$script:lastCompletedWorkday = [DateTime]::UtcNow.Date.AddDays(-1)
while ($script:lastCompletedWorkday.DayOfWeek -eq [DayOfWeek]::Saturday -or $script:lastCompletedWorkday.DayOfWeek -eq [DayOfWeek]::Sunday) {
    $script:lastCompletedWorkday = $script:lastCompletedWorkday.AddDays(-1)
}
$users = Import-Csv -LiteralPath (Join-Path $dataRoot 'users.csv')
$emails = @(Import-Csv -LiteralPath (Join-Path $dataRoot 'email.csv'))
$meetingNoteDirectory = Join-Path $dataRoot 'meeting-notes'
$meetingNotes = @(Get-ChildItem -LiteralPath $meetingNoteDirectory -Filter '*.docx' -File | Sort-Object Name)
if ($meetingNotes.Count -eq 0) { throw "No meeting-note DOCX files found in $meetingNoteDirectory." }
$events = @(Import-Csv -LiteralPath (Join-Path $dataRoot 'calendar.csv'))
$messages = @(Import-Csv -LiteralPath (Join-Path $dataRoot 'teams.csv'))
$conversations = Import-Csv -LiteralPath (Join-Path $dataRoot 'teams-conversations.csv')
$timelineRecords = @($emails) + @($events) + @($messages)
$meetingNoteMetadata = @(
    foreach ($noteFile in $meetingNotes) {
        Read-MeetingNoteDocument -RelativePath "meeting-notes/$($noteFile.Name)"
    }
)
$script:meetingNoteTimestampMap = @{}
$timestampFields = @('occurredAt', 'startsAt', 'endsAt')
foreach ($record in $timelineRecords) {
    foreach ($field in $timestampFields) {
        if ($record.$field) { $record.$field = Resolve-DatasetTimestamp -Value $record.$field }
    }
}
foreach ($note in $meetingNoteMetadata) { $script:meetingNoteTimestampMap[$note.dateToken] = $note.occurredAt }
$meetingNoteEmails = foreach ($noteFile in $meetingNotes) {
    $attachmentPath = "meeting-notes/$($noteFile.Name)"
    $noteId = Get-MeetingNoteIdFromFileName -FileName $noteFile.Name
    $note = $meetingNoteMetadata | Where-Object { $_.id -eq $noteId } | Select-Object -First 1
    [pscustomobject]@{
        id = "meeting-note-email-$($note.id)"
        occurredAt = $note.occurredAt
        title = "Minutes of Meeting - $($note.meetingTitle)"
        content = "Hi all,`r`n`r`nAttached are the minutes of our meeting, `"$($note.meetingTitle)`". Please review the decisions and actions relevant to you and let the group know if anything needs correction.`r`n`r`nThanks,`r`n$($note.organizer)"
        senderOrOrganizer = $note.organizer
        participants = $note.participantNames -join '|'
        folder = 'Inbox'
        importance = 'normal'
        topicId = $note.topicId
        attachments = $attachmentPath
    }
}
$emails = @($emails) + @($meetingNoteEmails)
# Format the source messages before hashing so changes are reconciled on reruns.
# These are fictional sandbox contact numbers, not employees' real mobile numbers.
$team = Get-Content -LiteralPath (Join-Path $dataRoot 'team.json') -Raw | ConvertFrom-Json
$mailProfiles = @{}
for ($profileIndex = 0; $profileIndex -lt $users.Count; $profileIndex++) {
    $profile = $users[$profileIndex]
    $mailProfiles[$profile.displayName] = @{
        address = "$($profile.displayName) <$($profile.userPrincipalName)>"
        signature = "$($profile.displayName)`r`n$($profile.role)`r`nMobile +44 7700 900$('{0:D3}' -f $profileIndex)`r`n$($team.companyName)"
    }
}
$formattedEmails = @{}
foreach ($mail in $emails) {
    $mail.content = "$($mail.content)`r`n`r`nKind regards,`r`n$($mailProfiles[$mail.senderOrOrganizer].signature)"
    if ($mail.replyToId) {
        $parent = $formattedEmails[$mail.replyToId]
        if (-not $parent) { throw "Email $($mail.id) must follow its parent $($mail.replyToId) in the dataset." }
        $parentTo = @($parent.participants -split '\|' | Where-Object { $_ -ne $parent.senderOrOrganizer } | ForEach-Object { $mailProfiles[$_].address }) -join '; '
        $parentSent = [DateTimeOffset]::Parse($parent.occurredAt).ToString('dddd, dd MMMM yyyy HH:mm zzz', [Globalization.CultureInfo]::InvariantCulture)
        $mail.content += "`r`n`r`n-----Original Message-----`r`nFrom: $($mailProfiles[$parent.senderOrOrganizer].address)`r`nSent: $parentSent`r`nTo: $parentTo`r`nCc: (none)`r`nSubject: $($parent.title)`r`n`r`n$($parent.content)"
    }
    $formattedEmails[$mail.id] = $mail
}
$invalidChannelMessages = @($messages | Where-Object {
    ($_.team -or $_.channel) -and
    ($_.team -ne $team.displayName -or $_.channel -notin $team.channels.displayName)
})
if ($invalidChannelMessages.Count -gt 0) {
    $references = @($invalidChannelMessages | ForEach-Object { "$($_.team)/$($_.channel)" } | Sort-Object -Unique)
    throw "Dataset refers to teams/channels not defined in team.json: $($references -join ', '). No Graph changes were made."
}
$userByName = @{}
foreach ($user in $users) { $userByName[$user.displayName] = $user }

Write-Output "Seed: $($users.Count) users, $($conversations.Count) chats, $($messages.Count) Teams messages, $($emails.Count) emails, $($events.Count) events"
if (-not $clientId -or -not $clientSecret) { throw 'Set WORK_OVERVIEW_ENTRA_CLIENT_ID and WORK_OVERVIEW_ENTRA_CLIENT_SECRET in server/.env or the process environment.' }

$script:graphToken = Get-GraphToken -TenantId $tenantId -ClientId $clientId -ClientSecret $clientSecret
$graphUsers = @{}
$userIndex = 0
$userResults = @(Invoke-GraphParallel -Items @($users) -GraphToken $script:graphToken -MaxConcurrency $MaxConcurrency -Functions @('Invoke-Graph') -Worker {
    param($user)
    $graphUser = (Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/users/$([uri]::EscapeDataString($user.userPrincipalName))?`$select=id,displayName,userPrincipalName").Body
    return [pscustomobject]@{ Name = $user.displayName; User = $graphUser }
})
foreach ($result in $userResults) {
    $userIndex++
    $graphUsers[$result.Name] = $result.User
    Write-TaskProgress -Activity 'Resolving dataset users' -Current $userIndex -Total $users.Count -Status $result.Name
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
    $savedGroupId = [uri]::EscapeDataString([string]$teamId)
    try { Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/groups/${savedGroupId}?`$select=id" | Out-Null }
    catch {
        if ($_ -notmatch 'HTTP 404') { throw }
        Write-Output 'Saved Team ID is no longer active in Graph; resolving the dataset Team by name.'
        $teamId = $null
        $script:seedState.teamId = $null
        $script:seedState.teamProvisioning = $null
        $script:seedState.channelIds = @{}
        $script:seedState.teamsMessages = @{}
        $script:seedState.conversationIds = @{}
    }
}
if (-not $teamId) {
    $matchingTeams = @(Get-AllGraphItems -Uri 'https://graph.microsoft.com/v1.0/teams?$select=id,displayName&$top=100' | Where-Object { $_.displayName -ceq $team.displayName })
    if ($matchingTeams.Count -gt 1) {
        $matchingIds = @($matchingTeams | ForEach-Object { $_.id }) -join ', '
        throw "Found multiple Microsoft Teams named '$($team.displayName)' ($matchingIds). Refusing to create another Team; resolve the duplicate Teams first."
    }
    if ($matchingTeams.Count -eq 1) {
        $teamId = [string]$matchingTeams[0].id
        $script:seedState.teamId = $teamId
        Write-Output "Reusing existing Microsoft Team '$($team.displayName)' found in Graph."
    }
    elseif ($matchingTeams.Count -eq 0) {
        Write-Output 'No active dataset Team found; creating a new Team.'
    }
}
if ($teamId) {
    $pendingProvisioning = $script:seedState.teamProvisioning
    if ($pendingProvisioning) {
        if ($pendingProvisioning.teamId -ne $teamId) {
            throw "In-memory Team provisioning data refers to $($pendingProvisioning.teamId), but the current Team ID is $teamId. Resolve the mismatch before continuing."
        }
        try { $existingChannels = @(Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels?`$select=id,displayName,description") }
        catch {
            if ($_ -notmatch 'HTTP 404') { throw }
            $existingChannels = @()
        }
        if ($existingChannels.Count -eq 0) {
            Write-Verbose "Resuming Team provisioning operation $($pendingProvisioning.operationId) for Team $teamId."
            Wait-TeamProvisioning -TeamId $teamId -OperationId $pendingProvisioning.operationId -OperationUri $pendingProvisioning.operationUri
            $channelResult = Get-TeamChannelsWhenAvailable -TeamId $teamId
            $existingChannels = @($channelResult.Channels)
        }
        else {
            Write-Verbose "Team $teamId is already provisioned; clearing its saved pending operation."
            $script:seedState.teamProvisioning = $null
        }
    }
    else {
        $channelResult = Get-TeamChannelsWhenAvailable -TeamId $teamId
        $existingChannels = @($channelResult.Channels)
    }
}
if (-not $teamId) {
    $teamResponse = Invoke-Graph -Method POST -Uri 'https://graph.microsoft.com/v1.0/teams' -Body $teamBody
    $contentLocation = $teamResponse.Headers['Content-Location']
    $operationLocation = $teamResponse.Headers['Location']
    if ($contentLocation -notmatch "/teams\('([^']+)'\)") { throw 'Could not read the team ID from the Graph Content-Location response header.' }
    $teamId = $Matches[1]
    $script:seedState.teamId = $teamId
    if ($operationLocation -notmatch "/teams\('[^']+'\)/operations\('([^']+)'\)") { throw 'Could not read the provisioning operation ID from the Graph Location response header.' }
    $operationId = $Matches[1]
    $operationUri = if ($operationLocation -match '^https?://') { $operationLocation } else { "https://graph.microsoft.com/v1.0$operationLocation" }
    $script:seedState.teamProvisioning = @{ teamId = $teamId; operationId = $operationId; operationUri = $operationUri }
    Write-Verbose "Team creation accepted. Team ID: $teamId; operation ID: $operationId"
    Wait-TeamProvisioning -TeamId $teamId -OperationId $operationId -OperationUri $operationUri
    $channelResult = Get-TeamChannelsWhenAvailable -TeamId $teamId
    $existingChannels = @($channelResult.Channels)
}
else { Write-Verbose "Reusing existing project Team: $teamId" }

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
    $existingChannelName = [string]$existingChannel.displayName
    if ([string]::IsNullOrWhiteSpace($existingChannelName)) {
        throw "Microsoft Graph returned a Team channel without a display name (ID: $($existingChannel.id)); refusing to create duplicate seed channels."
    }
    $channelIds[$existingChannelName] = $existingChannel.id
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
        $channelStatus = 'Created channel'
    }
    $channelIndex++
    Write-TaskProgress -Activity 'Preparing Team channels' -Current $channelIndex -Total $team.channels.Count -Status "$channelStatus | $($channel.displayName)"
}

$conversationIds = @{}
$ambiguousConversations = @{}
$conversationIndex = 0
$existingChatsByUser = @{}
foreach ($conversation in $conversations) {
    $members = foreach ($name in ($conversation.participants -split '\|')) {
        $graphUser = $graphUsers[$name]
        if (-not $graphUser) { throw "Unknown conversation participant '$name'." }
        @{ '@odata.type' = '#microsoft.graph.aadUserConversationMember'; roles = @('owner'); 'user@odata.bind' = "https://graph.microsoft.com/v1.0/users('$($graphUser.id)')" }
    }
    $chatBody = @{ chatType = $conversation.chatType; members = @($members) }
    if ($conversation.chatType -eq 'group' -and $conversation.topic) { $chatBody.topic = $conversation.topic }
    $chatId = $script:seedState.conversationIds[$conversation.conversationId]
    if (-not $chatId) {
        $firstUser = $graphUsers[($conversation.participants -split '\|')[0]]
        if (-not $existingChatsByUser.ContainsKey($firstUser.id)) {
            $existingChatsByUser[$firstUser.id] = @(Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$($firstUser.id)/chats?`$expand=members&`$top=50")
        }
        $expectedMembers = @(($conversation.participants -split '\|') | ForEach-Object { [string]$graphUsers[$_].id } | Sort-Object)
        $matchingChats = @($existingChatsByUser[$firstUser.id] | Where-Object {
            if ($_.chatType -ne $conversation.chatType) { return $false }
            if ($conversation.chatType -eq 'group' -and $_.topic -cne $conversation.topic) { return $false }
            $actualMembers = @($_.members | ForEach-Object { [string]$_.userId } | Sort-Object)
            return ($actualMembers -join '|') -ceq ($expectedMembers -join '|')
        })
        if ($matchingChats.Count -gt 1) {
            Write-Warning "Multiple chats match $($conversation.conversationId); skipping its messages."
            $ambiguousConversations[$conversation.conversationId] = $true
            continue
        }
        if ($matchingChats.Count -eq 1) { $chatId = [string]$matchingChats[0].id }
    }
    if (-not $chatId) {
        foreach ($message in @($messages | Where-Object { $_.conversationId -eq $conversation.conversationId })) {
            $script:seedState.teamsMessages.Remove($message.id)
        }
        $createdChat = (Invoke-Graph -Method POST -Uri 'https://graph.microsoft.com/v1.0/chats' -Body $chatBody).Body
        $chatId = $createdChat.id
    }
    $script:seedState.conversationIds[$conversation.conversationId] = $chatId
    $conversationIds[$conversation.conversationId] = $chatId
    $conversationIndex++
    Write-TaskProgress -Activity 'Creating chats' -Current $conversationIndex -Total $conversations.Count -Status $conversation.topic
}

foreach ($chatId in @($conversationIds.Values | Sort-Object -Unique)) {
    Ensure-SeedChatActive -ChatId $chatId
}
$historyStart = Resolve-DatasetTimestamp -Value 'WORKDAY-9T00:00:00Z'
$channelMessages = @($messages | Where-Object { $_.team -and $_.channel })
$channelMessageTotal = $channelMessages.Count
$channelMessageIndex = 0
$channelGroups = $channelMessages | Group-Object channel
$messageInventoryUris = [System.Collections.Generic.List[string]]::new()
foreach ($group in $channelGroups) {
    if (-not $channelIds[$group.Name]) { throw "Dataset refers to unknown channel '$($group.Name)'." }
    $messageInventoryUris.Add("https://graph.microsoft.com/v1.0/teams/$teamId/channels/$($channelIds[$group.Name])/messages")
}
foreach ($chatId in @($conversationIds.Values | Sort-Object -Unique)) {
    $messageInventoryUris.Add("https://graph.microsoft.com/v1.0/chats/$chatId/messages")
}
$messageInventory = @{}
$inventories = @(Invoke-GraphParallel -Items @($messageInventoryUris) -GraphToken $script:graphToken -MaxConcurrency $MaxConcurrency -Functions @('Invoke-Graph', 'Get-AllGraphItems', 'Get-SeedMessageInventory') -Worker {
    param($uri)
    return [pscustomobject]@{ Uri = $uri; Items = @(Get-SeedMessageInventory -MessagesUri $uri) }
})
foreach ($inventory in $inventories) { $messageInventory[$inventory.Uri] = @($inventory.Items) }
$chatListings = @{}
$delegatedSessions = @{}
$checkedOneOnOneChats = @{}
foreach ($conversation in $conversations) {
    if ($conversation.chatType -ne 'oneOnOne' -or $ambiguousConversations.ContainsKey($conversation.conversationId)) { continue }
    $chatId = $conversationIds[$conversation.conversationId]
    if (-not $chatId -or $checkedOneOnOneChats.ContainsKey($chatId)) { continue }
    Ensure-SeedOneOnOneListed -ChatId $chatId -Participants @($conversation.participants -split '\|') -GraphUsers $graphUsers -Listings $chatListings -Sessions $delegatedSessions
    $checkedOneOnOneChats[$chatId] = $true
}
foreach ($group in $channelGroups) {
    $channelId = $channelIds[$group.Name]
    if (-not $channelId) { throw "Dataset refers to unknown channel '$($group.Name)'." }
    $messagesUri = "https://graph.microsoft.com/v1.0/teams/$teamId/channels/$channelId/messages"
    Find-ExistingSeedMessages -MessagesUri $messagesUri -Records @($group.Group) -GraphUsers $graphUsers -ExistingMessages @($messageInventory[$messagesUri])
    $needsMigration = @($group.Group | Where-Object { $saved = $script:seedState.teamsMessages[$_.id]; -not $saved -or $saved.purged -or $saved.hash -ne (Get-RecordHash $_) }).Count -gt 0
    if (-not $needsMigration) {
        $channelUri = "https://graph.microsoft.com/v1.0/teams/$teamId/channels/$channelId"
        $channelState = (Invoke-Graph -Method GET -Uri "$channelUri`?`$select=id,migrationMode").Body
        if ($channelState.migrationMode -eq 'inProgress') { Complete-SeedMigration -ThreadUri $channelUri }
        foreach ($item in $group.Group) { $channelMessageIndex++; Write-TaskProgress -Activity 'Importing channel messages' -Current $channelMessageIndex -Total $channelMessageTotal -Status "Unchanged | $($item.id)" }
        continue
    }
    $channelUri = "https://graph.microsoft.com/v1.0/teams/$teamId/channels/$channelId"
    $channelState = (Invoke-Graph -Method GET -Uri "$channelUri`?`$select=id,migrationMode").Body
    if ($channelState.migrationMode -ne 'inProgress') {
        $channelHistoryStart = Get-MigrationStartTime -ThreadUri $channelUri -RequestedStart $historyStart
        Start-ChannelMigration -ChannelUri $channelUri -CreationDateTime $channelHistoryStart -PreviousMode ([string]$channelState.migrationMode)
    }
    foreach ($item in ($group.Group | Sort-Object occurredAt)) {
        $itemHash = Get-RecordHash $item
        $saved = $script:seedState.teamsMessages[$item.id]
        if ($saved -and -not $saved.purged -and $saved.hash -eq $itemHash) {
            $channelMessageIndex++
            Write-TaskProgress -Activity 'Importing channel messages' -Current $channelMessageIndex -Total $channelMessageTotal -Status "Unchanged | $($item.id)"
            continue
        }
        $author = $graphUsers[$item.senderOrOrganizer]
        $messageTime = Resolve-SeedMessageTime -DatasetTimestamp $item.occurredAt -Saved $saved
        if ($saved) { Write-Verbose "Dataset row $($item.id) changed; Teams migration messages are append-only, importing an updated version." }
        $body = @{ from = @{ user = @{ id = $author.id; displayName = $author.displayName } }; body = @{ contentType = 'html'; content = [System.Net.WebUtility]::HtmlEncode($item.content) } }
        $importResult = Import-TeamsMigrationMessage -Uri "https://graph.microsoft.com/v1.0/teams/$teamId/channels/$channelId/messages" -Body $body -MessageTime $messageTime -DatasetRowId $item.id
        $messageId = $importResult.Message.id
        $messageTime = $importResult.Timestamp
        $script:seedState.teamsMessages[$item.id] = @{ hash = $itemHash; graphId = $messageId; timestamp = $messageTime.ToString('yyyy-MM-ddTHH:mm:ss.fffZ') }
        $channelMessageIndex++
        Write-TaskProgress -Activity 'Importing channel messages' -Current $channelMessageIndex -Total $channelMessageTotal -Status $group.Name
    }
    Complete-SeedMigration -ThreadUri $channelUri
}

$chatMessages = @($messages | Where-Object { $_.conversationId })
$chatGroups = $chatMessages | Group-Object conversationId
$chatWork = @{}
foreach ($group in $chatGroups) {
    if ($ambiguousConversations.ContainsKey($group.Name)) { continue }
    $chatId = $conversationIds[$group.Name]
    if (-not $chatId) { throw "Dataset refers to unknown conversation '$($group.Name)'." }
    $chatConversation = $conversations | Where-Object { $_.conversationId -eq $group.Name } | Select-Object -First 1
    if (-not $chatConversation) { throw "Dataset refers to unknown conversation '$($group.Name)'." }
    if (-not $chatWork.ContainsKey($chatId)) {
        $messagesUri = "https://graph.microsoft.com/v1.0/chats/$chatId/messages"
        $chatWork[$chatId] = @{ ChatId = $chatId; ChatType = $chatConversation.chatType; Records = [System.Collections.Generic.List[object]]::new(); Participants = [System.Collections.Generic.List[string]]::new(); Existing = @($messageInventory[$messagesUri]); Users = $graphUsers; HistoryStart = $historyStart; DataRoot = $dataRoot }
    }
    foreach ($item in $group.Group) { $chatWork[$chatId].Records.Add($item) }
    foreach ($name in @($conversations | Where-Object { $_.conversationId -eq $group.Name } | ForEach-Object { $_.participants -split '\|' })) {
        if ($name -notin $chatWork[$chatId].Participants) { $chatWork[$chatId].Participants.Add($name) }
    }
}
$chatResults = @(Invoke-GraphParallel -Items @($chatWork.Values) -GraphToken $script:graphToken -MaxConcurrency $MaxConcurrency -Functions @('Invoke-Graph', 'Get-AllGraphItems', 'Get-MigrationStartTime', 'Start-ChatMigration', 'Complete-SeedMigration', 'Ensure-ChatFullHistory', 'Get-RecordHash', 'Resolve-SeedMessageTime', 'Import-TeamsMigrationMessage', 'Normalize-MailBody', 'Find-ExistingSeedMessages', 'Confirm-SeedChatMessages', 'Resolve-DatasetAttachment') -Worker {
    param($work)
    $script:graphUsers = $work.Users
    $script:dataRoot = $work.DataRoot
    $script:seedState = @{ teamsMessages = @{} }
    $chatId = $work.ChatId
    $uri = "https://graph.microsoft.com/v1.0/chats/$chatId/messages"
    $records = @($work.Records)
    Find-ExistingSeedMessages -MessagesUri $uri -Records $records -GraphUsers $work.Users -ExistingMessages @($work.Existing)
    $missing = @($records | Where-Object { -not $script:seedState.teamsMessages[$_.id] })
    if ($missing.Count -gt 0) {
        Start-ChatMigration -ChatId $chatId -CreationDateTime $work.HistoryStart
        foreach ($item in ($missing | Sort-Object occurredAt)) {
            $author = $work.Users[$item.senderOrOrganizer]
            $body = @{ from = @{ user = @{ id = $author.id; displayName = $author.displayName } }; body = @{ contentType = 'html'; content = [System.Net.WebUtility]::HtmlEncode($item.content) } }
            $messageTime = Resolve-SeedMessageTime -DatasetTimestamp $item.occurredAt -Saved $null
            Import-TeamsMigrationMessage -Uri $uri -Body $body -MessageTime $messageTime -DatasetRowId $item.id | Out-Null
        }
        Complete-SeedMigration -ThreadUri "https://graph.microsoft.com/v1.0/chats/$chatId"
    }
    else {
        $chatUri = "https://graph.microsoft.com/v1.0/chats/$chatId"
        $chatState = (Invoke-Graph -Method GET -Uri "$chatUri`?`$select=id,migrationMode").Body
        if ($chatState.migrationMode -eq 'inProgress') { Complete-SeedMigration -ThreadUri $chatUri }
    }
    if ($work.Participants.Count -gt 0) {
        $earliest = [DateTimeOffset]::Parse(($records | Sort-Object occurredAt | Select-Object -First 1).occurredAt)
        Ensure-ChatFullHistory -ChatId $chatId -Participants @($work.Participants) -EarliestMessage $earliest -ChatType $work.ChatType
    }
    Confirm-SeedChatMessages -MessagesUri $uri -Records $records -GraphUsers $work.Users
    return $records.Count
})
Write-Output "Chat messages: processed $((($chatResults | Measure-Object -Sum).Sum)) items across $($chatWork.Count) chats."
$chatListings = @{}
$checkedOneOnOneChats = @{}
$visibilityVerificationWork = [System.Collections.Generic.List[object]]::new()
foreach ($conversation in $conversations) {
    if ($conversation.chatType -ne 'oneOnOne' -or $ambiguousConversations.ContainsKey($conversation.conversationId)) { continue }
    $chatId = $conversationIds[$conversation.conversationId]
    if (-not $chatId -or $checkedOneOnOneChats.ContainsKey($chatId)) { continue }
    Ensure-SeedOneOnOneListed -ChatId $chatId -Participants @($conversation.participants -split '\|') -GraphUsers $graphUsers -Listings $chatListings -Sessions $delegatedSessions
    $checkedOneOnOneChats[$chatId] = $true
    if ($chatWork.ContainsKey($chatId)) {
        $participantSessions = @(
            foreach ($name in ($conversation.participants -split '\|')) {
                $user = $graphUsers[$name]
                $userId = [string]$user.id
                if (-not $delegatedSessions.ContainsKey($userId) -or $delegatedSessions[$userId].ExpiresAt -lt (Get-Date).AddMinutes(5)) {
                    $delegatedSessions[$userId] = Get-SeedDelegatedSession -User $user
                }
                @{ Name = $name; AccessToken = $delegatedSessions[$userId].AccessToken }
            }
        )
        $visibilityVerificationWork.Add(@{ ChatId = $chatId; Records = @($chatWork[$chatId].Records); Users = $graphUsers; Sessions = $participantSessions })
    }
}
$verifiedChats = @(Invoke-GraphParallel -Items @($visibilityVerificationWork) -GraphToken $script:graphToken -MaxConcurrency $MaxConcurrency -Functions @('Invoke-Graph', 'Get-AllGraphItems', 'Normalize-MailBody', 'Confirm-SeedChatMessages') -Worker {
    param($work)
    $uri = "https://graph.microsoft.com/v1.0/me/chats/$($work.ChatId)/messages"
    foreach ($session in $work.Sessions) {
        try { Confirm-SeedChatMessages -MessagesUri $uri -Records $work.Records -GraphUsers $work.Users -AccessToken $session.AccessToken }
        catch { throw "Could not verify seeded chat messages as $($session.Name). $_" }
    }
    return 1
})
Write-Output "Chat visibility: verified messages for both participants in $($verifiedChats.Count) one-to-one chats."

$mailIndex = 0
$sentItemsByUser = @{}
$emailById = @{}
foreach ($mail in $emails) { $emailById[$mail.id] = $mail }
foreach ($mail in $emails) {
    if ($mail.replyToId -and -not $script:seedState.emails[$mail.replyToId].internetMessageId) {
        Write-Warning "Skipping email $($mail.id): its parent $($mail.replyToId) is unavailable."
        continue
    }
    $itemHash = Get-RecordHash $mail
    $saved = $script:seedState.emails[$mail.id]
    $sender = $userByName[$mail.senderOrOrganizer]
    if (-not $saved) {
        $senderUpn = $sender.userPrincipalName
        if (-not $sentItemsByUser.ContainsKey($senderUpn)) {
            $sentItemsByUser[$senderUpn] = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$senderUpn/messages?`$select=id,isDraft,subject,body,toRecipients,internetMessageId,internetMessageHeaders&`$top=100"
        }
        $matchingSent = @($sentItemsByUser[$senderUpn] | Where-Object {
            -not $_.isDraft -and (Test-SeedMailMatch -Message $_ -Record $mail)
        })
        if ($matchingSent.Count -gt 1) { Write-Warning "Multiple sent messages match $($mail.id); skipping this item." }
        $legacy = $matchingSent | Select-Object -First 1
        if ($legacy) {
            $savedHash = $itemHash
            $legacyRecipients = @($sender.userPrincipalName) + @($legacy.toRecipients | ForEach-Object { $_.emailAddress.address }) | Sort-Object -Unique
            $saved = @{ graphId = $legacy.id; internetMessageId = $legacy.internetMessageId; hash = $savedHash; status = 'sent'; participants = $legacyRecipients; adopted = $true }
            $script:seedState.emails[$mail.id] = $saved
            Write-Verbose "Adopted previously seeded email $($mail.id); no duplicate will be sent."
        }
        if (-not $saved) {
            $draftCacheKey = "drafts:$senderUpn"
            if (-not $sentItemsByUser.ContainsKey($draftCacheKey)) {
                $sentItemsByUser[$draftCacheKey] = @(Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$senderUpn/mailFolders/drafts/messages?`$select=id,isDraft,internetMessageHeaders&`$top=100")
            }
            $matchingDrafts = @($sentItemsByUser[$draftCacheKey] | Where-Object {
                $datasetHeader = $_.internetMessageHeaders | Where-Object { $_.name -ieq 'X-WorkOverview-Seed' -and $_.value -eq 'atlas-developer-platform-v1' }
                $idHeader = $_.internetMessageHeaders | Where-Object { $_.name -ieq 'X-WorkOverview-Seed-Id' -and $_.value -eq $mail.id }
                $datasetHeader -and $idHeader
            })
            if ($matchingDrafts.Count -gt 1) {
                Write-Warning "Multiple drafts match dataset email $($mail.id); skipping this item."
                continue
            }
            if ($matchingDrafts.Count -eq 1) {
                $saved = @{ graphId = $matchingDrafts[0].id; hash = 'unverified'; status = 'draft' }
                $script:seedState.emails[$mail.id] = $saved
            }
        }
    }
    if ($saved -and $saved.hash -eq $itemHash -and $saved.status -eq 'sent') {
        try {
            $existingSentMessage = (Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages/$($saved.graphId)?`$select=id,isDraft,subject").Body
            $mailIndex++
            Write-TaskProgress -Activity 'Sending emails' -Current $mailIndex -Total $emails.Count -Status "Already present | $($mail.id)"
            continue
        }
        catch {
            if ($_ -notmatch 'HTTP 404') { throw }
            $script:seedState.emails[$mail.id].status = 'replacing'
            $saved.status = 'replacing'
            Write-Verbose "Seeded sender copy for $($mail.id) is missing; reconciling its recipients and recreating it."
        }
    }
    $recipients = foreach ($name in ($mail.participants -split '\|')) {
        if ($name -ne $mail.senderOrOrganizer) { @{ emailAddress = @{ address = $userByName[$name].userPrincipalName } } }
    }
            $mailboxUpns = if ($saved.participants) { @($saved.participants) } else { @($sender.userPrincipalName) + @($mail.participants -split '\|' | ForEach-Object { $userByName[$_].userPrincipalName }) | Sort-Object -Unique }
    if ($saved -and $saved.status -eq 'draft' -and $saved.graphId -and -not $mail.attachments) {
        $draftUri = "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages/$($saved.graphId)"
        $existingDraft = (Invoke-Graph -Method GET -Uri "$($draftUri)?`$select=id,isDraft").Body
        if (-not $existingDraft.isDraft) {
            $script:seedState.emails[$mail.id].status = 'sent'
            if ($saved.hash -ne $itemHash) { throw "Email $($mail.id) was sent before an interrupted run and its dataset row has since changed. Run Microsoft Graph - Seed Data again to reconcile the changed item." }
            $mailIndex++
            Write-TaskProgress -Activity 'Sending emails' -Current $mailIndex -Total $emails.Count -Status "Recovered sent item | $($mail.id)"
            continue
        }
        $draftUpdate = @{ body = @{ contentType = 'Text'; content = $mail.content } }
        if (-not $mail.replyToId) {
            $draftUpdate.subject = $mail.title
            $draftUpdate.toRecipients = @($recipients)
        }
        Invoke-Graph -Method PATCH -Uri $draftUri -Body $draftUpdate | Out-Null
        $script:seedState.emails[$mail.id].hash = $itemHash
        Invoke-Graph -Method POST -Uri "$draftUri/send" | Out-Null
        $sentMessage = Wait-SentMessage -Uri $draftUri
        $script:seedState.emails[$mail.id].internetMessageId = $sentMessage.internetMessageId
        $script:seedState.emails[$mail.id].status = 'sent'
    }
    else {
        if ($saved -and $saved.status -eq 'draft' -and $saved.graphId -and $mail.attachments) {
            $draftUri = "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages/$($saved.graphId)"
            $existingDraft = $null
            try { $existingDraft = (Invoke-Graph -Method GET -Uri "$($draftUri)?`$select=id,isDraft").Body }
            catch { if ($_ -notmatch 'HTTP 404') { throw } }
            if ($existingDraft -and -not $existingDraft.isDraft) {
                $script:seedState.emails[$mail.id].status = 'sent'
                if ($saved.hash -ne $itemHash) { throw "Email $($mail.id) was sent before an interrupted run and its dataset row has since changed. Run Microsoft Graph - Seed Data again to reconcile the changed item." }
                $mailIndex++
                Write-TaskProgress -Activity 'Sending emails' -Current $mailIndex -Total $emails.Count -Status "Recovered sent item | $($mail.id)"
                continue
            }
            if ($existingDraft) {
                try { Invoke-Graph -Method DELETE -Uri $draftUri | Out-Null }
                catch { if ($_ -notmatch 'HTTP 404') { throw } }
            }
            $script:seedState.emails.Remove($mail.id)
            $saved = $null
        }
        if ($saved -and ($saved.status -eq 'sent' -or $saved.status -eq 'replacing')) {
            if (-not $saved.internetMessageId) {
                $sentCopy = (Invoke-Graph -Method GET -Uri "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages/$($saved.graphId)?`$select=id,internetMessageId").Body
                $saved.internetMessageId = $sentCopy.internetMessageId
            }
            if (-not $saved.internetMessageId) { throw "Cannot reconcile changed email $($mail.id): Graph did not return its internetMessageId." }
            $script:seedState.emails[$mail.id].status = 'replacing'
            $script:seedState.emails[$mail.id].internetMessageId = $saved.internetMessageId
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
            Write-Verbose "Removed the prior seeded copies for $($mail.id); recreating the corrected message."
        }
        if ($mail.replyToId) {
            $parentMail = $emailById[$mail.replyToId]
            $parentState = $script:seedState.emails[$mail.replyToId]
            if (-not $parentMail -or -not $parentState.internetMessageId) { throw "Reply email $($mail.id) refers to missing or unsent parent $($mail.replyToId)." }
            $parentParticipants = @($parentMail.participants -split '\|')
            if ($mail.senderOrOrganizer -eq $parentMail.senderOrOrganizer -or $mail.senderOrOrganizer -notin $parentParticipants) {
                throw "Reply email $($mail.id) must be sent by a recipient of parent email $($mail.replyToId)."
            }
            $replyRecipients = @($mail.participants -split '\|' | Where-Object { $_ -ne $mail.senderOrOrganizer } | ForEach-Object { $userByName[$_].userPrincipalName })
            $referenceIds = [System.Collections.Generic.List[string]]::new()
            $ancestorId = $mail.replyToId
            while ($ancestorId) {
                $ancestorMail = $emailById[$ancestorId]
                $ancestorState = $script:seedState.emails[$ancestorId]
                if (-not $ancestorMail -or -not $ancestorState.internetMessageId) {
                    throw "Reply email $($mail.id) refers to unsent ancestor $ancestorId."
                }
                $referenceIds.Insert(0, [string]$ancestorState.internetMessageId)
                $ancestorId = $ancestorMail.replyToId
            }
            $replySubject = if ($parentMail.title -match '^(?i)RE:\s*') { $parentMail.title } else { "RE: $($parentMail.title)" }
            $mimeReply = New-MimeReplyDraftContent `
                -FromAddress $sender.userPrincipalName `
                -ToAddresses $replyRecipients `
                -Subject $replySubject `
                -Content $mail.content `
                -ParentInternetMessageId $parentState.internetMessageId `
                -References @($referenceIds) `
                -DatasetRowId $mail.id
            $draftMessage = (Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages" -Body $mimeReply -BodyContentType 'text/plain').Body
        }
        else {
            $draftBody = @{ subject = $mail.title; body = @{ contentType = 'Text'; content = $mail.content }; toRecipients = @($recipients); internetMessageHeaders = @(@{ name = 'X-WorkOverview-Seed'; value = 'atlas-developer-platform-v1' }, @{ name = 'X-WorkOverview-Seed-Id'; value = $mail.id }) }
            $attachments = @(Get-DatasetEmailAttachments -Mail $mail)
            if ($attachments.Count -gt 0) { $draftBody.attachments = $attachments }
            $draftMessage = (Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages" -Body $draftBody).Body
        }
        $script:seedState.emails[$mail.id] = @{ hash = $itemHash; graphId = $draftMessage.id; status = 'draft'; participants = $mailboxUpns; threadId = $mail.threadId; replyToId = $mail.replyToId }
        Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages/$($draftMessage.id)/send" | Out-Null
        $sentMessage = Wait-SentMessage -Uri "https://graph.microsoft.com/v1.0/users/$($sender.userPrincipalName)/messages/$($draftMessage.id)"
        $script:seedState.emails[$mail.id].internetMessageId = $sentMessage.internetMessageId
        $script:seedState.emails[$mail.id].status = 'sent'
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
    $organizerUpn = $organizer.userPrincipalName
    if (-not $calendarItemsByUser.ContainsKey($organizerUpn)) {
        $calendarItemsByUser[$organizerUpn] = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$organizerUpn/calendar/events?`$select=id,subject,start,transactionId,categories&`$top=100"
    }
    # Cleanup can leave saved IDs referring to deleted or non-calendar items.
    # Only reuse IDs returned by this organizer's live calendar collection.
    if ($saved -and $saved.graphId -notin @($calendarItemsByUser[$organizerUpn] | ForEach-Object { $_.id })) {
        $script:seedState.events.Remove($event.id)
        $saved = $null
        Write-Verbose "Discarded stale calendar reference for $($event.id); resolving it from the live calendar."
    }
    if (-not $saved) {
        $organizerUpn = $organizer.userPrincipalName
        if (-not $calendarItemsByUser.ContainsKey($organizerUpn)) {
            $calendarItemsByUser[$organizerUpn] = Get-AllGraphItems -Uri "https://graph.microsoft.com/v1.0/users/$organizerUpn/calendar/events?`$select=id,subject,start,transactionId,categories&`$top=100"
        }
        $matchingEvents = @($calendarItemsByUser[$organizerUpn] | Where-Object { Test-SeedEventMatch -Existing $_ -Record $event })
        if ($matchingEvents.Count -gt 1) { Write-Warning "Multiple calendar events match $($event.id); skipping this item." }
        $legacyEvent = $matchingEvents | Select-Object -First 1
        if ($legacyEvent) {
            $saved = @{ graphId = $legacyEvent.id; hash = $itemHash; adopted = $true }
            $script:seedState.events[$event.id] = $saved
            Write-Verbose "Adopted previously seeded calendar event $($event.id); no duplicate will be created."
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
                $saved = $null
                Write-Verbose "Seeded calendar event $($event.id) is missing from Graph; recreating it."
            }
        }
    }
    if ($saved -and $saved.graphId) {
        $eventPatch = @{}
        foreach ($key in $eventBody.Keys) { if ($key -ne 'transactionId') { $eventPatch[$key] = $eventBody[$key] } }
        try {
            Invoke-Graph -Method PATCH -Uri "https://graph.microsoft.com/v1.0/users/$($organizer.userPrincipalName)/events/$($saved.graphId)" -Body $eventPatch | Out-Null
            $script:seedState.events[$event.id].hash = $itemHash
            $eventIndex++
            Write-TaskProgress -Activity 'Reconciling calendar events' -Current $eventIndex -Total $events.Count -Status "Updated | $($event.title)"
            continue
        }
        catch { if ($_ -notmatch 'HTTP 404') { throw } }
    }
    $createdEvent = (Invoke-Graph -Method POST -Uri "https://graph.microsoft.com/v1.0/users/$($organizer.userPrincipalName)/calendar/events" -Body $eventBody).Body
    $script:seedState.events[$event.id] = @{ hash = $itemHash; graphId = $createdEvent.id }
    $eventIndex++
    Write-TaskProgress -Activity 'Creating calendar events' -Current $eventIndex -Total $events.Count -Status $event.title
}

$script:seedState.teamId = $teamId
Write-Output 'Seed complete.'
