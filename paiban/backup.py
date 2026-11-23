"""備份資料庫到 backups/paiban-日期時間.db，並只保留最近 60 份。
伺服器開著也可以安全執行（用 SQLite 的線上備份功能）。"""
import os, sqlite3, glob
from datetime import datetime

BASE = os.path.dirname(os.path.abspath(__file__))
src = os.environ.get("PAIBAN_DB", os.path.join(BASE, "paiban.db"))
if not os.path.exists(src):
    raise SystemExit("找不到資料庫：" + src)
out_dir = os.path.join(BASE, "backups")
os.makedirs(out_dir, exist_ok=True)
dst = os.path.join(out_dir, f"paiban-{datetime.now():%Y%m%d-%H%M}.db")
with sqlite3.connect(src) as s, sqlite3.connect(dst) as d:
    s.backup(d)
print("已備份到", dst)
for old in sorted(glob.glob(os.path.join(out_dir, "paiban-*.db")))[:-60]:
    os.remove(old)
