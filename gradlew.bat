@echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.

if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

:findJavaFromJavaHome
set "JAVA_HOME_CLEAN=%JAVA_HOME:"=%"
set "JAVA_EXE=%JAVA_HOME_CLEAN%\bin\java.exe"

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
goto fail

:execute
set "CLASSPATH=%DIRNAME%\gradle\wrapper\gradle-wrapper.jar"

"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

if %ERRORLEVEL% equ 0 goto mainEnd

:fail
exit /b 1

:mainEnd
endlocal
