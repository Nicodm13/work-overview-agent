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

$scopeOverride = ('mcp_servers.' + $ServerName + '.scopes=' + (ConvertTo-Json -InputObject @($OAuthScope) -Compress)).Replace('"', '\"')
$arguments = @('mcp', 'add', $ServerName, '--url', $serverUrl, '-c', $scopeOverride)
if (-not [string]::IsNullOrWhiteSpace($OAuthClientId)) {
    $arguments += @('--oauth-client-id', $OAuthClientId)
}
$loginArguments = @('mcp', 'login', $ServerName, '--scopes', $OAuthScope)
if ($DryRun) {
    Write-Output ('codex ' + ($arguments -join ' '))
    Write-Output ('codex ' + ($loginArguments -join ' '))
    exit 0
}

if (-not (Get-Command codex -ErrorAction SilentlyContinue)) {
    throw 'Codex CLI was not found. Install Codex, then run this installer again.'
}

$existingServerJson = & codex mcp get $ServerName --json 2>$null
if ($LASTEXITCODE -eq 0) {
    $existingServer = $existingServerJson | ConvertFrom-Json
    if ($existingServer.transport.type -ne 'streamable_http' -or $existingServer.transport.url.TrimEnd('/') -ne $serverUrl) {
        throw "Codex already has '$ServerName' configured with a different endpoint. Review it with 'codex mcp get $ServerName' before reinstalling."
    }
    Write-Output "'$ServerName' is already registered at $serverUrl."
} else {
    & codex @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Codex could not register '$ServerName' (exit code $LASTEXITCODE)."
    }
    Write-Output "Registered '$ServerName' at $serverUrl."
}

& codex mcp list

Write-Output "Opening Entra sign-in for '$ServerName'..."
& codex @loginArguments
if ($LASTEXITCODE -ne 0) {
    throw "Codex could not sign in to '$ServerName' (exit code $LASTEXITCODE)."
}
