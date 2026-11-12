@echo off
rem ---------------------------------------------------------------
rem  Build NameGenerator.exe (Windows, JDK 17+ required)
rem
rem    build.bat            - dist\NameGenerator\NameGenerator.exe
rem    build.bat installer  - also installer dist\NameGenerator-1.0.0.exe
rem                           (requires WiX Toolset 3: https://wixtoolset.org)
rem ---------------------------------------------------------------
setlocal
cd /d "%~dp0"

set APP_NAME=NameGenerator
set APP_VERSION=1.0.0
set MAIN_CLASS=NameGeneratorApp
set JSON_LIB=lib\json-simple-1.1.1.jar

if not exist "src\NameGeneratorApp.java" (
    echo [ERROR] src\NameGeneratorApp.java not found.
    echo If you opened a ZIP archive, extract it first ^(right click - Extract All^).
    goto :fail
)

where jpackage >nul 2>nul
if errorlevel 1 (
    echo [ERROR] jpackage not found.
    echo Install JDK 17 or newer, e.g. https://adoptium.net
    echo During installation enable "Add to PATH", then reopen this window.
    goto :fail
)

echo == Clean ==
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\classes build\jar

echo == Compile ==
javac -encoding UTF-8 -cp "%JSON_LIB%" -d build\classes src\NameGeneratorApp.java
if errorlevel 1 goto :fail

rem Embedded resources: default word list and window icon
copy /y data.json build\classes\ >nul
copy /y packaging\icon.png build\classes\ >nul

echo == Build JAR ==
jar --create --file build\jar\%APP_NAME%.jar --main-class %MAIN_CLASS% -C build\classes .
if errorlevel 1 goto :fail
copy /y "%JSON_LIB%" build\jar\ >nul

echo == Build EXE ==
jpackage --type app-image ^
    --name %APP_NAME% ^
    --app-version %APP_VERSION% ^
    --vendor webkarlik ^
    --input build\jar ^
    --main-jar %APP_NAME%.jar ^
    --main-class %MAIN_CLASS% ^
    --icon packaging\icon.ico ^
    --add-modules java.desktop ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --dest dist
if errorlevel 1 goto :fail

if /i "%~1"=="installer" (
    echo == Build installer ==
    jpackage --type exe ^
        --name %APP_NAME% ^
        --app-version %APP_VERSION% ^
        --vendor webkarlik ^
        --app-image dist\%APP_NAME% ^
        --win-menu --win-shortcut --win-dir-chooser --win-per-user-install ^
        --win-upgrade-uuid 6a1f5c0e-6d0b-4b7b-9a3e-5d0c1f2e7a41 ^
        --dest dist
    if errorlevel 1 goto :fail
)

echo.
echo DONE: %CD%\dist\%APP_NAME%\%APP_NAME%.exe
start "" explorer "dist\%APP_NAME%"
pause
endlocal
exit /b 0

:fail
echo.
echo BUILD FAILED. See the messages above.
pause
endlocal
exit /b 1
