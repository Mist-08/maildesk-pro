# Funciones compartidas por los scripts de MailDesk Pro (Windows PowerShell 5.1+ / PowerShell 7).
# Uso: . "$PSScriptRoot\common.ps1"

$ErrorActionPreference = "Stop"
$script:ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Get-ProjectRoot { return $script:ProjectRoot }

# Carga explícita de .env en el entorno del proceso actual. Las variables ya definidas en el
# sistema tienen prioridad; el archivo solo rellena lo que falte.
function Import-DotEnv {
    param([string]$Path = (Join-Path (Get-ProjectRoot) ".env"))
    if (-not (Test-Path $Path)) {
        Write-Host "Aviso: no existe $Path (se usarán valores predeterminados del perfil)." -ForegroundColor Yellow
        return
    }
    $loaded = 0
    foreach ($raw in Get-Content $Path -Encoding UTF8) {
        $line = $raw.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { continue }
        if ($line.StartsWith("export ")) { $line = $line.Substring(7).Trim() }
        $eq = $line.IndexOf("=")
        if ($eq -le 0) { continue }
        $key = $line.Substring(0, $eq).Trim()
        $value = $line.Substring($eq + 1).Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            if ($value.Length -ge 2) { $value = $value.Substring(1, $value.Length - 2) }
        } else {
            $hash = $value.IndexOf(" #")
            if ($hash -ge 0) { $value = $value.Substring(0, $hash).Trim() }
        }
        if ($key -notmatch '^[A-Za-z_][A-Za-z0-9_.]*$') { continue }
        $existing = [Environment]::GetEnvironmentVariable($key, "Process")
        if ($null -eq $existing -or $existing -eq "") {
            [Environment]::SetEnvironmentVariable($key, $value, "Process")
            $loaded++
        }
    }
    Write-Host "Cargadas $loaded variables desde .env (sin mostrar valores)." -ForegroundColor DarkGray
}

# Localiza un JDK 21 sin modificar la configuración global del sistema.
function Find-Jdk21 {
    $candidates = @()
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) { $candidates += $env:JAVA_HOME }
    foreach ($base in @("C:\Program Files\Java", "C:\Program Files\Eclipse Adoptium", "C:\Program Files\Microsoft", "C:\Program Files\Zulu", "C:\Program Files\Amazon Corretto")) {
        if (Test-Path $base) {
            Get-ChildItem $base -Directory | Where-Object { $_.Name -match "21" } | ForEach-Object { $candidates += $_.FullName }
        }
    }
    foreach ($c in $candidates) {
        $java = Join-Path $c "bin\java.exe"
        if (Test-Path $java) {
            $version = Get-JavaVersionLine $java
            if ($version -match '"21\.') { return $c }
        }
    }
    return $null
}

# Obtiene la primera línea de "java -version" sin que PowerShell 5.1 convierta stderr en error.
function Get-JavaVersionLine {
    param([string]$JavaExe = "java")
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $line = & cmd /c "`"$JavaExe`" -version 2>&1" | Select-Object -First 1
        return [string]$line
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Use-Jdk21 {
    $jdk = Find-Jdk21
    if ($null -eq $jdk) {
        Write-Host "No se encontró un JDK 21. Instale Temurin/Oracle JDK 21 o defina JAVA_HOME. Se usará el java del PATH." -ForegroundColor Yellow
        return
    }
    $env:JAVA_HOME = $jdk
    $env:PATH = (Join-Path $jdk "bin") + ";" + $env:PATH
    Write-Host "Usando JDK 21 en $jdk (solo para este proceso)." -ForegroundColor DarkGray
}

function Get-MavenWrapper {
    return (Join-Path (Get-ProjectRoot) "mvnw.cmd")
}

function New-RandomSecret {
    param([int]$Bytes = 32)
    $buffer = New-Object byte[] $Bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
    return ([Convert]::ToBase64String($buffer)).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}
