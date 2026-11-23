# 静签 (QuietSign) —— 基于 FUCK-GDUF 项目

广东金融学院「易广金」签到项目。同一套协议，三端归档：

| 端 | 位置 | 跑在哪 | 关机/断网还行不行 |
| --- | --- | --- | --- |
| **iOS 客户端** | 根目录 IPA + `src/` | 自己的 iPhone（未签名 IPA） | 不行，手机得开机联网 |
| **Android 客户端** | 根目录 APK + 本机构建目录 | 自己的安卓手机（已签名 APK） | 不行，手机得开机联网 |
| **服务端脚本** | `server/` | 任意 Linux 服务器 / 挂机主机 | **行**，本机只是控制端 |

> 私有仓库，仅供本人学习与自用。

## 安装包

| 平台 | 文件 | 大小 | 版本 | 安装方式 |
| --- | --- | --- | --- | --- |
| Android | **[QuietSign-v5.0.apk](./QuietSign-v5.0.apk)** | 336,349 | 5.0 / code 50 | 直接装（已签名） |
| iOS | **[QuietSign-v5.0-unsigned.ipa](./QuietSign-v5.0-unsigned.ipa)** | 104,960 | 5.0 / build 50 | 自己签名后装 |

```
https://github.com/Jacky2277/QuietSign-iOS/raw/main/QuietSign-v5.0.apk
https://github.com/Jacky2277/QuietSign-iOS/raw/main/QuietSign-v5.0-unsigned.ipa
```

**Android APK**

| 项 | 值 |
| --- | --- |
| 包名 | `com.gduf.chuxiao` |
| 版本 | 5.0 / versionCode 50 |
| 最低系统 | Android 8.0（API 26） |
| targetSdk | 34 |
| SHA256 | `77C90E1ACD38546753ACD6B9CDBCA8F10B498394D5C685CE5F463DC90EDF3AB4` |
| 签名 | 自签名（v1+v2+v3 全签名方案），证书 `CN=QuietSign` |

**iOS IPA**

| 项 | 值 |
| --- | --- |
| Bundle ID | `com.gduf.quietsign`（显示名「静签」） |
| 版本 | 5.0 / build 50 |
| 架构 | arm64，iOS 15.0+ |
| SHA256 | `B44E6F4B29AD58479BB0984A5B542062A960D2E600A8AF63916F07233B1466DD` |
| 签名 | **未签名** |

签名工具（iOS）：Sideloadly / ESign / AltStore / TrollStore。

## v5.0 改了什么（Android + iOS 同步）

**核心改动：内置「易班账号密码登录」，彻底不需要抓包、不需要 root。**

- 登录页输入易班手机号 + 密码 → 本地用易班 App 内置的 4096 位 RSA 公钥（`RSA/ECB/PKCS1Padding`）加密密码 → `POST https://m.yiban.cn/api/v4/passport/login` → 返回的 `access_token` 就是 `loginToken`，直接可换易广金的 `verify_request`。
- **替换掉原来的「易班扫码（网页）登录」**。该路径已被反复验证走不通：网页会话只能拿到 `yiban_user_token`，拿去访问 iapp 会返回「请重新登录」。
- 密码只在内存里用一次，不落盘、不写入任何配置，也不发往易班官方接口以外的地址。
- Android 端**保留**「一键读取本机 token（需 root）」，保留手动粘贴 32 位 token。
- iOS 端**保留**手动粘贴 token 入口。
- **修复 iOS 登录后自动生成出校码的 bug**：`refreshAll` → `bootstrap` 首步就是 `autoCreateOutSchoolRecord`，等于登录完就替用户向学校提交了一条常规出入申请。现在登录成功只停在「还没有生成凭证」，必须由用户点「生成凭证」才提交；Android 原本走的就是待生成状态，无此问题。
- 版本号统一为 5.0（Android versionCode 50 / iOS build 50）。

实测（与服务端接口层面）：

```
POST m.yiban.cn/api/v4/passport/login  →  {"response":100,"data":{"access_token":"<32位>"}}
GET  f.yiban.cn/iapp378946 (两跳)      →  ygj.gduf.edu.cn/index.aspx?verify_request=<448hex>   ✓ token VALID
```

### 致谢

- **[luoyily/yiban-reverse](https://github.com/luoyily/yiban-reverse)** —— 感谢作者 **luoyily**。
  v5.0 的「账号密码登录」完全是顺着这份逆向成果做出来的：`LoginUtil.login()` 的完整参数表、
  `ct/app/v/sig/apn/identify/sversion` 各字段含义、4096 位 RSA 加密公钥、`getSig()` 的取值，
  全都来自这个仓库。没有它提供的思路和还原代码，这一版做不出来。

### 历史版本

- **v3.5**：Android 新增「一键读取本机 token（需 root）」，从易班进程内存读 32 位 `loginToken` 并做服务端严格校验；修复 su 路径探测（`/product/bin/su`）。iOS 未改动。
- **v3.2 / v3.1**：尝试「网页扫码登录」自动取 token，实测拿不到可用 token，入口保留为兜底（v5.0 起正式移除）。
- **v3.0**：见下方「v3.0 改了什么」。

### v3.0 改了什么

- **iOS：修复「跨天返校」不出二维码**。原先只有「生成凭证」成功后学号才写进内存，冷启动直接点跨天返校会因学号为空静默失败；现在跨天返校每次提交前先 `loginAndBind:`（重新登录 + 设备对齐），拿回学号并刷新会话后再提交。
- **Android：签到页补齐课表与批量签到**。原来只有「扫码签到」，现在拉今日课表 → 列出每节课的签到窗口 → 勾选多门 → 一次批量提交；「仅在签到窗口内提交」开关会把未开窗的课挂起，到点自动提交。造码逻辑与 iOS 一致（`startSignProcess` 取 pid → `getPublicKey` 取 32 位 key → AES-256-ECB/PKCS7 → `saveSignNew2`），完全不依赖别人扫码。
- token 失效时两端都直接回登录页/提示更换，不再只留一行灰字。
- 版本号 3.0（iOS build 30 / Android versionCode 30）；产物带版本号，不再同名覆盖。

历史产物：`QuietSign-v3.5.apk`（332,253 字节）、`QuietSign-v3.2.apk`、`QuietSign-v3.1.apk`、`QuietSign-v3.0.apk`、`QuietSign-v3.0-unsigned.ipa`（100,515 字节）、`QuietSign-unsigned.ipa`（v2.6 / build 20，100,361 字节）。

## 客户端能力（两端一致）

- 登录方式：**易班账号密码登录（推荐）** / 手动粘贴 `loginToken` / Android 另有一键读取（需 root）
- 出校码：生成出校凭证 → 出校后再生成自动变入校凭证 → 跨天返校（出校超时申请进校）
- 拉今日课表，列出每节课的签到窗口（上课前 `startSignMin` 分钟 → `stopScanTime`）
- 批量签到：勾选今日课程后一次提交；没有任何“进页面就自动签”的行为
- 仅在签到窗口内提交：未开窗的先挂起，到点自动提交；已过截止直接跳过
- 扫码签到兜底（老师发码时仍可用）

> 「生成凭证」= 向学校提交一条常规出入申请，只有手动点按钮才会发生。登录本身不产生任何出入记录。

## 拿 loginToken 的几种方式（Android）

1. **账号密码登录（v5.0，推荐，零 root 零抓包）**：登录页填易班手机号 + 密码，App 自己调接口换 token。
2. **一键读取（v3.5，需 root）**：手机上打开已登录的易班 → 静签首页点「一键读取本机 token（需 root）」→ 弹框显示 token，可复制。原理是 root 下读易班进程内存里的明文 `loginToken`，并调用 `f.yiban.cn` 做严格校验。
3. **抓包**：ProxyPin / Stream 抓 `f.yiban.cn` 的请求头 `loginToken`（32 位）。
4. **粘贴**：任何来源拿到的 32 位 token 都可以直接粘进输入框。

> `loginToken` 是**账号级**凭据，与设备无关；但它和易班的登录会话绑定，**同账号单会话**——在别处重新登录易班（包括在静签里用账号密码登录）会让旧 token 立即失效，这是服务端 revoke，不是本地删除。

## 构建

### iOS

不依赖 Xcode，只依赖本地工具链（clang 18+ 与 iPhoneOS SDK）：

```powershell
pwsh ./tools/unpack.ps1                       # 还原源码
$env:QS_TOOLCHAIN = 'D:\path\to\toolchain'
pwsh ./build.ps1
```

产物：`build/QuietSign-unsigned.ipa`。

> 交叉编译时用 `SecKeyCreateWithData` + `SecKeyCreateEncryptedData(kSecKeyAlgorithmRSAEncryptionPKCS1)` 做密码加密，`src/Info.plist` 的 `CFBundleExecutable` 必须是 `GDUFQR`，与产物文件名保持一致。

### Android

不依赖 Gradle / Android Studio，用 SDK 自带的 `aapt2 + javac + d8 + zipalign + apksigner` 手工构建（ZXing core 3.5.3 已在 `libs/` 内，用于出校码渲染）：

```powershell
cd android
pwsh ./build.ps1 -VersionName 5.0 -VersionCode 50
java -jar $env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\lib\apksigner.jar sign `
  --ks quietsign-release.keystore --ks-pass pass:<口令> --key-pass pass:<口令> `
  --out build/QuietSign-v5.0.apk build/aligned.apk
```

要求：Android SDK build-tools + `platforms/android-*/android.jar` + JDK 11 以上。
注意 `build.ps1` 只做到 `build/aligned.apk`，签名是单独一步（有的 SDK 版本里得手动跑 apksigner）。

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

当前 `src/main.m.gz.b64` 对应 **v5.0** 源码：130,849 字节，SHA256 `9b9eed9d60b0a2ebcd10a2d873053365a7762af9ef4542edd4a23e0af716bced`。

## 目录

```
QuietSign-v5.0.apk          Android 安装包（v5.0 / code 50，已签名，336,349 字节）
QuietSign-v5.0-unsigned.ipa iOS 安装包（v5.0 / build 50，未签名，104,960 字节）
QuietSign-v3.5.apk          历史 Android 安装包（v3.5 / code 35）
QuietSign-v3.2.apk          历史 Android 安装包（v3.2 / code 32）
QuietSign-v3.1.apk          历史 Android 安装包（v3.1 / code 31）
QuietSign-v3.0.apk          Android 安装包（v3.0 / code 30）
QuietSign-v3.0-unsigned.ipa 历史 iOS 安装包（v3.0 / build 30）
QuietSign-unsigned.ipa      历史 iOS 安装包（v2.6 / build 20）
build.ps1                   iOS：clang 交叉编译 + 打包 IPA
src/main.m.gz.b64           iOS 源码（gzip+base64，v5.0）
src/main.m.sha256           iOS 源码 SHA256
src/Info.plist              iOS 配置（显示名「静签」，com.gduf.quietsign，5.0 / build 50）
tools/unpack.ps1            还原 iOS 源码
docs/PROTOCOL.md            签到协议：端点、二维码算法、时间窗口、幂等性
server/                     服务端脚本（协议库、CLI、零扫码自动签到、二维码解码）
```

> 安卓源码当前只在本机构建目录（未入库，体积/传输限制）；应用图标同样未入仓，`build.ps1` 会在缺图标时跳过。

## 免责

仅用于本人账号的考勤记录管理。请勿用于代替他人签到。使用者自行承担一切后果。

> 特别提示：v5.0 的账号密码登录会让该账号在易班 App 内的会话下线（服务端单会话机制），多人共用同一账号时请注意。
