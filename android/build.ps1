param(
  [string]$VersionName = "3.0",
  [int]$VersionCode = 30,
  [string]$Label = "静签"
)
$ErrorActionPreference = "Stop"
$proj = "D:\文档\deepseek-harness\default-workspace\gduf-qr\android"
$sdk  = "$env:LOCALAPPDATA\Android\Sdk"
$bt   = Join-Path $sdk "build-tools\37.0.0"
$aj   = Join-Path $sdk "platforms\android-36.1\android.jar"
$zx   = Join-Path $proj "libs\zxing-core-3.5.3.jar"
$aapt2 = Join-Path $bt "aapt2.exe"
$zipalign = Join-Path $bt "zipalign.exe"
$apksig = Join-Path $bt "lib\apksigner.jar"

$work = Join-Path $proj "build"
$gen  = Join-Path $proj "gen"
$cls  = Join-Path $proj "classes"
$dex  = Join-Path $proj "dex"
foreach ($d in @($work,$gen,$cls,$dex)) { if (Test-Path $d) { Remove-Item $d -Recurse -Force }; New-Item -ItemType Directory -Force -Path $d | Out-Null }

Push-Location $proj
try {
  Write-Host "[1/6] aapt2 compile res"
  & $aapt2 compile --dir res -o "$work\res.zip"
  if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

  Write-Host "[2/6] aapt2 link (versionName=$VersionName code=$VersionCode label=$Label)"
  $manifest = Get-Content "$proj\AndroidManifest.xml" -Raw -Encoding UTF8
  $manifest = $manifest -replace 'android:label="[^"]*"', "android:label=`"$Label`""
  Set-Content "$work\AndroidManifest.xml" $manifest -Encoding UTF8
  & $aapt2 link -o "$work\base.apk" -I $aj --manifest "$work\AndroidManifest.xml" -R "$work\res.zip" --auto-add-overlay --java $gen --min-sdk-version 26 --target-sdk-version 34 --version-code $VersionCode --version-name $VersionName
  if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

  Write-Host "[3/6] javac"
  $sources = Get-ChildItem "$proj\src","$gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
  & javac -encoding UTF-8 --release 11 -classpath "$aj;$zx" -d $cls @sources
  if ($LASTEXITCODE -ne 0) { throw "javac failed" }

  Write-Host "[4/6] d8"
  $classes = Get-ChildItem $cls -Recurse -Filter *.class | ForEach-Object { $_.FullName }
  & "$bt\d8.bat" --lib $aj --min-api 26 --output $dex @classes $zx
  if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

  Write-Host "[5/6] package + zipalign"
  Copy-Item "$work\base.apk" "$work\unsigned.apk" -Force
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zip = [System.IO.Compression.ZipFile]::Open("$work\unsigned.apk", 'Update')
  try {
    $dexFile = "$dex\classes.dex"
    if (-not (Test-Path $dexFile)) { throw "classes.dex not produced" }
    $null = [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dexFile, "classes.dex", [System.IO.Compression.CompressionLevel]::Optimal)
    Get-ChildItem $dex -Filter *.dex | Where-Object { $_.Name -ne 'classes.dex' } | ForEach-Object {
      $null = [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $_.Name, [System.IO.Compression.CompressionLevel]::Optimal)
    }
  } finally { $zip.Dispose() }
  & $zipalign -f 4 "$work\unsigned.apk" "$work\aligned.apk"
  if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }
}
finally { Pop-Location }
Write-Host "BUILD OK -> $work\aligned.apk"
