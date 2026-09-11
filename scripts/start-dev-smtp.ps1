<#
.SYNOPSIS
  Servidor SMTP LOCAL y AISLADO para desarrollo (no envía nada a Internet).
  Recibe los correos de la aplicación (códigos de verificación, invitaciones, mensajes) y los muestra en consola.
  Alternativa con interfaz web: docker compose up -d mailpit  (http://localhost:8025).
.PARAMETER Port
  Puerto SMTP local (predeterminado 1025, coincide con MAIL_PORT del perfil local/dev).
.PARAMETER LogFile
  Archivo donde también se guardan los correos recibidos (predeterminado data\correo-local.log).
#>
param([int]$Port = 1025, [string]$LogFile = "")
. "$PSScriptRoot\common.ps1"
$root = Get-ProjectRoot
Set-Location $root
Use-Jdk21
if ($LogFile -eq "") { $LogFile = Join-Path $root "data\correo-local.log" }
New-Item -ItemType Directory -Force (Split-Path $LogFile) | Out-Null
Write-Host "Iniciando SMTP local de desarrollo en 127.0.0.1:$Port (Ctrl+C para detener)..." -ForegroundColor Cyan
Write-Host "Los correos se muestran aquí y se guardan en $LogFile" -ForegroundColor DarkGray
& (Get-MavenWrapper) -B -q test-compile exec:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.mycompany.maildesk.devtools.DevSmtpCatcher" "-Dexec.args=$Port" 2>&1 |
    ForEach-Object { $_.ToString() } | Tee-Object -FilePath $LogFile -Append
