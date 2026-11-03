function Invoke-GraphParallel {
    param(
        [object[]]$Items,
        [scriptblock]$Worker,
        [string[]]$Functions,
        [string]$GraphToken,
        [ValidateRange(1, 16)][int]$MaxConcurrency = 4
    )
    if (-not $Items -or $Items.Count -eq 0) { return }
    $definitions = foreach ($name in $Functions) {
        $command = Get-Command -Name $name -CommandType Function -ErrorAction Stop
        "function ${name} {`n$($command.Definition)`n}"
    }
    $bootstrap = @'
param($definitions, $worker, $item, $graphToken)
$ErrorActionPreference = 'Stop'
$script:graphToken = $graphToken
foreach ($definition in $definitions) { . ([scriptblock]::Create($definition)) }
& ([scriptblock]::Create($worker)) $item
'@
    $pool = [runspacefactory]::CreateRunspacePool(1, $MaxConcurrency)
    $jobs = [System.Collections.Generic.List[object]]::new()
    try {
        $pool.Open()
        foreach ($item in $Items) {
            $powershell = [powershell]::Create()
            $powershell.RunspacePool = $pool
            [void]$powershell.AddScript($bootstrap).AddArgument([string[]]$definitions).AddArgument($Worker.ToString()).AddArgument($item).AddArgument($GraphToken)
            $jobs.Add(@{ PowerShell = $powershell; Handle = $powershell.BeginInvoke() })
        }
        $failures = [System.Collections.Generic.List[string]]::new()
        foreach ($job in $jobs) {
            try {
                $output = $job.PowerShell.EndInvoke($job.Handle)
                foreach ($warning in $job.PowerShell.Streams.Warning) { Write-Warning $warning.Message }
                if ($job.PowerShell.HadErrors) { throw ($job.PowerShell.Streams.Error | Out-String) }
                foreach ($result in $output) { Write-Output $result }
            }
            catch { $failures.Add([string]$_) }
        }
        if ($failures.Count -gt 0) { throw "Parallel Graph work failed: $($failures -join ' | ')" }
    }
    finally {
        foreach ($job in $jobs) { $job.PowerShell.Dispose() }
        $pool.Close()
        $pool.Dispose()
    }
}
