@echo off
rem Keep commands local to this batch file and avoid echoing each command.
setlocal
rem Build the runnable JAR without running tests.
rem %~dp0 expands to this batch file's drive and directory.
cd /d "%~dp0"
rem Call Maven and stop immediately if packaging fails.
call mvn -q -DskipTests package
if errorlevel 1 exit /b %errorlevel%
rem Confirm where Maven wrote the runnable artifact.
echo Built target\termijump.jar
