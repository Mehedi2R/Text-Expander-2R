@echo off
setlocal enabledelayedexpansion
title Text Expander 2R - Build & Install Tool

echo ========================================================
echo        TEXT EXPANDER 2R - BUILD & AUTO INSTALLER
echo ========================================================
echo.

:: 1. Auto detect JAVA_HOME
if defined JAVA_HOME (
    echo [OK] Using JAVA_HOME: %JAVA_HOME%
) else if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    echo [OK] Detected Android Studio JBR: !JAVA_HOME!
) else if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-17"
    echo [OK] Detected Java 17: !JAVA_HOME!
) else if exist "D:\Android\jdk-21\bin\java.exe" (
    set "JAVA_HOME=D:\Android\jdk-21"
    echo [OK] Detected Java 21: !JAVA_HOME!
) else (
    echo [ERROR] Java not found! Please set JAVA_HOME.
    pause
    exit /b 1
)

:: 2. Auto detect ADB
set "ADB_BIN=adb"
where adb >nul 2>&1
if %errorlevel% neq 0 (
    if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
        set "ADB_BIN=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    ) else if exist "C:\Users\%USERNAME%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
        set "ADB_BIN=C:\Users\%USERNAME%\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    )
)

:: 3. Build Signed Production Release APK
echo.
echo [1/3] Building production signed release APK...
call .\gradlew.bat --no-daemon assembleRelease
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Build failed! Check the Gradle error messages above.
    pause
    exit /b 1
)

:: 4. Extract generated APK and copy to root
echo.
echo [2/3] Extracting generated APK...
set "SOURCE_APK=app\build\outputs\apk\release\app-release.apk"
if not exist "%SOURCE_APK%" (
    echo [ERROR] Generated APK not found at %SOURCE_APK%
    pause
    exit /b 1
)

:: Detect version name from build.gradle.kts or find existing pattern
for /f "tokens=2 delims==" %%i in ('findstr "versionName" app\build.gradle.kts') do (
    set "RAW_VER=%%i"
    set "VER_NAME=!RAW_VER:"=!"
    set "VER_NAME=!VER_NAME: =!"
)

if not defined VER_NAME (
    set "VER_NAME=v3.0.51"
) else (
    set "VER_NAME=v!VER_NAME!"
)

set "TARGET_APK=TextExpander2R-!VER_NAME!.apk"
copy /y "%SOURCE_APK%" "%TARGET_APK%" >nul
echo [OK] Ready-to-use APK created: %TARGET_APK%

:: 5. Auto Install via ADB if device is connected
echo.
echo [3/3] Checking connected Android devices for installation...
"%ADB_BIN%" devices > "%TEMP%\adb_devices.txt" 2>&1

set "DEVICE_FOUND=0"
for /f "skip=1 tokens=1,2" %%A in ('type "%TEMP%\adb_devices.txt"') do (
    if "%%B"=="device" (
        set "DEVICE_FOUND=1"
        set "DEVICE_ID=%%A"
    )
)
del "%TEMP%\adb_devices.txt" >nul 2>&1

if "!DEVICE_FOUND!"=="1" (
    echo [OK] Connected device found: !DEVICE_ID!
    echo Installing %TARGET_APK% to your phone (keeping app data)...
    "%ADB_BIN%" install -r -d "%TARGET_APK%"
    if %errorlevel% equ 0 (
        echo.
        echo ========================================================
        echo [SUCCESS] APK INSTALLED SUCCESSFULLY ON YOUR DEVICE!
        echo ========================================================
    ) else (
        echo.
        echo [WARNING] Install failed via ADB. You can manually install: %TARGET_APK%
    )
) else (
    echo [INFO] No phone connected via USB debugging / WiFi ADB.
    echo --------------------------------------------------------
    echo Ready-to-use APK is ready at:
    echo %CD%\%TARGET_APK%
    echo You can copy and install this APK directly on your phone!
)

echo.
pause
