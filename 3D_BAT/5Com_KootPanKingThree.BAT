@echo off
chcp 65001 > nul

set "ROOT_DIR=%~dp0.."
set "CLASS_NAME=KootPanKingThree"
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

javac -d "%CLASS_DIR%" ^
      --module-path "%JAVAFX_LIB%" ^
      --add-modules javafx.controls,javafx.graphics,javafx.base,javafx.swing,javafx.fxml ^
      --class-path "%CLASS_DIR%;%LIB_PATH%\*" ^
      -encoding UTF-8 ^
      -sourcepath "%SOURCE_DIR%" ^
      "%SOURCE_FILE%"      -Xlint    2> "%ERROR_FILE%"

if errorlevel 1 (
    echo [ERROR] 컴파일 실패
    type "%ERROR_FILE%"
    pause
    start "" "C:\Program Files\Notepad++\notepad++.exe" "%ERROR_FILE%"
    goto :end
)

for %%A in ("%ERROR_FILE%") do set FILE_SIZE=%%~zA
if %FILE_SIZE% gtr 0 (
    type "%ERROR_FILE%"
    echo [WARNING] 경고 있음
    pause
)

echo [OK] 컴파일 성공!
:end




