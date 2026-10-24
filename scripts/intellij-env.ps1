# Internal provider for intellij-env.bat. Its output is an in-memory protocol,
# not diagnostic output: never invoke this provider directly in a terminal.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# Avoid displaying the transfer protocol on an accidental direct invocation.
if ($env:DRIVECONNECT_ENV_READER -ne '1') {
    [Console]::Error.WriteLine('DriveConnect: utiliser la configuration IntelliJ, pas ce fournisseur interne.')
    exit 1
}

try {
    . (Join-Path $PSScriptRoot 'dev-common.ps1')
    Import-DriveConnectEnvironment

    $names = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET',
        'JWT_ISSUER', 'JWT_AUDIENCE', 'JWT_TTL', 'CORS_ALLOWED_ORIGINS')
    $entries = foreach ($name in $names) {
        $value = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($null -ne $value) {
            if ($value.Contains("`r") -or $value.Contains("`n")) {
                throw 'Multiline environment variable'
            }
            '{0}={1}' -f $name, $value
        }
    }

    # The batch wrapper captures every line; no secret is echoed or written to disk.
    $entries
    'DRIVECONNECT_ENV_READY=1'
} catch {
    [Console]::Error.WriteLine('DriveConnect: chargement de .env.local.ps1 impossible. Verifier le fichier et les variables DB_PASSWORD / JWT_SECRET.')
    exit 1
}
