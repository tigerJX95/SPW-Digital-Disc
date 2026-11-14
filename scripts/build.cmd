@echo off
setlocal

if "%~1"=="" (
 echo Usage: build.cmd "C:\path\to\SPW\app\ffmpeg-x64.dll"
 exit /b 2
)
if not defined JAVA_HOME (
 echo Set JAVA_HOME to the existing JDK 25 folder.
 exit /b 2
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
call gradle -p "%~dp0.." --no-daemon "-PsaltPlayerArchive=%~f1" plugin
exit /b %errorlevel%
