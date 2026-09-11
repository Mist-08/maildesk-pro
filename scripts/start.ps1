<#
.SYNOPSIS
  Arranca MailDesk Pro cargando .env explícitamente.
.PARAMETER Profile
  Perfil de Spring: local (H2, sin PostgreSQL; predeterminado), dev (PostgreSQL local) o prod.
.PARAMETER Jar
  Ejecuta el .jar empaquetado (target/) en lugar de spring-boot:run.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\start.ps1
  powershell -ExecutionPolicy Bypass -File scripts\start.ps1 -Profile dev
  powershell -ExecutionPolicy Bypass -File scripts\start.ps1 -Profile prod -Jar
#>
param(
    [ValidateSet("local", "dev", "prod")] [string]$Profile = "",
    [switch]$Jar
)
. "$PSScriptRoot\common.ps1"
$root = Get-ProjectRoot
Set-Location $root
Use-Jdk21
Import-DotEnv

if ($Profile -ne "") { $env:SPRING_PROFILES_ACTIVE = $Profile }
if (-not $env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE = "local" }
Write-Host "Perfil activo: $($env:SPRING_PROFILES_ACTIVE)" -ForegroundColor Cyan
if ($env:APP_MAIL_REAL -ne "true") {
    Write-Host "Correo real pendiente de configuración: se usará el SMTP local $($env:MAIL_HOST):$($env:MAIL_PORT) (ver scripts\start-dev-smtp.ps1 o Mailpit)." -ForegroundColor Yellow
}

if ($Jar) {
    $jar = Get-ChildItem (Join-Path $root "target") -Filter "maildesk-pro-*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch "original" } | Select-Object -First 1
    if ($null -eq $jar) { throw "No existe el .jar. Ejecute primero scripts\setup.ps1 o mvnw -DskipTests package." }
    & java -jar $jar.FullName
} else {
    & (Get-MavenWrapper) -B -q spring-boot:run
}
