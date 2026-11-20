#!/usr/bin/env bash
# 一键准备运行环境：建 venv、装依赖
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v python3 >/dev/null 2>&1; then
  echo "缺少 python3"
  exit 1
fi

python3 -m venv .venv
# shellcheck disable=SC1091
. .venv/bin/activate
pip install --upgrade pip >/dev/null
pip install requests

echo
echo "环境就绪：$(pwd)/.venv"
echo "下一步："
echo "  1) . .venv/bin/activate"
echo "  2) python3 cli.py login --token <易班loginToken>"
echo "  3) python3 cli.py watch"