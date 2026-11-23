# 静签 (QuietSign) —— 基于 FUCK-GDUF 项目

广东金融学院「易广金」签到项目。同一套协议，三端归档：

| 端 | 位置 | 跑在哪 | 关机/断网还行不行 |
| --- | --- | --- | --- |
| **iOS 客户端** | 根目录 IPA + `src/` | 自己的 iPhone（未签名 IPA） | 不行，手机得开机联网 |
| **Android 客户端** | 根目录 APK + 本机构建目录 | 自己的安卓手机（已签名 APK） | 不行，手机得开机联网 |
| **服务端脚本** | `server/` | 任意 Linux 服务器 / 挂机主机 | **行**，本机只是控制端 |

> 仓库私有，但代码本身以 **CC BY-NC-SA 4.0** 授权：允许转载、二创二改、自行编译分发，须注明原作者 **Jacky（GitHub: Jacky2277）** 并说明改动，禁止任何商业用途。完整条款见 [LICENSE](./LICENSE)。

## 安装包

| 平台 | 文件 | 大小 | 版本 | 安装方式 |
| --- | --- | --- | --- | --- |
| Android | **[QuietSign-v6.0.apk](./QuietSign-v6.0.apk)** | 402,665 | 6.0 / code 60 | 直接装（已签名） |
| iOS | **[QuietSign-v6.0-unsigned.ipa](./QuietSign-v6.0-unsigned.ipa)** | 293,459 | 6.0 / build 60 | 自己签名后装 |

```
https://github.com/Jacky2277/QuietSign-iOS/raw/main/QuietSign-v6.0.apk
https://github.com/Jacky2277/QuietSign-iOS/raw/main/QuietSign-v6.0-unsigned.ipa
```

**Android APK**

| 项 | 值 |
| --- | --- |
| 包名 | `com.gduf.chuxiao` |
| 版本 | 6.0 / versionCode 60 |
| 最低系统 | Android 8.0（API 26） |
| targetSdk | 34 |
| SHA256 | `6642047F259D1027B7D6949C8AE8B5B55A65C91DB392099B10455C95FBBA733C` |
| 签名 | 自签名（v1+v2+v3 全签名方案），证书 `CN=QuietSign` |

**iOS IPA**

| 项 | 值 |
| --- | --- |
| Bundle ID | `com.gduf.quietsign`（显示名「静签」） |
| 版本 | 6.0 / build 60 |
| 架构 | arm64，iOS 15.0+ |
| SHA256 | `4944A8B02AC940FA254D5B0D128CEAA3D531B78DF2CD19389DCBD56FCB0B212E` |
| 签名 | **未签名** |

签名工具（iOS）：Sideloadly / ESign / AltStore / TrollStore。

## v6.0 改了什么（界面重做，功能不变）

这一版只动视觉与交互，协议、登录、造码、签到逻辑全部沿用 v5.x。

**统一视觉：Material 3 蓝色体系 + 深浅双主题**

| 语义色 | 浅色 | 深色 |
| --- | --- | --- |
| 背景 | `#F4F7FC` | `#101318` |
| 卡片 | `#FFFFFF` | `#1C2129` |
| 主色 | `#2F6BFF` | `#4D86FF` |
| 容器 | `#E8EFFF` | `#1E2A44` |
| 分割线 | `#EBF0F7` | `#2A3038` |

- Android：`applyTheme()` 读 `uiMode` 选两套色板，`onConfigurationChanged` 重建界面；状态栏同步深浅。
- iOS：`GFUserDynamic()` 用 `UIColor colorWithDynamicProvider` 包一套动态色（`GFBG/GFCard/GFInk/GFSub/GFPrimary/GFCont`），系统切换深浅色时自动跟随。

**布局与组件**

- Android 首页改 M3 结构：Top App Bar（「静签」+ 学校/学号 + 右上 `···`）→ section「我的凭证」→ 凭证卡片（标题 + 状态胶囊 + 二维码 + 姓名行 + 进度条 + 倒计时 + 状态）→ 双主按钮。
- **「签到考勤」与「生成凭证」并列为同级主按钮**（各占一半宽度，48dp 高，24dp 圆角胶囊）。
- 「跨天返校」「更换 token」降为次级 chip（淡蓝底、蓝字、24dp 圆角）。
- 新增「关于作者」卡片：Jacky2277 / github.com/Jacky2277 / 设置三行，配 GitHub 主页跳转。
- 底部标注「静签 v6.0 · 仅供技术交流」+ GitHub 地址。
- 二维码补四角 L 框（`CornerFrame` 自绘），内缩 10dp、臂长 22dp，**浅色/深色同一套参数**；待生成时角框环住占位块，生成后环住二维码。
- 卡片由投影改为 1dp 细描边，去掉 elevation 在圆角下方压出的直角灰带。
- 图标换成蓝底白色「Q + 对勾」：Q 的尾巴化成对勾；Android 用自适应图标（透明前景 + `#2F6BFF` 背景层，系统切圆切方都不掉主体），iOS 铺 1024 / 120 / 180 三档。

**弹窗全部自绘**

v6.0 写了统一的 `m3Dialog()`：24dp 圆角卡片、20sp 标题、13sp 正文、右下角蓝色实心 pill + 淡蓝胶囊次按钮，深浅色跟随，共 7 处（「关于静签」「详细教程」「更换 token」「跨天返校」「易班授权」「读取 token」「扫码测试成功」）。

> 弹窗首次出现不再「先弹出再往下挪一截」：`show()` 之前按屏幕 88% 宽度 `measure` 出最终高度，一次性 `setLayout`，并 `setWindowAnimations(0)` 关掉系统入场动画。

**签到考勤页**

顶栏改卡片白底 + 深色字；窗口开关、课程行改圆角卡片；批量签到为蓝色 pill，扫码签到为淡蓝 chip；勾选框选中态改主色。

**转场**

Android 点「签到考勤」改为**整页自下而上推入**（`slide_up_in` 350ms / `accelerate_decelerate`，前页 `stay` 不动），返回时向下滑出（`slide_down_out` 330ms），与 iOS `UIModalPresentationFullScreen` + `CoverVertical` 的观感对齐。

> 补丁：Android 14（targetSdk 34）起系统会直接忽略 `overridePendingTransition()`，这就是「点了没动画」的原因。现在签到页改成在 `startActivity` 之前调 `overrideActivityTransition()`（返回时在 `finish()` 里调 `OVERRIDE_TRANSITION_CLOSE`），并给 `SignActivity` 单独套了一层 `SignTheme`，`windowAnimationStyle` 也指向同一组动画——代码与主题双保险，各家 ROM 都能推得动。

**iOS 布局微调**

- 首页「生成凭证」「跨天返校」统一为 200pt × 44pt × 22pt 圆角（原来 180/200 不等宽，且圆角 26 超过半高会被画成两端鼓包）。
- 登录页「开始使用」同样修成 22 圆角。
- 新增底部「关于作者 · Jacky2277 · 仅供技术交流」，点击打开 GitHub。

## v5.1 修复了什么（随 v6.0 一并归档）

- 两端识别 `login.ashx` 返回 `code:3` + `yibanUrl`：该易班账号**从未授权过「易广金」**（`iapp378946` / `client_id 0b77c3ac53bd5c65`），此时 `verify_request` 只有 160 字节（正常 448）。
- 前端改为抛 `NEED_YIBAN_AUTH|<url>`，弹「需要先在易班授权一次」，点「去授权」用浏览器打开 `https://oauth.yiban.cn/code/html?client_id=0b77c3ac53bd5c65&redirect_uri=http://f.yiban.cn/iapp378946`。
- 顺带确认：`https://ygj.gduf.edu.cn/index.aspx` 是「登录中，请稍候…」中转页，浏览器直接打开本来就“打不开”，属正常现象。

## v5.0 改了什么（重大：彻底不抓包）

- 登录页改「易班账号密码登录」：手机号 + 密码 → 本地用易班 App 内置的 4096 位 RSA 公钥（`RSA/ECB/PKCS1Padding`）加密 → `POST https://m.yiban.cn/api/v4/passport/login` → 返回的 `access_token` 就是 `loginToken`，直接可换易广金的 `verify_request`。
- 移除「易班扇码（网页）登录」：该路径只能拿到 `yiban_user_token`，拿去访问 iapp 会返回「请重新登录」。
- 密码只在内存里用一次，不落盘、不上传。
- Android 保留「一键读取本机 token（需 root）」与手动粘贴；iOS 保留手动粘贴。
- 修复 iOS「登录后自动生成出校码」：登录成功只停在「还没有生成凭证」，必须手动点「生成凭证」才提交。

```
POST m.yiban.cn/api/v4/passport/login  →  {"response":100,"data":{"access_token":"<32位>"}}
GET  f.yiban.cn/iapp378946 (两跳)      →  ygj.gduf.edu.cn/index.aspx?verify_request=<448hex>   ✓ valid
```

## 客户端能力（两端一致）

- 登录方式：**易班账号密码登录（推荐）** / 手动粘贴 `loginToken` / Android 另有一键读取（需 root）
- 出校码：生成出校凭证 → 出校后再生成自动变入校凭证 → 跨天返校（出校超时申请进校）
- 拉今日课表，列出每节课的签到窗口（上课前 `startSignMin` 分钟 → `stopScanTime`）
- 批量签到：勾选今日课程后一次提交；没有任何“进页面就自动签”的行为
- 仅在签到窗口内提交：未开窗的先挂起，到点自动提交；已过截止直接跳过
- 扇码签到兜底（老师发码时仍可用）

> 「生成凭证」= 向学校提交一条常规出入申请，只有手动点按钮才会发生。登录本身不产生任何出入记录。

## 拿 loginToken 的几种方式（Android）

1. **账号密码登录（v5.0+，推荐，零 root 零抓包）**：登录页填易班手机号 + 密码，App 自己调接口换 token。
2. **一键读取（v3.5+，需 root）**：手机上打开已登录的易班 → 静签首页点「一键读取本机 token（需 root）」→ 弹框显示 token，可复制。
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

不依赖 Gradle / Android Studio，用 SDK 自带的 `aapt2 + javac + d8 + zipalign + apksigner` 手工构建（ZXing core 3.5.3 已在 `libs/` 内）：

```powershell
cd android
pwsh ./build.ps1 -VersionName 6.0 -VersionCode 60
java -jar $env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\lib\apksigner.jar sign `
  --ks quietsign-release.keystore --ks-pass pass:<口令> --key-pass pass:<口令> `
  --out build/QuietSign-v6.0.apk build/aligned.apk
```

要求：Android SDK build-tools + `platforms/android-*/android.jar` + JDK 11 以上。
注意 `build.ps1` 只做到 `build/aligned.apk`，签名是单独一步。

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

`auto.py` → `auto2.py` 的关键转折：v1 的 pid 必须来自真实考勤流程，v2 发现 `startSignProcess` **不校验身份、不校验时间、对同一节课幂等**，于是自己就能拿到合法 pid，彻底零依赖。

> **注意：`state.json` / `qr.txt` / `*.log` 含 token、cookie、studentID，已被 `.gitignore` 排除，永远不要入库或截图分享。**

## 源码存放说明（iOS 部分）

`src/main.m.gz.b64` 是 iOS 源码的 **gzip + base64**：推送环境只能通过 GitHub API 以文本传文件，二进制/超长内容会被截断，gzip 后体积降到约 1/4 才能完整上传。

```powershell
pwsh ./tools/unpack.ps1   # 输出 SHA256 并自动校验
```

还原后可用 `src/main.m.sha256` 核对，字节精确。

当前 `src/main.m.gz.b64` 对应 **v6.0** 源码：134,642 字节，SHA256 `efc710d999a8e5e1c6cf28dfe951529c19126abbc193c8f3bb1175e559373fec`。

## 目录

```
QuietSign-v6.0.apk          Android 安装包（v6.0 / code 60，已签名，402,665 字节）
QuietSign-v6.0-unsigned.ipa iOS 安装包（v6.0 / build 60，未签名，293,459 字节）
QuietSign-v5.0.apk          历史 Android 安装包（v5.0 / code 50）
QuietSign-v5.0-unsigned.ipa 历史 iOS 安装包（v5.0 / build 50）
QuietSign-v3.5.apk          历史 Android 安装包（v3.5 / code 35）
QuietSign-v3.2.apk          历史 Android 安装包（v3.2 / code 32）
QuietSign-v3.1.apk          历史 Android 安装包（v3.1 / code 31）
QuietSign-v3.0.apk          历史 Android 安装包（v3.0 / code 30）
QuietSign-v3.0-unsigned.ipa 历史 iOS 安装包（v3.0 / build 30）
QuietSign-unsigned.ipa      历史 iOS 安装包（v2.6 / build 20）
build.ps1                   iOS：clang 交叉编译 + 打包 IPA
src/main.m.gz.b64           iOS 源码（gzip+base64，v6.0）
src/main.m.sha256           iOS 源码 SHA256
src/Info.plist              iOS 配置（显示名「静签」，com.gduf.quietsign，6.0 / build 60）
tools/unpack.ps1            还原 iOS 源码
docs/PROTOCOL.md            签到协议：端点、二维码算法、时间窗口、幂等性
server/                     服务端脚本（协议库、CLI、零扫码自动签到、二维码解码）
```

> 安卓源码当前只在本机构建目录（未入库）；应用图标同样未入仓。

## 许可协议

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-2F6BFF.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)

本项目采用 **[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh-hans)**（知识共享 署名—非商业性使用—相同方式共享 4.0 国际）。完整法律文本见 [LICENSE](./LICENSE)。

| | 内容 |
| --- | --- |
| **允许** | 转载、二次开发、修改、重新编译、自行签名分发（含带改动的版本） |
| **必须** | 注明原作者 **Jacky**（GitHub: [Jacky2277](https://github.com/Jacky2277)）及本仓库链接；作过修改的，说明改了哪里 |
| **禁止** | 任何商业用途：售卖 APK/IPA、付费下载、收费代签或代跑、广告与引流变现、捆绑进付费产品或服务 |
| **衍生作品** | 必须以同一协议 CC BY-NC-SA 4.0 发布，不得改成更宽松的协议 |

- 自愿打赏不视为商业使用；但「付费才能用」「付款解锁功能」属于商业使用。
- 商业授权、白标或分发合作，请经 [GitHub](https://github.com/Jacky2277) 联系作者。
- 「静签」名称与「Q + 对勾」图标不在许可范围内（CC 协议不授予商标权），不得用于标识你发布的衍生版本或暗示官方背书。
- 本项目实现上参考了 [luoyily/yiban-reverse](https://github.com/luoyily/yiban-reverse)（MIT）；如涉及该仓库的代码或文件，该部分仍依 MIT 许可，不受本协议限制。

## 免责

仅供技术交流，请勿用于违规用途。仅用于本人账号的考勤记录管理，请勿代替他人签到。使用者自行承担一切后果。

> 特别提示：账号密码登录会让该账号在易班 App 内的会话下线（服务端单会话机制），多人共用同一账号时请注意。

## 致谢

- **[luoyily/yiban-reverse](https://github.com/luoyily/yiban-reverse)** —— 感谢作者 **luoyily**。
v5.0 的「账号密码登录」完全是顺着这份逆向成果做出来的：`LoginUtil.login()` 的完整参数表、`ct/app/v/sig/apn/identify/sversion` 各字段含义、4096 位 RSA 加密公钥、`getSig()` 的取值，全都来自这个仓库。
