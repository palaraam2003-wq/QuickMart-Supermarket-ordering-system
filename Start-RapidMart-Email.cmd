@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-with-email.ps1"
if errorlevel 1 (
    echo.
    echo Email startup failed. Copy the error text above, without your password.
    pause
)
