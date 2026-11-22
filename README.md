# 静签 (QuietSign)----基于FUCK-GDUF项目

广东金融学院「易广金」签到项目。同一套协议，三端归档：

| 端 | 位置 | 跑在哪 | 关机/断网还行不行 |
| --- | --- | --- | --- |
| **iOS 客户端** | 根目录 IPA + `src/` | 自己的 iPhone（未签名 IPA） | 不行，手机得开机联网 |
| **Android 客户端** | 根目录 APK + 本机构建目录 | 自己的安卓手机（已签名 APK） | 不行，手机得开机联网 |
| **服务端脚本** | `server/` | 任意 Linux 服务器 / 挂机主机 | **行**，本机只是控制端 |

> 私有仓库，仅供本人学习与自用。

## 安装包（全部 v3.0）

| 平台 | 文件 | 大小 | 安装方式 |
| --- | --- | --- | --- |
| Android | **[QuietSign-v3.0.apk](./QuietSign-v3.0.apk)** | 324,061 | 直接装（已签名） |
| iOS | **[QuietSign-v3.0-unsigned.ipa](./QuietSign-v3.0-unsigned.ipa)** | 100,515 | 自己签名后装 |

```
https://github.com/Jacky2277/QuietSign-iOS/raw/main/QuietSign-v3.0.apk
https://github.com/Jacky2277/QuietSign-iOS/raw/main/QuietSign-v3.0-unsigned.ipa
```

**Android APK**

| 项 | 值 |
| --- | --- |
| 包名 | `com.gduf.chuxiao` |
| 版本 | 3.0 / versionCode 30 |
| 最低系统 | Android 8.0（API 26） |
| targetSdk | 34 |
| SHA256 | `498FA7BF8B36D7416B711BF0714F576EB7956377780A728CB41D7F0E47FB4B36` |
| 签名 | 自签名（v1+v2+v3 全签名方案），证书 `CN=QuietSign` |

**iOS IPA**

| 项 | 值 |
| --- | --- |
| Bundle ID | `com.gduf.quietsign`（显示名「静签」） |
| 版本 | 3.0 / build 30 |
| 架构 | arm64，iOS 15.0+ |
| SHA256 | `7D4DA565D96468F33F290E460AE0A4B2BA6C6081938C7A0368B0E294A04836E2` |
| 签名 | **未签名** |

签名工具（iOS）：Sideloadly / ESign / AltStore / TrollStore。

### v3.0 改了什么

- **iOS：修复「跨天返校」不出二维码**。原先只有「生成凭证」成功后学号才写进内存，冷启动直接点跨天返校会因学号为空静默失败；现在跨天返校每次提交前先 `loginAndBind:`（重新登录 + 设备对齐），拿回学号并刷新会话后再提交。
- **Android：签到页补齐课表与批量签到**。原来只有「扫码签到」，现在拉今日课表 → 列出每节课的签到窗口 → 勾选多门 → 一次批量提交；「仅在签到窗口内提交」开关会把未开窗的课挂起，到点自动提交。造码逻辑与 iOS 一致（`startSignProcess` 取 pid → `getPublicKey` 取 32 位 key → AES-256-ECB/PKCS7 → `saveSignNew2`），完全不依赖别人扫码。
- token 失效时两端都直接回登录页/提示更换，不再只留一行灰字。
- 版本号 3.0（iOS build 30 / Android versionCode 30）；产物带版本号，不再同名覆盖。

历史版本：`QuietSign-unsigned.ipa`（v2.6 / build 20，100,361 字节）。

## 客户端能力（两端一致）

- 用易班 `loginToken` 登录，服务端会话由客户端持有
- 出校码：生成出校凭证 → 出校后再生成自动变入校凭证 → 跨天返校（出校超时申请进校）
- 拉今日课表，列出每节课的签到窗口（上课前 `startSignMin` 分钟 → `stopScanTime`）
- 批量签到：勾选今日课程后一次提交；没有任何“进页面就自动签”的行为
- 仅在签到窗口内提交：未开窗的先挂起，到点自动提交；已过截止直接跳过
- 扫码签到兜底（老师发码时仍可用）

## 构建

### iOS

不依赖 Xcode，只依赖本地工具链（clang 18+ 与 iPhoneOS SDK）：

```powershell
pwsh ./tools/unpack.ps1                       # 还原源码
$env:QS_TOOLCHAIN = 'D:\path\to\toolchain'
pwsh ./build.ps1
```

产物：`build/QuietSign-unsigned.ipa`。

### Android

不依赖 Gradle / Android Studio，用 SDK 自带的 `aapt2 + javac + d8 + zipalign + apksigner` 手工构建（ZXing core 3.5.3 已在 `libs/` 内，用于出校码渲染）：

```powershell
cd android
pwsh ./build.ps1 -VersionName 3.0 -VersionCode 30
java -jar $env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\lib\apksigner.jar sign `
  --ks quietsign-release.keystore --ks-pass pass:<口令> --key-pass pass:<口令> `
  --out build/QuietSign-v3.0.apk build/aligned.apk
```

要求：Android SDK build-tools + `platforms/android-*/android.jar` + JDK 11 以上。

## 服务端脚本（云端版）

全部逻辑在 `server/`，纯 HTTP，不需要 WebView、不需要模拟器。

- `cli.py` / `ygj.py` —— **协议层**：登录、设备对齐、课表、当前课程、提交。`cli.py watch` 只等别人发码。
- `auto.py` / `auto2.py` —— **零扫码**：`startSignProcess` 自取 processID → `getPublicKey` 现取 key → AES-256-ECB 现造码 → `saveSignNew2` 提交。
- `readqr.py` / `sign_from_qr.py` —— 从一张截图里解出 processID 再重造码。

```bash
cd server
bash install.sh
. .venv/bin/activate
python3 cli.py login --token <易班loginToken>
python3 cli.py status
python3 auto2.py --wait-until 13:58 --until 14:20
```

`auto.py` → `auto2.py` 的关键转折：v1 的 pid 必须来自真实考勤流程（要么有人扫码，要么 `--allow-start` 自己开），v2 发现 `startSignProcess` **不校验身份、不校验时间、对同一节课幂等**，于是自己就能拿到合法 pid，彻底零依赖。

> **注意：`state.json` / `qr.txt` / `*.log` 含 token、cookie、studentID，已被 `.gitignore` 排除，永远不要入库或截图分享。**

## 源码存放说明（iOS 部分）

`src/main.m.gz.b64` 是 iOS 源码的 **gzip + base64**：推送环境只能通过 GitHub API 以文本传文件，二进制/超长内容会被截断，gzip 后体积降到约 1/4 才能完整上传。

```powershell
pwsh ./tools/unpack.ps1   # 输出 SHA256 并自动校验
```

还原后可用 `src/main.m.sha256` 核对，字节精确。安装包为 binary，走的是 GitHub 网页直传，不受此限制。

## 目录

```
QuietSign-v3.0.apk          Android 安装包（v3.0 / versionCode 30，已签名，324,061 字节）
QuietSign-v3.0-unsigned.ipa iOS 安装包（v3.0 / build 30，未签名，100,515 字节）
QuietSign-unsigned.ipa      历史 iOS 安装包（v2.6 / build 20，100,361 字节）
build.ps1                   iOS：clang 交叉编译 + 打包 IPA
src/main.m.gz.b64           iOS 源码（gzip+base64）
src/main.m.sha256           iOS 源码 SHA256
src/Info.plist              iOS 配置（显示名「静签」，com.gduf.quietsign，3.0 / build 30）
tools/unpack.ps1            还原 iOS 源码
docs/PROTOCOL.md            签到协议：端点、二维码算法、时间窗口、幂等性
server/                     服务端脚本（协议库、CLI、零扫码自动签到、二维码解码）
```

> 安卓源码当前只在本机构建目录（未入库，体积/传输限制）；应用图标同样未入仓，`build.ps1` 会在缺图标时跳过。

## 免责

仅用于本人账号的考勤记录管理。请勿用于代替他人签到。使用者自行承担一切后果。