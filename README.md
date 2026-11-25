# 静签 QuietSign

<p align="center">
  <img src="android/res/drawable/ic_launcher.png" width="96" alt="静签图标">
</p>

<p align="center">
  <b>把「易广金」的签到与出入凭证，收进一个安静的按钮。</b><br>
  广东金融学院非官方客户端 · Android / iOS · 账号密码登录 · 出校码 · 签到考勤
</p>

<p align="center">
  <a href="https://github.com/Jacky2277/QuietSign-iOS-Android/releases"><img src="https://img.shields.io/badge/Release-v6.8-brightgreen.svg" alt="Release"></a>
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20%7C%20iOS%2015.0%2B-blue.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Java%20%2F%20Objective--C-orange.svg" alt="Language">
  <img src="https://img.shields.io/badge/Special-v2.5-8A2BE2.svg" alt="Special">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg" alt="License"></a>
</p>

> ⚠️ **非官方项目**：与广东金融学院、易班、易广金及其关联方没有隶属、合作或授权关系。签到与出入都会向学校系统真实提交记录，请仅用于本人账号，并自行遵守校规与法律。

> 🧪 **服务端脚本已废弃**：`server/` 仅作协议研究与学习参考，不再维护，接口随时可能失效。若你仍要使用，请自行审查、修改、加固并承担全部风险。

---

## ✨ 核心特性

- 🎫 **出校 / 入校凭证**：登录一次，点「生成凭证」直接出示二维码；二维码随服务端校验动态刷新。
- 🔄 **方向自动跟随**：出校后再次生成，自动切换为入校凭证，不需要手动选出/入方向。
- 🌙 **跨天返校**：按学校流程提交「出校超时（申请进校）」，生成入校码。
- 📅 **今日课表**：拉取当天课程与每节课的签到窗口，不再靠记忆判断能不能签。
- ✅ **批量签到**：勾选课程后一次提交。普通版会保留未开窗课程并到点提交；Special 版默认只勾当前可签课程。
- 📷 **扫码兜底**：老师临时发码时，仍可调用相机扫描签到二维码。
- 🧩 **双版本线**：普通版保持原版交互；Special 版重做首页、凭证页与签到流程，两个包名不同，可以同时安装。
- 🔐 **凭据只在本机**：token 保存在本机，不经过本项目服务器；没有账号系统，也没有云端后端。
- 🎨 **深浅双主题**：Material 3 蓝色体系，跟随系统深浅色切换。

## 🧩 两个版本线怎么选

| | 普通版 | Special 版 |
| --- | --- | --- |
| 包名 | `com.gduf.chuxiao` | `com.gduf.chuxiao.special` |
| 应用名 | 静签 | 静签 Special |
| 首页 | 原版结构：顶部运行日志 / `···`、凭证卡、底部操作入口 | 重做：去掉底部双主按钮与右上 `···`，新增「关于作者」，今日进度按课程分段 |
| 凭证页 | 原版凭证卡 | 二维码居中，移除「保存到相册」，只保留全宽刷新 |
| 签到 | 勾选今日课程，未开窗课程挂起到点提交 | 底部「选择课程」sheet，默认只勾当前可签课程；批量时跳过未开始 / 已截止 |
| 注意事项 | V6.8 版说明（含易班 5.1.8 必读） | 完整 6 节 + 三枚设备状态 chip，只保留「我已知晓」 |
| 适合谁 | 习惯原版布局、想要稳定更新 | 喜欢新界面、需要更直接的签到流程 |

> 💡 两个包名不同，可以同时安装；但应用数据、token、登录状态彼此独立，需要分别登录或粘贴 token。

## 📲 下载与安装

| 版本 | 平台 | 安装包 | 版本号 | 安装方式 |
| --- | --- | --- | --- | --- |
| **普通版** | Android 8.0+ | [QuietSign-v6.8.apk](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/v6.8/QuietSign-v6.8.apk) | 6.8 / code 68 | 直接安装；同一签名可覆盖旧版 |
| **Special 版** | Android 8.0+ | [QuietSign-Special-V2.5.apk](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/special-v2.5/QuietSign-Special-V2.5.apk) | V2.5 / code 25 | 直接安装；独立包名，可与普通版共存 |
| **iOS** | iOS 15.0+ | [QuietSign-v6.6-unsigned.ipa](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/v6.8/QuietSign-v6.6-unsigned.ipa) | 6.6 / build 66 | 未签名，需 Sideloadly / ESign / AltStore 等自签安装 |

**Android**：允许安装未知来源应用即可。普通版沿用原签名证书，可直接覆盖旧版；Special 版首次安装不会影响普通版数据。

**iOS**：安装包未签名，需要自己的证书自签；更新时用同一张证书覆盖安装，换证书要先卸载。

### 💡 易班 5.1.8 必读

> 易班 / 易广金官方版本存在已知设备绑定 Bug。若想减少「设备已经被绑定，需要联系导员解绑」一类问题，建议按下面步骤操作：
>
> 1. 若当前易班版本高于 5.1.8，先卸载，再安装 5.1.8；低于 5.1.8 可忽略。
> 2. 安装后登录易班，再进入易广金，绑定你当前使用的设备。
> 3. 绑定完成后不要再更新易广金。
> 4. 回到静签，再登录 / 粘贴 token 使用。
>
> 备用包：[yiban-5.1.8.apk](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/v6.8/yiban-5.1.8.apk)（仅作版本降级备用，版权归易班官方）。

## 🆕 新版本特性

### V6.8 · Android 普通版

- **设备状态卡重新定位**：从原来的位置移到「广东金融学院」与「我的凭证」之间，视觉上居中；卡片内新增「官方易班 Bug / 5.1.8 必读」常驻提示。
- **注意事项升级**：补充官方易班 Bug 说明、易班 5.1.8 的五步降级与绑定流程、设备状态含义、绑定 / 解绑边界、账号与隐私说明。
- **保留只读设备状态与二维码边界校验**：设备不在绑定状态时不伪造绑定；遇到异常状态直接提示，不再把内部错误码原样丢给用户。
- **版本与签名**：`6.8 / code 68`，沿用原签名证书，可覆盖安装。APK 423,145 字节，SHA-256 `1FECF1A25A21E33F5A2A2E9BF5D76C6ED7FA10332FDB28ECCBD3957D0C54F921`。

### Special V2.5 · Android 独立版

<p align="center">
  <img src="docs/special-icon.png" width="76" alt="静签 Special 图标">
</p>

- **独立安装包**：包名 `com.gduf.chuxiao.special`，应用名「静签 Special」，与普通版共存，不影响普通版数据。
- **首页重做**：去掉底部双主按钮与右上角 `···`；新增「关于作者」两行卡片（Jacky2277 / GitHub）；设备状态卡落在校名与「我的凭证」之间；今日进度按课程数量分段显示。
- **凭证页收敛**：二维码居中；移除「保存到相册」；只保留一条全宽「刷新二维码」按钮。
- **注意事项完整化**：恢复完整 6 节说明，顶部增加 `active / 本机已落库 / 账号已落库本机未绑定` 三枚设备状态 chip；底部只保留「我已知晓」。
- **签到改成底部 sheet**：点「签到考勤」从底部弹出「选择课程」，默认只勾选当前可签的待签课程；批量提交时跳过未开始 / 已截止课程；移除「仅在签到窗口内提交」的旧限制。
- **成功回传首页**：签到成功后回到首页，用绿色成功条显示「签到成功」「第 X 节 · HH:mm 已提交」「查看记录」，不再只停留在签到页。
- **新图标**：蓝底白色「清单 + 勾」，普通图标与自适应图标均已替换。
- **版本与签名**：`V2.5 / code 25`，APK 505,065 字节，SHA-256 `F13F56CFA9E629B4BD4E2664870CAD96679F74EC26724F3D0B8CF0A2B24C4615`。
- **源码包**：[QuietSign-Special-V2.5-source.zip](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/special-v2.5/QuietSign-Special-V2.5-source.zip)，10 个源文件，不含签名密钥。

## 📦 历史版本

历史包统一归档在 [archive-v2.6-v6.5](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/tag/archive-v2.6-v6.5)，仅作留档，不再维护。旧接口变更后，历史包可能已经无法使用。

| 版本 | 平台 | 文件 | 大小 |
| --- | --- | --- | --- |
| v6.5 | Android | [QuietSign-v6.5.apk](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/archive-v2.6-v6.5/QuietSign-v6.5.apk) | 402,665 B |
| v6.5 | iOS | [QuietSign-v6.5-unsigned.ipa](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/archive-v2.6-v6.5/QuietSign-v6.5-unsigned.ipa) | 294,034 B |
| v6.0 | Android | [QuietSign-v6.0.apk](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/archive-v2.6-v6.5/QuietSign-v6.0.apk) | 402,665 B |
| v6.0 | iOS | [QuietSign-v6.0-unsigned.ipa](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/archive-v2.6-v6.5/QuietSign-v6.0-unsigned.ipa) | 293,459 B |
| v5.0 | Android | [QuietSign-v5.0.apk](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/archive-v2.6-v6.5/QuietSign-v5.0.apk) | 336,349 B |
| v5.0 | iOS | [QuietSign-v5.0-unsigned.ipa](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/archive-v2.6-v6.5/QuietSign-v5.0-unsigned.ipa) | 104,960 B |
| v3.5 | Android | [QuietSign-v3.5.apk](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/archive-v2.6-v6.5/QuietSign-v3.5.apk) | 332,253 B |
| v3.2 | Android | [QuietSign-v3.2.apk](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/archive-v2.6-v6.5/QuietSign-v3.2.apk) | 332,253 B |
| v3.1 | Android | [QuietSign-v3.1.apk](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/archive-v2.6-v6.5/QuietSign-v3.1.apk) | 328,157 B |
| v3.0 | Android | [QuietSign-v3.0.apk](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/archive-v2.6-v6.5/QuietSign-v3.0.apk) | 324,061 B |
| v3.0 | iOS | [QuietSign-v3.0-unsigned.ipa](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/archive-v2.6-v6.5/QuietSign-v3.0-unsigned.ipa) | 100,515 B |
| v2.6 | iOS | [QuietSign-unsigned.ipa](https://github.com/Jacky2277/QuietSign-iOS-Android/releases/download/archive-v2.6-v6.5/QuietSign-unsigned.ipa) | 100,361 B |

> v5.1 的授权状态修复随 v6.0 一起归档，未单独发布 APK/IPA。

<details>
<summary>旧版本更新摘要（v6.5 / v6.0 / v5.x）</summary>

**v6.5 · 授权入口补齐 + 登录页适配**

- 签到考勤页补「去授权」入口：遇到 `NEED_YIBAN_AUTH` 不再显示原始错误串，直接弹窗引导去易班授权。
- Android 登录页按 V6 蓝色体系重做：24dp 圆角卡片、淡蓝输入框、50dp pill 主按钮、跟随系统深浅色。
- iOS 同步识别授权错误，保留 token 并提示去授权。

**v6.0 · 界面重做，功能不变**

- 统一 Material 3 蓝色体系与深浅双主题；Android 与 iOS 同步动态色。
- 首页改为 Top App Bar + 凭证卡 + 双主按钮；新增「关于作者」卡片与底部版本标注。
- 弹窗全部自绘；二维码增加四角 L 框；图标换成蓝底白色「Q + 对勾」。

**v5.1 · 授权状态识别**

- 识别 `login.ashx` 的 `code:3` + `yibanUrl`：账号从未授权「易广金」，此时 `verify_request` 只有 160 字节。
- 前端改为抛出 `NEED_YIBAN_AUTH|<url>`，引导打开易班授权页。

**v5.0 · 彻底不抓包**

- 新增易班账号密码登录：手机号 + 密码 → 本地 RSA PKCS1 加密 → `passport/login` 换 `loginToken`。
- 移除只能拿到 `yiban_user_token` 的网页登录路径；密码只在内存使用一次，不落盘、不上传。
- 修复 iOS 登录后自动生成出校码的问题：登录成功只停留在凭证页，必须手动点「生成凭证」。

</details>

## 🔐 登录与凭据

支持三种方式：

1. **易班账号密码登录（推荐）**：手机号 + 密码在本地加密后换 token，不用抓包、不用 root。
2. **手动粘贴 `loginToken`**：从 ProxyPin / Stream 等抓包工具复制 32 位 token。
3. **一键读取本机 token（Android，需 root）**：从已登录的易班 App 内存读取。

> `loginToken` 是账号级凭据，和易班登录会话绑定。同一账号在别处重新登录（包括在静签里用账号密码登录）会让旧 token 立即失效，这是服务端 revoke，不是本地删除。

> token、Cookie 只保存在本机；不要截图分享，也不要把 `state.json`、`qr.txt`、`*.log`、keystore 提交到仓库。

## 🗂 源码结构

```text
main 分支
├─ android/                 普通版 Android 源码（手写 aapt2 + javac + d8 构建）
├─ src/                     iOS 源码（gzip + base64）与图标、Info.plist
├─ docs/PROTOCOL.md         签到协议：端点、二维码算法、时间窗口、幂等性
├─ server/                  服务端脚本（已废弃，仅供研究）
├─ build.ps1                iOS 交叉编译与 IPA 打包
└─ tools/unpack.ps1         还原 iOS 源码

special 分支
└─ special-source/QuietSign-Special-V2.5-source.zip   Special 版完整源码包（独立包名）
```

- 普通版与 Special 版共用同一套易广金协议层，差异集中在首页、凭证页、注意事项与签到交互。
- Android 不依赖 Gradle / Android Studio；签名密钥库不入库。
- iOS 使用 clang 交叉编译，`src/Info.plist` 的 `CFBundleExecutable` 必须与产物名一致。

### 构建 Android

```powershell
cd android
pwsh ./build.ps1 -VersionName 6.8 -VersionCode 68
# 产物：build/aligned.apk，签名需单独执行 apksigner
```

### 构建 iOS

```powershell
pwsh ./tools/unpack.ps1
$env:QS_TOOLCHAIN = 'D:\path\to\toolchain'
pwsh ./build.ps1
# 产物：build/QuietSign-unsigned.ipa
```

## 🧪 服务端脚本（已废弃，仅供研究）

`server/` 曾是「云端挂机 + 零扫码」的研究实现，包含协议库、CLI、自动签到与二维码解码脚本。

> **状态：已废弃。** 不再维护、不再保证可用，也不提供使用支持。仅保留作协议流程与二维码算法的学习参考；若你需要继续使用，请自行审查代码、适配接口、加固凭据管理，并自行承担全部风险。

- `cli.py` / `ygj.py`：登录、设备对齐、课表、当前课程、提交。
- `auto.py` / `auto2.py`：研究 `startSignProcess` 与 `getPublicKey` 的调用链。
- `readqr.py` / `sign_from_qr.py`：从截图解出 `processID` 再重造码。

> 不要把 `state.json`、`qr.txt`、`*.log`、token、cookie、studentID 提交到仓库或发给他人。

## 🧩 第三方

- [ZXing](https://github.com/zxing/zxing)（Apache-2.0）：安卓端二维码解码。
- [qrcode-generator](https://github.com/kazuhikoarase/qrcode-generator)（MIT）：Scriptable 端二维码生成。
- CoreImage（iOS 系统框架）：iOS 端二维码生成。

## 📄 许可协议

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-2F6BFF.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)

本项目原创部分采用 **[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh-hans)**；完整法律文本见 [LICENSE](LICENSE)。

| | 内容 |
| --- | --- |
| **允许** | 转载、二次开发、修改、重新编译、自行签名分发（含带改动的版本） |
| **必须** | 注明原作者 **Jacky**（GitHub: [Jacky2277](https://github.com/Jacky2277)）及本仓库链接；修改过的说明改了哪里 |
| **禁止** | 任何商业用途：售卖 APK/IPA、付费下载、收费代签或代跑、广告与引流变现、捆绑进付费产品或服务 |
| **衍生作品** | 必须以同一协议 CC BY-NC-SA 4.0 发布，不得改成更宽松的协议 |

- 上游 [FUCK-GDUF](https://github.com/Jacky2277/FUCK-GDUF) 为 MIT；来自上游的 MIT 部分继续按 MIT 使用，本项目新增与重构部分按 CC BY-NC-SA 4.0。
- [ZXing](https://github.com/zxing/zxing) 按 Apache-2.0；[qrcode-generator](https://github.com/kazuhikoarase/qrcode-generator) 与 [luoyily/yiban-reverse](https://github.com/luoyily/yiban-reverse) 按 MIT。
- 「静签」名称与「Q + 对勾」图标不在 CC 许可范围内，不得用于标识衍生版本或暗示官方背书。
- 自愿打赏不视为商业使用；「付费才能用」「付款解锁功能」属于商业使用。

## ⚠️ 免责声明

本项目是个人学习与技术研究用途的非官方客户端，与广东金融学院、易班、易广金及其关联方没有任何隶属、合作或授权关系。

- **签到会真实提交考勤记录，生成凭证会真实产生出入记录**；请只用于本人账号，不要代签、批量刷记录或伪造信息。
- 使用本项目产生的考勤 / 出入记录、账号异常、设备绑定变更、纪律处分或其他后果，由使用者自行承担。
- 易班 / 易广金的接口、页面与风控可能随时变化，作者不保证功能持续可用，也不承诺维护或技术支持。
- 请遵守学校规章制度、学院要求与当地法律法规；如学校或相关平台认为本项目不当，请联系作者停止维护并删除相关内容。
- 本项目按「按原样」提供，不附带任何明示或暗示担保。

## 👥 致谢

- **[luoyily/yiban-reverse](https://github.com/luoyily/yiban-reverse)** —— 感谢作者 **luoyily**。v5.0 的「账号密码登录」完全是顺着这份逆向成果做出来的：`LoginUtil.login()` 的完整参数表、`ct/app/v/sig/apn/identify/sversion` 各字段含义、4096 位 RSA 加密公钥、`getSig()` 的取值，全都来自这个仓库。
- **[FUCK-GDUF](https://github.com/Jacky2277/FUCK-GDUF)** —— 项目起点与出入码思路来源。
