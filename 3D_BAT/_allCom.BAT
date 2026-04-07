@echo off
chcp 65001 > nul
set "ROOT_DIR=%~dp0.."
set "CLASS_DIR=%ROOT_DIR%\3D_Class"
set "BAT_DIR=%ROOT_DIR%\3D_BAT"
del  %CLASS_DIR%\*.CLASS
rem CALL  %BAT_DIR%\0Com_AlarmController.BAT
CALL  %BAT_DIR%\0Com_AppLogger.BAT
CALL  %BAT_DIR%\0Com_AppRestarter.BAT
CALL  %BAT_DIR%\0Com_CaptureManager.BAT
CALL  %BAT_DIR%\0Com_ChimeController.BAT
CALL  %BAT_DIR%\0Com_GmailSender.BAT
CALL  %BAT_DIR%\0Com_GoogleCalendarService.BAT
CALL  %BAT_DIR%\0Com_IniController.BAT
CALL  %BAT_DIR%\0Com_ItsCctvManager.BAT
CALL  %BAT_DIR%\0Com_Kakao.BAT
CALL  %BAT_DIR%\0Com_NaverCalendarService.BAT
CALL  %BAT_DIR%\0Com_TelegramBot.BAT
CALL  %BAT_DIR%\5Com_FxGPUNeon.BAT
CALL  %BAT_DIR%\0Com_BackgroundPlayer.BAT
CALL  %BAT_DIR%\0Com_FxSplashWindow.BAT
CALL  %BAT_DIR%\5Com_KootPanKingThree.BAT
ECHO  ALL  COMPLETED
