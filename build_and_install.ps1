# Text Expander 2R - One-Click Build & Auto Install Script (PowerShell)
$Host.UI.RawUI.WindowTitle = "Text Expander 2R - Build & Install Tool"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "       TEXT EXPANDER 2R - BUILD & AUTO INSTALLER" -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan

# 1. Detect Java Home
$javaCandidates = @(
    $env:JAVA_HOME,
    "C:\Program Files\Android\Android Studio\jbr",
    "C:\Program Files\Java\jdk-17",
    "D:\Android\jdk-21"
)
$validJava = $null
foreach ($path in $javaCandidates) {
    if ($path -and (Test-Path "$path\bin\java.exe")) {
        $validJava = $path
        break
    }
}

if (-not $validJava) {
    Write-Host "[ERROR] Java Runtime not found! Please install Java or Android Studio." -ForegroundColor Red
    pause
    exit 1
}
$env:JAVA_HOME = $validJava
Write-Host "[OK] Using Java: $validJava" -ForegroundColor Green

# 2. Detect ADB
$adbPath = "adb"
$adbCandidates = @(
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools\adb.exe"
)
foreach ($candidate in $adbCandidates) {
    if (Test-Path $candidate) {
        $adbPath = $candidate
        break
    }
}

# 3. Build Release APK
Write-Host "`n[1/3] Building signed production APK..." -ForegroundColor Yellow
$process = Start-Process -FilePath ".\gradlew.bat" -ArgumentList "--no-daemon", "assembleRelease" -NoNewWindow -Wait -PassThru

if ($process.ExitCode -ne 0) {
    Write-Host "`n[ERROR] Build failed with exit code $($process.ExitCode)!" -ForegroundColor Red
    pause
    exit 1
}

# 4. Copy to Root Directory
Write-Host "`n[2/3] Extracting generated APK..." -ForegroundColor Yellow
$sourceApk = "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $sourceApk)) {
    Write-Host "[ERROR] Output APK not found at $sourceApk" -ForegroundColor Red
    pause
    exit 1
}

$versionName = "v3.0.51"
if (Test-Path "app\build.gradle.kts") {
    $match = Select-String -Path "app\build.gradle.kts" -Pattern 'versionName\s*=\s*"([^"]+)"'
    if ($match.Matches.Groups.Count -gt 1) {
        $versionName = "v" + $match.Matches.Groups[1].Value.Trim()
    }
}

$targetApk = "TextExpander2R-$versionName.apk"
Copy-Item -Path $sourceApk -Destination $targetApk -Force
Write-Host "[OK] Ready-to-use APK created: $targetApk" -ForegroundColor Green

# 5. Check ADB and Auto Install
Write-Host "`n[3/3] Checking connected Android devices for installation..." -ForegroundColor Yellow
$devicesOutput = & $adbPath devices 2>$null
$onlineDevices = $devicesOutput | Where-Object { $_ -match "\bdevice$" }

if ($onlineDevices) {
    $deviceId = ($onlineDevices[0] -split "\s+")[0]
    Write-Host "[OK] Connected phone found: $deviceId" -ForegroundColor Green
    Write-Host "Installing $targetApk directly to your phone..." -ForegroundColor Cyan
    & $adbPath install -r -d $targetApk
    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n========================================================" -ForegroundColor Green
        Write-Host "[SUCCESS] APK INSTALLED SUCCESSFULLY ON YOUR DEVICE!" -ForegroundColor Green
        Write-Host "========================================================" -ForegroundColor Green
    } else {
        Write-Host "`n[WARNING] ADB install failed. You can manually install: $targetApk" -ForegroundColor Yellow
    }
} else {
    Write-Host "[INFO] No phone connected via USB debugging or Wireless ADB." -ForegroundColor Gray
    Write-Host "--------------------------------------------------------" -ForegroundColor Gray
    Write-Host "Your ready-to-use signed APK is available at:" -ForegroundColor Cyan
    Write-Host (Join-Path (Get-Location) $targetApk) -ForegroundColor White
    Write-Host "You can transfer and install this APK directly on your phone!" -ForegroundColor Gray
}

Write-Host "`nDone!" -ForegroundColor Green
pause
