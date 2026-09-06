@echo off
REM H2S Dosimeter Backend - Start Script
REM Runs the Spring Boot JAR directly (more stable than bootRun)
REM Database credentials are passed as system properties (not env vars, avoids shell escaping issues)

set JAR=build\libs\h2s-dosimeter-backend-1.0.0.jar
set JAVA="C:\Program Files\Java\jdk-21\bin\java.exe"

echo Starting H2S Dosimeter Backend...
echo Server will be available at: http://localhost:8080/api
echo Health check: http://localhost:8080/api/health
echo.

%JAVA% ^
  -Dspring.profiles.active=local ^
  -Dspring.datasource.url="jdbc:mysql://localhost:3306/h2s_dosimeter?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" ^
  -Dspring.datasource.username=h2s_user ^
  -Dspring.datasource.password=H2sPass2026 ^
  -jar %JAR%
