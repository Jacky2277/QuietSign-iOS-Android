#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""零扫码自动签到 —— 复刻网页端二维码生成

链路：getPublicKey → AES-256-ECB/PKCS7(JSON.stringify("pid,时间")) → base64+key
     → POST saveSignNew2

用法：
  python3 auto.py --at 09:45 --pid 999999          # 只试提交，不开考勤（零副作用）
  python3 auto.py --at 09:45 --allow-start          # 失败时自己开考勤拿真 pid
  python3 auto.py --once --pid 156092               # 立刻试一次
"""
import argparse, base64, json, sys, time
from datetime import datetime, timezone, timedelta

sys.path.insert(0, ".")
from ygj import YgjClient, BASE, YgjError, _grade, _week_no
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad

CST = timezone(timedelta(hours=8))
LOG = "auto.log"


def log(msg):
    line = f"[{datetime.now(CST):%m-%d %H:%M:%S}] {msg}"
    print(line, flush=True)
    with open(LOG, "a", encoding="utf-8") as f:
        f.write(line + "\n")


def now_str(d=None):
    d = d or datetime.now(CST)
    return f"{d.year}/{d.month}/{d.day} {d.hour:02d}:{d.minute:02d}:{d.second:02d}"


def build_qr(pid, key, t=None):
    plain = json.dumps(f"{pid},{t or now_str()}", ensure_ascii=False)
    raw = AES.new(key.encode(), AES.MODE_ECB).encrypt(pad(plain.encode(), 16))
    return f"{BASE}/index.aspx?qrcode={base64.b64encode(raw).decode()}{key}", plain


def pick_section(c, sched, lead_min):
    """镜像页面的 calcLessonTime：用 当前时间+提前分钟 去找节次"""
    now = datetime.now(CST) + timedelta(minutes=lead_min)
    hhmm = now.strftime("%H:%M")
    rows = c._filter_schedule(sched, _grade(sched.get("yearIn")), sched.get("school", "清远校区"))
    for it in rows:
        if it["timeS"] <= hhmm <= it["timeE"]:
            return it["jc"], it["timeS"]
    return "-1", "00:00"


def course_now(c, sched):
    jc, st = pick_section(c, sched, int(sched.get("startSignMin") or 10))
    params = {
        "studentID": c.student_id, "grade": str(_grade(sched.get("yearIn"))),
        "thisWeek": str(_week_no(sched.get("firstWeekDate"))), "jc": jc,
        "xn": sched.get("xn"), "xnxq": sched.get("xnxq"),
        "startSignMin": str(sched.get("startSignMin", 10)),
        "stopSignMin": str(sched.get("stopSignMin", 15)),
        "lateSignMin": str(sched.get("lateSignMin", 2)),
        "startTime": st, "school": sched.get("school"),
    }
    j = c._json(c._req("POST", f"{BASE}/Handler/sign.ashx?flag=getCurrentCourse", data=params,
                       headers={"X-Requested-With": "XMLHttpRequest"}, timeout=20))
    return j, jc, st


def fresh_key(c):
    return c._json(c._req("GET", f"{BASE}/Handler/sign.ashx?flag=getPublicKey",
                          headers={"X-Requested-With": "XMLHttpRequest"}))["data"]["key"]


def start_process(c, course):
    d = {"kkdm": course["KKDM"], "lessonNum": course["DDJ"], "xn": course["XN"], "xq": course["XQ"],
         "weekNum": course["weekNum"], "skxq": course["SKXQ"], "kcmc": course["KCMC"],
         "sksj": course["startTime"]}
    return c._json(c._req("POST", f"{BASE}/Handler/sign.ashx?flag=startSignProcess", data=d,
                          headers={"X-Requested-With": "XMLHttpRequest"}, timeout=20))


def check(c, course):
    return c._json(c._req("GET", f"{BASE}/Handler/sign.ashx?flag=checkStatus",
                          data={"studentID": c.student_id, "kkdm": course["KKDM"], "ddj": course["DDJ"],
                                "weekNum": course["weekNum"], "skxq": course["SKXQ"],
                                "xn": course["XN"], "xq": course["XQ"]},
                          headers={"X-Requested-With": "XMLHttpRequest"}, timeout=20))


def attempt(c, pid, tag):
    key = fresh_key(c)                      # key 每次调用都变，必须现取现用
    qr, plain = build_qr(pid, key)
    res = c.submit(qr)
    log(f"[{tag}] pid={pid} 明文={plain} → code={res['code']} msg={res['msg']}")
    return res


def run_window(c, sched, pid, allow_start):
    j, jc, st = course_now(c, sched)
    if j.get("code") != 0 or not j.get("data"):
        return None
    course = j["data"][0]
    log(f"发现课程 {course['KCMC']} jc={jc} start={st} stopScan={course['stopScanTime']}")

    # 一级：用不存在的 pid 试提交（零副作用）
    for probe in (pid,):
        r = attempt(c, probe, "一级/无副作用")
        if r["ok"]:
            log(f"一级即成功；checkStatus={json.dumps(check(c, course), ensure_ascii=False)}")
            return r
    log("一级失败：pid 必须对应真实考勤流程")

    # 二级：自己开考勤（需显式授权）
    if not allow_start:
        log("二级已跳过（未授权 --allow-start）")
        return None
    sp = start_process(c, course)
    if sp.get("code") != 0:
        log(f"开考勤失败：{sp}")
        return None
    real_pid = sp["data"]
    log(f"开考勤成功 processID={real_pid}")
    for delay in (0, 5, 15):          # 时间戳可能与服务端有时差，多试几个时点
        if delay:
            time.sleep(delay)
        r = attempt(c, real_pid, "二级/自开考勤")
        if r["ok"]:
            log(f"二级成功；checkStatus={json.dumps(check(c, course), ensure_ascii=False)}")
            return r
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--at", default="", help="等到这个 CST 时刻再开始，如 09:45")
    ap.add_argument("--until", default="", help="最晚跑到这个时刻")
    ap.add_argument("--pid", type=int, default=999998)
    ap.add_argument("--interval", type=int, default=30)
    ap.add_argument("--span", type=int, default=0, help="从此刻起只轮询这么多秒（云端定时任务用）")
    ap.add_argument("--allow-start", action="store_true", help="允许自己开考勤拿真实 pid")
    ap.add_argument("--once", action="store_true")
    a = ap.parse_args()

    c = YgjClient("state.json")
    c.require_login()
    sched = c.schedule()

    if a.once:
        j, jc, st = course_now(c, sched)
        log(f"当前探测 jc={jc} start={st} code={j.get('code')} msg={j.get('msg')}")
        if j.get("data"):
            log(f"课程 {j['data'][0]['KCMC']}，进入提交")
            log(f"结果：{run_window(c, sched, a.pid, a.allow_start)}")
        return

    if a.at:
        h, m = a.at.split(":")
        target = datetime.now(CST).replace(hour=int(h), minute=int(m), second=0, microsecond=0)
        wait = (target - datetime.now(CST)).total_seconds()
        if wait > 0:
            log(f"挂起，等待 {a.at}（{int(wait)} 秒）")
            time.sleep(wait)

    if a.span > 0:
        stop_at = datetime.now(CST) + timedelta(seconds=a.span)
    else:
        stop_at = datetime.now(CST) + timedelta(minutes=40)
        if a.until:
            h, m = a.until.split(":")
            stop_at = datetime.now(CST).replace(hour=int(h), minute=int(m), second=0)

    log(f"开始轮询，间隔 {a.interval}s，直到 {stop_at:%H:%M}，allow_start={a.allow_start}")
    while datetime.now(CST) < stop_at:
        try:
            r = run_window(c, sched, a.pid, a.allow_start)
            if r and r["ok"]:
                log("签到成功，退出")
                return
        except YgjError as e:
            log(f"接口错误 {e.code}: {e.msg}")
            if e.code in ("TOKEN_EXPIRED", "NOT_LOGGED_IN"):
                return
        except Exception as e:
            log(f"异常 {e!r}")
        time.sleep(a.interval)
    log("窗口结束，未成功")


if __name__ == "__main__":
    main()