[CmdletBinding()]
param(
    [string]$Tag = "",
    [string]$Flow = "",
    [switch]$SmokeOnly,
    [switch]$NoReport,
    [switch]$IncludeAuth
)

$ErrorActionPreference = "Continue"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo_root = Split-Path -Parent $here

$env_file = Join-Path $here ".env"
$env_pairs = @()
if (Test-Path $env_file) {
    Get-Content $env_file | ForEach-Object {
        if ($_ -match '^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*?)\s*$') {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
            $env_pairs += "$($matches[1])=$($matches[2])"
        }
    }
} else {
    Write-Warning ".maestro/.env not found - auth-required flows will fail. Copy from .env.example."
}

$maestro = Get-Command maestro -ErrorAction SilentlyContinue
if (-not $maestro) {
    $candidate = Join-Path $env:USERPROFILE ".maestro\maestro\bin\maestro.bat"
    if (Test-Path $candidate) { $maestro_path = $candidate } else {
        Write-Error "Maestro CLI not found. See .maestro/README.md for install steps."
        exit 1
    }
} else { $maestro_path = $maestro.Source }

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$devices = & $adb devices | Select-String -Pattern "device$"
if ($devices.Count -eq 0) {
    Write-Error "No device attached. Plug in your phone with USB debugging enabled."
    exit 1
}
Write-Host "Device: $($devices[0].Line.Split()[0])" -ForegroundColor Cyan

$power_state = & $adb shell dumpsys power | Select-String -Pattern "mWakefulness=" | Select-Object -First 1
$locked = & $adb shell dumpsys window | Select-String -Pattern "mDreamingLockscreen=true|KeyguardServiceDelegate.*?showing=true" | Select-Object -First 1
if ($power_state -match "Asleep|Dozing" -or $locked) {
    Write-Host "Device is asleep or locked. Waking..." -ForegroundColor Yellow
    & $adb shell input keyevent KEYCODE_WAKEUP | Out-Null
    Start-Sleep -Milliseconds 400
    & $adb shell input swipe 500 1500 500 500 | Out-Null
    Start-Sleep -Milliseconds 400
    $still_locked = & $adb shell dumpsys window | Select-String -Pattern "mDreamingLockscreen=true" | Select-Object -First 1
    if ($still_locked) {
        Write-Warning "Device is still locked behind a PIN/pattern. Unlock the phone manually, then re-run."
        exit 1
    }
}

$flows_root = Join-Path $here "flows"
$report_dir = Join-Path $here ".reports"
New-Item -ItemType Directory -Force $report_dir | Out-Null

$base_args = @("test")
foreach ($p in $env_pairs) { $base_args += "-e"; $base_args += $p }

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$summary_log = Join-Path $report_dir "summary_$stamp.txt"
"" | Out-File -Encoding utf8 $summary_log

if ($Flow) {
    $files = @(Get-Item (Join-Path $flows_root $Flow))
} else {
    $files = Get-ChildItem -Path $flows_root -Recurse -Filter "*.yaml" | Where-Object { $_.Name -ne "config.yaml" }
    if (-not $IncludeAuth) {
        $files = $files | Where-Object { $_.FullName -notmatch "\\flows\\auth\\" -and $_.FullName -notmatch "\\flows\\system\\60_notification_permission" }
        Write-Host "Skipping auth/* and 60_notification_permission (clearState=true). Pass -IncludeAuth to run them." -ForegroundColor Yellow
    }
}

$pass = 0; $fail = 0; $errors = @()
foreach ($f in $files) {
    Write-Host "`n=== $($f.FullName) ===" -ForegroundColor Cyan
    & $adb shell cmd statusbar collapse | Out-Null
    $run_args = $base_args + $f.FullName
    & $maestro_path @run_args
    $rc = $LASTEXITCODE
    if ($rc -eq 0) {
        $pass++
        $line = "PASS  $($f.FullName)"
    } else {
        $fail++
        $errors += $f.FullName
        $line = "FAIL  $($f.FullName)"
    }
    for ($i = 0; $i -lt 5; $i++) {
        try {
            [System.IO.File]::AppendAllText($summary_log, "$line`r`n")
            break
        } catch {
            Start-Sleep -Milliseconds 200
        }
    }
}

Write-Host "`n========== SUITE SUMMARY ==========" -ForegroundColor Cyan
Write-Host "Pass: $pass" -ForegroundColor Green
Write-Host "Fail: $fail" -ForegroundColor $(if ($fail -gt 0) { "Red" } else { "Green" })
Write-Host "Summary log: $summary_log"
if ($fail -gt 0) {
    Write-Host "Failed flows:"
    $errors | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
    exit 1
}
exit 0
