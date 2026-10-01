[CmdletBinding()]
param(
    [string]$ServerUrl,

    [string]$ServerName = 'work-overview-agent',

    [string]$OAuthClientId,

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
    $ServerUrl = 'http://localhost:8080/mcp'
}

try {
    $uri = [Uri]$ServerUrl
} catch {
    throw 'ServerUrl must be an absolute HTTP or HTTPS URL.'
}

if ($uri.Scheme -notin @('http', 'https')) {
    throw 'ServerUrl must use HTTP or HTTPS.'
}

$arguments = @('mcp', 'add', $ServerName, '--url', $uri.AbsoluteUri.TrimEnd('/'))
if (-not [string]::IsNullOrWhiteSpace($OAuthClientId)) {
    $arguments += @('--oauth-client-id', $OAuthClientId)
}
if ($DryRun) {
    Write-Output ('codex ' + ($arguments -join ' '))
    Write-Output "codex mcp login $ServerName"
    exit 0
}

if (-not (Get-Command codex -ErrorAction SilentlyContinue)) {
    throw 'Codex CLI was not found. Install Codex, then run this installer again.'
}

& codex @arguments
if ($LASTEXITCODE -ne 0) {
    throw "Codex could not register '$ServerName' (exit code $LASTEXITCODE)."
}

Write-Output "Registered '$ServerName' at $($uri.AbsoluteUri.TrimEnd('/'))."
& codex mcp list

Write-Output "Opening Entra sign-in for '$ServerName'..."
& codex mcp login $ServerName
if ($LASTEXITCODE -ne 0) {
    throw "Codex could not sign in to '$ServerName' (exit code $LASTEXITCODE)."
}
