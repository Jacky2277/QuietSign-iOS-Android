#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""C 方案：拿一张（可以是过期、压缩过的）签到二维码截图，直接用它签到

  python3 sign_from_qr.py shot.jpg            # 解码 → 取 pid → 现造码 → 提交
  python3 sign_from_qr.py --text "<原文>"      # 只有文本时
  python3 sign_from_qr.py shot.jpg --dry      # 只解码不提交
"""
import argparse, json, sys

sys.path.insert(0, ".")
import readqr
import auto
from ygj import YgjClient


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image", nargs="?")
    ap.add_argument("--text", default="")
    ap.add_argument("--dry", action="store_true", help="只解码并造码，不提交")
    a = ap.parse_args()

    raw = a.text or readqr.decode_image(a.image)
    p = readqr.parse(raw)
    d = readqr.decrypt(p)
    pid = d.get("pid")
    if not pid:
        raise SystemExit(f"解不出 processID：{d}")
    print(f"① 解出 processID={pid}（原码生成于 {d['time']}）")

    c = YgjClient("state.json")
    c.require_login()

    j, jc, st = auto.course_now(c, c.schedule())
    if j.get("code") == 0 and j.get("data"):
        course = j["data"][0]
        print(f"② 当前课程：{course['KCMC']}（jc={jc} {st}，扫码截止 {course['stopScanTime']}）")
    else:
        course = None
        print(f"② 当前无课：code={j.get('code')} msg={j.get('msg')}")

    key = auto.fresh_key(c)
    qr, plain = auto.build_qr(pid, key)
    print(f"③ 现造码成功，明文 {plain}，key {key[:8]}…")

    if a.dry:
        print("④ --dry，不提交")
        return

    res = c.submit(qr)
    print(f"④ 提交结果：code={res['code']} msg={res['msg']} ok={res['ok']}")

    if course and res["ok"]:
        print("⑤ 复核 checkStatus：", json.dumps(auto.check(c, course), ensure_ascii=False))


if __name__ == "__main__":
    main()