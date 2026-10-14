@echo off
rem Puts CatMap Web live at https://catmap-dubai.web.app (Firebase Hosting only). Double-click it.
rem Run it again after any change to the website to publish the new version.
cd /d "%~dp0"
call npx.cmd -y firebase-tools@latest login:list | findstr /c:"Logged in as" >nul || call npx.cmd -y firebase-tools@latest login || (pause & exit /b 1)
node tools\publish.mjs
if errorlevel 1 (
  echo.
  echo Publishing failed. Send Claude a screenshot of this window.
)
pause
