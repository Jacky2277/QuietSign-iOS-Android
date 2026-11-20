# 静签 (QuietSign-iOS)

广东金融学院「易广金」签到客户端。Objective-C + UIKit 单文件实现，在 Windows 上用 clang 交叉编译成 arm64 未签名 IPA。

> 私有仓库，仅供本人学习与自用。

## 功能

- 用易班 `loginToken` 登录，服务端会话由 App 持有
- 拉今日课表，列出每节课的**签到窗口**（上课前 `startSignMin` 分钟 → `stopScanTime`）
- **批量签到**：勾选今日指定课程后一次提交；没有任何“进页面就自动签”的行为
- **仅在签到窗口内提交**开关：未到开窗时间的课先挂起，到点自动提交；已过截止直接跳过
- 扫码签到兜底（老师发码时仍可用）

## 构建

不依赖 Xcode，只依赖本地工具链（clang 18+ 与 iPhoneOS SDK）：

```powershell
# 1. 还原源码
pwsh ./tools/unpack.ps1

# 2. 指向工具链目录（内含 LLVM\bin\clang.exe 与 SDK）
$env:QS_TOOLCHAIN = 'D:\path\to\toolchain'
pwsh ./build.ps1
```

产物：`build/QuietSign-unsigned.ipa`，需用 Sideloadly / AltStore / ESign / TrollStore 自行签名后安装（iOS 15+）。

## 源码存放说明

`src/main.m.gz.b64` 是**源码的 gzip + base64**。因为推送环境只能通过 GitHub API 以文本传输，二进制/超长内容会被截断，gzip 后体积降到约 1/4 才能完整上传。

还原后可用 `src/main.m.sha256` 核对，字节精确：

```powershell
pwsh ./tools/unpack.ps1   # 输出 SHA256 并自动校验
```

## 目录

```
build.ps1                 编译 + 打包 IPA 的完整流程
src/main.m.gz.b64         源码（gzip+base64）
src/main.m.sha256         源码 SHA256，用于校验
src/Info.plist            App 配置（显示名「静签」，bundle id com.gduf.quietsign，2.6 / build 20）
tools/unpack.ps1          还原源码
docs/PROTOCOL.md          签到协议：端点、二维码算法、时间窗口、幂等性
tools/                    辅助脚本
```

> 应用图标未入仓（体积/传输限制）。`build.ps1` 会在 `src/` 下找不到 `AppIcon*.png` 时跳过图标。

## 免责

仅用于本人账号的考勤记录管理。请勿用于代替他人签到。使用者自行承担一切后果。
