# 静签服务端（QuietSign Server）

> 把易广金的「签到」做成一条纯 HTTP 流程：**贴一次易班 token，之后到点在服务器上盯课、自己造码、自己提交。**
> 协议来自 [Jacky2277/FUCK-GDUF](https://github.com/Jacky2277/FUCK-GDUF) 的 Android 实现，逐字段对齐，不依赖任何网页/WebView。

---

## ⚠️ 先读这段

- 这里的 token 是**易班 App 的登录态本身**，等同于账号。存到服务器 = 账号交到服务器，请只放在你自己的机器上，`state.json` 权限 600。
- 易广金是**单设备绑定 + 单会话**：本服务跑起来后占用的设备指纹会参与 `checkBindDevice`，你在手机上重新登录易班会让 token 立即失效，反之亦然。
- 签到会在学校系统里**真实生成记录**。连续刷、替他人签、伪造记录都可能触发风控或纪律问题，后果自负。
- 非公开接口，学校改版即失效。

---

## 协议链路

```
① 易班 loginToken（32 位，Cookie 里 loginToken= 后面的值）
      ↓ GET https://f.yiban.cn/iapp378946
      ↓      Authorization: Bearer <token> / loginToken: <token>
      ↓      Cookie: client=iOS; loginToken=<token>; yiban_user_token=<token>
      ↓ 302 Location 里截 verify_request
② POST https://ygj.gduf.edu.cn/Handler/login.ashx?flag=login   (multipart)
      ↓ {code:0, goToUrl:"...studentID=xxxx"}
③ GET goToUrl  →  POST device.ashx?flag=getNotice
      ↓ POST device.ashx?flag=getStudentDevice   （读出服务端已绑定设备）
      ↓ POST device.ashx?flag=checkBindDevice    （借壳绑定，失败才 autoBind=true）
④ GET  sign.ashx?flag=getStuInfoSchedule&studentID=<sid>
   POST sign.ashx?flag=getCurrentCourse
   POST sign.ashx?flag=startSignProcess      （自取 processID，零扫码用）
   GET  sign.ashx?flag=getPublicKey          （现取 AES key）
   POST sign.ashx?flag=saveSignNew2&studentID=<sid>   key=后32位  content=前缀  type=1
```

二维码算法：`JSON.stringify("pid,yyyy/M/d HH:mm:ss")` → AES-256-ECB/PKCS7 → base64 → **拼上 key**。
所以只要拿到 `pid` 和一把新 `key`，不需要任何人扫码也能自己造出一张能提交的有效码。

---

## 快速开始

```bash
bash install.sh          # 建 .venv 装依赖（requests + pycryptodome）
. .venv/bin/activate

# 1) 登录（token 从易班 App 抓包拿，见下）
python3 cli.py login --token <32位loginToken>

# 2) 看看现在有没有课要签
python3 cli.py status

# 3) 零扫码自签（到点自己取 pid、自己造码、自己提交）
python3 auto2.py --wait-until 13:58 --until 14:20
```

### token 怎么拿

手机上用 ProxyPin / Stream 抓包，打开 易班 → 易广金 → 出入申报/签到 任意页面，
找 `f.yiban.cn` 的请求，请求头 `Cookie` 里 `loginToken=` 后面那 32 位就是。

---

## 运行形态

| 形态 | 命令 | 依赖扫码 |
|---|---|---|
| 手动提交 | `python3 cli.py sign --qr "..."` | 需一张有效二维码 |
| 盯课等码 | `python3 cli.py watch` | 需别人发码 |
| 接口收码 | `python3 cli.py serve --immediate --key <自定义>` | 需别人发码 |
| **零扫码自签** | `python3 auto2.py --jc 6 --until 14:20` | **不需要** |
| 截图重生码 | `python3 sign_from_qr.py shot.jpg` | 截图可过期/模糊 |

### auto.py vs auto2.py

- `auto.py`（v1）：先用假 pid 试提交（零副作用），失败才需要真 pid——要么有人扫码，要么 `--allow-start` 自己开考勤。
- `auto2.py`（v2）：直接调 `startSignProcess` 自取 pid。该接口**不校验身份、不校验时间、对同一节课幂等**，所以彻底摆脱对扫码的依赖；循环里每轮重新 `getPublicKey`，现取 key 现造码，时戳始终新鲜。

### watch

- 每 `--interval`（默认 20s）查一次 `getCurrentCourse`
- 课表缓存 `--refresh`（默认 10 分钟）
- 同一 (班级, 节次, 起始时间) 只提交一次
- token 失效会自动停止（返回码 2）

### serve

```bash
python3 cli.py serve --host 127.0.0.1 --port 8765 --key mysecret --immediate
curl -X POST http://127.0.0.1:8765/qr -H "X-Key: mysecret" -d "<二维码内容>"
curl http://127.0.0.1:8765/status
```

如果要绑到公网，**务必带 `--key`**，并且只在受信网络里开。

---

## 常驻部署（systemd）

```bash
sudo cp deploy/quietsign.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now quietsign
sudo journalctl -u quietsign -f
```

或者简单点用 cron 在窗口前后各跑一次（不如 `auto2.py` 轮询稳，仅举例）：

```
*/1 8-9,13-14,15-16,18-19 * * 1-5  cd /opt/quietsign && .venv/bin/python auto2.py --span 3600 >/dev/null 2>&1
```

---

## 错误码

| code | 含义 | 处理 |
|---|---|---|
| `NOT_LOGGED_IN` | 还没 login | 先 `login --token` |
| `BAD_TOKEN` | token 长度/形状不对 | 重新抓 |
| `TOKEN_EXPIRED` | 易班 token 失效（被顶号/过期） | 重新抓 token |
| `LOGIN_FAILED` | 易广金登录被拒 | 看 msg，多为 verify_request 过期 |
| `DEVICE_MISMATCH` | 设备绑定不一致 | 代码会自动 autoBind 一次 |
| `DEVICE_BIND_FAILED` | 绑定被服务端拒绝 | 可能触发了绑定次数限制，隔一会儿再试 |
| `SCHEDULE_FAILED` / `COURSE_FAILED` | 课表/当前课程接口异常 | 多为登录态问题，重新 login |
| `START_FAILED` | 取 processID 失败 | 换节次/确认今天有这门课 |
| `BAD_QR` | 二维码内容不合法 | 检查是不是只复制了一半 |
| `NETWORK` | 网络失败 | 重试 |

`saveSignNew2` 返回 `code=0` 或 `2` 都算成功（2 通常是"已签过"）。

---

## 文件

```
ygj.py            协议实现（登录 / 设备对齐 / 课表 / 当前课程 / 提交）
cli.py            命令行：login / schedule / status / sign / watch / serve
auto.py           v1：假 pid 试提交，失败才需真 pid（需扫码或 --allow-start）
auto2.py          v2：自取 processID + 现造码，零扫码
readqr.py         从二维码截图解出 processID
sign_from_qr.py   C 方案：拿一张过期截图重生码并提交
install.sh        一键建 venv 装依赖
deploy/quietsign.service   systemd 单元
state.json        运行时生成：token、Cookie、studentID、设备指纹（600 权限，勿入库）
qr.txt            运行时生成：待提交的二维码内容（勿入库）
```

## 与 App 版的关系

App 版（仓库根目录 `src/`）跑在自己手机上，手机开机联网才能签；
服务端版（本目录）跑在服务器/挂机主机上，本机关机也不影响。两者共用同一套协议，各自独立登录，**不要同时用**——易广金单会话，会互相顶掉。