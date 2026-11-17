param([string]$Path = "$PWD/easypiano-release.jks", [string]$Alias = "easypiano")
$ErrorActionPreference = "Stop"
if (Test-Path $Path) { throw "A chave já existe: $Path" }
& keytool -genkeypair -v -keystore $Path -alias $Alias -keyalg RSA -keysize 3072 -validity 10000
if ($LASTEXITCODE -ne 0) { throw "keytool falhou" }
Write-Host "Guarde uma cópia segura. Atualizações precisam da mesma chave."
