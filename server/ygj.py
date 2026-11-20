#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""易广金（GDUF）签到协议客户端 —— 纯 HTTP，不依赖任何网页/WebView。

协议对照：https://github.com/Jacky2277/FUCK-GDUF
  android/src/com/gduf/chuxiao/YgjClient.java   （登录 / 设备校验）
  android/src/com/gduf/chuxiao/SignActivity.java（课表 / 当前课程 / 提交）

四步链路：
  1. 易班 loginToken  ->  f.yiban.cn/iapp378946  ->  verify_request
  2. verify_request   ->  ygj/Handler/login.ashx ->  studentID + Cookie
  3. 设备对齐         ->  device.ashx (getStudentDevice / checkBindDevice)
  4. 签到             ->  sign.ashx (getStuInfoSchedule / getCurrentCourse / saveSignNew2)
"""

import json
import math
import os
import re
import time
import uuid
from datetime import datetime, timedelta

import requests

BASE = "https://ygj.gduf.edu.cn"
VERIFY_URL = "https://f.yiban.cn/iapp378946"
UA = ("Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 "
      "(KHTML, like Gecko) Mobile/15E148 yiban_iOS/5.2.5(5104)")
DEFAULT_MODEL = "iPhone15,2"
DEFAULT_IOS = "18.7"
APP_VERSION = "5.2.5"


class YgjError(Exception):
    """所有可预期的失败：code 用于程序判断，msg 用于给人看。"""

    def __init__(self, code, msg=""):
        self.code = code
        self.msg = msg
        super().__init__(f"{code}: {msg}" if msg else code)


def _clean(s):
    """服务端 msg 里常带 HTML/实体，清成一行文本。"""
    if not s:
        return ""
    s = re.sub(r"<[^>]+>", "", str(s))
    s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("\r", " ").replace("\n", " ")
    return re.sub(r"\s+", " ", s).strip()


def _param(url, key):
    from urllib.parse import urlparse, parse_qs
    q = parse_qs(urlparse(url).query)
    return (q.get(key) or [None])[0]


def _grade(year_in, now=None):
    now = now or datetime.now()
    try:
        y = int(str(year_in).strip()[:4])
    except Exception:
        y = now.year
    return (now.year - y + 1) if now.month >= 9 else (now.year - y)


def _week_no(first_week_date, now=None):
    now = now or datetime.now()
    try:
        d = datetime.strptime(str(first_week_date).strip(), "%Y/%m/%d %H:%M:%S")
    except Exception:
        return 1
    start = d.replace(hour=0, minute=0, second=0, microsecond=0)
    start -= timedelta(days=start.weekday())          # 对齐到周一
    today = now.replace(hour=0, minute=0, second=0, microsecond=0)
    days = (today - start).days
    return max(int(math.ceil((days + 1) / 7.0)), 1)


def _hhmm_plus(minutes, now=None):
    now = now or datetime.now()
    return (now + timedelta(minutes=minutes)).strftime("%H:%M")


def _split_qr(raw):
    """二维码拆包：最后 32 位是 key，前面全部是 content。"""
    code = (raw or "").strip()
    i = code.find("qrcode=")
    if i >= 0:
        code = code[i + len("qrcode="):]
    if len(code) <= 32:
        raise YgjError("BAD_QR", "二维码内容太短，不是有效的签到码")
    return code[-32:], code[:-32]


class YgjClient:
    def __init__(self, state_path="state.json"):
        self.state_path = state_path
        self.state = self._load()
        self.s = requests.Session()
        self.s.headers.update({"User-Agent": UA})
        for k, v in (self.state.get("cookies") or {}).items():
            self.s.cookies.set(k, v, domain="ygj.gduf.edu.cn")

    # ---------- 状态 ----------

    def _load(self):
        if os.path.exists(self.state_path):
            try:
                with open(self.state_path, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception:
                pass
        return {}

    def _save(self):
        self.state["cookies"] = requests.utils.dict_from_cookiejar(self.s.cookies)
        tmp = self.state_path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(self.state, f, ensure_ascii=False, indent=2)
        os.replace(tmp, self.state_path)
        try:
            os.chmod(self.state_path, 0o600)
        except Exception:
            pass

    @property
    def token(self):
        return self.state.get("token") or ""

    @property
    def student_id(self):
        return self.state.get("student_id") or ""

    def require_login(self):
        if not self.student_id or not self.token:
            raise YgjError("NOT_LOGGED_IN", "还没有登录，先执行 login")
        return self.student_id

    # ---------- 基础请求 ----------

    def _req(self, method, url, data=None, files=None, headers=None, allow_redirects=False, timeout=20):
        h = {"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9",
             "Accept": "text/html,application/json,application/xhtml+xml,*/*"}
        if headers:
            h.update(headers)
        try:
            r = self.s.request(method, url, data=data, files=files, headers=h,
                               allow_redirects=allow_redirects, timeout=timeout)
        except requests.RequestException as e:
            raise YgjError("NETWORK", f"请求失败：{e}")
        return r

    @staticmethod
    def _json(r):
        body = r.text or ""
        try:
            return json.loads(body)
        except Exception:
            raise YgjError("BAD_RESPONSE", f"HTTP {r.status_code}，返回不是 JSON：{body[:160]}")

    # ---------- 1. 换 verify_request ----------

    def fetch_verify(self, token):
        h = {
            "Authorization": f"Bearer {token}",
            "loginToken": token,
            "AppVersion": APP_VERSION,
            "Cookie": f"client=iOS; loginToken={token}; yiban_user_token={token}",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        }
        r = self._req("GET", VERIFY_URL, headers=h)
        loc = r.headers.get("Location") or ""
        if "verify_request=" in loc:
            return _param(loc, "verify_request")
        # 有的账号会先跳到 iapp/index，再跳一次才带 verify_request
        if loc and ("act=iapp378946" in loc or "/iapp/index" in loc):
            if loc.startswith("/"):
                loc = "https://f.yiban.cn" + loc
            elif not loc.startswith("http"):
                loc = "https://f.yiban.cn/" + loc
            r2 = self._req("GET", loc, headers=h)
            loc2 = r2.headers.get("Location") or ""
            if "verify_request=" in loc2:
                return _param(loc2, "verify_request")
        return None

    # ---------- 2. 登录取 studentID ----------

    def login(self, token):
        token = (token or "").strip()
        if len(token) < 16:
            raise YgjError("BAD_TOKEN", "token 看起来不对，应该是易班 Cookie 里 32 位字符")
        verify = self.fetch_verify(token)
        if not verify:
            raise YgjError("TOKEN_EXPIRED", "易班 loginToken 已失效（可能被其它设备登录顶掉）")

        r = self._req(
            "POST", f"{BASE}/Handler/login.ashx?flag=login",
            files={"loginToken": (None, ""), "verify_request": (None, verify)},
            headers={"X-Requested-With": "XMLHttpRequest", "Cookie": "client=iOS"},
        )
        j = self._json(r)
        if j.get("code") != 0:
            raise YgjError("LOGIN_FAILED", _clean(j.get("msg")) or "易广金登录失败")

        go = j.get("goToUrl") or ""
        sid = ""
        if "studentID=" in go:
            sid = re.split(r"[^0-9]", go.split("studentID=", 1)[1])[0]

        self.state.update({"token": token, "student_id": sid, "logged_in_at": int(time.time())})
        if not sid:
            raise YgjError("NO_STUDENT_ID", "登录成功但没解析到 studentID")

        xhr = {"X-Requested-With": "XMLHttpRequest",
               "Referer": f"{BASE}/ygj2024/student-default.aspx?studentID={sid}"}
        url = BASE + (go if go.startswith("/") else "/" + go.lstrip("/"))
        self._req("GET", url, headers=xhr)
        self._req("POST", f"{BASE}/Handler/device.ashx?flag=getNotice",
                  data={"studentID": sid}, headers=xhr)
        self.bind_device(xhr)
        self._save()
        return sid

    # ---------- 3. 设备对齐（借壳已绑定的设备） ----------

    def bind_device(self, xhr=None, auto=True):
        sid = self.require_login()
        xhr = xhr or {"X-Requested-With": "XMLHttpRequest"}
        dev = self.state.get("device") or {}
        dev.setdefault("uuid", str(uuid.uuid4()))
        dev.setdefault("deviceModel", DEFAULT_MODEL)
        dev.setdefault("systemVersion", DEFAULT_IOS)
        dev.setdefault("appVersion", APP_VERSION)

        try:
            r = self._req("POST", f"{BASE}/Handler/device.ashx?flag=getStudentDevice",
                          data={"studentID": sid}, headers=xhr)
            for o in (self._json(r).get("data") or []):
                if str(o.get("state")) == "1":
                    dev = {
                        "uuid": o.get("uuid") or dev["uuid"],
                        "deviceModel": o.get("deviceModel") or dev["deviceModel"],
                        "systemVersion": o.get("systemVersion") or dev["systemVersion"],
                        "appVersion": o.get("appVersion") or dev["appVersion"],
                    }
                    break
        except YgjError:
            pass

        payload = {"deviceData": json.dumps(dev, ensure_ascii=False, separators=(",", ":")),
                   "autoBind": "false"}
        j = self._json(self._req("POST", f"{BASE}/Handler/device.ashx?flag=checkBindDevice",
                                 data=payload, headers=xhr))

        if j.get("code") == 1:
            if not auto:
                raise YgjError("DEVICE_MISMATCH", _clean(j.get("msg")) or "设备绑定与账号不一致")
            dev["uuid"] = str(uuid.uuid4())
            dev["deviceModel"] = DEFAULT_MODEL
            dev["systemVersion"] = DEFAULT_IOS
            j2 = self._json(self._req(
                "POST", f"{BASE}/Handler/device.ashx?flag=checkBindDevice",
                data={"deviceData": json.dumps(dev, ensure_ascii=False, separators=(",", ":")),
                      "autoBind": "true"}, headers=xhr))
            if j2.get("code") != 0:
                raise YgjError("DEVICE_BIND_FAILED", _clean(j2.get("msg")) or "设备绑定失败")
        elif j.get("code") != 0:
            raise YgjError("DEVICE_CHECK_FAILED", _clean(j.get("msg")) or "设备校验失败")

        self.state["device"] = dev
        self._save()
        return dev

    # ---------- 4. 课表 / 当前课程 / 提交 ----------

    def schedule(self):
        sid = self.require_login()
        r = self._req("GET", f"{BASE}/Handler/sign.ashx?flag=getStuInfoSchedule&studentID={sid}")
        j = self._json(r)
        if j.get("code") != 0:
            raise YgjError("SCHEDULE_FAILED", _clean(j.get("msg")) or "获取课表失败")
        return j.get("data") or {}

    @staticmethod
    def _filter_schedule(data, grade, school):
        rows = []
        for it in (data.get("scheduleInfo") or []):
            if str(it.get("grade")) != str(grade):
                continue
            if school == "广州校本部":
                ts, te = it.get("timeS"), it.get("timeE")
            elif school == "肇庆校区":
                ts, te = it.get("zqTimeS"), it.get("zqTimeE")
            else:
                ts, te = it.get("qyTimeS"), it.get("qyTimeE")
            if not ts or not te:
                continue
            rows.append({"timeS": ts, "timeE": te,
                         "jc": str(it.get("lessonTime", "")).replace("第", "").replace("小节", "")})
        return rows

    def current_course(self, data=None, now=None):
        sid = self.require_login()
        d = data or self.schedule()
        year_in = d.get("yearIn", "")
        school = d.get("school", "清远校区")
        start_min = int(d.get("startSignMin") or 10)
        xn, xnxq = d.get("xn", ""), d.get("xnxq", "")
        week = _week_no(d.get("firstWeekDate", ""), now)
        g = _grade(year_in, now)

        rows = self._filter_schedule(d, g, school)
        hhmm = _hhmm_plus(start_min, now)
        jc, start_time = "-1", "00:00"
        for it in rows:
            if it["timeS"] <= hhmm <= it["timeE"]:
                jc, start_time = it["jc"], it["timeS"]
                break

        params = {
            "studentID": sid, "grade": str(g), "thisWeek": str(week), "jc": jc,
            "xn": xn, "xnxq": xnxq, "startSignMin": str(start_min),
            "stopSignMin": str(d.get("stopSignMin", 15)), "lateSignMin": str(d.get("lateSignMin", 2)),
            "startTime": start_time, "school": school,
        }
        j = self._json(self._req("POST", f"{BASE}/Handler/sign.ashx?flag=getCurrentCourse",
                                 data=params, headers={"X-Requested-With": "XMLHttpRequest"}))
        if j.get("code") != 0:
            raise YgjError("COURSE_FAILED", _clean(j.get("msg")) or "获取当前课程失败")

        return {
            "school": school, "grade": g, "week": week, "jc": jc, "startTime": start_time,
            "startSignMin": start_min, "courses": j.get("data") or [],
        }

    def submit(self, qr):
        sid = self.require_login()
        key, content = _split_qr(qr)
        j = self._json(self._req(
            "POST", f"{BASE}/Handler/sign.ashx?flag=saveSignNew2&studentID={sid}",
            data={"key": key, "content": content, "studentID": sid, "type": "1"},
            headers={"X-Requested-With": "XMLHttpRequest"}))
        code = j.get("code")
        return {"ok": code in (0, 2), "code": code, "msg": _clean(j.get("msg")), "raw": j}