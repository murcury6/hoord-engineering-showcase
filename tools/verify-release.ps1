[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $root 'release-manifest.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.schema -ne 'hoord-public-review/1') { throw 'Unknown release manifest schema.' }
$approved = @{}
foreach ($entry in $manifest.files) {
    if ($entry.path -notmatch '^[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)*$' -or
        $entry.path -match '(^|/)\.{1,2}(/|$)' -or $approved.ContainsKey($entry.path) -or
        $entry.path -match '^\.git(/|$)|^\.build(/|$)' -or $entry.sha256 -notmatch '^[a-f0-9]{64}$') {
        throw 'Invalid or duplicate manifest entry.'
    }
    $approved[$entry.path] = $entry.sha256
}
$seen = @{}
$failures = [System.Collections.Generic.List[string]]::new()
$secretPatterns = @(
    '(?i)\b(?:sk-(?:proj-|ant-)?[A-Za-z0-9_-]{20,}|gh[pousr]_[A-Za-z0-9]{25,}|github_pat_[A-Za-z0-9_]{25,}|hf_[A-Za-z0-9]{25,}|AIza[A-Za-z0-9_-]{30,}|AKIA[A-Z0-9]{16})\b',
    '-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----',
    '\btskey-[A-Za-z]+-[A-Za-z0-9_-]{20,}\b',
    '(?i)(?:api[_-]?key|password|access[_-]?token|client[_-]?secret)\s*[:=]\s*["''][A-Za-z0-9_+/=-]{16,}["'']',
    '(?i)(?:https?://)[^\s/:]+:[^\s/@]+@',
    '(?i)[A-Z]:[\\/]+Users[\\/]',
    '(?im)^\s*(?:from|import)\s+(?:torch|tensorflow|transformers|diffusers|peft|safetensors)\b'
)

function Inspect-Directory([string]$Directory) {
    foreach ($item in Get-ChildItem -LiteralPath $Directory -Force) {
        $relative = $item.FullName.Substring($root.Length + 1).Replace('\', '/')
        if ($relative -in @('.git', '.build')) { continue }
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            $failures.Add("Link/reparse point: $relative"); continue
        }
        if ($item.PSIsContainer) { Inspect-Directory $item.FullName; continue }
        $seen[$relative] = $true
        if ($relative -ne 'release-manifest.json' -and -not $approved.ContainsKey($relative)) {
            $failures.Add("Unreviewed file: $relative"); continue
        }
        $reviewTransportSource = $relative.StartsWith('reference/remote-review/') -and
            $item.Extension -in @('.py', '.gs', '.cjs', '.go', '.mod', '.sum')
        if (-not $reviewTransportSource -and $item.Extension -notin @('.java', '.md', '.ps1', '.json', '.yml') -and
            $relative -notin @('LICENSE', '.gitignore', '.gitattributes')) {
            $failures.Add("Disallowed file type: $relative"); continue
        }
        if ($item.Length -gt 200KB) { $failures.Add("Oversized file: $relative"); continue }
        if ($relative -ne 'release-manifest.json') {
            $hash = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($hash -ne $approved[$relative]) { $failures.Add("Content changed since review: $relative") }
        }
        $text = [IO.File]::ReadAllText($item.FullName)
        if ($text.Contains([char]0)) { $failures.Add("Binary content: $relative") }
        foreach ($pattern in $secretPatterns) {
            if ($text -match $pattern) { $failures.Add("Credential, private path or model dependency pattern: $relative") }
        }
    }
}

Inspect-Directory $root
foreach ($name in $approved.Keys) {
    if (-not $seen.ContainsKey($name)) { $failures.Add("Missing reviewed file: $name") }
}
if (Test-Path -LiteralPath (Join-Path $root '.git')) {
    $tracked = @(& git -c "safe.directory=$($root.Replace('\','/'))" -C $root ls-files)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect tracked files.' }
    foreach ($name in $tracked) {
        if ($name -ne 'release-manifest.json' -and -not $approved.ContainsKey($name)) {
            $failures.Add("Unreviewed file in Git index: $name")
        }
    }
}
if ($failures.Count) { throw ("Release rejected:`n" + ($failures -join "`n")) }
Write-Host "PASS: $($approved.Count) reviewed file hashes; manifest and credential/model checks passed."
