# 静签 Android 端源码

手写构建，不依赖 Gradle / Android Studio，用 SDK 自带的 `aapt2 + javac + d8 + zipalign + apksigner` 编译打包。

> ⚠️ 服务端脚本 `server/` 已废弃，仅供协议研究参考；本目录只负责 Android 客户端。

## 两条版本线

| | 普通版 | Special 版 |
| --- | --- | --- |
| 分支 | `main` | `special` |
| 包名 | `com.gduf.chuxiao` | `com.gduf.chuxiao.special` |
| 应用名 | 静签 | 静签 Special |
| 源码 | `src/com/gduf/chuxiao/`，8 个源文件 | `special-source/QuietSign-Special-V2.5-source.zip`，10 个源文件 |
| 差异 | 原版首页、凭证卡与批量签到 | 重做首页 / 凭证页 / 注意事项，签到底部 sheet |

两个包名不同，可以同时安装；应用数据、token 与登录状态互相独立。

## 结构

```text
AndroidManifest.xml          应用清单
build.ps1                    Windows 构建脚本（compile → link → javac → d8 → 打包 → zipalign）
build.sh                     Linux / macOS 等价脚本
keystore.properties.example  签名配置模板（复制为 keystore.properties 后填自己的口令）
src/com/gduf/chuxiao/        普通版 8 个源文件
special-source/               Special 版完整源码包（仅 special 分支，10 个源文件）
libs/zxing-core-3.5.3.jar    ZXing（二维码编解码，Apache-2.0）
res/anim/                    转场：slide_up_in / slide_down_out / stay
res/values/                  主题色、SignTheme / SheetTheme 动画样式
res/drawable/                图标（自适应前景 + 传统图标）
res/mipmap-*/                各密度图标（48 / 72 / 96 / 144 / 192）
res/mipmap-anydpi-v26/       自适应图标描述
```

## 普通版模块

| 文件 | 作用 |
| --- | --- |
| `MainActivity.java` | 首页：设备状态卡、凭证卡、二维码 `CornerFrame` 四角 L 框、运行日志入口、注意事项、自绘 M3 弹窗、深浅主题 |
| `SignActivity.java` | 签到考勤页：课程列表、批量签到、扫码签到、下滑退出转场 |
| `YgjClient.java` | 易广金接口层：登录、课表、当前课程、造码、提交、设备状态 |
| `PwdLoginActivity.java` | 易班账号密码登录（本地 RSA PKCS1 加密后调 `passport/login`） |
| `LoginWebActivity.java` | 网页登录兜底 |
| `ScanActivity.java` | 扫码（ZXing） |
| `QrGen.java` | 本地生成签到二维码 |
| `RootToken.java` | root 环境下一键读取本机 `loginToken` |

## Special 版模块

Special 在普通版基础上换了首页与交互，协议层保持一致，新增：

| 文件 | 作用 |
| --- | --- |
| `special/MainActivity.java` | 新首页：去掉底部双主按钮与右上 `···`，新增关于作者、按课程分段的今日进度、完整注意事项与三状态 chip |
| `special/SignActivity.java` | 底部「选择课程」sheet：默认只勾当前可签课程，批量跳过未开始 / 已截止，成功回传首页绿色条 |
| `special/LogActivity.java` / `special/Logx.java` | Special 版运行日志页与日志工具 |

其余 `PwdLoginActivity`、`LoginWebActivity`、`ScanActivity`、`QrGen`、`RootToken`、`YgjClient` 与普通版同构。

## 构建

见仓库根目录 `README.md` 的「构建 → Android」一节。要点：

- 需要 Android SDK（`build-tools`、`platforms/android-*`）与 JDK 11 以上；
- `build.ps1` 只产出 `build/aligned.apk`，签名是单独一步；
- **签名密钥库不入库**：仓库里没有 `quietsign-release.keystore`，请按 `keystore.properties.example` 配置自己的 keystore 后再签；
- 普通版与 Special 版使用不同包名，但可以复用同一张签名证书；
- `res/mipmap-*`、`res/drawable` 中的图标可直接复用；Special 版图标为蓝底白色「清单 + 勾」。

```powershell
cd android
pwsh ./build.ps1 -VersionName 6.8 -VersionCode 68
# 产物：build/aligned.apk
```

## 第三方

- [ZXing](https://github.com/zxing/zxing)（Apache-2.0）：安卓端二维码解码。
- [qrcode-generator](https://github.com/kazuhikoarase/qrcode-generator)（MIT）：Scriptable 端二维码生成。
- CoreImage（iOS 系统框架）：iOS 端二维码生成。

## 许可

原创部分与仓库其他部分一致：CC BY-NC-SA 4.0，见根目录 [`LICENSE`](../LICENSE)。上游 FUCK-GDUF 的 MIT 部分继续按 MIT；ZXing 按 Apache-2.0。
