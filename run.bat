@echo off
setlocal
cd /d "%~dp0"
call mvn -q -DskipTests package
if errorlevel 1 exit /b %errorlevel%
java -jar "target\termijump.jar"
exit /b %errorlevel%
