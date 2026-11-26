@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
title Crear base.apk (plantilla de APK Forge)

rem ---- Rutas (cambialas aqui si mueves algo) ----
set "ROOT=%~dp0"
set "SDK=D:\Documentos\_Android\_AndroidStudioSDK"
set "BT=%SDK%\build-tools\34.0.0"
set "AJ=%SDK%\platforms\android-33\android.jar"
set "JH=C:\Users\dskmu\.jdks\jbr-17.0.14"
if not exist "%JH%\bin\javac.exe" set "JH=%JAVA_HOME%"
set "TPL=%ROOT%plantilla\template"
set "TMPB=%ROOT%plantilla\build_tmp"
set "DEST=%ROOT%app\src\main\assets\base.apk"
set "MSG="

echo ============================================================
echo  CREAR base.apk  (plantilla WebView de APK Forge)
echo ============================================================
echo  Fuentes : %TPL%
echo  SDK     : %SDK%
echo  JDK     : %JH%
echo  Destino : %DEST%
echo.

echo [0/6] Comprobando herramientas...
if not exist "%JH%\bin\javac.exe" ( set "MSG=No encuentro javac (JDK). Revisa JH en este .bat." & goto :fail )
if not exist "%BT%\aapt.exe" ( set "MSG=No encuentro aapt.exe en %BT%" & goto :fail )
if not exist "%BT%\d8.bat" ( set "MSG=No encuentro d8.bat en %BT%" & goto :fail )
if not exist "%AJ%" ( set "MSG=No encuentro android.jar en %AJ%" & goto :fail )
if not exist "%TPL%\src" ( set "MSG=No encuentro %TPL%\src" & goto :fail )
set "JAVA_HOME=%JH%"
set "PATH=%JH%\bin;%PATH%"
echo      OK
echo.

echo [1/6] Limpiando carpeta temporal...
if exist "%TMPB%" rmdir /s /q "%TMPB%"
mkdir "%TMPB%\classes"
echo      %TMPB%
echo.

echo [2/6] Compilando Java (javac)...
if exist "%TMPB%\src.txt" del "%TMPB%\src.txt"
for /r "%TPL%\src" %%f in (*.java) do (
  set "p=%%f"
  echo "!p:\=/!" >> "%TMPB%\src.txt"
  echo      + %%~nxf
)
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "%AJ%" -d "%TMPB%\classes" @"%TMPB%\src.txt"
if errorlevel 1 ( set "MSG=Fallo al compilar el Java (ver arriba)." & goto :fail )
echo      OK
echo.

echo [3/6] Convirtiendo a DEX (d8)...
jar cf "%TMPB%\classes.jar" -C "%TMPB%\classes" .
if errorlevel 1 ( set "MSG=Fallo al empaquetar las clases." & goto :fail )
call "%BT%\d8.bat" --min-api 21 --lib "%AJ%" --output "%TMPB%" "%TMPB%\classes.jar"
if errorlevel 1 ( set "MSG=Fallo en d8 (ver arriba)." & goto :fail )
if not exist "%TMPB%\classes.dex" ( set "MSG=d8 no genero classes.dex." & goto :fail )
echo      classes.dex generado
echo.

echo [4/6] Empaquetando recursos y manifiesto (aapt)...
"%BT%\aapt.exe" package -f -M "%TPL%\AndroidManifest.xml" -S "%TPL%\res" -A "%TPL%\assets" -I "%AJ%" -F "%TMPB%\base.apk"
if errorlevel 1 ( set "MSG=Fallo en aapt package (ver arriba)." & goto :fail )
pushd "%TMPB%"
"%BT%\aapt.exe" add base.apk classes.dex
set "E=!errorlevel!"
popd
if not "!E!"=="0" ( set "MSG=Fallo al anadir classes.dex al APK." & goto :fail )
echo      base.apk generado en %TMPB%\base.apk
echo.

echo [5/6] Comprobando marcadores en el APK...
powershell -NoProfile -Command "Add-Type -A System.IO.Compression.FileSystem; $z=[IO.Compression.ZipFile]::OpenRead('%TMPB%\base.apk'); $e=$z.GetEntry('AndroidManifest.xml'); $m=New-Object IO.MemoryStream; $s=$e.Open(); $s.CopyTo($m); $z.Dispose(); $t=[Text.Encoding]::Unicode.GetString($m.ToArray()); $bad=0; foreach($k in '__APPNAME__','__VERSIONNAME__','com.dskmusic.template'){ if($t.Contains($k)){Write-Host ('     OK  '+$k)}else{Write-Host ('     FALTA '+$k); $bad=1} }; exit $bad"
if errorlevel 1 ( set "MSG=Al APK le faltan marcadores; no se copia." & goto :fail )
echo.

echo [6/6] Copiando al proyecto principal...
copy /y "%TMPB%\base.apk" "%DEST%" >nul
if errorlevel 1 ( set "MSG=No se pudo copiar a %DEST%" & goto :fail )
for %%a in ("%DEST%") do set "SZ=%%~za"
echo      Copiado a: %DEST%
echo      Tamano   : !SZ! bytes
rmdir /s /q "%TMPB%"
echo.
echo ============================================================
echo  LISTO. Ahora compila APK Forge en Android Studio.
echo ============================================================
set "MSG=base.apk generado y copiado a: %DEST%  -  Ahora compila APK Forge en Android Studio."
goto :end

:fail
echo.
echo ************************************************************
echo  ERROR: !MSG!
echo ************************************************************
set "MSG=ERROR: !MSG!"

:end
if /i "%~1"=="nopause" exit /b 0
powershell -NoProfile -Command "Add-Type -A PresentationFramework; [void][Windows.MessageBox]::Show(($env:MSG -replace '\^',''), 'Crear base.apk')"
exit /b 0
