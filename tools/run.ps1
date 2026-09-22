[CmdletBinding()]
param([string]$JdkRoot, [switch]$Test)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$javac = 'javac'
$java = 'java'
if ($JdkRoot) {
    $suffix = if ($env:OS -eq 'Windows_NT') { '.exe' } else { '' }
    $javac = Join-Path $JdkRoot "bin/javac$suffix"
    $java = Join-Path $JdkRoot "bin/java$suffix"
}
Push-Location $root
try {
    New-Item -ItemType Directory -Force '.build/classes' | Out-Null
    $sources = @(Get-ChildItem 'src' -Recurse -Filter '*.java' -File | ForEach-Object FullName)
    & $javac --release 17 -encoding UTF-8 -d '.build/classes' @sources
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
    if ($Test) { & $java '-Djava.awt.headless=true' -cp '.build/classes' ShowcaseTests }
    else { & $java -cp '.build/classes' Showcase }
    if ($LASTEXITCODE -ne 0) { throw 'Showcase execution failed.' }
} finally { Pop-Location }
