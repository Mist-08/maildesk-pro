<#
.SYNOPSIS
  Restaura un respaldo creado con backup-db.ps1. DETENGA la aplicación antes de restaurar.
.PARAMETER BackupDir
  Carpeta del respaldo (contiene maildesk.dump o maildesk-local.mv.db y storage.zip).
#>
param([Parameter(Mandatory = $true)][string]$BackupDir)
. "$PSScriptRoot\common.ps1"
$root = Get-ProjectRoot
Set-Location $root
Import-DotEnv
if (-not (Test-Path $BackupDir)) { throw "No existe $BackupDir" }

$profile = $env:SPRING_PROFILES_ACTIVE
if (-not $profile) { $profile = "local" }

if ($profile -eq "local") {
    $src = Join-Path $BackupDir "maildesk-local.mv.db"
    if (Test-Path $src) {
        New-Item -ItemType Directory -Force (Join-Path $root "data") | Out-Null
        Copy-Item $src (Join-Path $root "data\maildesk-local.mv.db") -Force
        Write-Host "Base H2 restaurada."
    }
} else {
    $dump = Join-Path $BackupDir "maildesk.dump"
    if (-not (Test-Path $dump)) { throw "No se encontró maildesk.dump" }
    $dbUrl = $env:DB_URL; if (-not $dbUrl) { $dbUrl = "jdbc:postgresql://localhost:5432/maildesk" }
    $dbName = ($dbUrl -split "/")[-1]
    $dbUser = $env:DB_USERNAME; if (-not $dbUser) { $dbUser = "maildesk" }
    if (Get-Command pg_restore -ErrorAction SilentlyContinue) {
        $env:PGPASSWORD = $env:DB_PASSWORD
        & pg_restore --clean --if-exists -h localhost -U $dbUser -d $dbName $dump
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    } elseif (Get-Command docker -ErrorAction SilentlyContinue) {
        Get-Content $dump -Encoding Byte -ReadCount 0 | & docker compose exec -T db pg_restore --clean --if-exists -U $dbUser -d $dbName
    } else {
        throw "Ni pg_restore ni docker están disponibles."
    }
    Write-Host "Base PostgreSQL restaurada."
}

$zip = Join-Path $BackupDir "storage.zip"
if (Test-Path $zip) {
    $storage = $env:APP_STORAGE_DIR; if (-not $storage) { $storage = Join-Path $root "data\storage" }
    New-Item -ItemType Directory -Force $storage | Out-Null
    Expand-Archive -Path $zip -DestinationPath $storage -Force
    Write-Host "Archivos restaurados en $storage"
}
$key = Join-Path $BackupDir "otp-hmac.key"
if ((Test-Path $key) -and -not (Test-Path (Join-Path $root "data\otp-hmac.key"))) {
    Copy-Item $key (Join-Path $root "data\otp-hmac.key")
    Write-Host "Clave HMAC local restaurada."
}
Write-Host "Restauración completa." -ForegroundColor Green
