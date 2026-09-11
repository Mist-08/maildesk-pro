<#
.SYNOPSIS
  Servidor SMTP LOCAL y AISLADO para desarrollo (no envía nada a Internet).
  Recibe los correos de la aplicación (códigos de verificación, invitaciones, mensajes) y los muestra en consola.
  Alternativa con interfaz web: docker compose up -d mailpit  (http://localhost:8025).
.PARAMETER Port
  Puerto SMTP local (predeterminado 1025, coincide con MAIL_PORT del perfil local/dev).
#>
param([int]$Port = 1025)
. "$PSScriptRoot\common.ps1"
Set-Location (Get-ProjectRoot)
Use-Jdk21
Write-Host "Iniciando SMTP local de desarrollo en 127.0.0.1:$Port (Ctrl+C para detener)..." -ForegroundColor Cyan
& (Get-MavenWrapper) -B -q test-compile exec:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.mycompany.maildesk.devtools.DevSmtpCatcher" "-Dexec.args=$Port"
