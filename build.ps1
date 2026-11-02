$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    # Parent/shared are installed from this checkout, never assumed to be on Maven Central.
    & .\mvnw.cmd -B -ntp -N -f build-support/pom.xml install
    if ($LASTEXITCODE -ne 0) { throw 'Parent POM installation failed' }
    & .\mvnw.cmd -B -ntp -f build-support/shared/pom.xml clean install
    if ($LASTEXITCODE -ne 0) { throw 'Shared module verification failed' }
    & .\mvnw.cmd -B -ntp clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Paper verification failed' }
} finally {
    Pop-Location
}
