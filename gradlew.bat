@rem Gradle wrapper with auto-download support for Windows.
@rem Downloads Gradle 9.3.1 from gradle-wrapper.properties if not already cached.
@echo off
setlocal enabledelayedexpansion

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%

if not exist "%APP_HOME%gradle\wrapper\gradle-wrapper.properties" (
    echo ERROR: gradle\wrapper\gradle-wrapper.properties not found
    exit /b 1
)

REM Read gradle-wrapper.properties
for /f "tokens=1,* delims==" %%a in (gradle\wrapper\gradle-wrapper.properties) do (
    if "%%a"=="distributionUrl" set DIST_URL=%%b
    if "%%a"=="distributionSha256Sum" set DIST_CHECKSUM=%%b
)

REM Unescape URL (remove backslashes before slashes)
set DIST_URL=%DIST_URL:\=%

REM Extract Gradle version from URL
for /f "tokens=2 delims=-" %%a in ("%DIST_URL%") do set GRADLE_VERSION=%%a

REM Gradle user home
if not defined GRADLE_USER_HOME set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set DIST_DIR=%GRADLE_USER_HOME%\wrapper\dists

REM Create dist directory
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"

REM Check if Gradle is already cached
set GRADLE_BIN_HOME=
for /d %%d in ("%DIST_DIR%\gradle-%GRADLE_VERSION%-*") do (
    if exist "%%d\gradle-%GRADLE_VERSION%\bin\gradle.bat" (
        set GRADLE_BIN_HOME=%%d\gradle-%GRADLE_VERSION%
        goto found_gradle
    )
)

:download_gradle
echo Downloading Gradle %GRADLE_VERSION%...
for %%f in (%DIST_URL%) do set ZIP_FILE=%%~nxf
set ZIP_PATH=%DIST_DIR%\%ZIP_FILE%

powershell -Command "try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('%DIST_URL%', '%ZIP_PATH%') } catch { exit 1 }"
if errorlevel 1 (
    echo Failed to download Gradle
    exit /b 1
)

echo Extracting Gradle...
set UNZIP_DIR=%DIST_DIR%\gradle-%GRADLE_VERSION%-temp
if exist "%UNZIP_DIR%" rmdir /s /q "%UNZIP_DIR%"
mkdir "%UNZIP_DIR%"

powershell -Command "Expand-Archive -Path '%ZIP_PATH%' -DestinationPath '%UNZIP_DIR%'"

for /d %%d in ("%UNZIP_DIR%\gradle-*") do (
    set FINAL_DIR=%DIST_DIR%\%%~nxd-%RANDOM%
    move "%%d" "!FINAL_DIR!"
    set GRADLE_BIN_HOME=!FINAL_DIR!
)
rmdir /s /q "%UNZIP_DIR%"
del "%ZIP_PATH%"

:found_gradle
if not exist "%GRADLE_BIN_HOME%\bin\gradle.bat" (
    echo ERROR: Gradle not found at %GRADLE_BIN_HOME%
    exit /b 1
)

if defined JAVA_HOME (set JAVA_EXE=%JAVA_HOME%\bin\java.exe) else (set JAVA_EXE=java.exe)
"%GRADLE_BIN_HOME%\bin\gradle.bat" %*
endlocal
