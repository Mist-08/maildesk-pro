<#
.SYNOPSIS
  Prepara MailDesk Pro en Windows: verifica JDK 21, crea .env con secretos aleatorios y compila.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
#>
. "$PSScriptRoot\common.ps1"
$root = Get-ProjectRoot
Set-Location $root

Write-Host "== MailDesk Pro · configuración inicial ==" -ForegroundColor Cyan
Use-Jdk21
$java = Get-JavaVersionLine "java"
Write-Host "Java detectado: $java"
if ($java -notmatch '"21\.') {
    Write-Host "Se recomienda JDK 21 (objetivo del proyecto). Otras versiones pueden funcionar pero no están verificadas." -ForegroundColor Yellow
}

$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) {
    Copy-Item (Join-Path $root ".env.example") $envFile
    Write-Host "Creado .env a partir de .env.example." -ForegroundColor Green
}

# Rellena secretos vacíos con valores aleatorios (no se muestran en pantalla).
$content = Get-Content $envFile -Encoding UTF8
$changed = $false
$content = $content | ForEach-Object {
    if ($_ -match '^APP_SETUP_SECRET=\s*$') { $changed = $true; "APP_SETUP_SECRET=" + (New-RandomSecret 24) }
    elseif ($_ -match '^APP_OTP_HMAC_SECRET=\s*$') { $changed = $true; "APP_OTP_HMAC_SECRET=" + (New-RandomSecret 48) }
    else { $_ }
}
if ($changed) {
    $content | Set-Content $envFile -Encoding UTF8
    Write-Host "Secretos generados en .env (APP_SETUP_SECRET y APP_OTP_HMAC_SECRET)." -ForegroundColor Green
    Write-Host "Consulte APP_SETUP_SECRET en .env: lo necesitará una sola vez para el alta del administrador." -ForegroundColor Yellow
}

New-Item -ItemType Directory -Force (Join-Path $root "data\storage") | Out-Null

Write-Host "Compilando (descarga dependencias la primera vez)..." -ForegroundColor Cyan
& (Get-MavenWrapper) -B -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "La compilación falló (código $LASTEXITCODE)." }
Write-Host "Listo. Arranque con: scripts\start.ps1  (y en otra consola scripts\start-dev-smtp.ps1 para recibir los códigos)." -ForegroundColor Green
