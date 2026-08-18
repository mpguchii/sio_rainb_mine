# Script de Compilação Headless do APK Android (sem Android Studio)
# Instala o Android SDK Command Line Tools em uma pasta local e compila o APK via Gradle.

$ErrorActionPreference = "Stop"

$ScriptDir = $PSScriptRoot
$ProjectDir = Join-Path $ScriptDir "android_naval_monitor"
$SdkDir = Join-Path $ProjectDir ".android_sdk"
$CmdLineToolsZipUrl = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
$GradleZipUrl = "https://services.gradle.org/distributions/gradle-8.4-bin.zip"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "     COMPILADOR HEADLESS DE APK ANDROID (SEM STUDIO)     " -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Configura JAVA_HOME se não estiver definido
if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    $JavaCandidates = Get-ChildItem "C:\Program Files\Java" -Filter "jdk*" -ErrorAction SilentlyContinue
    if ($JavaCandidates) {
        $env:JAVA_HOME = $JavaCandidates[0].FullName
        Write-Host "[JDK] Usando Java JDK em: $env:JAVA_HOME" -ForegroundColor Green
    } else {
        Write-Error "[ERRO] Não foi possível encontrar um JDK do Java instalado em C:\Program Files\Java."
    }
} else {
    Write-Host "[JDK] JAVA_HOME já configurado em: $env:JAVA_HOME" -ForegroundColor Green
}

# 2. Configura ANDROID_HOME
$env:ANDROID_HOME = $SdkDir
$env:ANDROID_SDK_ROOT = $SdkDir
Write-Host "[SDK] Diretório Android SDK: $SdkDir" -ForegroundColor Yellow

# 3. Baixa e Extrai o Android Command Line Tools se necessário
$CmdLineToolsDir = Join-Path $SdkDir "cmdline-tools\latest"
if (-not (Test-Path (Join-Path $CmdLineToolsDir "bin\sdkmanager.bat"))) {
    Write-Host "[DOWNLOAD] Baixando Android Command Line Tools via curl..." -ForegroundColor Yellow
    $ZipPath = Join-Path $ScriptDir "cmdline-tools.zip"
    $TempExtract = Join-Path $ScriptDir "temp_cmdline"
    
    curl.exe -L -o $ZipPath $CmdLineToolsZipUrl
    
    Write-Host "[EXTRAIR] Extraindo Command Line Tools..." -ForegroundColor Yellow
    Expand-Archive -Path $ZipPath -DestinationPath $TempExtract -Force
    
    New-Item -ItemType Directory -Path $CmdLineToolsDir -Force | Out-Null
    Move-Item -Path "$TempExtract\cmdline-tools\*" -Destination $CmdLineToolsDir -Force
    
    Remove-Item $ZipPath -Force -ErrorAction SilentlyContinue
    Remove-Item $TempExtract -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "[SDK] Command Line Tools instalado com sucesso." -ForegroundColor Green
}

# 4. Aceita licenças e instala platforms;android-34 e build-tools;34.0.0
$SdkManager = Join-Path $CmdLineToolsDir "bin\sdkmanager.bat"
Write-Host "[SDK] Aceitando licenças e instalando SDK componentes (android-34)..." -ForegroundColor Yellow

$YesInput = ("y`n" * 30)
$YesInput | & $SdkManager --licenses --sdk_root=$SdkDir | Out-Null
& $SdkManager "platforms;android-34" "build-tools;34.0.0" --sdk_root=$SdkDir

# 5. Baixa o Gradle se não estiver presente
$GradleDir = Join-Path $ScriptDir ".gradle_portable\gradle-8.4"
$GradleExe = Join-Path $GradleDir "bin\gradle.bat"
if (-not (Test-Path $GradleExe)) {
    Write-Host "[DOWNLOAD] Baixando Gradle 8.4 Portátil..." -ForegroundColor Yellow
    $GradleZipPath = Join-Path $ScriptDir "gradle-8.4.zip"
    $GradleExtractDir = Join-Path $ScriptDir ".gradle_portable"
    
    curl.exe -L -o $GradleZipPath $GradleZipUrl
    Expand-Archive -Path $GradleZipPath -DestinationPath $GradleExtractDir -Force
    Remove-Item $GradleZipPath -Force -ErrorAction SilentlyContinue
    Write-Host "[GRADLE] Gradle portátil instalado com sucesso." -ForegroundColor Green
}

# 6. Compila o APK usando o Gradle
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "[BUILD] Iniciando a compilação do APK via Gradle..." -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Cyan

Set-Location $ProjectDir
& $GradleExe assembleDebug --warning-mode none

# 7. Verifica o resultado
$ApkPath = Join-Path $ProjectDir "app\build\outputs\apk\debug\app-debug.apk"
$DistApk = Join-Path $ScriptDir "SurvivorNavalMonitor-debug.apk"

if (Test-Path $ApkPath) {
    Copy-Item -Path $ApkPath -Destination $DistApk -Force
    Write-Host "`n==================================================" -ForegroundColor Green
    Write-Host " 🎉 APK COMPILADO COM SUCESSO! 🎉" -ForegroundColor Green
    Write-Host "==================================================" -ForegroundColor Green
    Write-Host "Arquivo gerado: $DistApk" -ForegroundColor Yellow
    Write-Host "Tamanho       : $([math]::Round((Get-Item $DistApk).Length / 1MB, 2)) MB" -ForegroundColor Yellow
} else {
    Write-Error "[ERRO] A compilação falhou. O arquivo APK não foi encontrado."
}
