@echo off
setlocal EnableExtensions
set "BASE_DIR=%~dp0"
set "PROPS=%BASE_DIR%.mvn\wrapper\maven-wrapper.properties"
if not exist "%PROPS%" (
  echo Missing %PROPS% 1>&2
  exit /b 1
)
for /f "tokens=1,* delims==" %%A in ('findstr /b "distributionUrl=" "%PROPS%"') do set "DIST_URL=%%B"
if "%DIST_URL%"=="" (
  echo Missing distributionUrl in %PROPS% 1>&2
  exit /b 1
)
set "DIST_NAME=apache-maven-3.9.16"
if "%MAVEN_USER_HOME%"=="" set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "MAVEN_HOME=%MAVEN_USER_HOME%\wrapper\dists\%DIST_NAME%"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $u='%DIST_URL%'; $t=Join-Path $env:TEMP ('mvnw-'+[guid]::NewGuid()); New-Item -ItemType Directory -Path $t | Out-Null; $z=Join-Path $t 'maven.zip'; Invoke-WebRequest -UseBasicParsing $u -OutFile $z; Expand-Archive -Path $z -DestinationPath $t; $target='%MAVEN_HOME%'; New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null; if (-not (Test-Path $target)) { Move-Item (Join-Path $t '%DIST_NAME%') $target }; Remove-Item -Recurse -Force $t"
  if errorlevel 1 exit /b 1
)
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
