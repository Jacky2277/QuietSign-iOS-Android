# 还原 src/main.m.gz.b64 -> src/main.m（字节精确）
# 用法: pwsh ./tools/unpack.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$src = Join-Path $root 'src'
$b64File = Join-Path $src 'main.m.gz.b64'
$outFile = Join-Path $src 'main.m'

if (-not (Test-Path $b64File)) { throw "找不到 $b64File" }

# 去掉所有空白再解码（仓库里的 base64 是 76 列折行的）
$b64 = (Get-Content -LiteralPath $b64File -Raw) -replace "\s", ""
$gzBytes = [Convert]::FromBase64String($b64)

$inMs = [System.IO.MemoryStream]::new($gzBytes)
$gz = [System.IO.Compression.GZipStream]::new($inMs, [System.IO.Compression.CompressionMode]::Decompress)
$outMs = [System.IO.MemoryStream]::new()
$gz.CopyTo($outMs)
$gz.Dispose()
$raw = $outMs.ToArray()
[System.IO.File]::WriteAllBytes($outFile, $raw)

$sha = (Get-FileHash -LiteralPath $outFile -Algorithm SHA256).Hash
$expect = ((Get-Content -LiteralPath (Join-Path $src 'main.m.sha256') -Raw).Trim())
Write-Host "main.m 已还原: $outFile"
Write-Host "SHA256 = $sha"
if ($sha -ne $expect) {
    Write-Host "✗ 校验失败，期望 $expect" -ForegroundColor Red
    exit 1
}
Write-Host "✓ SHA256 校验通过" -ForegroundColor Green
