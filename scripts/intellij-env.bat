@echo off
rem IntelliJ runs this environment script for each launch, even if already open.
rem Do not use SETLOCAL: IntelliJ must read these variables after CALL returns.
set "DRIVECONNECT_ENV_READY="
set "DRIVECONNECT_ENV_READER=1"
for /f "usebackq delims=" %%L in (`powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%~dp0intellij-env.ps1"`) do set "%%L"
set "DRIVECONNECT_ENV_READER="
if not defined DRIVECONNECT_ENV_READY exit /b 1
set "DRIVECONNECT_ENV_READY="
exit /b 0
