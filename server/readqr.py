#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从二维码截图里解出 processID（C 方案用）

用法：
  python3 readqr.py shot.png              # 从图片解码
  python3 readqr.py --text "<二维码原文>"   # 已经有文本时直接解析
"""
import argparse, base64, json, re, sys
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad

QRC = "qrcode="


def parse(text):
    """原文 -> {pid, time, key, cipher_b64}

    形如 https://ygj.gduf.edu.cn/index.aspx?qrcode=<base64><32位key>
    末尾 32 位是 key，前面是 base64 密文（可能自带 '=' 填充）
    """
    t = text.strip()
    i = t.find(QRC)
    body = t[i + len(QRC):] if i >= 0 else t
    # base64 可能以 '=' 结尾，key 是纯字面量，直接按总长切
    key = body[-32:]
    enc = body[:-32]
    return {"key": key, "cipher_b64": enc, "raw_len": len(t)}


def decrypt(p):
    raw = AES.new(p["key"].encode(), AES.MODE_ECB).decrypt(base64.b64decode(p["cipher_b64"]))
    plain = unpad(raw, 16)
    s = json.loads(plain.decode())
    m = re.match(r"^(\d+)\s*,\s*(.+)$", s.strip())
    return {"plain": s, "pid": m.group(1) if m else None, "time": m.group(2) if m else None}


def decode_image(path):
    import cv2
    img = cv2.imread(path)
    if img is None:
        raise SystemExit(f"读不到图片：{path}")
    det = cv2.QRCodeDetector()
    data, box, _ = det.detectAndDecode(img)
    if not data:                        # 小图/糊图放大再试
        import numpy as np
        for s in (2, 3):
            big = cv2.resize(img, None, fx=s, fy=s, interpolation=cv2.INTER_CUBIC)
            data, box, _ = det.detectAndDecode(big)
            if data:
                break
    if not data:
        raise SystemExit("没解出二维码，换张清晰点的图")
    return data


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image", nargs="?")
    ap.add_argument("--text", default="")
    a = ap.parse_args()
    text = a.text or decode_image(a.image)
    print("二维码原文：", text[:90] + ("…" if len(text) > 90 else ""), f"（共 {len(text)} 字符）")
    p = parse(text)
    try:
        d = decrypt(p)
    except Exception as e:
        print(f"解密失败：{e!r}（可能是 key 被截断，或图不完整）")
        return
    print("key      ：", p["key"])
    print("明文     ：", d["plain"])
    print("processID：", d["pid"], "  生成时间：", d["time"])


if __name__ == "__main__":
    main()