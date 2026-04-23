rem   rename.bat
@echo off

rem chcp 65001 > nul
chcp 949 > nul

cd /d "%~dp0"
for  %%z  in   ( *.java ) do echo  %%z
pause

for %%z in (*.java) do (
	echo  %%z
	rename "%%z" "영어전환1차_%%z"
	rem timeout /t 1  /nobreak >nul
)
pause