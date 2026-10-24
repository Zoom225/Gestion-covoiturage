[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'dev-common.ps1')
$result = 1
Push-Location $script:DriveConnectRoot
try {
    Import-DriveConnectEnvironment
    Assert-DriveConnectJava
    Assert-DriveConnectDocker
    Write-Host 'Tests sur PostgreSQL Testcontainers ephemere ; premier lancement : acces Maven Central et Docker Hub requis.'
    $startedAt = [DateTime]::UtcNow
    Invoke-DriveConnectMaven -MavenArguments @('clean', 'verify')
    $result = $script:DriveConnectMavenExit
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    $reports = @(Get-ChildItem -LiteralPath (Join-Path $script:DriveConnectRoot 'target/surefire-reports') -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTimeUtc -ge $startedAt })
    foreach ($report in $reports) {
        [xml]$xml = [IO.File]::ReadAllText($report.FullName)
        $tests += [int]$xml.testsuite.tests
        $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors
        $skipped += [int]$xml.testsuite.skipped
    }
    $passed = $tests - $failures - $errors - $skipped
    Write-Host "Tests rapportes : $tests ; reussis : $passed ; echecs : $failures ; erreurs : $errors ; ignores : $skipped."
    if ($reports.Count -eq 0 -or $tests -eq 0 -or $failures -gt 0 -or $errors -gt 0 -or $skipped -gt 0) { $result = 1 }
    if ($result -ne 0) { [Console]::Error.WriteLine('Verification en echec ou incomplete. Corriger la cause ; aucun test desactive.') }
} catch {
    [Console]::Error.WriteLine('DriveConnect : ' + (Protect-DriveConnectOutput -Line $_.Exception.Message))
    $result = 1
} finally { Pop-Location }
exit $result
