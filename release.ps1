# Nuova versione di release: alza versionCode (+1) e versionName, poi genera APK e AAB firmati.
#   .\release.ps1                 -> versionName con l'ultima cifra +1 (0.1.0 -> 0.1.1)
#   .\release.ps1 -Name 1.0.0     -> versionName scelto
#   .\release.ps1 -NoBump         -> ricompila senza cambiare versione
param([string]$Name, [switch]$NoBump)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$file = 'app\build.gradle.kts'
$text = Get-Content $file -Raw -Encoding UTF8

$code = [int]([regex]::Match($text, 'versionCode = (\d+)').Groups[1].Value)
$old = [regex]::Match($text, 'versionName = "([^"]+)"').Groups[1].Value
if (-not $NoBump) {
    if (-not $Name) {
        $parts = $old.Split('.')
        $parts[-1] = [string]([int]$parts[-1] + 1)
        $Name = $parts -join '.'
    }
    $text = $text -replace 'versionCode = \d+', "versionCode = $($code + 1)"
    $text = $text -replace 'versionName = "[^"]+"', "versionName = `"$Name`""
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot $file), $text, (New-Object Text.UTF8Encoding $false))
    Write-Host "Versione: $old ($code) -> $Name ($($code + 1))"
} else {
    Write-Host "Versione invariata: $old ($code)"
}

if (-not (Test-Path 'keystore.properties')) { throw 'Manca keystore.properties: la build non sarebbe firmata.' }
.\gradlew.bat :app:assembleRelease :app:bundleRelease
if ($LASTEXITCODE -ne 0) { throw 'Build fallita.' }

Write-Host ''
Write-Host 'APK: app\build\outputs\apk\release\app-release.apk'
Write-Host 'AAB (per Play Console): app\build\outputs\bundle\release\app-release.aab'

# File simboli di questa versione (mapping R8 + simboli nativi) per leggere i crash. L'AAB li contiene già:
# la copia in simboli\<versione> serve se vuoi caricarli a mano o conservarli.
$ver = [regex]::Match((Get-Content $file -Raw -Encoding UTF8), 'versionName = "([^"]+)"').Groups[1].Value
$dir = "simboli\$ver"
New-Item -ItemType Directory -Force $dir | Out-Null
Copy-Item 'app\build\outputs\mapping\release\mapping.txt' $dir -Force
$native = Get-ChildItem 'app\build\outputs\native-debug-symbols\release' -Filter *.zip -ErrorAction SilentlyContinue | Select-Object -First 1
if ($native) { Copy-Item $native.FullName (Join-Path $dir 'native-debug-symbols.zip') -Force }
Write-Host "Simboli: $dir"
