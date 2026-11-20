# 易广金签到协议

域名：`https://ygj.gduf.edu.cn`
入口页面：`/ygj2024/sign/student-sign.aspx`（无会话时返回 0 字节）
所有接口：`/Handler/sign.ashx?flag=<name>`，除 `getStuInfoSchedule` / `checkStatus` 走 GET 外均为 POST 表单。

## 1. 会话

`loginToken`（32 位）→ `https://f.yiban.cn/iapp378946` 换 `verify_request` → `login.ashx?flag=login`（multipart）→ 拿到 `ASP.NET_SessionId` 等 Cookie，返回 `goToUrl` 中带 `studentID`。
随后依次调 `getNotice` / `getStudentDevice` / `checkBindDevice` 把设备指纹对齐。

设备信息可直接指定（UA + UUID + 型号 + 系统版本），不校验真实性。

## 2. 课表

`GET sign.ashx?flag=getStuInfoSchedule&studentID=...`

返回 `xn` `xnxq` `startSignMin`(10) `stopSignMin`(15) `lateSignMin`(2) `firstWeekDate` `school` `yearIn` 以及 `scheduleInfo[]`（每节课的 `grade` `lessonTime` `timeS/timeE`(校本部) `qyTimeS/qyTimeE`(清远) `zqTimeS/zqTimeE`(肇庆)）。

周次用 `firstWeekDate` 推：找到那周的周一，算到今天的天数 `days`，`week = ceil((days+1)/7)`。
年级用 `yearIn` 推：`month>=9 ? year-yearIn+1 : year-yearIn`。

## 3. 课程对象

`POST sign.ashx?flag=getCurrentCourse`，表单：

```
studentID grade thisWeek jc xn xnxq startSignMin stopSignMin lateSignMin startTime school
```

**这个接口不看真实时间**，完全按传进去的 `jc + startTime` 返回对应节次的课程。字段：

```
KKDM KCMC SKJSMC DDJ BJMC XN XQ SKXQ startTime endTime stopScanTime weekNum
```

## 4. 二维码

```js
key      = getPublicKey()                       // 32 位 hex，每次调用都变
plain    = JSON.stringify(pid + ',' + now)      // now 形如 2026/11/20 14:00:33
content  = AES-256-ECB(plain, key) + base64     // PKCS7 补码
qrcode   = content + key                        // key 明写在码尾
```

提交（与真扫码完全一致）：

```
POST sign.ashx?flag=saveSignNew2&studentID=...
key=<末32位> content=<前面全部> studentID=... type=1
```

`code == 0` 或 `2` 视为成功。

## 5. 三个决定性事实

1. **`startSignProcess` 不做身份校验**：`checkDuty` 对普通学生返回 `isDuty:false`，但该接口依旧返回 `{"code":0,"msg":"成功","data":"<pid>"}`。
2. **幂等**：同一节课反复调，返回同一个 pid（不会撞车）。
3. **提交只校验上限**：`now > stopScanTime` → `签到时间已过`；`now < stopScanTime` → `签到成功`，提前多久都行。

## 6. 时间窗口

- 开窗 = 上课时间 − `startSignMin`（默认 10 分钟）
- 截止 = `stopScanTime` = 上课时间 + `stopSignMin`（默认 15 分钟）

注意这只是“正常扫码时段”的产品定义，服务端并不强制下限；提前提交也能成功，只是记录里的签到时间会偏早。

## 7. 设备与风控

`startSignProcess` 请求体里没有 `studentID`，服务端只能靠会话识别调用者；客户端无法查回“是谁发起的”。提交端参数与真扫码完全一致，密码学上不可区分。真正显眼的痕迹是**签到时间**和**请求来源 IP**。
