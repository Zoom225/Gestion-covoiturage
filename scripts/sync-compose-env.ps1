[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'dev-common.ps1')
try {
    # The PowerShell file is authoritative; .env is only a generated Compose export.
    Import-DriveConnectEnvironment -ComposeOnly
    Write-Host '.env synchronise depuis .env.local.ps1. Aucun secret affiche ; aucun identifiant PostgreSQL modifie.'
} catch {
    [Console]::Error.WriteLine('DriveConnect : ' + (Protect-DriveConnectOutput -Line $_.Exception.Message))
    exit 1
}
exit 0
