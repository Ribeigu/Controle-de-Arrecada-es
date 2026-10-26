@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
    echo Instale Java 17 ou superior para executar a aplicacao.
    pause
    exit /b 1
)
if not exist "executaveis\API.jar" (
    echo O executavel ainda nao foi gerado. Execute: mvn package
    pause
    exit /b 1
)
java -Dfile.encoding=UTF-8 -jar "executaveis\API.jar"
if errorlevel 1 pause
