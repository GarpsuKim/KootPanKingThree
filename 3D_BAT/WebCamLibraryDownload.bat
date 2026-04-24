
@echo off
rem  *******************************
rem  WebCamLibraryDownload.BAT
rem  *******************************

# Windows PowerShell 또는 Git Bash에서 실행
# libs 폴더에 한번에 받기

curl -L -o webcam-capture-0.3.12.jar "https://repo1.maven.org/maven2/com/github/sarxos/webcam-capture/0.3.12/webcam-capture-0.3.12.jar"

curl -L -o bridj-0.7.0.jar "https://repo1.maven.org/maven2/com/nativelibs4java/bridj/0.7.0/bridj-0.7.0.jar"

curl -L -o slf4j-api-1.7.36.jar "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar"

curl -L -o slf4j-simple-1.7.36.jar "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/1.7.36/slf4j-simple-1.7.36.jar"

pause
exit