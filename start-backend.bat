@echo off
set JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.5\jbr
cd /d "%~dp0backend"
call "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.5\plugins\maven\lib\maven3\bin\mvn.cmd" spring-boot:run
