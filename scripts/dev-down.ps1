[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'dev-common.ps1')
$result = 0
try {
    Import-DriveConnectEnvironment -ComposeOnly
    Assert-DriveConnectDocker
    $compose = Get-DriveConnectComposeArguments
    $null = Invoke-DriveConnectDocker -DockerArguments ($compose + @('config', '--quiet')) -FailureMessage 'Compose invalide : verifier le fichier local et compose.yaml.'
    Write-Host 'Arreter d abord le backend avec Ctrl+C dans son terminal. Aucun processus Java termine automatiquement.'
    $null = Invoke-DriveConnectDocker -DockerArguments ($compose + @('stop', '--timeout', '30', 'postgres')) -FailureMessage 'Arret PostgreSQL impossible. Verifier Docker Desktop.'
    Write-Host 'PostgreSQL arrete proprement ; conteneur, volume et donnees conserves.'
} catch {
    [Console]::Error.WriteLine('DriveConnect : ' + (Protect-DriveConnectOutput -Line $_.Exception.Message))
    $result = 1
}
exit $result
