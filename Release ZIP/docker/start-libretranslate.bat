@echo off
setlocal

cd /d "%~dp0"

where docker >nul 2>&1
if errorlevel 1 (
    echo Docker Desktop is not installed or docker.exe is not available in PATH.
    echo Install Docker Desktop, start it, and then run this file again.
    echo.
    pause
    exit /b 1
)

docker info >nul 2>&1
if errorlevel 1 (
    echo Docker Desktop is installed but not running yet.
    echo Start Docker Desktop, wait until it says it is running, and then run this file again.
    echo.
    pause
    exit /b 1
)

echo Starting LibreTranslate with Docker...
docker compose -f docker-compose.yml up -d
if errorlevel 1 (
    echo.
    echo Docker Compose failed to start LibreTranslate.
    echo Check Docker Desktop and try again.
    echo.
    pause
    exit /b 1
)

echo.
echo LibreTranslate is starting on:
echo http://127.0.0.1:5000
echo.
echo The first start can take a few minutes because Docker downloads
echo the container image and translation models.
echo.
pause
