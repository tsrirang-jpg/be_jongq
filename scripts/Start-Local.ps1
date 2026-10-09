param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [int]$DatabasePort = 5433
)
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
$localRoot = Join-Path $backendRoot '.local-postgres'
$clusterPath = Join-Path $localRoot 'data'
$passwordPath = Join-Path $localRoot 'password.txt'
if (!(Test-Path (Join-Path $PostgresBin 'initdb.exe'))) { throw 'PostgreSQL is not installed at PostgresBin. Specify -PostgresBin or use Docker Compose.' }
New-Item -ItemType Directory -Path $localRoot -Force | Out-Null
if (!(Test-Path $passwordPath)) {
    [System.IO.File]::WriteAllText($passwordPath, [Guid]::NewGuid().ToString('N'), (New-Object System.Text.UTF8Encoding($false)))
}
$databasePassword = Get-Content -LiteralPath $passwordPath -Raw
if (!(Test-Path (Join-Path $clusterPath 'PG_VERSION'))) {
    & (Join-Path $PostgresBin 'initdb.exe') -D $clusterPath -U jongq --auth-host=scram-sha-256 --auth-local=scram-sha-256 --pwfile $passwordPath --encoding=UTF8
    if ($LASTEXITCODE -ne 0) { throw 'initdb failed' }
}
$started = $false
Push-Location $backendRoot
try {
    & (Join-Path $PostgresBin 'pg_ctl.exe') -D $clusterPath status *> $null
    if ($LASTEXITCODE -ne 0) {
        & (Join-Path $PostgresBin 'pg_ctl.exe') -D $clusterPath -l (Join-Path $localRoot 'postgres.log') -o "-p $DatabasePort -h 127.0.0.1" -w start
        if ($LASTEXITCODE -ne 0) { throw 'Database startup failed; check that DatabasePort is free.' }
        $started = $true
    }
    $env:PGPASSWORD = $databasePassword
    $exists = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $DatabasePort -U jongq -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'jongq'"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot connect to the local database' }
    if ($exists -ne '1') {
        & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $DatabasePort -U jongq jongq
        if ($LASTEXITCODE -ne 0) { throw 'Cannot create database' }
    }
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$DatabasePort/jongq"
    $env:DB_USERNAME = 'jongq'
    $env:DB_PASSWORD = $databasePassword
    Write-Host 'Starting backend at http://localhost:8080 (local development admin: admin / admin123 unless overridden).'
    & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev' '-Dspring-boot.run.arguments=--debug=false'
} finally {
    if ($started) { & (Join-Path $PostgresBin 'pg_ctl.exe') -D $clusterPath -w stop }
    Pop-Location
}