# Shared local environment loader for PowerShell and the IntelliJ bridge.
$script:DriveConnectRoot = Split-Path -Parent $PSScriptRoot

function Import-DriveConnectEnvironment {
    param([switch]$ComposeOnly)
    $localFile = Join-Path $script:DriveConnectRoot '.env.local.ps1'
    if (-not (Test-Path -LiteralPath $localFile -PathType Leaf)) {
        throw 'Fichier .env.local.ps1 absent : copier .env.local.example.ps1 et configurer ses valeurs privees.'
    }
    try { . $localFile *> $null } catch {
        throw 'Chargement de .env.local.ps1 impossible. Verifier sa syntaxe sans afficher son contenu.'
    }
    $required = @('DB_PASSWORD')
    if (-not $ComposeOnly) { $required += 'JWT_SECRET' }
    foreach ($name in $required) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            throw "Variable obligatoire absente : $name. Utiliser `$env:$name dans .env.local.ps1."
        }
    }
    foreach ($name in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET', 'JWT_ISSUER', 'JWT_AUDIENCE', 'JWT_TTL', 'CORS_ALLOWED_ORIGINS')) {
        $value = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($null -ne $value -and ($value.Contains("`r") -or $value.Contains("`n"))) {
            throw "Variable multiligne non autorisee : $name."
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_URL)) { $env:DB_URL = 'jdbc:postgresql://127.0.0.1:15432/carshare' }
    if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { $env:DB_USERNAME = 'carshare' }
    if ($env:DB_URL -cne 'jdbc:postgresql://127.0.0.1:15432/carshare') {
        throw 'DB_URL doit cibler PostgreSQL local : jdbc:postgresql://127.0.0.1:15432/carshare. Aucun changement automatique des identifiants.'
    }
    if (-not $ComposeOnly) {
        try { $key = [Convert]::FromBase64String($env:JWT_SECRET) } catch { throw 'JWT_SECRET doit etre une cle Base64 valide, conservee hors Git.' }
        if ($key.Length -lt 32) { throw 'JWT_SECRET doit encoder au moins 32 octets aleatoires.' }
        $key = $null
    }
    # Do not silently override a datasource, Flyway connection or injected JVM option.
    $overrides = @(Get-ChildItem Env: | Where-Object {
        $_.Name -match '^SPRING_(DATASOURCE_|FLYWAY_|CONFIG_)|^DRIVECONNECT_SECURITY_JWT_' -or
        $_.Name -in @('SPRING_APPLICATION_JSON', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'MAVEN_OPTS', 'MAVEN_ARGS', 'COMPOSE_FILE', 'COMPOSE_PROJECT_NAME', 'COMPOSE_PROFILES')
    } | Where-Object { -not [string]::IsNullOrWhiteSpace($_.Value) })
    if ($overrides.Count -gt 0) {
        throw ('Surcharge externe presente : ' + (($overrides | ForEach-Object { $_.Name }) -join ', ') + '. La retirer de ce processus avant de lancer les scripts locaux.')
    }
    foreach ($name in @('SPRING_PROFILES_ACTIVE', 'SPRING_PROFILES_INCLUDE')) {
        $profiles = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($profiles -match '(?i)(^|[,;\s])prod($|[,;\s])') { throw 'Les scripts locaux sont interdits avec le profil prod, meme combine avec dev.' }
    }
    Sync-DriveConnectComposeEnvironment
}

function ConvertTo-DriveConnectDotEnvValue {
    param([string]$Value)
    if ($Value.Contains("`r") -or $Value.Contains("`n")) { throw 'Valeur multiligne interdite dans le fichier Compose local.' }
    # Quoted dotenv syntax: preserve backslashes, quotes and literal dollar signs.
    return '"' + $Value.Replace('\', '\\').Replace('"', '\"').Replace('$', '$$') + '"'
}

function Sync-DriveConnectComposeEnvironment {
    $destination = Join-Path $script:DriveConnectRoot '.env'
    $header = '# DriveConnect Compose environment - generated from .env.local.ps1; do not edit.'
    $content = $header + "`r`nDB_USERNAME=" + (ConvertTo-DriveConnectDotEnvValue $env:DB_USERNAME) +
        "`r`nDB_PASSWORD=" + (ConvertTo-DriveConnectDotEnvValue $env:DB_PASSWORD) + "`r`n"
    $previous = $null
    if (Test-Path -LiteralPath $destination) {
        if (-not (Test-Path -LiteralPath $destination -PathType Leaf)) { throw '.env existe mais n est pas un fichier. Aucune modification effectuee.' }
        $previous = [IO.File]::ReadAllText($destination)
        if ($previous -ceq $content) { return }
        $lines = @($previous -split '\r?\n' | Where-Object { $_.Length -gt 0 })
        if ($lines.Count -ne 3 -or $lines[0] -cne $header -or
            -not $lines[1].StartsWith('DB_USERNAME=') -or -not $lines[2].StartsWith('DB_PASSWORD=')) {
            throw '.env existant non gere ou personnalise : contenu preserve. Examiner localement sa provenance avant de le remplacer ; ne pas afficher ses secrets.'
        }
    }
    # Temporary secret files are also ignored by .env.*; publish by atomic rename.
    $temporary = Join-Path $script:DriveConnectRoot ('.env.' + [Guid]::NewGuid().ToString('N') + '.tmp')
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes($content)
        $stream = [IO.FileStream]::new($temporary, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
        if ($null -eq $previous) {
            [IO.File]::Move($temporary, $destination)
        } else {
            if ([IO.File]::ReadAllText($destination) -cne $previous) { throw 'Le fichier .env a change pendant la synchronisation ; relancer la commande.' }
            [IO.File]::Replace($temporary, $destination, [NullString]::Value)
        }
    } catch {
        throw 'Synchronisation .env impossible. Verifier les droits et les modifications concurrentes ; aucun secret affiche.'
    } finally {
        if (Test-Path -LiteralPath $temporary -PathType Leaf) { Remove-Item -LiteralPath $temporary }
    }
}

function Protect-DriveConnectOutput {
    param([string]$Line)
    foreach ($name in @('DB_PASSWORD', 'JWT_SECRET')) {
        $secret = [Environment]::GetEnvironmentVariable($name, 'Process')
        if (-not [string]::IsNullOrEmpty($secret)) { $Line = $Line.Replace($secret, '[REDACTED]') }
    }
    $Line = $Line -replace '\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}', '[BCRYPT REDACTED]'
    return ($Line -replace 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', '[JWT REDACTED]')
}

function Invoke-DriveConnectDocker {
    param([string[]]$DockerArguments, [string]$FailureMessage)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& docker @DockerArguments 2>&1)
        $nativeExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($nativeExit -ne 0) { throw $FailureMessage }
    return $output
}

function Get-DriveConnectComposeArguments {
    return @('compose', '--project-directory', $script:DriveConnectRoot, '-f', (Join-Path $script:DriveConnectRoot 'compose.yaml'))
}

function Assert-DriveConnectDocker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker absent du PATH. Installer ou ouvrir Docker Desktop, puis rouvrir le terminal.' }
    $os = Invoke-DriveConnectDocker -DockerArguments @('info', '--format', '{{.OSType}}') -FailureMessage 'Moteur Docker indisponible. Ouvrir Docker Desktop et attendre le moteur Linux.'
    if (($os -join '').Trim() -ne 'linux') { throw 'Docker doit utiliser les conteneurs Linux.' }
    $null = Invoke-DriveConnectDocker -DockerArguments @('compose', 'version') -FailureMessage 'Docker Compose indisponible. Mettre a jour Docker Desktop.'
}

function Assert-DriveConnectJava {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java absent du PATH. Configurer un JDK 21.' }
    if (-not (Test-Path -LiteralPath (Join-Path $script:DriveConnectRoot 'mvnw.cmd'))) { throw 'Maven Wrapper absent de la racine du projet.' }
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $version = @(& (Join-Path $script:DriveConnectRoot 'mvnw.cmd') -version 2>&1)
        $nativeExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($nativeExit -ne 0) { throw 'Maven Wrapper ne demarre pas. Verifier JAVA_HOME, le JDK et l acces reseau pour son premier telechargement.' }
    if (($version -join "`n") -notmatch 'Java version: 21\.') { throw 'Maven doit utiliser Java 21. Corriger JAVA_HOME et le JDK IntelliJ.' }
    Write-Host 'Java 21 et Maven Wrapper : OK.'
}

function Test-DriveConnectPort {
    param([int]$Port)
    $client = New-Object Net.Sockets.TcpClient
    try {
        $pending = $client.ConnectAsync('127.0.0.1', $Port)
        if (-not $pending.Wait(1000)) { return $false }
        return $client.Connected
    } catch { return $false } finally { $client.Dispose() }
}

function Invoke-DriveConnectMaven {
    param([string[]]$MavenArguments)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & (Join-Path $script:DriveConnectRoot 'mvnw.cmd') @MavenArguments 2>&1 | ForEach-Object {
            Write-Host (Protect-DriveConnectOutput -Line ([string]$_))
        }
        $script:DriveConnectMavenExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
}
