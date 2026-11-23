# 静签 Android 端源码

手写构建，不依赖 Gradle / Android Studio，用 SDK 自带的 `aapt2 + javac + d8 + zipalign + apksigner` 编译打包。

## 结构

```
AndroidManifest.xml          应用清单（包名 com.gduf.chuxiao）
build.ps1                    Windows 构建脚本（compile → link → javac → d8 → 打包 → zipalign）
build.sh                     Linux / macOS 等价脚本
keystore.properties.example  签名配置模板（复制为 keystore.properties 后填自己的口令）
src/com/gduf/chuxiao/        8 个源文件
libs/zxing-core-3.5.3.jar    ZXing（二维码编解码）
res/anim/                    转场：slide_up_in / slide_down_out / stay
res/values/                  主题色与 SignTheme 动画样式
res/drawable/                新图标（自适应前景 + 传统图标）
res/mipmap-*/               各密度图标（48 / 72 / 96 / 144 / 192）
res/mipmap-anydpi-v26/       自适应图标描述
```

## 模块

| 文件 | 作用 |
| --- | --- |
| `MainActivity.java` | 首页：凭证卡、二维码（`CornerFrame` 四角 L 框）、双主按钮、自绘 M3 弹窗、深浅主题 |
| `SignActivity.java` | 签到考勤页：课程列表、批量签到、扫码签到、下滑退出转场 |
| `YgjClient.java` | 易广金接口层：登录、课表、当前课程、造码、提交 |
| `PwdLoginActivity.java` | 易班账号密码登录（本地 RSA PKCS1 加密后调 passport/login） |
| `LoginWebActivity.java` | 网页登录兜底 |
| `ScanActivity.java` | 扫码（ZXing） |
| `QrGen.java` | 本地生成签到二维码 |
| `RootToken.java` | root 环境下一键读取本机 loginToken |

## 构建

见仓库根目录 `README.md` 的「构建 → Android」一节。几个要点：

- 需要 Android SDK（`build-tools`、`platforms/android-*`）与 JDK 11 以上；
- `build.ps1` 只产出 `build/aligned.apk`，签名是单独一步；
- **签名密钥库不入库**：仓库里没有 `quietsign-release.keystore`，请自行生成 keystore 并按 `keystore.properties.example` 配置后再签名；
- `res/mipmap-*`、`res/drawable` 里的图标即仓库所用的「Q + 对勾」图标，可直接复用。

## 许可

与本仓库其他部分一致：CC BY-NC-SA 4.0，见根目录 [`LICENSE`](../LICENSE)。
