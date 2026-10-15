[CmdletBinding()]
param(
    [string]$ServerUrl,

    [string]$ServerName = 'work-overview-agent',

    [string]$OAuthClientId,

    [string]$OAuthScope,

    [switch]$DryRun
)

function Get-LocalEnvironmentValue {
    param(
        [string]$Name
    )

    $environmentFile = Join-Path $PSScriptRoot '..\server\.env'
    if (-not (Test-Path -LiteralPath $environmentFile)) {
        return $null
    }

    $match = Get-Content -LiteralPath $environmentFile |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=(.*)$" } |
        Select-Object -First 1

    if ($null -eq $match) {
        return $null
    }

    return ($match -replace "^$([regex]::Escape($Name))=", '').Trim()
}

if ([string]::IsNullOrWhiteSpace($OAuthClientId)) {
    $OAuthClientId = $env:WORK_OVERVIEW_ENTRA_CLIENT_ID
}
if ([string]::IsNullOrWhiteSpace($OAuthClientId)) {
    $OAuthClientId = Get-LocalEnvironmentValue 'WORK_OVERVIEW_ENTRA_CLIENT_ID'
}
if ([string]::IsNullOrWhiteSpace($ServerUrl)) {
    $ServerUrl = $env:WORK_OVERVIEW_MCP_URL
}
if ([string]::IsNullOrWhiteSpace($ServerUrl)) {
    $ServerUrl = Get-LocalEnvironmentValue 'WORK_OVERVIEW_MCP_URL'
}
if ([string]::IsNullOrWhiteSpace($ServerUrl)) {
    $ServerUrl = 'https://localhost:8080/mcp'
}

try {
    $uri = [Uri]$ServerUrl
} catch {
    throw 'ServerUrl must be an absolute HTTP or HTTPS URL.'
}

if ($uri.Scheme -notin @('http', 'https')) {
    throw 'ServerUrl must use HTTP or HTTPS.'
}

$serverUrl = $uri.AbsoluteUri.TrimEnd('/')
if ([string]::IsNullOrWhiteSpace($OAuthScope)) {
    $OAuthScope = $env:WORK_OVERVIEW_ENTRA_SCOPE
}
if ([string]::IsNullOrWhiteSpace($OAuthScope)) {
    $OAuthScope = Get-LocalEnvironmentValue 'WORK_OVERVIEW_ENTRA_SCOPE'
}
if ([string]::IsNullOrWhiteSpace($OAuthScope)) {
    $OAuthScope = "$serverUrl/access_as_user"
}
$OAuthScope = $OAuthScope.Trim()

if ([string]::IsNullOrWhiteSpace($OAuthClientId)) {
    throw 'WORK_OVERVIEW_ENTRA_CLIENT_ID must be configured before installing the MCP client.'
}
$OAuthClientId = $OAuthClientId.Trim()

$serverNameKey = ConvertTo-Json -InputObject $ServerName -Compress
$serverUrlValue = ConvertTo-Json -InputObject $serverUrl -Compress
$scopeValue = ConvertTo-Json -InputObject $OAuthScope -Compress
$clientIdValue = ConvertTo-Json -InputObject $OAuthClientId -Compress
$serverConfiguration = @"
[mcp_servers.$serverNameKey]
url = $serverUrlValue
scopes = [$scopeValue]

[mcp_servers.$serverNameKey.oauth]
client_id = $clientIdValue
"@
$loginArguments = @('mcp', 'login', $ServerName, '--scopes', $OAuthScope)
if ($DryRun) {
    Write-Output "codex mcp logout $ServerName (if already registered)"
    Write-Output "codex mcp remove $ServerName (if already registered)"
    Write-Output 'Append the following MCP registration to Codex config.toml:'
    Write-Output $serverConfiguration
    Write-Output ('codex ' + ($loginArguments -join ' '))
    exit 0
}

if (-not (Get-Command codex -ErrorAction SilentlyContinue)) {
    throw 'Codex CLI was not found. Install Codex, then run this installer again.'
}

$null = & codex mcp get $ServerName --json 2>$null
if ($LASTEXITCODE -eq 0) {
    & codex mcp logout $ServerName *> $null
    & codex mcp remove $ServerName | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Codex could not remove the previous '$ServerName' registration (exit code $LASTEXITCODE)."
    }
}
$codexHome = if ([string]::IsNullOrWhiteSpace($env:CODEX_HOME)) {
    Join-Path $env:USERPROFILE '.codex'
} else {
    $env:CODEX_HOME
}
$configPath = Join-Path $codexHome 'config.toml'
if (-not (Test-Path -LiteralPath $codexHome)) {
    $null = New-Item -ItemType Directory -Path $codexHome -Force
}
[System.IO.File]::AppendAllText(
    $configPath,
    [Environment]::NewLine + $serverConfiguration + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false))

$null = & codex mcp get $ServerName --json 2>$null
if ($LASTEXITCODE -ne 0) {
    throw "Codex could not load the new '$ServerName' registration from $configPath."
}
Write-Output "Registered '$ServerName' at $serverUrl."

Write-Output "Opening Entra sign-in for '$ServerName'..."
& codex @loginArguments | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Codex could not sign in to '$ServerName' (exit code $LASTEXITCODE)."
}
Write-Output "Signed in to '$ServerName'."
