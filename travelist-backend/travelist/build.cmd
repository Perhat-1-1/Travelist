@echo off
rem ============================================================
rem  Travelist backend build entry (recommended over mvnw.cmd)
rem
rem  Purpose: pin JAVA_HOME to an installed JDK 26 (checks
rem  ~/.jdks/openjdk-26.0.2.1, then openjdk-26.0.1, then
rem  C:\Program Files\Eclipse Adoptium\jdk-26*) so the build does
rem  not fail with "release version 26 not supported" when the
rem  system JAVA_HOME points to an older JDK (21/25).
rem
rem  Usage (run from travelist-backend\travelist):
rem    .\build.cmd -s ..\maven-proxy-settings.xml -DskipTests package
rem    .\build.cmd -s ..\maven-proxy-settings.xml spring-boot:run
rem  All arguments are passed through to mvnw.cmd.
rem ============================================================
setlocal

if exist "%USERPROFILE%\.jdks\openjdk-26.0.2.1\bin\javac.exe" (
  set "JAVA_HOME=%USERPROFILE%\.jdks\openjdk-26.0.2.1"
) else if exist "%USERPROFILE%\.jdks\openjdk-26.0.1\bin\javac.exe" (
  set "JAVA_HOME=%USERPROFILE%\.jdks\openjdk-26.0.1"
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-26*\bin\javac.exe" (
  for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-26*") do set "JAVA_HOME=%%~fd"
)

echo [build.cmd] JAVA_HOME=%JAVA_HOME%
call "%~dp0mvnw.cmd" %*
exit /b %ERRORLEVEL%
