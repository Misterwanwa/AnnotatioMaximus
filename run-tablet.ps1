<#
.SYNOPSIS
    Baut Annotatio Maximus, installiert die Debug-APK direkt auf dem verbundenen Android-Tablet (USB oder WLAN) und startet die App.
#>
param(
    [switch]$SkipBuild,
    [switch]$BuildOnly,
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Fail([string]$Message) {
    Write-Host "`nFEHLER: $Message" -ForegroundColor Red
    exit 1
}

function Find-JavaHome {
    $candidates = @()

    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        $candidates += $env:JAVA_HOME
    }

    # Gradle-eigene Toolchain (JDK 21) bevorzugen
    $gradleJdkRoot = Join-Path $env:USERPROFILE ".gradle\jdks"
    if (Test-Path $gradleJdkRoot) {
        Get-ChildItem $gradleJdkRoot -Directory | Sort-Object Name -Descending | ForEach-Object {
            if (Test-Path (Join-Path $_.FullName "bin\java.exe")) {
                $candidates += $_.FullName
            }
        }
    }

    # Eclipse Adoptium JDK 17 / 21
    $adoptiumRoot = "C:\Program Files\Eclipse Adoptium"
    if (Test-Path $adoptiumRoot) {
        Get-ChildItem $adoptiumRoot -Directory | Sort-Object Name -Descending | ForEach-Object {
            if (Test-Path (Join-Path $_.FullName "bin\java.exe")) {
                $candidates += $_.FullName
            }
        }
    }

    # Android Studio JBR (nur falls vollständig)
    $asJbr = "C:\Program Files\Android\Android Studio\jbr"
    if ((Test-Path (Join-Path $asJbr "bin\java.exe")) -and (Test-Path (Join-Path $asJbr "lib\jvm.cfg"))) {
        $candidates += $asJbr
    }

    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        $javaExe = Join-Path $candidate "bin\java.exe"
        try {
            $verLine = (& cmd /c "`"$javaExe`" -version 2>&1" | Select-Object -First 1) -as [string]
            if ($verLine -match '"(\d+)') {
                $major = [int]$Matches[1]
                if ($major -ge 17) {
                    return $candidate
                }
            }
        } catch {
            # Nächsten Kandidaten prüfen
        }
    }

    Fail "Kein JDK >= 17 gefunden. Bitte JDK 17 oder 21 installieren oder JAVA_HOME setzen."
}

function Find-SdkDir {
    $localProps = Join-Path $ProjectRoot "local.properties"
    if (Test-Path $localProps) {
        $line = Get-Content $localProps | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($line) {
            $raw = ($line -replace '^sdk\.dir=', '').Trim()
            $unescaped = $raw -replace '\\\\', '\' -replace '\\:', ':'
            if (Test-Path $unescaped) {
                return $unescaped
            }
        }
    }

    foreach ($dir in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA "Android\Sdk"))) {
        if ($dir -and (Test-Path $dir)) {
            return $dir
        }
    }

    return $null
}

function Find-Adb([string]$SdkDir) {
    if ($SdkDir) {
        $sdkAdb = Join-Path $SdkDir "platform-tools\adb.exe"
        if (Test-Path $sdkAdb) { return $sdkAdb }
    }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    Fail "adb.exe nicht gefunden. Bitte Android SDK Platform-Tools installieren."
}

function Find-Gradle {
    $localWrapper = Join-Path $ProjectRoot "gradlew.bat"
    if (Test-Path $localWrapper) { return $localWrapper }

    $distsRoot = Join-Path $env:USERPROFILE ".gradle\wrapper\dists"
    if (Test-Path $distsRoot) {
        $found = Get-ChildItem $distsRoot -Recurse -Filter "gradle.bat" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($found) { return $found.FullName }
    }

    $cmd = Get-Command gradle -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    Fail "Weder gradlew.bat noch eine gecachte Gradle-Installation gefunden."
}

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

if ($BuildOnly) {
    $javaHome = Find-JavaHome
    $env:JAVA_HOME = $javaHome
    $gradle = Find-Gradle

    Write-Step "Baue Debug-APK (JDK: $(Split-Path $javaHome -Leaf))..."
    & $gradle assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { Fail "Gradle assembleDebug fehlgeschlagen (Exit-Code $LASTEXITCODE)." }

    $stopwatch.Stop()
    Write-Host "`nBuild erfolgreich in $([math]::Round($stopwatch.Elapsed.TotalSeconds, 1))s!" -ForegroundColor Green
    exit 0
}

$sdkDir = Find-SdkDir
$adb = Find-Adb $sdkDir

Write-Step "Suche verbundenes Tablet / Gerät..."
$rawDevices = & $adb devices
$readyDevices = @()
$unauthorizedDevices = @()
$offlineDevices = @()

foreach ($line in $rawDevices) {
    if ($line -match '^(\S+)\s+(device|unauthorized|offline)\s*$') {
        $id = $Matches[1]
        $state = $Matches[2]
        if ($state -eq "device") { $readyDevices += $id }
        elseif ($state -eq "unauthorized") { $unauthorizedDevices += $id }
        elseif ($state -eq "offline") { $offlineDevices += $id }
    }
}

if ($readyDevices.Count -eq 0) {
    if ($unauthorizedDevices.Count -gt 0) {
        Fail "Gerät gefunden ($($unauthorizedDevices -join ', ')), aber noch nicht autorisiert. Bitte am Tablet 'USB-Debugging zulassen?' bestätigen."
    }
    if ($offlineDevices.Count -gt 0) {
        Fail "Gerät ist offline ($($offlineDevices -join ', ')). Bitte USB-Kabel kurz neu einstecken oder WLAN-ADB neu verbinden."
    }
    Fail "Kein Android-Gerät gefunden. Bitte Tablet per USB (mit USB-Debugging) oder WLAN-ADB verbinden."
}

$targetSerial = $Serial
if (-not $targetSerial) {
    if ($readyDevices.Count -eq 1) {
        $targetSerial = $readyDevices[0]
    } else {
        Write-Host "Mehrere Geräte gefunden:" -ForegroundColor Yellow
        for ($i = 0; $i -lt $readyDevices.Count; $i++) {
            $model = (& $adb -s $readyDevices[$i] shell getprop ro.product.model 2>$null).Trim()
            Write-Host "  [$($i + 1)] $($readyDevices[$i]) ($model)"
        }
        $choice = Read-Host "Nummer wählen (Enter = 1)"
        $index = if ([string]::IsNullOrWhiteSpace($choice)) { 0 } else { [int]$choice - 1 }
        if ($index -lt 0 -or $index -ge $readyDevices.Count) {
            Fail "Ungültige Auswahl."
        }
        $targetSerial = $readyDevices[$index]
    }
} elseif ($readyDevices -notcontains $targetSerial) {
    Fail "Gewünschtes Gerät '$targetSerial' ist nicht verbunden. Verfügbar: $($readyDevices -join ', ')"
}

$deviceModel = (& $adb -s $targetSerial shell getprop ro.product.model 2>$null).Trim()
Write-Host "Zielgerät: $targetSerial $(if ($deviceModel) { "($deviceModel)" })" -ForegroundColor Green

$env:ANDROID_SERIAL = $targetSerial

if ($SkipBuild) {
    $apkPath = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apkPath)) {
        Fail "Keine fertige APK unter '$apkPath' gefunden. Bitte einmal ohne -SkipBuild ausführen."
    }
    Write-Step "Installiere vorhandene Debug-APK..."
    & $adb -s $targetSerial install -r $apkPath
    if ($LASTEXITCODE -ne 0) { Fail "adb install fehlgeschlagen (Exit-Code $LASTEXITCODE)." }
} else {
    $javaHome = Find-JavaHome
    $env:JAVA_HOME = $javaHome
    $gradle = Find-Gradle

    Write-Step "Baue & installiere Debug-APK (JDK: $(Split-Path $javaHome -Leaf))..."
    & $gradle installDebug --console=plain
    if ($LASTEXITCODE -ne 0) { Fail "Gradle installDebug fehlgeschlagen (Exit-Code $LASTEXITCODE)." }
}

Write-Step "Starte Annotatio Maximus auf dem Gerät..."
& $adb -s $targetSerial shell am start -n "com.annotatio.maximus/.MainActivity"
if ($LASTEXITCODE -ne 0) { Fail "App-Start fehlgeschlagen (Exit-Code $LASTEXITCODE)." }

$stopwatch.Stop()
Write-Host "`nFertig in $([math]::Round($stopwatch.Elapsed.TotalSeconds, 1))s! App läuft auf $targetSerial." -ForegroundColor Green
