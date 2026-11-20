#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""静签服务端 · 命令行入口

用法：
  python3 cli.py login --token <易班loginToken>     登录并做设备对齐
  python3 cli.py status                             看当前有没有课要签
  python3 cli.py schedule                           看课表拉取结果
  python3 cli.py sign --qr "<二维码内容>"            手动提交一次
  python3 cli.py watch                              到窗口自动盯课 + 发现码就提交
  python3 cli.py serve                              开一个本地接口收二维码（手机/脚本投递）
"""

import argparse
import json
import os
import sys
import threading
import time
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from ygj import YgjClient, YgjError

HERE = os.path.dirname(os.path.abspath(__file__))
STATE = os.environ.get("YGJ_STATE", os.path.join(HERE, "state.json"))
QRFILE = os.environ.get("YGJ_QRFILE", os.path.join(HERE, "qr.txt"))
QRLOCK = threading.Lock()


def log(msg):
    print(f"[{datetime.now():%m-%d %H:%M:%S}] {msg}", flush=True)


def read_qr():
    if not os.path.exists(QRFILE):
        return ""
    with QRLOCK:
        try:
            with open(QRFILE, "r", encoding="utf-8") as f:
                return f.read().strip()
        except Exception:
            return ""


def write_qr(text):
    with QRLOCK:
        with open(QRFILE, "w", encoding="utf-8") as f:
            f.write(text.strip())
        try:
            os.chmod(QRFILE, 0o600)
        except Exception:
            pass


def clear_qr():
    with QRLOCK:
        if os.path.exists(QRFILE):
            os.remove(QRFILE)


# ---------------- 子命令 ----------------

def cmd_login(args):
    c = YgjClient(STATE)
    sid = c.login(args.token)
    log(f"登录成功 studentID={sid}")
    log(f"设备借用：{json.dumps(c.state.get('device', {}), ensure_ascii=False)}")
    log(f"状态文件：{STATE}")
    return 0


def cmd_schedule(args):
    c = YgjClient(STATE)
    data = c.schedule()
    rows = data.get("scheduleInfo") or []
    log(f"校区={data.get('school')} 年级={data.get('grade')} "
        f"第{data.get('firstWeekDate')}起 共{len(rows)}条")
    log(f"时间参数 startSignMin={data.get('startSignMin')} "
        f"stopSignMin={data.get('stopSignMin')} lateSignMin={data.get('lateSignMin')}")
    if args.raw:
        print(json.dumps(data, ensure_ascii=False, indent=2))
    return 0


def cmd_status(args):
    c = YgjClient(STATE)
    cur = c.current_course()
    if args.json:
        print(json.dumps(cur, ensure_ascii=False, indent=2))
        return 0
    log(f"校区={cur['school']} 年级={cur['grade']} 第{cur['week']}周 "
        f"节次={cur['jc']} 起始={cur['startTime']}")
    courses = cur["courses"]
    if not courses:
        log("当前没有需要签到的课程")
        return 0
    for one in courses:
        log("有课：" + " | ".join([
            str(one.get("KCMC", "")), str(one.get("BJMC", "")), str(one.get("SKJSMC", "")),
            f"{one.get('startTime','')}~{one.get('endTime','')}",
        ]))
    return 0


def cmd_sign(args):
    c = YgjClient(STATE)
    qr = args.qr or read_qr()
    if not qr:
        log("没有二维码：用 --qr 传，或先把内容写进 qr.txt")
        return 2
    res = c.submit(qr)
    log(f"提交完成 code={res['code']} msg={res['msg']} ok={res['ok']}")
    if args.raw:
        print(json.dumps(res.get("raw"), ensure_ascii=False, indent=2))
    return 0 if res["ok"] else 1


def cmd_watch(args):
    c = YgjClient(STATE)
    c.require_login()
    log(f"开始监听：间隔 {args.interval}s，课表缓存 {args.refresh}min")
    data, data_at, done = None, 0.0, set()
    while True:
        try:
            if data is None or (time.time() - data_at) > args.refresh * 60:
                data = c.schedule()
                data_at = time.time()
                log(f"课表已刷新（{len(data.get('scheduleInfo') or [])} 条）")
            cur = c.current_course(data)
            courses = cur["courses"]
            if not courses:
                log("当前没有需要签到的课程")
            else:
                one = courses[0]
                ident = (one.get("BJMC", ""), cur["jc"], cur["startTime"])
                log(f"有课：{one.get('KCMC','?')} / {one.get('BJMC','')} / 第{cur['jc']}节 "
                    f"({one.get('startTime','')}~{one.get('endTime','')})")
                qr = read_qr()
                if qr and ident not in done:
                    log(f"发现二维码（{len(qr)} 字符），提交中…")
                    res = c.submit(qr)
                    log(f"提交结果 code={res['code']} msg={res['msg']}")
                    if res["ok"]:
                        done.add(ident)
                        clear_qr()
                elif not qr:
                    log("还没收到二维码：等同学发码后写进 qr.txt，或 POST 到 serve 接口")
        except YgjError as e:
            log(f"出错 {e.code}: {e.msg}")
            if e.code in ("TOKEN_EXPIRED", "NOT_LOGGED_IN", "LOGIN_FAILED"):
                log("登录态失效，监听停止")
                return 2
        except KeyboardInterrupt:
            log("手动停止")
            return 0
        except Exception as e:  # 网络抖动等，不退出
            log(f"未预期异常：{e!r}")
        time.sleep(args.interval)


def cmd_serve(args):
    c = YgjClient(STATE)
    state = {"last": None}
    lock = threading.Lock()

    class Handler(BaseHTTPRequestHandler):
        server_version = "QuietSign/1.0"

        def _reply(self, code, obj):
            body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _authorized(self):
            if not args.key:
                return True
            return (self.headers.get("X-Key") or "").strip() == args.key

        def do_POST(self):
            if self.path.split("?")[0] != "/qr":
                return self._reply(404, {"ok": False, "error": "not found"})
            if not self._authorized():
                return self._reply(401, {"ok": False, "error": "bad key"})
            n = int(self.headers.get("Content-Length") or 0)
            raw = self.rfile.read(n).decode("utf-8", "replace").strip()
            if not raw:
                return self._reply(400, {"ok": False, "error": "empty body"})
            write_qr(raw)
            log(f"收到二维码 {len(raw)} 字符")
            if not args.immediate:
                return self._reply(200, {"ok": True, "queued": True})
            try:
                res = c.submit(raw)
                with lock:
                    state["last"] = {"at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                                     "code": res["code"], "msg": res["msg"], "ok": res["ok"]}
                return self._reply(200 if res["ok"] else 400,
                                   {"ok": res["ok"], "code": res["code"], "msg": res["msg"]})
            except YgjError as e:
                return self._reply(400, {"ok": False, "error": e.code, "msg": e.msg})

        def do_GET(self):
            if self.path.split("?")[0] != "/status":
                return self._reply(404, {"ok": False, "error": "not found"})
            with lock:
                payload = {"studentID": c.student_id, "last": state["last"],
                           "has_qr": bool(read_qr()), "time": datetime.now().strftime("%Y-%m-%d %H:%M:%S")}
            return self._reply(200, payload)

        def log_message(self, fmt, *a):
            log("http " + fmt % a)

    srv = ThreadingHTTPServer((args.host, args.port), Handler)
    log(f"监听 http://{args.host}:{args.port}  POST /qr   GET /status"
        + (f"  （需要 X-Key: {args.key}）" if args.key else ""))
    if args.host not in ("127.0.0.1", "localhost"):
        log("注意：绑定在公网地址，务必设置 --key，否则任何人都能投递二维码")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        log("服务停止")
    return 0


def main():
    p = argparse.ArgumentParser(description="静签服务端：易广金签到协议命令行")
    sub = p.add_subparsers(dest="cmd", required=True)

    q = sub.add_parser("login", help="用易班 token 登录并做设备对齐")
    q.add_argument("--token", required=True)
    q.set_defaults(func=cmd_login)

    s = sub.add_parser("schedule", help="拉取课表")
    s.add_argument("--raw", action="store_true")
    s.set_defaults(func=cmd_schedule)

    st = sub.add_parser("status", help="查询当前是否有课要签")
    st.add_argument("--json", action="store_true")
    st.set_defaults(func=cmd_status)

    sg = sub.add_parser("sign", help="提交一次签到")
    sg.add_argument("--qr", default="")
    sg.add_argument("--raw", action="store_true")
    sg.set_defaults(func=cmd_sign)

    w = sub.add_parser("watch", help="窗口内循环盯课，收到二维码就提交")
    w.add_argument("--interval", type=int, default=20)
    w.add_argument("--refresh", type=int, default=10, help="课表缓存分钟")
    w.set_defaults(func=cmd_watch)

    sv = sub.add_parser("serve", help="起接口收二维码")
    sv.add_argument("--host", default="127.0.0.1")
    sv.add_argument("--port", type=int, default=8765)
    sv.add_argument("--key", default="", help="简单鉴权，请求头带 X-Key")
    sv.add_argument("--immediate", action="store_true", help="收到就直接提交")
    sv.set_defaults(func=cmd_serve)

    args = p.parse_args()
    try:
        return args.func(args)
    except YgjError as e:
        log(f"失败 {e.code}: {e.msg}")
        return 1


if __name__ == "__main__":
    sys.exit(main())