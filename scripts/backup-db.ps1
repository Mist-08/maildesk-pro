<#
.SYNOPSIS
  Respalda la base de datos y los archivos (adjuntos/logotipo) de MailDesk Pro.
  - PostgreSQL (perfiles dev/prod): usa pg_dump local o el contenedor "db" de Docker Compose.
  - H2 (perfil local): copia el archivo de la base.
.PARAMETER OutDir
  Carpeta de destino (predeterminada: backups\AAAA-MM-DD_HHmm).
#>
param([string]$OutDir = "")
. "$PSScriptRoot\common.ps1"
$root = Get-ProjectRoot
Set-Location $root
Import-DotEnv
if ($OutDir -eq "") { $OutDir = Join-Path $root ("backups\" + (Get-Date -Format "yyyy-MM-dd_HHmm")) }
New-Item -ItemType Directory -Force $OutDir | Out-Null

$profile = $env:SPRING_PROFILES_ACTIVE
if (-not $profile) { $profile = "local" }

if ($profile -eq "local") {
    $h2 = Join-Path $root "data\maildesk-local.mv.db"
    if (Test-Path $h2) { Copy-Item $h2 (Join-Path $OutDir "maildesk-local.mv.db"); Write-Host "Base H2 copiada." }
    else { Write-Host "No se encontró la base H2 en $h2" -ForegroundColor Yellow }
} else {
    $dbUrl = $env:DB_URL; if (-not $dbUrl) { $dbUrl = "jdbc:postgresql://localhost:5432/maildesk" }
    $dbName = ($dbUrl -split "/")[-1]
    $dbUser = $env:DB_USERNAME; if (-not $dbUser) { $dbUser = "maildesk" }
    $dump = Join-Path $OutDir "maildesk.dump"
    if (Get-Command pg_dump -ErrorAction SilentlyContinue) {
        $env:PGPASSWORD = $env:DB_PASSWORD
        & pg_dump -Fc -h localhost -U $dbUser -d $dbName -f $dump
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    } elseif (Get-Command docker -ErrorAction SilentlyContinue) {
        & docker compose exec -T db pg_dump -Fc -U $dbUser -d $dbName | Set-Content -Path $dump -Encoding Byte
    } else {
        throw "Ni pg_dump ni docker están disponibles para respaldar PostgreSQL."
    }
    Write-Host "Volcado PostgreSQL: $dump"
}

$storage = $env:APP_STORAGE_DIR; if (-not $storage) { $storage = Join-Path $root "data\storage" }
if (Test-Path $storage) {
    Compress-Archive -Path (Join-Path $storage "*") -DestinationPath (Join-Path $OutDir "storage.zip") -Force
    Write-Host "Archivos comprimidos en storage.zip"
}
$key = Join-Path $root "data\otp-hmac.key"
if (Test-Path $key) { Copy-Item $key (Join-Path $OutDir "otp-hmac.key"); Write-Host "Clave HMAC local copiada (guárdela en lugar seguro)." -ForegroundColor Yellow }
Write-Host "Respaldo completo en $OutDir" -ForegroundColor Green
