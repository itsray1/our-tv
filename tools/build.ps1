# Builds app/ into a signed APK without Gradle. Usage: build.ps1 -work <work dir> -ks <keystore> [-ksPass <password>]
param([string]$work, [string]$ks, [string]$ksPass = 'android')
$ErrorActionPreference = 'Stop'
$p = Split-Path $PSScriptRoot -Parent
$n = "$p\app"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"; $bt = "$sdk\build-tools\35.0.0"; $jar = "$sdk\platforms\android-36\android.jar"
$jbin = 'C:\Program Files\Android\Android Studio\jbr\bin'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$jbin;$env:Path"
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Item -ItemType Directory -Force "$work\classes", "$work\dex" | Out-Null
Copy-Item "$n\res" "$work\res" -Recurse
Copy-Item "$n\assets" "$work\assets" -Recurse
# app/private (never committed) holds a family's own name, banner, family.json and avatars; it is laid over the public files.
if (Test-Path "$n\private\res") { Copy-Item "$n\private\res\*" "$work\res" -Recurse -Force }
if (Test-Path "$n\private\assets") { Copy-Item "$n\private\assets\*" "$work\assets" -Recurse -Force }
# family.json may keep an older app id, so an installed TV updates in place instead of starting over.
$rename = @()
if (Test-Path "$work\assets\family.json") {
    $appId = (Get-Content "$work\assets\family.json" -Raw -Encoding UTF8 | ConvertFrom-Json).app_id
    if ($appId) { $rename = @('--rename-manifest-package', $appId) }
}
& "$bt\aapt2.exe" compile -o "$work\res.zip" --dir "$work\res"
& "$bt\aapt2.exe" link -o "$work\base.apk" -I $jar --manifest "$n\AndroidManifest.xml" -A "$work\assets" --no-compress-regex "\.(png|ttf|wav)$" @rename "$work\res.zip"
$src = Get-ChildItem "$n\src" -Recurse -Filter *.java | ForEach-Object FullName
$ErrorActionPreference = 'Continue'
$libs = Get-ChildItem "$n\libs" -Filter *.jar | ForEach-Object FullName
$out = & javac -nowarn -encoding UTF-8 -source 8 -target 8 -classpath ((@($jar) + $libs) -join ';') -d "$work\classes" $src 2>&1
if ($LASTEXITCODE -ne 0) { $out | ForEach-Object { "$_" }; throw 'javac failed' }
$ErrorActionPreference = 'Stop'
$cls = Get-ChildItem "$work\classes" -Recurse -Filter *.class | ForEach-Object FullName
& "$bt\d8.bat" --min-api 26 --lib $jar --output "$work\dex" $cls $libs
Push-Location "$work\dex"
py -c "import zipfile,sys;z=zipfile.ZipFile(sys.argv[1],'a');z.write('classes.dex');z.close()" "$work\base.apk"
Pop-Location
& "$bt\zipalign.exe" -f 4 "$work\base.apk" "$work\aligned.apk"
& "$bt\apksigner.bat" sign --ks $ks --ks-pass "pass:$ksPass" --key-pass "pass:$ksPass" --out "$work\ourtv.apk" "$work\aligned.apk"
(Get-Item "$work\ourtv.apk").Length
