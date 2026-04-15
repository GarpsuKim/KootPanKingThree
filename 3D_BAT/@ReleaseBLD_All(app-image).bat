@echo off
chcp 65001 > nul
setlocal EnableExtensions EnableDelayedExpansion

set "PROJECT=KootPanKingThree"
set "VERSION=1.0.0"
set "MAIN_CLASS=KootPanKingThreeLaunch"

echo ============================================
echo   BUILD SCRIPT - KootPanKingThree
echo ============================================

cd /d "%~dp0"
cd ..

set "ROOT_DIR=C:\Users\garpsu\KootPanKing_3D_Proj"
set "SOURCE_DIR=%ROOT_DIR%\3D_Source"
set "LIB_PATH=%ROOT_DIR%\3D_Lib"
set "BUILD_DIR=%ROOT_DIR%\build"
set "DIST_DIR=%ROOT_DIR%\3D_Dist"
set "INPUT_DIR=%ROOT_DIR%\input_dir"
set "TEMP_DIR=%ROOT_DIR%\@Temp"
set "ERROR_DIR=%ROOT_DIR%\ERROR_OF_COMPILE"
set "LOG_FILE=%ERROR_DIR%\build_log.txt"
set "JPACKAGE_LOG=%ERROR_DIR%\jpackage_Out.txt"
set "JarFile=%PROJECT%.jar"
set "ICO_PATH=%ROOT_DIR%\%PROJECT%.ico"
set "JAVAFX_LIB=%ROOT_DIR%\3D_Lib\javafx-sdk-21.0.10\lib"
set "JAVAFX_BIN=%ROOT_DIR%\3D_Lib\javafx-sdk-21.0.10\bin"
set "FX_DEST=%DIST_DIR%\%PROJECT%\app\javafx"

if not exist "%SOURCE_DIR%" ( echo [ERROR] %SOURCE_DIR%  & pause & exit /b 1 )
if not exist "%LIB_PATH%"   ( echo [ERROR] %LIB_PATH%    & pause & exit /b 1 )
if not exist "%JAVAFX_LIB%" ( echo [ERROR] %JAVAFX_LIB%  & pause & exit /b 1 )

REM Verify prism DLLs exist in bin\
if not exist "%JAVAFX_BIN%\prism_d3d.dll" (
    echo [ERROR] prism_d3d.dll not found in: %JAVAFX_BIN%
    pause & exit /b 1
)
echo JavaFX DLLs confirmed in: %JAVAFX_BIN%

echo [1] Preparing directories...
if exist "%BUILD_DIR%"  rmdir /s /q "%BUILD_DIR%"
if exist "%INPUT_DIR%"  rmdir /s /q "%INPUT_DIR%"
if exist "%DIST_DIR%"   rmdir /s /q "%DIST_DIR%"
if exist "%TEMP_DIR%"   rmdir /s /q "%TEMP_DIR%"
mkdir "%BUILD_DIR%" & mkdir "%DIST_DIR%" & mkdir "%INPUT_DIR%" & mkdir "%TEMP_DIR%"
if not exist "%ERROR_DIR%" mkdir "%ERROR_DIR%"
if exist "%LOG_FILE%"     del /q "%LOG_FILE%"
if exist "%JPACKAGE_LOG%" del /q "%JPACKAGE_LOG%"
echo     Done.

echo [2] Compiling...
cd /d "%SOURCE_DIR%"
dir /b *.java > "%TEMP_DIR%\java_files.txt"
type "%TEMP_DIR%\java_files.txt"

javac -encoding UTF-8 ^
    --module-path "%JAVAFX_LIB%" ^
    --add-modules javafx.controls,javafx.graphics,javafx.base,javafx.swing,javafx.fxml,javafx.web,javafx.media ^
    -cp "%LIB_PATH%\*;%JAVAFX_LIB%\*" ^
    -d "%BUILD_DIR%" ^
    "@%TEMP_DIR%\java_files.txt" 2> "%LOG_FILE%"

if errorlevel 1 ( echo [ERROR] Compile failed & type "%LOG_FILE%" & pause & exit /b 1 )
del "%TEMP_DIR%\java_files.txt"
echo     Done.

echo [3] Creating JAR...
(
    echo Main-Class: %MAIN_CLASS%
    echo.
) > "%BUILD_DIR%\manifest.txt"

for %%F in ("%LIB_PATH%\*.jar") do copy /Y "%%F" "%INPUT_DIR%\" >nul

jar cfm "%INPUT_DIR%\%JarFile%" "%BUILD_DIR%\manifest.txt" -C "%BUILD_DIR%" . >> "%LOG_FILE%" 2>&1
if errorlevel 1 ( echo [ERROR] JAR failed & type "%LOG_FILE%" & pause & exit /b 1 )
jar tf "%INPUT_DIR%\%JarFile%" | findstr "KootPanKingThreeLaunch.class" >nul
if errorlevel 1 ( echo [ERROR] KootPanKingThreeLaunch.class not in JAR & pause & exit /b 1 )
echo     JAR OK.

echo [4] Running jpackage...
set "JP_ICON="
if exist "%ICO_PATH%" set "JP_ICON=--icon ""%ICO_PATH%"""

jpackage --verbose ^
    --name        %PROJECT%     ^
    --type        app-image     ^
    --app-version %VERSION%     ^
    --input       "%INPUT_DIR%" ^
    --dest        "%DIST_DIR%"  ^
    --main-jar    %JarFile%     ^
    --main-class  %MAIN_CLASS%  ^
    --java-options "--module-path $APPDIR/javafx" ^
    --java-options "--add-modules javafx.controls,javafx.graphics,javafx.base,javafx.swing,javafx.fxml,javafx.web,javafx.media" ^
    --java-options "-Djava.library.path=$APPDIR/javafx" ^
    --vendor      %PROJECT%     ^
    --description "KootPanKingThree Application" ^
    %JP_ICON% > "%JPACKAGE_LOG%" 2>&1

if errorlevel 1 ( echo [ERROR] jpackage failed & type "%JPACKAGE_LOG%" & pause & exit /b 1 )
echo     Done.

REM  JAR from lib\, DLL from bin\  -> both go to app\javafx\
echo [5] Copying JavaFX JARs (lib) and DLLs (bin) to app\javafx\...
mkdir "%FX_DEST%"
copy /Y "%JAVAFX_LIB%\*.jar" "%FX_DEST%\" >nul
copy /Y "%JAVAFX_BIN%\*.dll" "%FX_DEST%\"
if errorlevel 1 ( echo [ERROR] DLL copy failed & pause & exit /b 1 )
echo     Done.

echo [6] Cleaning up...
if exist "%INPUT_DIR%"  rmdir /s /q "%INPUT_DIR%"
if exist "%BUILD_DIR%"  rmdir /s /q "%BUILD_DIR%"
if exist "%TEMP_DIR%"   rmdir /s /q "%TEMP_DIR%"

echo ============================================
echo   BUILD COMPLETE: %DIST_DIR%\%PROJECT%
echo ============================================
pause
exit /b 0
