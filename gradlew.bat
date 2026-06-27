@ECHO OFF
SETLOCAL ENABLEDELAYEDEXPANSION

SET APP_HOME=%~dp0
SET DIST_ROOT=%APP_HOME%gradle-dist
SET GRADLE_VERSION=9.3.1
SET DIST_NAME=gradle-%GRADLE_VERSION%-bin.zip
SET DIST_URL=https://services.gradle.org/distributions/%DIST_NAME%
SET DIST_DIR=%DIST_ROOT%\gradle-%GRADLE_VERSION%
SET ZIP_PATH=%DIST_ROOT%\%DIST_NAME%

IF NOT EXIST "%DIST_ROOT%" MKDIR "%DIST_ROOT%"

IF NOT EXIST "%DIST_DIR%\bin\gradle.bat" (
  ECHO Downloading Gradle %GRADLE_VERSION%...
  powershell -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%ZIP_PATH%'"
  powershell -Command "Expand-Archive -LiteralPath '%ZIP_PATH%' -DestinationPath '%DIST_ROOT%' -Force"
)

CALL "%DIST_DIR%\bin\gradle.bat" %*
