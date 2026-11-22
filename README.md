# 静签 (QuietSign)

广东金融学院「易广金」签到项目。同一套协议、两条实现路线，合并归档：

| 版本 | 位置 | 跑在哪 | 关机/断网还行不行 |
| --- | --- | --- | --- |
| **iOS 客户端** | `src/` + `build.ps1` | 自己的 iPhone（未签名 IPA） | 不行，手机得开机联网 |
| **服务端脚本** | `server/` | 任意 Linux 服务器 / 挂机主机 | **行**，本机只是控制端 |

> 私有仓库，仅供本人学习与自用。

## 安装包（最新 v3.0）

**[QuietSign-v3.0-unsigned.ipa](./QuietSign-v3.0-unsigned.ipa)**（100,515 字节）

```
https://github.com/Jacky2277/QuietSign-iOS/raw/main/QuietSign-v3.0-unsigned.ipa
```

| 项 | 值 |
| --- | --- |
| 版本 | 3.0 / build 30 |
| 架构 | arm64，iOS 15.0+ |
| SHA256 | `7D4DA565D96468F33F290E460AE0A4B2BA6C6081938C7A0368B0E294A04836E2` |
| 签名 | **未签名**，需自行签名后安装 |

### v3.0 改了什么

- **修复「跨天返校」不出二维码**：原先只在「生成凭证」成功后才把学号写进内存，刚打开 App 就点跨天返校会因学号为空而静默失败。现在跨天返校每次提交前先跑一遍 `loginAndBind:`（重新登录 + 设备对齐），拿到学号并刷新会话后再提交。
- token 失效时不再只留一行小字，直接回登录页并提示重新抓 token。
- 版本号 3.0 / build 30；产物文件名带版本号（不再同名覆盖）。

历史版本：`QuietSign-unsigned.ipa`（v2.6 / build 20，100,361 字节，SHA256 `83ABEF04…D772`）。

签名工具：Sideloadly / ESign / AltStore / TrollStore（有 TrollStore 的手机可直接装）。从源码重编也可以，见下方「构建」。

## iOS 客户端（App 版）

Objective-C + UIKit 单文件实现，在 Windows 上用 clang 交叉编译成 arm64 未签名 IPA。

- 用易班 `loginToken` 登录，服务端会话由 App 持有
- 出口码：出校凭证生成 / 出校后再生成自动变入校凭证 / 跨天返校（出校超时申请进校）
- 拉今日课表，列出每节课的**签到窗口**（上课前 `startSignMin` 分钟 → `stopScanTime`）
- **批量签到**：勾选今日指定课程后一次提交；没有任何“进页面就自动签”的行为
- **仅在签到窗口内提交**开关：未到开窗时间的课先挂起，到点自动提交；已过截止直接跳过
- 扫码签到兜底（老师发码时仍可用）

### 构建

不依赖 Xcode，只依赖本地工具链（clang 18+ 与 iPhoneOS SDK）：

```powershell
# 1. 还原源码
pwsh ./tools/unpack.ps1

# 2. 指向工具链目录（内含 LLVM\bin\clang.exe 与 SDK）
$env:QS_TOOLCHAIN = 'D:\path\to\toolchain'
pwsh ./build.ps1
```

产物：`build/QuietSign-unsigned.ipa`，需用 Sideloadly / AltStore / ESign / TrollStore 自行签名后安装（iOS 15+）。

## 服务端脚本（云端版）

全部逻辑在 `server/`，纯 HTTP，不需要 WebView、不需要模拟器。两层能力：

- `cli.py` / `ygj.py` —— **协议层**：登录、设备对齐、课表、当前课程、提交。`cli.py watch` 只等别人发码（依赖外部二维码）。
- `auto.py` / `auto2.py` —— **零扫码**：`startSignProcess` 自取 processID → `getPublicKey` 现取 key → AES-256-ECB 现造码 → `saveSignNew2` 提交，完全不依赖任何人扫码。
- `readqr.py` / `sign_from_qr.py` —— 从一张截图里解出 processID 再重造码（截图可以糊、可以过期）。

```bash
cd server
bash install.sh                      # 建 .venv 装依赖
. .venv/bin/activate
python3 cli.py login --token <易班loginToken>
python3 cli.py status                # 现在有没有课要签
python3 auto2.py --wait-until 13:58 --until 14:20   # 到点自己造码自己签
```

`auto.py` → `auto2.py` 的关键转折：v1 的 pid 必须来自真实考勤流程（要么有人扫码、要么 `--allow-start` 自己开），v2 发现 `startSignProcess` **不校验身份、不校验时间、对同一节课幂等**，于是自己就能拿到合法 pid，彻底零依赖。

> **注意：`state.json` / `qr.txt` / `*.log` 含 token、cookie、studentID，已被 `.gitignore` 排除，永远不要入库或截图分享。**

## 源码存放说明（App 部分）

`src/main.m.gz.b64` 是**源码的 gzip + base64**。因为推送环境只能通过 GitHub API 以文本传输，二进制/超长内容会被截断，gzip 后体积降到约 1/4 才能完整上传。

还原后可用 `src/main.m.sha256` 核对，字节精确：

```powershell
pwsh ./tools/unpack.ps1   # 输出 SHA256 并自动校验
```

> 安装包为 binary，走的是 GitHub 网页上传（浏览器直传），不受 gzip+base64 限制。

## 目录

```
QuietSign-v3.0-unsigned.ipa  最新安装包（v3.0 / build 30，未签名，100,515 字节）
QuietSign-unsigned.ipa        历史安装包（v2.6 / build 20，100,361 字节）
build.ps1                     编译 + 打包 IPA 的完整流程
src/main.m.gz.b64             App 源码（gzip+base64）
src/main.m.sha256             App 源码 SHA256，用于校验
src/Info.plist                App 配置（显示名「静签」，bundle id com.gduf.quietsign，3.0 / build 30）
tools/unpack.ps1              还原 App 源码
docs/PROTOCOL.md              签到协议：端点、二维码算法、时间窗口、幂等性
server/                       服务端脚本（协议库、CLI、零扫码自动签到、二维码解码）
```

> 应用图标未入仓（体积/传输限制）。`build.ps1` 会在 `src/` 下找不到 `AppIcon*.png` 时跳过图标。

## 免责

仅用于本人账号的考勤记录管理。请勿用于代替他人签到。使用者自行承担一切后果。