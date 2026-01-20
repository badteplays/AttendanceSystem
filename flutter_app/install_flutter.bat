@echo off
echo ========================================
echo       Flutter SDK Installer
echo ========================================
echo.

set FLUTTER_PATH=C:\flutter

if exist "%FLUTTER_PATH%" (
    echo Flutter already exists at %FLUTTER_PATH%
    echo.
    goto :add_path
)

echo Downloading Flutter SDK...
echo This will take a few minutes depending on your internet speed.
echo.

powershell -Command "& {Invoke-WebRequest -Uri 'https://storage.googleapis.com/flutter_infra_release/releases/stable/windows/flutter_windows_3.24.5-stable.zip' -OutFile '%TEMP%\flutter.zip'}"

if %errorlevel% neq 0 (
    echo.
    echo Download failed! Please download manually from:
    echo https://docs.flutter.dev/get-started/install/windows
    pause
    exit /b 1
)

echo.
echo Extracting Flutter SDK to C:\flutter...
powershell -Command "& {Expand-Archive -Path '%TEMP%\flutter.zip' -DestinationPath 'C:\' -Force}"

echo.
echo Cleaning up...
del "%TEMP%\flutter.zip"

:add_path
echo.
echo Adding Flutter to PATH...
setx PATH "%PATH%;%FLUTTER_PATH%\bin" /M 2>nul || setx PATH "%PATH%;%FLUTTER_PATH%\bin"

echo.
echo ========================================
echo Flutter installed successfully!
echo.
echo Please restart your terminal and run:
echo   flutter doctor
echo ========================================
echo.
pause
