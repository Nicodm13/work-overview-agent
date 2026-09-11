param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("start", "reset", "delete", "status")]
    [string]$Command
)

$composeFile = Join-Path $PSScriptRoot "..\compose.yaml"

switch ($Command) {
    "start" {
        docker compose -f $composeFile up --detach postgres
        docker compose -f $composeFile run --rm --build flyway
    }
    "reset" {
        docker compose -f $composeFile down --volumes
        docker compose -f $composeFile up --detach postgres
        docker compose -f $composeFile run --rm --build flyway
    }
    "delete" {
        docker compose -f $composeFile down --volumes
    }
    "status" {
        docker compose -f $composeFile ps
    }
}
