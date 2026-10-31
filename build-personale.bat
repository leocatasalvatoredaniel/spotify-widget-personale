@echo off
rem Builds the APK of the widget (see README.md)
setlocal
cd /d "%~dp0"

rem Gradle 8.14 runs on Java 17 to 24, not on Java 25: look for a JDK 17 first,
rem then use the Java of Android Studio only if it is version 17 or 21
set "JDKDIR="
for /d %%D in ("C:\Program Files\Microsoft\jdk-17*" "C:\Program Files\Eclipse Adoptium\jdk-17*" "C:\Program Files\Java\jdk-17*") do set "JDKDIR=%%~D"
set "JBR=C:\Program Files\Android\Android Studio\jbr"
if not defined JDKDIR if exist "%JBR%\release" (
  findstr /r /c:"JAVA_VERSION=.1[7]" /c:"JAVA_VERSION=.2[1]" "%JBR%\release" >nul && set "JDKDIR=%JBR%"
)
if not defined JDKDIR (
  echo Java 17 non trovato. Installalo con: winget install Microsoft.OpenJDK.17
  pause
  exit /b 1
)
echo Java usato: %JDKDIR%
set "JAVA_HOME=%JDKDIR%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem First run: local.properties with the Android SDK and an empty Client ID
if not exist local.properties (
  if not exist "%LOCALAPPDATA%\Android\Sdk" (
    echo Android SDK non trovato: installa Android Studio e aprilo una volta.
    pause
    exit /b 1
  )
  echo sdk.dir=%LOCALAPPDATA:\=/%/Android/Sdk> local.properties
  echo spotify.clientId=>> local.properties
)

rem The Client ID of the Spotify app must be written in local.properties
findstr /r /c:"^spotify.clientId=..*" local.properties >nul
if errorlevel 1 (
  echo.
  echo Manca il Client ID di Spotify.
  echo Si apre local.properties: scrivi il Client ID dopo "spotify.clientId=",
  echo salva, chiudi il Blocco note e avvia di nuovo questo file.
  notepad local.properties
  pause
  exit /b 1
)

call gradlew.bat assembleRelease
if errorlevel 1 (
  echo.
  echo Compilazione non riuscita: cerca "What went wrong" qui sopra.
  pause
  exit /b 1
)

echo.
echo APK pronto: si apre la cartella che lo contiene.
explorer app\build\outputs\apk\release
pause
