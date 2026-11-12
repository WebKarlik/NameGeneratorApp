@echo off
rem ---------------------------------------------------------------
rem  Сборка NameGenerator.exe (Windows, нужен JDK 17 или новее)
rem
rem    build.bat            - папка dist\NameGenerator с NameGenerator.exe
rem    build.bat installer  - дополнительно установщик dist\NameGenerator-1.0.0.exe
rem                           (нужен WiX Toolset 3: https://wixtoolset.org)
rem ---------------------------------------------------------------
setlocal
chcp 65001 >nul
cd /d "%~dp0"

set APP_NAME=NameGenerator
set APP_VERSION=1.0.0
set MAIN_CLASS=NameGeneratorApp
set JSON_LIB=lib\json-simple-1.1.1.jar

where jpackage >nul 2>nul
if errorlevel 1 (
    echo [ОШИБКА] jpackage не найден. Установите JDK 17+ и добавьте его папку bin в PATH.
    exit /b 1
)

echo == Очистка ==
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\classes build\jar

echo == Компиляция ==
javac -encoding UTF-8 -cp "%JSON_LIB%" -d build\classes src\NameGeneratorApp.java
if errorlevel 1 exit /b 1

rem Встроенные ресурсы: стандартный набор слов и иконка окна
copy /y data.json build\classes\ >nul
copy /y packaging\icon.png build\classes\ >nul

echo == Сборка JAR ==
jar --create --file build\jar\%APP_NAME%.jar --main-class %MAIN_CLASS% -C build\classes .
if errorlevel 1 exit /b 1
copy /y "%JSON_LIB%" build\jar\ >nul

echo == Сборка EXE ==
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
if errorlevel 1 exit /b 1

if /i "%~1"=="installer" (
    echo == Сборка установщика ==
    jpackage --type exe ^
        --name %APP_NAME% ^
        --app-version %APP_VERSION% ^
        --vendor webkarlik ^
        --app-image dist\%APP_NAME% ^
        --win-menu --win-shortcut --win-dir-chooser --win-per-user-install ^
        --win-upgrade-uuid 6a1f5c0e-6d0b-4b7b-9a3e-5d0c1f2e7a41 ^
        --dest dist
    if errorlevel 1 exit /b 1
)

echo.
echo Готово: dist\%APP_NAME%\%APP_NAME%.exe
endlocal
