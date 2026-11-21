# Cree la base (DB_NAME du .env), charge le schema puis le seed.
# Usage (depuis la racine du projet) :
#   powershell -ExecutionPolicy Bypass -File db\setup-db.ps1          # schema + seed
#   powershell -ExecutionPolicy Bypass -File db\setup-db.ps1 -NoSeed  # schema seul
#   powershell -ExecutionPolicy Bypass -File db\setup-db.ps1 -Reset   # DROP + recree
param([switch]$NoSeed, [switch]$Reset)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) { throw ".env introuvable ($envFile). Copie .env.example en .env." }

$cfg = @{}
foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $k, $v = $line -split '=', 2
    $cfg[$k.Trim()] = $v.Trim()
}

$psqlDir = "C:\Program Files\PostgreSQL\18\bin"
$psql = Join-Path $psqlDir "psql.exe"
if (-not (Test-Path $psql)) { $psql = "psql" }

$dbHost = if ($cfg["DB_HOST"]) { $cfg["DB_HOST"] } else { "localhost" }
$dbPort = if ($cfg["DB_PORT"]) { $cfg["DB_PORT"] } else { "5432" }
$dbUser = if ($cfg["DB_USER"]) { $cfg["DB_USER"] } else { "postgres" }
$dbName = $cfg["DB_NAME"]
if (-not $dbName) { throw "DB_NAME vide dans .env" }
if (-not $cfg["DB_PASSWORD"]) { throw "DB_PASSWORD vide dans .env" }
$env:PGPASSWORD = $cfg["DB_PASSWORD"]
$env:PGCLIENTENCODING = "UTF8"

$common = @("-h", $dbHost, "-p", $dbPort, "-U", $dbUser, "-v", "ON_ERROR_STOP=1")

if ($Reset) {
    Write-Host "DROP DATABASE $dbName ..."
    & $psql @common -d postgres -c "DROP DATABASE IF EXISTS `"$dbName`" WITH (FORCE);"
    if ($LASTEXITCODE -ne 0) { throw "Echec du DROP" }
}

$exists = & $psql @common -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$dbName'"
if ($LASTEXITCODE -ne 0) { throw "Connexion impossible (verifie DB_USER / DB_PASSWORD)" }
if ($exists -ne "1") {
    Write-Host "CREATE DATABASE $dbName ..."
    & $psql @common -d postgres -c "CREATE DATABASE `"$dbName`" ENCODING 'UTF8';"
    if ($LASTEXITCODE -ne 0) { throw "Echec du CREATE DATABASE" }
}

$hasTables = & $psql @common -d $dbName -tAc "SELECT 1 FROM information_schema.tables WHERE table_name = 'users'"
if ($hasTables -eq "1") {
    Write-Host "Le schema existe deja dans $dbName (utilise -Reset pour tout recreer)."
} else {
    Write-Host "Chargement du schema ..."
    & $psql @common -d $dbName -q -f (Join-Path $PSScriptRoot "01_schema.sql")
    if ($LASTEXITCODE -ne 0) { throw "Echec du schema" }
    if (-not $NoSeed) {
        Write-Host "Chargement du seed ..."
        & $psql @common -d $dbName -q -f (Join-Path $PSScriptRoot "02_seed.sql")
        if ($LASTEXITCODE -ne 0) { throw "Echec du seed" }
    }
}
Write-Host "OK : base '$dbName' prete."
