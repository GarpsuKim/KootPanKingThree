@echo off
chcp 65001 > nul

set "CURRENT_DIR=%~dp0"
set "ROOT_DIR=%~dp0.."
set "CLASS_NAME=Launcher"
set "CLASS_DIR=%ROOT_DIR%\3D_Class"
set "SOURCE_DIR=%ROOT_DIR%\3D_Source"
set "SOURCE_FILE=%SOURCE_DIR%\%CLASS_NAME%.java"
set "LIB_PATH=%ROOT_DIR%\3D_Lib"
set "ERROR_DIR=%ROOT_DIR%\ERROR_OF_COMPILE"
set "ERROR_FILE=%ERROR_DIR%\ERROR_%CLASS_NAME%.txt"
set "JAVAFX_LIB=%ROOT_DIR%\3D_Lib\javafx-sdk-21.0.10\lib"

if not exist "%ERROR_DIR%" mkdir "%ERROR_DIR%"
if not exist "%CLASS_DIR%"  mkdir "%CLASS_DIR%"
type nul > "%ERROR_FILE%"

echo [RUN] 실행 시작...

cd  %ROOT_DIR%
start " " javaw -Dapplogger.callerTracking=true ^
     --module-path "%JAVAFX_LIB%" ^
     --add-modules javafx.controls,javafx.graphics,javafx.base,javafx.swing,javafx.fxml,javafx.web ^
     -Dprism.order=d3d ^
     -Dprism.verbose=true ^
     --enable-native-access=javafx.graphics ^
     -cp "%CLASS_DIR%;%LIB_PATH%\*;C:\opencv\build\java\opencv-4120.jar" ^
     %CLASS_NAME% AAAAA "%CURRENT_DIR%" %CLASS_NAME% > "%ERROR_DIR%\run_%CLASS_NAME%.txt" 2>&1
timeout /t 5
:end