@echo off
setlocal
cd /d "%~dp0"
call mvn -q test
exit /b %errorlevel%
