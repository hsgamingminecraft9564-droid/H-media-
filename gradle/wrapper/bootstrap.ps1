# Windows counterpart of gradlew. Same rules: pinned version, SHA-256 checked before use.
param([Parameter(ValueFromRemainingArguments = $true)] $GradleArgs)
$ErrorActionPreference = 'Stop'

$props = @{}
Get-Content (Join-Path $PSScriptRoot 'gradle-wrapper.properties') |
    Where-Object { $_ -match '^\s*[^#].*=' } |
    ForEach-Object {
        $key, $value = $_ -split '=', 2
        $props[$key.Trim()] = $value.Trim().Replace('\:', ':')
    }

$url = $props['distributionUrl']
$expected = $props['distributionSha256Sum']
if (-not $url) { throw 'distributionUrl is not set in gradle-wrapper.properties' }
if (-not $expected) { throw 'distributionSha256Sum is not set. Refusing to run an unverified Gradle.' }

$name = [System.IO.Path]::GetFileNameWithoutExtension($url)
$userHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$distHome = Join-Path $userHome "wrapper\dists\$name"

function Find-GradleBat {
    Get-ChildItem -Path $distHome -Recurse -Filter 'gradle.bat' -ErrorAction SilentlyContinue |
        Where-Object { $_.DirectoryName -like '*\bin' } | Select-Object -First 1
}

$gradleBat = Find-GradleBat
if (-not $gradleBat) {
    New-Item -ItemType Directory -Force -Path $distHome | Out-Null
    $zip = Join-Path $distHome "$name.zip"
    Write-Host "gradlew: downloading $url"
    Invoke-WebRequest -Uri $url -OutFile $zip
    $actual = (Get-FileHash -Algorithm SHA256 -Path $zip).Hash.ToLower()
    if ($actual -ne $expected.ToLower()) {
        Remove-Item $zip
        throw 'Checksum does not match. The download was discarded and not used.'
    }
    Expand-Archive -Path $zip -DestinationPath $distHome -Force
    Remove-Item $zip
    $gradleBat = Find-GradleBat
    if (-not $gradleBat) { throw 'Could not find bin\gradle.bat after unpacking.' }
}

& $gradleBat.FullName @GradleArgs
exit $LASTEXITCODE
