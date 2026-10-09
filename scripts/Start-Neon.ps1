$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
$neonConfig = Join-Path $backendRoot '.neon.properties'
$backendJar = Join-Path $backendRoot 'target/jongq-0.0.1-SNAPSHOT.jar'
if (!(Test-Path -LiteralPath $neonConfig)) {
    throw 'Copy .neon.example.properties to .neon.properties and enter your Neon connection settings first.'
}
$neonSettings = Get-Content -LiteralPath $neonConfig -Raw
if ($neonSettings -match '(?m)^\s*[^#!\s][^\r\n]*=\s*[^\r\n]*REPLACE_') { throw 'Complete the REPLACE_* fields in .neon.properties first.' }
if ($neonSettings -notmatch '(?m)^DB_URL\s*=\s*jdbc:postgresql://[^\r\n]+[?&]sslmode=(require|verify-full)\b') {
    throw 'DB_URL must be a PostgreSQL JDBC URL with sslmode=require or sslmode=verify-full.'
}
if (!(Test-Path -LiteralPath $backendJar)) { throw 'Build the backend JAR first with Maven package -DskipTests.' }
Push-Location $backendRoot
try {
    Write-Host 'Starting backend with Neon. Flyway will apply schema migrations to the configured database.'
    & java -jar $backendJar '--spring.profiles.active=neon' '--spring.config.additional-location=file:./.neon.properties' '--debug=false'
    if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed. Review the startup error above.' }
} finally {
    Pop-Location
}
