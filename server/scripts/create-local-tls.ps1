[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$certificateDirectory = Join-Path $PSScriptRoot '..\.local-tls'
$keyStore = Join-Path $certificateDirectory 'localhost.p12'
$certificate = Join-Path $certificateDirectory 'localhost.cer'

if ((Test-Path -LiteralPath $keyStore) -or (Test-Path -LiteralPath $certificate)) {
    throw 'Local TLS files already exist. Move them aside before generating a new certificate.'
}

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw 'Java keytool was not found. Install the project JDK before generating the local certificate.'
}

New-Item -ItemType Directory -Path $certificateDirectory -Force | Out-Null

& keytool -genkeypair -alias localhost -keyalg RSA -keysize 3072 -validity 365 `
    -storetype PKCS12 -keystore $keyStore -storepass local-development-only `
    -dname 'CN=localhost' -ext 'SAN=dns:localhost,ip:127.0.0.1' -noprompt
if ($LASTEXITCODE -ne 0) {
    throw 'keytool could not generate the local TLS keystore.'
}

& keytool -exportcert -rfc -alias localhost -keystore $keyStore `
    -storepass local-development-only -file $certificate
if ($LASTEXITCODE -ne 0) {
    throw 'keytool could not export the local TLS certificate.'
}

Write-Output "Created local TLS files in $certificateDirectory."
Write-Output 'The certificate is not trusted yet. Follow the trust instructions in server/README.md.'
