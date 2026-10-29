@echo off
rem Keep commands local to this batch file and avoid echoing each command.
setlocal
rem Build and launch Termi-Jump from this script's directory.
rem %~dp0 expands to this batch file's drive and directory.
cd /d "%~dp0"
rem Build first; do not launch an old JAR if the build fails.
call mvn -q -DskipTests package
if errorlevel 1 exit /b %errorlevel%
rem Start the application with the generated JAR.
java -jar "target\termijump.jar"
rem Return Java's exit status to the command prompt.
exit /b %errorlevel%
