@echo off
rem Bootstrap for Gradle on Windows. It does the same job as gradlew: downloads the version named in
rem gradle\wrapper\gradle-wrapper.properties once, checks its SHA-256, then runs it.
setlocal
set "APP_HOME=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%APP_HOME%gradle\wrapper\bootstrap.ps1" %*
exit /b %ERRORLEVEL%
