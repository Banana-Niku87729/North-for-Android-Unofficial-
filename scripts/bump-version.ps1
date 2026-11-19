param([Parameter(Mandatory = $true)][string]$Version)
$ErrorActionPreference = 'Stop'

# Called by release.bat. Rewrites the default versionName / versionCode in app\build.gradle.kts.
# F-Droid reads these values at the tag, so they must be updated before tagging.
# versionCode = major*10000 + minor*100 + patch (1.1.6 -> 10106). Same formula as release.yml.
# NOTE: keep this file ASCII-only (Windows PowerShell 5.1 misreads BOM-less UTF-8).

$path = Join-Path $PSScriptRoot '..\app\build.gradle.kts'
$parts = $Version.Split('.')
if ($parts.Count -ne 3) { throw "Version must look like 1.2.3: $Version" }
$major = [int]$parts[0]; $minor = [int]$parts[1]; $patch = [int]$parts[2]
if ($minor -gt 99 -or $patch -gt 99) { throw 'minor and patch must be 99 or less (versionCode would collide).' }
$code = $major * 10000 + $minor * 100 + $patch

# UTF-8 without BOM; line endings (CRLF) are kept as is.
$enc = New-Object System.Text.UTF8Encoding($false)
$text = [IO.File]::ReadAllText($path, $enc)

$codePat = '(versionCode = .*toIntOrNull\(\) \?: )\d+'
$namePat = '(findProperty\("VERSION_NAME"\) as String\?\) \?: ")[0-9.]+(")'
if (-not ([regex]::IsMatch($text, $codePat) -and [regex]::IsMatch($text, $namePat))) {
    throw 'versionCode / versionName lines not found in build.gradle.kts (format changed?).'
}
$text = $text -replace $codePat, ('${1}' + $code)
$text = $text -replace $namePat, ('${1}' + $Version + '${2}')
[IO.File]::WriteAllText($path, $text, $enc)

Write-Host "Updated: versionName = $Version, versionCode = $code"