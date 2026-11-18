@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-tablet.ps1" %*
set "EXIT_CODE=%ERRORLEVEL%"

echo %cmdcmdline% | findstr /i /c:"/c \"\"" >nul
if %ERRORLEVEL%==0 (
    echo.
    if not "%EXIT_CODE%"=="0" (
        echo Fehler aufgetreten (Exit-Code %EXIT_CODE%^).
    )
    pause
)
exit /b %EXIT_CODE%
