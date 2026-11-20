#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""零扫码全自动签到 v2：自己取 processID → 现造码 → 提交

和 auto.py 的区别：不再需要任何人扫码。processID 由 startSignProcess 直接取回
（该接口没有时间校验，且对同一节课幂等：谁先调都拿到同一个 id）。
"""
import argparse, json, sys, time
from datetime import datetime, timezone, timedelta

sys.path.insert(0, ".")
from ygj import YgjClient, BASE, YgjError, _grade, _week_no
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad
import base64

CST = timezone(timedelta(hours=8))
LOG = "auto2.log"


def log(msg):
    line = f"[{datetime.now(CST):%H:%M:%S}] {msg}"
    print(line, flush=True)
    try:
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass


def now_str(d=None):
    d = d or datetime.now(CST)
    return f"{d.year}/{d.month}/{d.day} {d.hour:02d}:{d.minute:02d}:{d.second:02d}"


def build_qr(pid, key):
    plain = json.dumps(f"{pid},{now_str()}", ensure_ascii=False)
    raw = AES.new(key.encode(), AES.MODE_ECB).encrypt(pad(plain.encode(), 16))
    return f"{BASE}/index.aspx?qrcode={base64.b64encode(raw).decode()}{key}", plain


def pick_jc(sched, lead):
    now = datetime.now(CST) + timedelta(minutes=lead)
    hhmm = now.strftime("%H:%M")
    rows = YgjClient._filter_schedule(sched, _grade(sched.get("yearIn")), sched.get("school", "清远校区"))
    for it in rows:
        if it["timeS"] <= hhmm <= it["timeE"]:
            return it["jc"], it["timeS"]
    return "-1", "00:00"


def get_course(c, sched, jc=None, start_time=None, lead=10):
    if jc is None:
        jc, start_time = pick_jc(sched, lead)
    elif start_time is None:
        # 指定了节次，就从课表里补齐这一节的开始时间
        rows = YgjClient._filter_schedule(sched, _grade(sched.get("yearIn")),
                                          sched.get("school", "清远校区"))
        for it in rows:
            if str(it["jc"]) == str(jc):
                start_time = it["timeS"]
                break
    p = {"studentID": c.student_id, "grade": str(_grade(sched.get("yearIn"))),
         "thisWeek": str(_week_no(sched.get("firstWeekDate"))), "jc": jc,
         "xn": sched.get("xn"), "xnxq": sched.get("xnxq"),
         "startSignMin": str(sched.get("startSignMin", 10)),
         "stopSignMin": str(sched.get("stopSignMin", 15)),
         "lateSignMin": str(sched.get("lateSignMin", 2)),
         "startTime": start_time, "school": sched.get("school")}
    j = c._json(c._req("POST", f"{BASE}/Handler/sign.ashx?flag=getCurrentCourse", data=p,
                       headers={"X-Requested-With": "XMLHttpRequest"}, timeout=20))
    return (j.get("data") or [None])[0], jc


def start_process(c, cu):
    d = {"kkdm": cu["KKDM"], "lessonNum": cu["DDJ"], "xn": cu["XN"], "xq": cu["XQ"],
         "weekNum": cu["weekNum"], "skxq": cu["SKXQ"], "kcmc": cu["KCMC"], "sksj": cu["startTime"]}
    r = c._json(c._req("POST", f"{BASE}/Handler/sign.ashx?flag=startSignProcess", data=d,
                       headers={"X-Requested-With": "XMLHttpRequest"}, timeout=20))
    if r.get("code") != 0:
        raise YgjError("START_FAILED", r.get("msg") or "取 processID 失败")
    return r["data"]


def fresh_key(c):
    return c._json(c._req("GET", f"{BASE}/Handler/sign.ashx?flag=getPublicKey",
                          headers={"X-Requested-With": "XMLHttpRequest"}))["data"]["key"]


def check(c, cu):
    return c._json(c._req("GET", f"{BASE}/Handler/sign.ashx?flag=checkStatus",
                          data={"studentID": c.student_id, "kkdm": cu["KKDM"], "ddj": cu["DDJ"],
                                "weekNum": cu["weekNum"], "skxq": cu["SKXQ"],
                                "xn": cu["XN"], "xq": cu["XQ"]},
                          headers={"X-Requested-With": "XMLHttpRequest"}, timeout=20))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--wait-until", default="", help="先睡到这个时刻，如 13:58")
    ap.add_argument("--until", default="", help="最晚跑到这个时刻，默认 25 分钟后")
    ap.add_argument("--interval", type=int, default=25)
    ap.add_argument("--jc", default="", help="直接指定节次，如 6")
    ap.add_argument("--pid", default="", help="已知 processID 时直接用，不再调 startSignProcess")
    ap.add_argument("--lead", type=int, default=10, help="提前多少分钟找节次")
    a = ap.parse_args()

    c = YgjClient("state.json")
    c.require_login()
    sched = c.schedule()

    if a.wait_until:
        h, m = a.wait_until.split(":")
        t = datetime.now(CST).replace(hour=int(h), minute=int(m), second=0, microsecond=0)
        w = (t - datetime.now(CST)).total_seconds()
        if w > 0:
            log(f"待命，{a.wait_until} 开始动手（{int(w)} 秒）")
            time.sleep(w)

    if a.until:
        h, m = a.until.split(":")
        stop = datetime.now(CST).replace(hour=int(h), minute=int(m), second=0)
    else:
        stop = datetime.now(CST) + timedelta(minutes=25)

    cu, jc = get_course(c, sched, jc=(a.jc or None), lead=a.lead)
    if not cu:
        log(f"节次 {jc} 今天没有课，退出")
        return
    log(f"目标：{cu['KCMC']} 节次{cu['DDJ']} 开始{cu['startTime']} 截止{cu['stopScanTime']}")

    pid = a.pid or start_process(c, cu)
    log(f"processID={pid}" + ("（复用）" if a.pid else "（本次自取）"))

    n = 0
    while datetime.now(CST) < stop:
        n += 1
        try:
            key = fresh_key(c)
            qr, plain = build_qr(pid, key)
            r = c.submit(qr)
            ok = r.get("ok") or str(r.get("code")) in ("0", "2")
            log(f"#{n} {plain} → code={r.get('code')} msg={r.get('msg')}")
            if ok:
                log(f"签到成功 checkStatus={json.dumps(check(c, cu), ensure_ascii=False)}")
                return
            if "已签" in str(r.get("msg")):
                log("服务端说已签到，结束")
                return
        except YgjError as e:
            log(f"接口错误 {e.code}: {e.msg}")
            if e.code in ("TOKEN_EXPIRED", "NOT_LOGGED_IN"):
                return
        except Exception as e:
            log(f"异常 {e!r}")
        time.sleep(a.interval)
    log("窗口结束仍未成功")


if __name__ == "__main__":
    main()