[CmdletBinding()]
param(
    [string]$ServerUrl,

    [string]$ServerName = 'work-overview-agent',

    [switch]$DryRun
)

if ([string]::IsNullOrWhiteSpace($ServerUrl)) {
    $ServerUrl = if ([string]::IsNullOrWhiteSpace($env:WORK_OVERVIEW_MCP_URL)) {
        'http://localhost:8080/mcp'
    } else {
        $env:WORK_OVERVIEW_MCP_URL
    }
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
if ($DryRun) {
    Write-Output ('codex ' + ($arguments -join ' '))
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
