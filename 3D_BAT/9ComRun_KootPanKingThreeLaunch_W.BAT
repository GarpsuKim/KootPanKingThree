rem  @echo off
chcp 65001 > nul

rem cd C:\Users\garpsu\KootPanKing_3D_Proj\3D_BAT
rem set "CURRENT_DIR=%~dp0"
rem set "ROOT_DIR=%~dp0.."

cd C:\Users\garpsu\KootPanKing_3D_Proj\3D_BAT
set "CURRENT_DIR=C:\Users\garpsu\KootPanKing_3D_Proj\3D_BAT"
set "ROOT_DIR=C:\Users\garpsu\KootPanKing_3D_Proj"

set "CLASS_NAME=KootPanKingThreeLaunch"
set "CLASS_DIR=%ROOT_DIR%\3D_Class"
set "SOURCE_DIR=%ROOT_DIR%\3D_Source"
set "SOURCE_FILE=%SOURCE_DIR%\%CLASS_NAME%.java"
set "LIB_PATH=%ROOT_DIR%\3D_Lib"
set "ERROR_DIR=%ROOT_DIR%\ERROR_OF_COMPILE"
set "ERROR_FILE=%ERROR_DIR%\ERROR_%CLASS_NAME%.txt"
set "JAVAFX_LIB=%ROOT_DIR%\3D_Lib\javafx-sdk-21.0.10\lib"

@echo off

if not exist "%ERROR_DIR%" mkdir "%ERROR_DIR%"
if not exist "%CLASS_DIR%"  mkdir "%CLASS_DIR%"

type nul > "%ERROR_FILE%"

javac -d "%CLASS_DIR%" ^
      --module-path "%JAVAFX_LIB%" ^
      --add-modules javafx.controls,javafx.graphics,javafx.base,javafx.swing,javafx.fxml,javafx.web ^
      --class-path "%CLASS_DIR%;%LIB_PATH%\*;C:\opencv\build\java\opencv-4120.jar" ^
      -encoding UTF-8 ^
      -sourcepath "%SOURCE_DIR%" ^
      "%SOURCE_FILE%" 2> "%ERROR_FILE%"

if errorlevel 1 (
    echo [ERROR] 컴파일 실패
    type "%ERROR_FILE%"
    pause
    start "" "C:\Program Files\Notepad++\notepad++.exe" "%ERROR_FILE%"
    goto :end
)
rem  timeout /t 35 /nobreak >nul
for %%A in ("%ERROR_FILE%") do set FILE_SIZE=%%~zA
if %FILE_SIZE% gtr 0 (
    type "%ERROR_FILE%"
    echo [WARNING] 경고 있음
    pause
)

echo [OK] 컴파일 성공!

echo [RUN] 실행 시작...

start " " javaw -Dapplogger.callerTracking=true ^
     --module-path "%JAVAFX_LIB%" ^
     --add-modules javafx.controls,javafx.graphics,javafx.base,javafx.swing,javafx.fxml,javafx.web ^
     -Dprism.order=d3d ^
     -Dprism.verbose=true ^
     --enable-native-access=javafx.graphics ^
     -cp "%CLASS_DIR%;%LIB_PATH%\*" ^
     %CLASS_NAME% AAAAA "%CURRENT_DIR%" %CLASS_NAME% > "%ERROR_DIR%\run_%CLASS_NAME%.txt" 2>&1

echo.
echo [RUN] 종료 코드: %ERRORLEVEL%
echo [RUN] 실행 로그: %ERROR_DIR%\run_%CLASS_NAME%.txt

:end
exit




