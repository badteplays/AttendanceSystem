@echo off
echo Generating SSL certificates...
echo.

REM Check if OpenSSL is installed
openssl version >nul 2>&1
if %errorlevel% neq 0 (
    echo OpenSSL is not installed
    echo Please install OpenSSL from https://slproweb.com/products/Win32OpenSSL.html
    pause
    exit /b 1
)

REM Generate private key
echo Generating private key...
openssl genrsa -out key.pem 2048

REM Generate certificate signing request
echo Generating certificate signing request...
openssl req -new -key key.pem -out csr.pem -subj "/CN=localhost"

REM Generate self-signed certificate
echo Generating self-signed certificate...
openssl x509 -req -days 365 -in csr.pem -signkey key.pem -out cert.pem

REM Clean up
del csr.pem

echo.
echo SSL certificates generated successfully!
echo cert.pem and key.pem have been created in the current directory
pause 