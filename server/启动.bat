@echo off
chcp 65001 >nul
setlocal
cd /d %~dp0
title MiraiNikki - Server

echo ============================================================
echo    MiraiNikki  ^|  Computer Side (API + Web)
echo    First time? Read  README-FIRST.txt
echo ============================================================
echo.

where node >nul 2>nul
if errorlevel 1 (
  echo  [ERROR] Node.js not found.
  echo  Install it first:  https://nodejs.org   ^(LTS version^)
  echo  Then double-click this file again.
  echo.
  pause
  exit /b 1
)
for /f "tokens=*" %%v in ('node --version') do echo  Node.js version: %%v
echo.

if not exist node_modules (
  echo  First run: installing dependencies ^(needs internet, ~1 minute^)...
  call npm install --no-audit --no-fund
  echo.
)

echo ============================================================
echo  Starting... when you see "listening" below, it is ready.
echo.
echo  [On this PC]     open in browser:  http://localhost:8787
echo.
echo  [On your phone]  App Settings page -^> Server Address,
echo                   use one of these (phone on the SAME WiFi):
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do (
  for /f "tokens=* delims= " %%b in ("%%a") do echo                      http://%%b:8787
)
echo.
echo  Notes:
echo   - Click "Allow" when Windows Firewall asks.
echo   - Your data lives in  data\  and  uploads\  in this folder.
echo   - Closing this window stops the server (phone data is safe).
echo ============================================================
echo.

start "" http://localhost:8787
call npm start
pause
