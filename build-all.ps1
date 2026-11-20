param(
    [string]$Jdk8 = $env:JAVA8_HOME,
    [string]$Jdk17 = $env:JAVA17_HOME,
    [string]$Jdk21 = $env:JAVA21_HOME,
    [string]$Jdk25 = $env:JAVA25_HOME,
    [string[]]$Only = @()
)
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$jdks = @{ 8 = $Jdk8; 17 = $Jdk17; 21 = $Jdk21; 25 = $Jdk25 }
$projects = @(
    @{ Dir = 'core';           Jdk = 21; Task = 'test';  Copy = $false },
    @{ Dir = 'fabric-1.16.5';  Jdk = 25; Task = 'build'; Copy = $false },
    @{ Dir = 'fabric-1.18.2';  Jdk = 25; Task = 'build'; Copy = $false },
    @{ Dir = 'fabric-1.20.1';  Jdk = 25; Task = 'build'; Copy = $false },
    @{ Dir = 'fabric-1.21.1';  Jdk = 25; Task = 'build'; Copy = $false },
    @{ Dir = 'fabric-1.21.11'; Jdk = 25; Task = 'build'; Copy = $false },
    @{ Dir = 'fabric-26.3';    Jdk = 25; Task = 'build'; Copy = $false },
    @{ Dir = 'fabric';         Jdk = 21; Task = 'build'; Copy = $true },
    @{ Dir = 'forge-1.20.1';   Jdk = 17; Task = 'build'; Copy = $true },
    @{ Dir = 'forge-1.12.2';   Jdk = 8;  Task = 'build'; Copy = $true },
    @{ Dir = 'neoforge-1.21.1'; Jdk = 21; Task = 'build'; Copy = $true }
)
$wrapper = if ($IsWindows -or $env:OS -eq 'Windows_NT') { '.\gradlew.bat' } else { './gradlew' }
$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force $dist | Out-Null
$results = @()
foreach ($p in $projects) {
    if ($Only.Count -gt 0 -and $Only -notcontains $p.Dir) { continue }
    $jdk = $jdks[$p.Jdk]
    if (-not $jdk) {
        Write-Host "JDK $($p.Jdk) missing: pass -Jdk$($p.Jdk) or set JAVA$($p.Jdk)_HOME"
        $results += "{0,-16} {1}" -f $p.Dir, 'SKIPPED'
        continue
    }
    $dir = Join-Path $root $p.Dir
    $env:JAVA_HOME = $jdk
    Write-Host "== $($p.Dir) (Java $($p.Jdk))"
    Push-Location $dir
    try {
        & $wrapper $p.Task --console=plain
        $ok = ($LASTEXITCODE -eq 0)
    } finally {
        Pop-Location
    }
    if ($ok -and $p.Copy) {
        $ver = (Select-String -Path (Join-Path $dir 'gradle.properties') -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value.Trim()
        Get-ChildItem (Join-Path $dir 'build/libs') -Filter "player-event-log-$ver+*.jar" |
            Where-Object { $_.Name -notmatch '-(sources|dev|slim)\.jar$' } |
            ForEach-Object { Copy-Item $_.FullName $dist -Force }
    }
    $results += "{0,-16} {1}" -f $p.Dir, $(if ($ok) { 'OK' } else { 'FAILED' })
}
Write-Host ''
$results | ForEach-Object { Write-Host $_ }

$sums = Get-ChildItem $dist -Filter 'player-event-log-*.jar' | Sort-Object Name | ForEach-Object {
    '{0} *{1}' -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower(), $_.Name
}
[System.IO.File]::WriteAllText((Join-Path $dist 'SHA256SUMS.txt'), (($sums -join "`n") + "`n"))
