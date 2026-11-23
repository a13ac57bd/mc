"""排班系統伺服器：只用 Python 標準函式庫（不需要 pip install）。

執行：python server.py          （http://localhost:8080，只有這台電腦能連）
      python server.py --port 80
資料存在同資料夾的 paiban.db（SQLite），備份只要複製這個檔案。
"""
import argparse
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import threading
import time
import webbrowser
from datetime import date, datetime, timedelta
from http import cookies
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

BASE = os.path.dirname(os.path.abspath(__file__))
STATIC = os.path.join(BASE, "static")
DB_PATH = os.environ.get("PAIBAN_DB", os.path.join(BASE, "paiban.db"))
SESSION_HOURS = 12  # 共用電腦：登入最多維持 12 小時（另有前端閒置自動登出）
FULL_DAY = (1 << 24) - 1
ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,20}$")

db_lock = threading.Lock()  # SQLite 一次只讓一個寫入，用鎖避免 "database is locked"


# ---------------------------------------------------------------- 資料庫
def connect():
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db():
    with connect() as c:
        c.execute("PRAGMA journal_mode = WAL")
        c.executescript(
            """
            CREATE TABLE IF NOT EXISTS users (
                id        TEXT PRIMARY KEY,
                name      TEXT NOT NULL,
                pw_hash   TEXT,               -- NULL = 尚未設定（下次登入的密碼即成為密碼）
                is_admin  INTEGER NOT NULL DEFAULT 0,
                active    INTEGER NOT NULL DEFAULT 1
            );
            CREATE TABLE IF NOT EXISTS sessions (
                token     TEXT PRIMARY KEY,
                user_id   TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                expires   REAL NOT NULL
            );
            -- 員工填的「願意上班時段」。hours 是 24 位元：bit h = h:00~h+1:00
            CREATE TABLE IF NOT EXISTS availability (
                user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                week_start TEXT NOT NULL,      -- 週一日期 YYYY-MM-DD
                day        INTEGER NOT NULL,   -- 0=週一 … 6=週日
                hours      INTEGER NOT NULL,
                PRIMARY KEY (user_id, week_start, day)
            );
            CREATE TABLE IF NOT EXISTS availability_meta (
                user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                week_start TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                PRIMARY KEY (user_id, week_start)
            );
            -- 管理員排定的班表
            CREATE TABLE IF NOT EXISTS schedule (
                user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                week_start TEXT NOT NULL,
                day        INTEGER NOT NULL,
                hours      INTEGER NOT NULL,
                PRIMARY KEY (user_id, week_start, day)
            );
            CREATE TABLE IF NOT EXISTS weeks (
                week_start   TEXT PRIMARY KEY,
                published_at TEXT              -- NULL = 草稿，員工看不到
            );
            """
        )
        if c.execute("SELECT COUNT(*) FROM users").fetchone()[0] == 0:
            c.execute("INSERT INTO users (id, name, is_admin) VALUES ('admin', '管理員', 1)")
            c.executemany(
                "INSERT INTO users (id, name) VALUES (?, ?)",
                [(f"{i:03d}", f"員工{i:03d}") for i in range(1, 101)],
            )


# ---------------------------------------------------------------- 密碼
def hash_pw(pw):
    salt = secrets.token_bytes(16)
    h = hashlib.scrypt(pw.encode(), salt=salt, n=2**14, r=8, p=1)
    return f"scrypt${salt.hex()}${h.hex()}"


def check_pw(pw, stored):
    try:
        _, salt, h = stored.split("$")
        calc = hashlib.scrypt(pw.encode(), salt=bytes.fromhex(salt), n=2**14, r=8, p=1)
        return hmac.compare_digest(calc.hex(), h)
    except Exception:
        return False


# 登入失敗次數限制：同一個帳號或 IP 10 分鐘內失敗 10 次就暫停
_fails = {}
_fails_lock = threading.Lock()


def too_many_fails(key):
    now = time.time()
    with _fails_lock:
        lst = [t for t in _fails.get(key, []) if now - t < 600]
        _fails[key] = lst
        return len(lst) >= 10


def add_fail(key):
    with _fails_lock:
        _fails.setdefault(key, []).append(time.time())


# ---------------------------------------------------------------- 週次
def monday_of(d):
    return d - timedelta(days=d.weekday())


def weeks_now():
    """回傳 (本週一, 下週一, 下下週一)。週一 00:00 自動往後推一週。"""
    this = monday_of(date.today())
    return this, this + timedelta(days=7), this + timedelta(days=14)


def parse_week(s):
    try:
        d = date.fromisoformat(s)
    except (TypeError, ValueError):
        return None
    return d if d.weekday() == 0 else None


def valid_days(days):
    return (
        isinstance(days, list)
        and len(days) == 7
        and all(isinstance(x, int) and not isinstance(x, bool) and 0 <= x <= FULL_DAY for x in days)
    )


def load_days(c, table, user_id, week):
    days = [0] * 7
    for r in c.execute(f"SELECT day, hours FROM {table} WHERE user_id=? AND week_start=?", (user_id, week)):
        days[r["day"]] = r["hours"]
    return days


def save_days(c, table, user_id, week, days):
    c.execute(f"DELETE FROM {table} WHERE user_id=? AND week_start=?", (user_id, week))
    c.executemany(
        f"INSERT INTO {table} (user_id, week_start, day, hours) VALUES (?,?,?,?)",
        [(user_id, week, i, h) for i, h in enumerate(days) if h],
    )


def is_published(c, week):
    r = c.execute("SELECT published_at FROM weeks WHERE week_start=?", (week,)).fetchone()
    return bool(r and r["published_at"])


# ---------------------------------------------------------------- HTTP
class ApiError(Exception):
    def __init__(self, status, msg):
        super().__init__(msg)
        self.status, self.msg = status, msg


MIME = {".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8",
        ".css": "text/css; charset=utf-8", ".svg": "image/svg+xml", ".ico": "image/x-icon"}


class Handler(BaseHTTPRequestHandler):
    server_version = "Paiban/1.0"

    def log_message(self, fmt, *args):  # 精簡記錄
        print(f"{datetime.now():%m-%d %H:%M:%S} {self.client_address[0]} {fmt % args}")

    # ---- 共用
    def send_json(self, status, obj, extra_headers=()):
        body = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        for k, v in extra_headers:
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def read_json(self):
        n = int(self.headers.get("Content-Length") or 0)
        if n == 0:
            return {}
        if not self.headers.get("Content-Type", "").startswith("application/json"):
            raise ApiError(415, "需要 JSON")
        if n > 1_000_000:
            raise ApiError(413, "資料太大")
        try:
            return json.loads(self.rfile.read(n) or b"{}")
        except ValueError:
            raise ApiError(400, "JSON 格式錯誤")

    def client_ip(self):
        return self.client_address[0]

    def session_token(self):
        c = cookies.SimpleCookie(self.headers.get("Cookie", ""))
        return c["sid"].value if "sid" in c else None

    def current_user(self, c):
        tok = self.session_token()
        if not tok:
            return None
        r = c.execute(
            "SELECT u.* FROM sessions s JOIN users u ON u.id=s.user_id "
            "WHERE s.token=? AND s.expires>? AND u.active=1",
            (tok, time.time()),
        ).fetchone()
        return r

    # ---- 路由
    def do_GET(self):
        self.dispatch("GET")

    def do_POST(self):
        self.dispatch("POST")

    def do_PUT(self):
        self.dispatch("PUT")

    def dispatch(self, method):
        url = urlparse(self.path)
        if not url.path.startswith("/api/"):
            if method != "GET":
                return self.send_json(405, {"error": "不支援"})
            return self.serve_static(url.path)
        route = ROUTES.get((method, url.path))
        if not route:
            return self.send_json(404, {"error": "找不到"})
        fn, need = route
        try:
            with db_lock, connect() as c:
                user = self.current_user(c)
                if need and not user:
                    raise ApiError(401, "請重新登入")
                if need == "admin" and not user["is_admin"]:
                    raise ApiError(403, "需要管理員權限")
                query = {k: v[0] for k, v in parse_qs(url.query).items()}
                body = self.read_json() if method in ("POST", "PUT") else None
                result = fn(self, c, user, query, body)
            if isinstance(result, tuple):
                obj, headers = result
                self.send_json(200, obj, headers)
            else:
                self.send_json(200, result)
        except ApiError as e:
            self.send_json(e.status, {"error": e.msg})
        except Exception as e:  # 不把內部錯誤細節回給瀏覽器
            print("ERROR", repr(e))
            self.send_json(500, {"error": "伺服器錯誤"})

    def serve_static(self, path):
        if path in ("/", ""):
            path = "/index.html"
        full = os.path.normpath(os.path.join(STATIC, path.lstrip("/")))
        if not full.startswith(STATIC + os.sep) or not os.path.isfile(full):
            full = os.path.join(STATIC, "index.html")
        with open(full, "rb") as f:
            data = f.read()
        self.send_response(200)
        self.send_header("Content-Type", MIME.get(os.path.splitext(full)[1], "application/octet-stream"))
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-cache")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.end_headers()
        self.wfile.write(data)


# ---------------------------------------------------------------- API：帳號
def user_info(u):
    return {"id": u["id"], "name": u["name"], "is_admin": bool(u["is_admin"])}


def api_login(h, c, _user, _q, body):
    uid = str(body.get("id", "")).strip()
    pw = str(body.get("password", ""))
    ip = h.client_ip()
    if too_many_fails("ip:" + ip) or too_many_fails("id:" + uid):
        raise ApiError(429, "嘗試太多次，請 10 分鐘後再試")
    u = c.execute("SELECT * FROM users WHERE id=? AND active=1", (uid,)).fetchone()
    if not u:
        add_fail("ip:" + ip)
        raise ApiError(401, "編號或密碼錯誤")
    first = u["pw_hash"] is None
    if first:
        if len(pw) < 4:
            raise ApiError(400, "第一次登入：請設定至少 4 個字的密碼")
        c.execute("UPDATE users SET pw_hash=? WHERE id=?", (hash_pw(pw), uid))
    elif not check_pw(pw, u["pw_hash"]):
        add_fail("ip:" + ip)
        add_fail("id:" + uid)
        raise ApiError(401, "編號或密碼錯誤")
    tok = secrets.token_urlsafe(32)
    c.execute("DELETE FROM sessions WHERE expires<?", (time.time(),))
    c.execute("INSERT INTO sessions VALUES (?,?,?)", (tok, uid, time.time() + SESSION_HOURS * 3600))
    # 不設 Max-Age：關掉瀏覽器就登出
    cookie = f"sid={tok}; Path=/; HttpOnly; SameSite=Strict"
    return {"user": user_info(u), "first": first}, [("Set-Cookie", cookie)]


def api_logout(h, c, _user, _q, _body):
    tok = h.session_token()
    if tok:
        c.execute("DELETE FROM sessions WHERE token=?", (tok,))
    return {"ok": True}, [("Set-Cookie", "sid=; Path=/; Max-Age=0")]


def api_me(_h, _c, user, _q, _body):
    this, nxt, fill = weeks_now()
    return {
        "user": user_info(user) if user else None,
        "today": date.today().isoformat(),
        "this_week": this.isoformat(),
        "next_week": nxt.isoformat(),
        "fill_week": fill.isoformat(),
        # 截止：下週一 00:00（= 本週日結束）
        "deadline": datetime.combine(nxt, datetime.min.time()).isoformat(timespec="minutes"),
    }


def api_change_pw(_h, c, user, _q, body):
    old, new = str(body.get("old", "")), str(body.get("new", ""))
    if not check_pw(old, user["pw_hash"]):
        raise ApiError(400, "舊密碼錯誤")
    if len(new) < 4:
        raise ApiError(400, "新密碼至少 4 個字")
    c.execute("UPDATE users SET pw_hash=? WHERE id=?", (hash_pw(new), user["id"]))
    return {"ok": True}


# ---------------------------------------------------------------- API：員工
def api_get_availability(_h, c, user, _q, _body):
    week = weeks_now()[2].isoformat()
    meta = c.execute("SELECT updated_at FROM availability_meta WHERE user_id=? AND week_start=?",
                     (user["id"], week)).fetchone()
    return {"week_start": week, "days": load_days(c, "availability", user["id"], week),
            "updated_at": meta["updated_at"] if meta else None}


def api_put_availability(_h, c, user, _q, body):
    week = weeks_now()[2].isoformat()
    if body.get("week_start") != week:
        raise ApiError(409, "填寫期限已過，頁面已更新到新的週次，請重新整理")
    days = body.get("days")
    if not valid_days(days):
        raise ApiError(400, "資料格式錯誤")
    save_days(c, "availability", user["id"], week, days)
    now = datetime.now().isoformat(timespec="seconds")
    c.execute("INSERT OR REPLACE INTO availability_meta VALUES (?,?,?)", (user["id"], week, now))
    return {"ok": True, "updated_at": now}


def api_my_schedule(_h, c, user, q, _body):
    this, nxt, _ = weeks_now()
    week = parse_week(q.get("week")) or nxt
    if week not in (this, nxt) and not user["is_admin"]:
        raise ApiError(400, "只能查看本週與下週")
    wk = week.isoformat()
    if not is_published(c, wk):
        return {"week_start": wk, "published": False, "days": None}
    return {"week_start": wk, "published": True, "days": load_days(c, "schedule", user["id"], wk)}


# ---------------------------------------------------------------- API：管理員
def api_admin_week(_h, c, _user, q, _body):
    week = parse_week(q.get("week"))
    if not week:
        raise ApiError(400, "週次錯誤")
    wk = week.isoformat()
    users = c.execute("SELECT id, name FROM users WHERE active=1 AND is_admin=0 ORDER BY id").fetchall()
    avail, sched, upd = {}, {}, {}
    for r in c.execute("SELECT user_id, day, hours FROM availability WHERE week_start=?", (wk,)):
        avail.setdefault(r["user_id"], [0] * 7)[r["day"]] = r["hours"]
    for r in c.execute("SELECT user_id, day, hours FROM schedule WHERE week_start=?", (wk,)):
        sched.setdefault(r["user_id"], [0] * 7)[r["day"]] = r["hours"]
    for r in c.execute("SELECT user_id, updated_at FROM availability_meta WHERE week_start=?", (wk,)):
        upd[r["user_id"]] = r["updated_at"]
    pub = c.execute("SELECT published_at FROM weeks WHERE week_start=?", (wk,)).fetchone()
    return {
        "week_start": wk,
        "published_at": pub["published_at"] if pub else None,
        "users": [
            {"id": u["id"], "name": u["name"], "avail": avail.get(u["id"], [0] * 7),
             "sched": sched.get(u["id"], [0] * 7), "submitted_at": upd.get(u["id"])}
            for u in users
        ],
    }


def api_admin_save(_h, c, _user, _q, body):
    week = parse_week(body.get("week_start"))
    sched = body.get("sched")
    if not week or not isinstance(sched, dict):
        raise ApiError(400, "資料格式錯誤")
    wk = week.isoformat()
    ids = {r["id"] for r in c.execute("SELECT id FROM users")}
    for uid, days in sched.items():
        if uid not in ids or not valid_days(days):
            raise ApiError(400, f"資料格式錯誤：{uid}")
    for uid, days in sched.items():
        save_days(c, "schedule", uid, wk, days)
    c.execute("INSERT OR IGNORE INTO weeks (week_start) VALUES (?)", (wk,))
    if "publish" in body:
        val = datetime.now().isoformat(timespec="seconds") if body["publish"] else None
        c.execute("UPDATE weeks SET published_at=? WHERE week_start=?", (val, wk))
    pub = c.execute("SELECT published_at FROM weeks WHERE week_start=?", (wk,)).fetchone()
    return {"ok": True, "published_at": pub["published_at"]}


def api_admin_users(_h, c, _user, _q, _body):
    rows = c.execute("SELECT id, name, is_admin, active, pw_hash IS NOT NULL AS has_pw FROM users ORDER BY is_admin DESC, id")
    return {"users": [dict(r) for r in rows]}


def api_admin_user_save(_h, c, user, _q, body):
    uid = str(body.get("id", "")).strip()
    name = str(body.get("name", "")).strip()[:40]
    if not ID_RE.match(uid):
        raise ApiError(400, "編號只能用英數字，最多 20 字")
    if not name:
        raise ApiError(400, "請輸入姓名")
    exists = c.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone()
    active = 1 if body.get("active", True) else 0
    if exists:
        if uid == user["id"] and not active:
            raise ApiError(400, "不能停用自己")
        c.execute("UPDATE users SET name=?, active=? WHERE id=?", (name, active, uid))
        if not active:
            c.execute("DELETE FROM sessions WHERE user_id=?", (uid,))
    else:
        c.execute("INSERT INTO users (id, name, active) VALUES (?,?,?)", (uid, name, active))
    return {"ok": True}


def api_admin_reset_pw(_h, c, _user, _q, body):
    uid = str(body.get("id", ""))
    if not c.execute("SELECT 1 FROM users WHERE id=?", (uid,)).fetchone():
        raise ApiError(404, "找不到此編號")
    c.execute("UPDATE users SET pw_hash=NULL WHERE id=?", (uid,))
    c.execute("DELETE FROM sessions WHERE user_id=?", (uid,))
    return {"ok": True}


ROUTES = {
    ("POST", "/api/login"): (api_login, None),
    ("POST", "/api/logout"): (api_logout, None),
    ("GET", "/api/me"): (api_me, None),
    ("POST", "/api/password"): (api_change_pw, "user"),
    ("GET", "/api/availability"): (api_get_availability, "user"),
    ("PUT", "/api/availability"): (api_put_availability, "user"),
    ("GET", "/api/schedule"): (api_my_schedule, "user"),
    ("GET", "/api/admin/week"): (api_admin_week, "admin"),
    ("PUT", "/api/admin/schedule"): (api_admin_save, "admin"),
    ("GET", "/api/admin/users"): (api_admin_users, "admin"),
    ("POST", "/api/admin/user"): (api_admin_user_save, "admin"),
    ("POST", "/api/admin/reset-password"): (api_admin_reset_pw, "admin"),
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")  # 只接受本機連線
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--open", action="store_true", help="啟動後自動打開瀏覽器")
    a = ap.parse_args()
    init_db()
    srv = ThreadingHTTPServer((a.host, a.port), Handler)
    print(f"排班系統已啟動：http://127.0.0.1:{a.port}  （資料庫：{DB_PATH}）")
    print("關閉：按 Ctrl+C 或直接關掉這個視窗")
    if a.open:
        threading.Timer(1, webbrowser.open, (f"http://127.0.0.1:{a.port}",)).start()
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
