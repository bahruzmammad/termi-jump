@echo off
rem Keep commands local to this batch file and avoid echoing each command.
setlocal
rem Run the Maven test suite from this script's directory.
rem %~dp0 expands to this batch file's drive and directory.
cd /d "%~dp0"
rem Return the test command's status to the command prompt.
call mvn -q test
exit /b %errorlevel%
