@echo off
rem ============================================================
rem  Travelist local-profile launcher
rem
rem  Purpose: start the backend with the "local" Spring profile
rem  (local MySQL at 127.0.0.1:3306/travelist_dev, see
rem  src/main/resources/application-local.yaml).
rem
rem  It sets SPRING_PROFILES_ACTIVE=local (standard Spring env
rem  var) instead of passing -Dspring-boot.run.profiles=local on
rem  the command line. This avoids a Windows PowerShell 5.1
rem  native-argument bug that splits dotted -D arguments
rem  (-Dspring-boot.run.profiles=local becomes "-Dspring-boot"
rem  and ".run.profiles=local"), which makes Maven fail with
rem  "Unknown lifecycle phase .run.profiles=local".
rem
rem  Usage (run from travelist-backend\travelist):
rem    .\run-local.cmd -s ..\maven-proxy-settings.xml spring-boot:run
rem  All arguments are passed through to build.cmd/mvnw.cmd.
rem ============================================================
setlocal
set "SPRING_PROFILES_ACTIVE=local"
call "%~dp0build.cmd" %*
exit /b %ERRORLEVEL%
