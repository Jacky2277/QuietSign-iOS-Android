# 编译 + 打包未签名 IPA
#
# 依赖：
#   一个 iPhoneOS SDK（例如从 Xcode 里拿到的 iPhoneOS*.sdk）
#   LLVM/clang + ld64.lld（Windows 原生版即可）
#
# 用法：
#   $env:QS_TOOLCHAIN = 'D:\path\to\toolchain'   # 内含 LLVM\bin\clang.exe 和 SDK
#   pwsh ./build.ps1
#
# 说明：Windows 上子进程不能直接写某些受保护目录，临时文件统一落在 build\tmp。
param(
    [string]$Toolchain = $env:QS_TOOLCHAIN,
    [string]$SdkName   = 'sdk_fixed2'
)

$ErrorActionPreference = 'Stop'
if (-not $Toolchain) { throw '请先设置 $env:QS_TOOLCHAIN' }

$ROOT  = $PSScriptRoot
$SDK   = Join-Path $Toolchain $SdkName
$CLANG = Join-Path $Toolchain 'LLVM\bin\clang.exe'
$OUT   = Join-Path $ROOT 'build'
$APP   = Join-Path $OUT 'Payload\QuietSign.app'
$SRC   = Join-Path $ROOT 'src'
$TMP   = Join-Path $OUT 'tmp'

if (-not (Test-Path $CLANG)) { throw "找不到 clang: $CLANG" }
if (-not (Test-Path $SDK))   { throw "找不到 SDK: $SDK" }
if (-not (Test-Path (Join-Path $SRC 'main.m'))) {
    Write-Host 'src/main.m 不存在，先运行 tools/unpack.ps1' -ForegroundColor Yellow
    & (Join-Path $ROOT 'tools\unpack.ps1')
}

if (Test-Path $OUT) { Remove-Item $OUT -Recurse -Force }
New-Item -ItemType Directory -Path $APP -Force | Out-Null
New-Item -ItemType Directory -Path $TMP -Force | Out-Null
$env:TMP = $TMP; $env:TEMP = $TMP; $env:TMPDIR = $TMP

Write-Host '[1/4] compile main.m'
$a = @(
    '-target', 'arm64-apple-ios15.0',
    '-isysroot', $SDK,
    '-F', (Join-Path $SDK 'System\Library\SubFrameworks'),
    '-I', (Join-Path $SDK 'System\Library\SubFrameworks'),
    '-F', (Join-Path $SDK 'System\Library\PrivateFrameworks'),
    '-I', (Join-Path $SDK 'System\Library\PrivateFrameworks'),
    '-fobjc-arc', '-fblocks', '-O1', '-fuse-ld=lld',
    '-miphoneos-version-min=15.0',
    '-framework', 'UIKit', '-framework', 'Foundation', '-framework', 'WebKit',
    '-framework', 'CoreImage', '-framework', 'CoreGraphics', '-framework', 'AVFoundation',
    (Join-Path $SRC 'main.m'),
    '-o', (Join-Path $APP 'GDUFQR')
)
Push-Location $TMP
try { & $CLANG @a } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw "clang 编译失败，退出码 $LASTEXITCODE" }

Write-Host '[2/4] bundle'
Copy-Item -LiteralPath (Join-Path $SRC 'Info.plist') $APP -Force
# 图标未入仓：有就带上，没有就跳过
$icons = Get-ChildItem -Path (Join-Path $SRC 'AppIcon*.png') -ErrorAction SilentlyContinue
if ($icons) { $icons | Copy-Item -Destination $APP -Force } else { Write-Host '  (未提供图标，跳过)' }

Write-Host '[3/4] package ipa'
$py = @'
import os, sys, zipfile
out = sys.argv[1]
ipa = os.path.join(out, "QuietSign-unsigned.ipa")
with zipfile.ZipFile(ipa, "w", zipfile.ZIP_DEFLATED) as z:
    for base, _, files in os.walk(os.path.join(out, "Payload")):
        for f in files:
            p = os.path.join(base, f)
            z.write(p, os.path.relpath(p, out).replace("\\", "/"))
with open(os.path.join(out, "Payload", "QuietSign.app", "GDUFQR"), "rb") as f:
    magic = f.read(4)
print("magic", magic.hex(), "OK" if magic == b"\xcf\xfa\xed\xfe" else "BAD")
print("IPA", ipa, os.path.getsize(ipa), "bytes")
'@
$pyFile = Join-Path $TMP 'mk_ipa.py'
Set-Content -LiteralPath $pyFile -Value $py -Encoding UTF8
python $pyFile $OUT

Write-Host '[4/4] done'
Get-ChildItem $APP | Select-Object Name, Length
