<#
.SYNOPSIS
  Ejecuta la suite completa de pruebas (H2 en memoria + GreenMail; sin correo externo).
.PARAMETER Test
  Nombre de clase o patrón para ejecutar solo algunas pruebas, p. ej. -Test TwoStepLoginIT
.PARAMETER SecurityAudit
  Además ejecuta OWASP Dependency-Check (requiere descargar la base NVD; puede tardar).
#>
param([string]$Test = "", [switch]$SecurityAudit)
. "$PSScriptRoot\common.ps1"
Set-Location (Get-ProjectRoot)
Use-Jdk21
$args = @("-B", "test")
if ($Test -ne "") { $args += "-Dtest=$Test"; $args += "-Dsurefire.failIfNoSpecifiedTests=false" }
& (Get-MavenWrapper) @args
if ($LASTEXITCODE -ne 0) { throw "Pruebas fallidas (código $LASTEXITCODE)." }
if ($SecurityAudit) {
    Write-Host "Ejecutando OWASP Dependency-Check..." -ForegroundColor Cyan
    & (Get-MavenWrapper) -B -Psecurity-audit -DskipTests verify
    Write-Host "Informe: target\dependency-check-report.html"
}
