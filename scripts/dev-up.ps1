[CmdletBinding()]
param([switch]$DatabaseOnly, [ValidateRange(1, 65535)][int]$Port = 8080)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'dev-common.ps1')
$result = 0
Push-Location $script:DriveConnectRoot
try {
    Import-DriveConnectEnvironment
    Assert-DriveConnectJava
    Assert-DriveConnectDocker
    $compose = Get-DriveConnectComposeArguments
    $null = Invoke-DriveConnectDocker -DockerArguments ($compose + @('config', '--quiet')) -FailureMessage 'Compose invalide. Verifier compose.yaml et les variables locales ; ne pas afficher la configuration resolue.'
    $existing = @(Invoke-DriveConnectDocker -DockerArguments ($compose + @('ps', '-a', '-q', 'postgres')) -FailureMessage 'Lecture des conteneurs PostgreSQL impossible.')
    $allContainers = @(Invoke-DriveConnectDocker -DockerArguments @('ps', '-a', '--no-trunc', '-q') -FailureMessage 'Lecture des conteneurs Docker impossible.')
    foreach ($id in $allContainers) {
        if ($existing -contains $id) { continue }
        $bindings = (Invoke-DriveConnectDocker -DockerArguments @('inspect', '--format', '{{json .HostConfig.PortBindings}}', [string]$id) -FailureMessage 'Lecture des ports Docker impossible.') -join ''
        if ($bindings -ne 'null') {
            $ports = $bindings | ConvertFrom-Json
            foreach ($property in $ports.PSObject.Properties) {
                if (@($property.Value | Where-Object { $_.HostPort -eq '15432' }).Count -gt 0) {
                    throw 'Un conteneur externe, meme arrete, utilise deja la configuration du port 15432. Aucun second PostgreSQL cree ; verifier son projet et son volume.'
                }
            }
        }
    }
    if ($existing.Count -eq 0 -and (Test-DriveConnectPort -Port 15432)) {
        throw 'Port 15432 deja utilise par un service externe a ce projet Compose. Aucun second PostgreSQL cree ; identifier ce service avant de continuer.'
    }
    if ($existing.Count -gt 0) {
        # Inspect only non-secret fields; never dump Config.Env.
        $image = Invoke-DriveConnectDocker -DockerArguments @('inspect', '--format', '{{.Config.Image}}', [string]$existing[0]) -FailureMessage 'Conteneur PostgreSQL illisible.'
        if (($image -join '').Trim() -ne 'postgres:16.6-alpine') { throw 'Conteneur existant incompatible avec PostgreSQL 16.6 attendu. Aucun remplacement automatique.' }
        $bindings = ((Invoke-DriveConnectDocker -DockerArguments @('inspect', '--format', '{{json .HostConfig.PortBindings}}', [string]$existing[0]) -FailureMessage 'Ports PostgreSQL illisibles.') -join '') | ConvertFrom-Json
        $expectedPort = @($bindings.'5432/tcp' | Where-Object { $_.HostIp -eq '127.0.0.1' -and $_.HostPort -eq '15432' })
        $mounts = ((Invoke-DriveConnectDocker -DockerArguments @('inspect', '--format', '{{json .Mounts}}', [string]$existing[0]) -FailureMessage 'Volume PostgreSQL illisible.') -join '') | ConvertFrom-Json
        $expectedVolume = @($mounts | Where-Object { $_.Name -eq 'driveconnect-dev_postgres-data' -and $_.Destination -eq '/var/lib/postgresql/data' })
        if ($expectedPort.Count -ne 1 -or $expectedVolume.Count -ne 1) { throw 'Port ou volume du conteneur existant incompatible. Aucun remplacement ni suppression automatique.' }
    }
    Write-Host 'Demarrage PostgreSQL : reutilisation du conteneur et du volume Compose existants si presents.'
    $null = Invoke-DriveConnectDocker -DockerArguments ($compose + @('up', '-d', '--no-recreate', '--wait', '--wait-timeout', '90', 'postgres')) -FailureMessage 'PostgreSQL ne devient pas healthy. Verifier Docker, le port 15432 et le volume existant ; ne pas le supprimer.'
    $container = @(Invoke-DriveConnectDocker -DockerArguments ($compose + @('ps', '-q', 'postgres')) -FailureMessage 'Conteneur PostgreSQL introuvable apres demarrage.')
    if ($container.Count -ne 1) { throw 'Un seul service PostgreSQL est attendu.' }
    $health = Invoke-DriveConnectDocker -DockerArguments @('inspect', '--format', '{{.State.Health.Status}}', [string]$container[0]) -FailureMessage 'Etat de sante PostgreSQL indisponible.'
    if (($health -join '').Trim() -ne 'healthy') { throw 'PostgreSQL non healthy. Verifier le service avant de lancer Java.' }
    if (-not (Test-DriveConnectPort -Port 15432)) { throw 'PostgreSQL healthy mais 127.0.0.1:15432 inaccessible. Verifier la publication du port et le pare-feu.' }
    Write-Host 'PostgreSQL healthy ; port 127.0.0.1:15432 accessible. Variables locales chargees sans affichage.'
    if (-not $DatabaseOnly) {
        if (Test-DriveConnectPort -Port $Port) {
            if ($Port -eq 8080 -and -not (Test-DriveConnectPort -Port 8081)) { $Port = 8081 }
            else { throw 'Port HTTP occupe. Relancer avec -Port suivi d un port libre ; aucune instance existante arretee.' }
        }
        $env:SPRING_PROFILES_ACTIVE = 'dev'
        Write-Host "Backend dev sur http://127.0.0.1:$Port ; Ctrl+C pour arreter le backend, puis dev-down.ps1 pour PostgreSQL."
        Invoke-DriveConnectMaven -MavenArguments @('spring-boot:run', '-Dspring-boot.run.profiles=dev', "-Dspring-boot.run.arguments=--server.port=$Port")
        $result = $script:DriveConnectMavenExit
        if ($result -ne 0) { [Console]::Error.WriteLine('Backend en echec : 28P01 = identifiants du volume a verifier ; connexion refusee = port/service ; erreur Flyway = migration. Ne pas reinitialiser le volume.') }
    }
} catch {
    [Console]::Error.WriteLine('DriveConnect : ' + (Protect-DriveConnectOutput -Line $_.Exception.Message))
    $result = 1
} finally { Pop-Location }
exit $result
