"use strict";
// ---------------------------------------------------------------- 工具
const $ = (s, el = document) => el.querySelector(s);
const DAYS = ["一", "二", "三", "四", "五", "六", "日"];
const FULL = (1 << 24) - 1;
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const bit = (v, h) => (v >> h) & 1;
const setBit = (v, h, on) => (on ? v | (1 << h) : v & ~(1 << h));
const count = (v) => { let n = 0; for (let h = 0; h < 24; h++) n += bit(v, h); return n; };
const pad = (n) => String(n).padStart(2, "0");

function addDays(iso, n) {
  const [y, m, d] = iso.split("-").map(Number);
  const t = new Date(y, m - 1, d + n);
  return `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(t.getDate())}`;
}
const md = (iso) => { const [, m, d] = iso.split("-"); return `${+m}/${+d}`; };
const weekLabel = (wk) => `${md(wk)}（一）～ ${md(addDays(wk, 6))}（日）`;

// 24 位元 → "08:00–12:00、13:00–17:00"
function ranges(v) {
  const out = [];
  for (let h = 0; h < 24; h++) {
    if (!bit(v, h)) continue;
    let e = h;
    while (e + 1 < 24 && bit(v, e + 1)) e++;
    out.push(`${pad(h)}:00–${pad(e + 1)}:00`);
    h = e;
  }
  return out.join("、");
}

async function api(method, path, body) {
  const opt = { method, headers: {} };
  if (body !== undefined) { opt.headers["Content-Type"] = "application/json"; opt.body = JSON.stringify(body); }
  const r = await fetch(path, opt);
  const data = await r.json().catch(() => ({}));
  if (r.status === 401 && path !== "/api/login") { location.reload(); throw new Error("請重新登入"); }
  if (!r.ok) throw new Error(data.error || `錯誤 ${r.status}`);
  return data;
}

let toastTimer;
function toast(msg, err = false) {
  const t = $("#toast");
  t.textContent = msg; t.className = err ? "err" : ""; t.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => (t.hidden = true), 3000);
}

// 讓表格可以點選／拖曳上色。滑鼠：按住拖曳；觸控：點一下切換（不影響捲動）。
function paintable(table, get, set, onChange) {
  let mode = null, lastType = "mouse";
  const cellOf = (el) => el && el.closest && el.closest("td.c");
  table.addEventListener("pointerdown", (e) => {
    lastType = e.pointerType;
    if (e.pointerType !== "mouse" || e.button !== 0) return;
    const c = cellOf(e.target); if (!c) return;
    e.preventDefault();
    mode = !get(c); set(c, mode); onChange();
  });
  table.addEventListener("pointerover", (e) => {
    if (mode === null) return;
    const c = cellOf(e.target); if (c && get(c) !== mode) { set(c, mode); onChange(); }
  });
  window.addEventListener("pointerup", () => (mode = null));
  table.addEventListener("click", (e) => {
    if (lastType === "mouse") return; // 滑鼠已在 pointerdown 處理
    const c = cellOf(e.target); if (!c) return;
    set(c, !get(c)); onChange();
  });
}

// ---------------------------------------------------------------- 狀態與導覽
let ME = null;      // /api/me 的回應
let dirty = false;  // 有未儲存的修改
window.addEventListener("beforeunload", (e) => { if (dirty) { e.preventDefault(); e.returnValue = ""; } });

function confirmLeave() {
  if (!dirty) return true;
  if (!confirm("有修改還沒儲存，確定要離開？")) return false;
  dirty = false; return true;
}

async function boot() {
  ME = await api("GET", "/api/me");
  if (!ME.user) return showLogin();
  $("#top").hidden = false;
  $("#who").textContent = `${ME.user.id} ${ME.user.name}`;
  idleLimit = ME.user.is_admin ? 30 * 60 : 2 * 60; // 秒：管理員 30 分鐘、員工 2 分鐘
  idleReset();
  const tabs = ME.user.is_admin
    ? [["排班", viewAdminSchedule], ["員工管理", viewAdminUsers]]
    : [["填寫可上班時段", viewFill], ["我的班表", viewMySchedule]];
  const nav = $("#tabs"); nav.innerHTML = "";
  tabs.forEach(([label, fn], i) => {
    const b = document.createElement("button");
    b.textContent = label;
    b.onclick = () => { if (!confirmLeave()) return; [...nav.children].forEach((x) => x.classList.remove("on")); b.classList.add("on"); fn(); };
    nav.appendChild(b);
    if (i === 0) b.click();
  });
}

function showLogin() {
  $("#top").hidden = true;
  const app = $("#app");
  app.innerHTML = "";
  app.appendChild($("#tpl-login").content.cloneNode(true));
  $("#login-form").onsubmit = async (e) => {
    e.preventDefault();
    const f = e.target;
    try {
      const r = await api("POST", "/api/login", { id: f.id.value.trim(), password: f.password.value });
      if (r.first) alert("密碼設定完成！以後請用同一組密碼登入。");
      boot();
    } catch (err) { $("#login-err").textContent = err.message; }
  };
}

async function logout() {
  dirty = false;
  try { await api("POST", "/api/logout", {}); } catch (e) { /* 已登出 */ }
  location.reload();
}
$("#btn-logout").onclick = () => { if (confirmLeave()) logout(); };

// 共用電腦：一段時間沒操作就自動登出（最後 30 秒顯示倒數，動一下就取消）
const IDLE_WARN = 30;
let idleLeft = Infinity, idleLimit = 0;
function idleReset() { if (idleLimit) { idleLeft = idleLimit; $("#idle").hidden = true; } }
["pointerdown", "pointermove", "keydown", "wheel", "touchstart"].forEach((ev) => window.addEventListener(ev, idleReset, { passive: true }));
setInterval(() => {
  if (!idleLimit) return;
  idleLeft--;
  if (idleLeft <= 0) { idleLimit = 0; logout(); return; }
  if (idleLeft <= IDLE_WARN) {
    $("#idle-n").textContent = idleLeft;
    $("#idle").hidden = false;
  }
}, 1000);
$("#btn-pw").onclick = async () => {
  const old = prompt("目前密碼："); if (old === null) return;
  const nw = prompt("新密碼（至少 4 個字）："); if (nw === null) return;
  if (prompt("再輸入一次新密碼：") !== nw) return toast("兩次輸入不一樣", true);
  try { await api("POST", "/api/password", { old, new: nw }); toast("密碼已更新"); } catch (e) { toast(e.message, true); }
};

// ---------------------------------------------------------------- 員工：7 天 × 24 小時表格
function weekGrid(wk, days, { readonly = false, cls = "on" } = {}) {
  let h = `<table class="wk${readonly ? " readonly" : ""}"><thead><tr><th></th>`;
  DAYS.forEach((d, i) => (h += `<th data-day="${i}">${d}<small>${md(addDays(wk, i))}</small></th>`));
  h += "</tr></thead><tbody>";
  for (let hr = 0; hr < 24; hr++) {
    h += `<tr><td class="h" data-hour="${hr}">${pad(hr)}:00</td>`;
    for (let d = 0; d < 7; d++) h += `<td class="c${bit(days[d], hr) ? " " + cls : ""}" data-d="${d}" data-h="${hr}"></td>`;
    h += "</tr>";
  }
  return h + "</tbody></table>";
}

async function viewFill() {
  const app = $("#app");
  app.innerHTML = "<p>載入中…</p>";
  const data = await api("GET", "/api/availability");
  const wk = data.week_start;
  let days = data.days.slice();
  const deadline = addDays(ME.next_week, -1); // 本週日
  app.innerHTML = `
    <div class="card">
      <h2>填寫 ${weekLabel(wk)} 願意上班的時段</h2>
      <p class="sub">截止：${md(deadline)}（日）23:59，之後會自動換成下一週。截止前可以一直修改。<br>
        點格子選取／取消；電腦可按住滑鼠拖曳。點上方的「星期」可整天選取或取消。</p>
      <div class="bar">
        <button id="copy-mon">把週一複製到每一天</button>
        <button id="clear-all" class="danger">全部清除</button>
        <span class="spacer"></span>
        <span class="total" id="total"></span>
        <button id="save">儲存</button>
        <button class="primary" id="save-out">儲存並登出</button>
      </div>
      <div id="grid"></div>
      <p class="sub" id="saved"></p>
    </div>`;
  const updated = (t) => ($("#saved").textContent = t ? `上次儲存：${t.replace("T", " ")}` : "尚未儲存過");
  updated(data.updated_at);

  const render = () => {
    $("#grid").innerHTML = weekGrid(wk, days);
    const t = $("#grid table");
    paintable(t, (c) => c.classList.contains("on"), (c, on) => {
      c.classList.toggle("on", on);
      days[+c.dataset.d] = setBit(days[+c.dataset.d], +c.dataset.h, on);
    }, changed);
    t.querySelectorAll("thead th[data-day]").forEach((th) => (th.onclick = () => {
      const d = +th.dataset.day; days[d] = days[d] === FULL ? 0 : FULL; changed(); render();
    }));
    t.querySelectorAll("td.h").forEach((td) => (td.onclick = () => {
      const hr = +td.dataset.hour, on = !days.every((v) => bit(v, hr));
      days = days.map((v) => setBit(v, hr, on)); changed(); render();
    }));
  };
  const changed = () => {
    dirty = true;
    $("#total").textContent = `共 ${days.reduce((a, v) => a + count(v), 0)} 小時（未儲存）`;
  };
  render();
  $("#total").textContent = `共 ${days.reduce((a, v) => a + count(v), 0)} 小時`;
  $("#copy-mon").onclick = () => { days = days.map(() => days[0]); changed(); render(); };
  $("#clear-all").onclick = () => { if (confirm("確定全部清除？")) { days = [0, 0, 0, 0, 0, 0, 0]; changed(); render(); } };
  const save = async () => {
    try {
      const r = await api("PUT", "/api/availability", { week_start: wk, days });
      dirty = false; updated(r.updated_at);
      $("#total").textContent = `共 ${days.reduce((a, v) => a + count(v), 0)} 小時`;
      toast("已儲存");
      return true;
    } catch (e) { toast(e.message, true); return false; }
  };
  $("#save").onclick = save;
  $("#save-out").onclick = async () => { if (await save()) { alert("已儲存，按確定後登出。"); logout(); } };
}

async function viewMySchedule(week) {
  const app = $("#app");
  week = week || ME.next_week;
  app.innerHTML = "<p>載入中…</p>";
  const data = await api("GET", `/api/schedule?week=${week}`);
  const btn = (w, label) => `<button data-w="${w}" class="${w === week ? "primary" : ""}">${label}</button>`;
  let body;
  if (!data.published) {
    body = `<p>這週的班表還沒公布。</p>`;
  } else {
    const total = data.days.reduce((a, v) => a + count(v), 0);
    body = `<p><b>本週共 ${total} 小時</b></p><table class="list">` +
      DAYS.map((d, i) => `<tr><th>週${d} ${md(addDays(week, i))}</th><td>${data.days[i] ? ranges(data.days[i]) : '<span class="sub">休</span>'}</td></tr>`).join("") +
      `</table><details style="margin-top:12px"><summary>表格檢視</summary>${weekGrid(week, data.days, { readonly: true, cls: "s" })}</details>`;
  }
  app.innerHTML = `<div class="card">
      <div class="bar">${btn(ME.this_week, "本週")}${btn(ME.next_week, "下週")}</div>
      <h2>${weekLabel(week)} 班表</h2>${body}</div>`;
  app.querySelectorAll("button[data-w]").forEach((b) => (b.onclick = () => viewMySchedule(b.dataset.w)));
}

// ---------------------------------------------------------------- 管理員：排班
const adm = { week: null, day: 0, data: null, onlySubmitted: false, search: "" };

async function viewAdminSchedule() {
  adm.week = adm.week || ME.next_week;
  await loadAdminWeek();
}

async function loadAdminWeek() {
  $("#app").innerHTML = "<p>載入中…</p>";
  adm.data = await api("GET", `/api/admin/week?week=${adm.week}`);
  dirty = false;
  renderAdmin();
}

function renderAdmin() {
  const d = adm.data, wk = adm.week;
  const submitted = d.users.filter((u) => u.submitted_at).length;
  const tag = wk === ME.this_week ? "本週" : wk === ME.next_week ? "下週" : wk === ME.fill_week ? "下下週（員工填寫中）" : "";
  const status = d.published_at
    ? `<span class="badge ok">已公布 ${d.published_at.replace("T", " ")}</span>`
    : `<span class="badge draft">草稿（員工看不到）</span>`;
  $("#app").innerHTML = `
    <div class="card">
      <div class="bar">
        <button id="prev">◀</button>
        <h2 style="margin:0">${weekLabel(wk)} ${tag}</h2>
        <button id="next">▶</button>
        <button id="go-next">跳到下週</button>
        ${status}
      </div>
      <p class="sub">已填寫可上班時段：${submitted} / ${d.users.length} 人</p>
      <div class="bar">
        <div class="daytabs">${DAYS.map((x, i) => `<button data-day="${i}" class="${i === adm.day ? "on" : ""}">週${x} ${md(addDays(wk, i))}</button>`).join("")}</div>
      </div>
      <div class="bar">
        <label><input type="checkbox" id="only" ${adm.onlySubmitted ? "checked" : ""}> 只顯示有填寫的人</label>
        <input id="search" placeholder="搜尋編號或姓名" value="${esc(adm.search)}" size="14">
        <div class="legend">
          <span><i style="background:var(--avail)"></i>員工可上</span>
          <span><i style="background:var(--sched)"></i>已排班</span>
          <span><i style="background:var(--warn)"></i>排了但員工沒填可上</span>
        </div>
        <span class="spacer"></span>
        <button id="fill-day" title="把今天每個人可上的時段全部排進去">當天全部依填寫帶入</button>
        <button id="clear-day" class="danger">清除當天排班</button>
      </div>
      <div class="scroll" id="grid"></div>
      <div class="bar">
        <span class="sub">點格子排班／取消；按住滑鼠可拖曳。</span>
        <span class="spacer"></span>
        <span id="dirty" class="low"></span>
        <button id="save">儲存草稿</button>
        ${d.published_at ? '<button id="unpub" class="danger">取消公布</button><button id="pub" class="primary">儲存並更新公布</button>'
                         : '<button id="pub" class="primary">儲存並公布</button>'}
      </div>
    </div>`;
  const move = (n) => { if (confirmLeave()) { adm.week = addDays(adm.week, 7 * n); loadAdminWeek(); } };
  $("#prev").onclick = () => move(-1);
  $("#next").onclick = () => move(1);
  $("#go-next").onclick = () => { if (confirmLeave()) { adm.week = ME.next_week; loadAdminWeek(); } };
  document.querySelectorAll(".daytabs button").forEach((b) => (b.onclick = () => { adm.day = +b.dataset.day; renderAdmin(); markDirty(dirty); }));
  $("#only").onchange = (e) => { adm.onlySubmitted = e.target.checked; renderGrid(); };
  $("#search").oninput = (e) => { adm.search = e.target.value; renderGrid(); };
  $("#fill-day").onclick = () => { visibleUsers().forEach((u) => (u.sched[adm.day] = u.avail[adm.day])); markDirty(true); renderGrid(); };
  $("#clear-day").onclick = () => {
    if (!confirm(`清除週${DAYS[adm.day]}（目前列表中的人）的排班？`)) return;
    visibleUsers().forEach((u) => (u.sched[adm.day] = 0)); markDirty(true); renderGrid();
  };
  $("#save").onclick = () => saveAdmin(undefined);
  $("#pub").onclick = () => saveAdmin(true);
  if ($("#unpub")) $("#unpub").onclick = () => { if (confirm("取消公布後員工就看不到這週班表，確定？")) saveAdmin(false); };
  renderGrid();
  markDirty(dirty);
}

function markDirty(v) { dirty = v; const el = $("#dirty"); if (el) el.textContent = v ? "有未儲存的修改" : ""; }

function visibleUsers() {
  const q = adm.search.trim().toLowerCase();
  return adm.data.users.filter((u) =>
    (!adm.onlySubmitted || u.submitted_at || u.sched.some((x) => x)) &&
    (!q || u.id.toLowerCase().includes(q) || u.name.toLowerCase().includes(q)));
}

function renderGrid() {
  const day = adm.day, users = visibleUsers();
  let h = `<table class="ad"><thead><tr><th class="nm">員工</th>`;
  for (let hr = 0; hr < 24; hr++) h += `<th>${pad(hr)}</th>`;
  h += `<th class="sum">當天</th><th class="sum">本週</th></tr></thead><tbody>`;
  users.forEach((u) => {
    const a = u.avail[day], s = u.sched[day];
    h += `<tr data-id="${esc(u.id)}"><td class="nm">${esc(u.id)} ${esc(u.name)}</td>`;
    for (let hr = 0; hr < 24; hr++) {
      const c = bit(s, hr) ? (bit(a, hr) ? "s" : "x") : bit(a, hr) ? "a" : "";
      h += `<td class="c ${c}" data-h="${hr}"></td>`;
    }
    h += `<td class="sum d">${count(s)}</td><td class="sum w">${u.sched.reduce((t, v) => t + count(v), 0)}</td></tr>`;
  });
  if (!users.length) h += `<tr><td class="nm" colspan="27">沒有符合的員工</td></tr>`;
  h += `</tbody><tfoot><tr><td class="nm">每小時人數</td>`;
  for (let hr = 0; hr < 24; hr++) h += `<td id="cnt-${hr}"></td>`;
  h += `<td></td><td></td></tr></tfoot></table>`;
  $("#grid").innerHTML = h;
  updateCounts();

  const byId = Object.fromEntries(adm.data.users.map((u) => [u.id, u]));
  const userOf = (c) => byId[c.parentElement.dataset.id];
  paintable($("#grid table"), (c) => !!bit(userOf(c).sched[day], +c.dataset.h), (c, on) => {
    const u = userOf(c), hr = +c.dataset.h;
    u.sched[day] = setBit(u.sched[day], hr, on);
    c.className = "c " + (on ? (bit(u.avail[day], hr) ? "s" : "x") : bit(u.avail[day], hr) ? "a" : "");
    const tr = c.parentElement;
    tr.querySelector(".d").textContent = count(u.sched[day]);
    tr.querySelector(".w").textContent = u.sched.reduce((t, v) => t + count(v), 0);
  }, () => { markDirty(true); updateCounts(); });
}

function updateCounts() {
  for (let hr = 0; hr < 24; hr++) {
    const n = adm.data.users.reduce((t, u) => t + bit(u.sched[adm.day], hr), 0);
    const el = $(`#cnt-${hr}`); if (el) el.textContent = n || "";
  }
}

async function saveAdmin(publish) {
  const sched = Object.fromEntries(adm.data.users.map((u) => [u.id, u.sched]));
  const body = { week_start: adm.week, sched };
  if (publish !== undefined) body.publish = publish;
  try {
    const r = await api("PUT", "/api/admin/schedule", body);
    adm.data.published_at = r.published_at;
    dirty = false;
    toast(publish === true ? "已公布，員工現在看得到" : publish === false ? "已取消公布" : "草稿已儲存");
    renderAdmin();
  } catch (e) { toast(e.message, true); }
}

// ---------------------------------------------------------------- 管理員：員工管理
async function viewAdminUsers() {
  const app = $("#app");
  const { users } = await api("GET", "/api/admin/users");
  app.innerHTML = `
    <div class="card">
      <h2>新增員工</h2>
      <form class="bar" id="add">
        <input name="id" placeholder="編號，例如 101" required size="8">
        <input name="name" placeholder="姓名" required>
        <button type="submit">新增</button>
      </form>
    </div>
    <div class="card">
      <h2>員工列表</h2>
      <p class="sub">「重設密碼」後，該員工下次登入輸入的密碼就會成為新密碼。</p>
      <table class="list"><thead><tr><th>編號</th><th>姓名</th><th>密碼</th><th></th></tr></thead><tbody>
      ${users.map((u) => `<tr class="${u.active ? "" : "off"}" data-id="${esc(u.id)}" data-name="${esc(u.name)}" data-active="${u.active}">
          <td>${esc(u.id)}${u.is_admin ? ' <span class="badge">管理員</span>' : ""}</td>
          <td>${esc(u.name)}</td>
          <td>${u.has_pw ? "已設定" : '<span class="sub">尚未設定</span>'}</td>
          <td><button class="link" data-act="rename">改名</button>
              <button class="link" data-act="reset">重設密碼</button>
              ${u.is_admin ? "" : `<button class="link" data-act="toggle">${u.active ? "停用" : "啟用"}</button>`}</td></tr>`).join("")}
      </tbody></table>
    </div>`;
  $("#add").onsubmit = async (e) => {
    e.preventDefault();
    try { await api("POST", "/api/admin/user", { id: e.target.id.value.trim(), name: e.target.name.value.trim() }); toast("已新增"); viewAdminUsers(); }
    catch (err) { toast(err.message, true); }
  };
  app.querySelectorAll("button[data-act]").forEach((b) => (b.onclick = async () => {
    const tr = b.closest("tr"), id = tr.dataset.id, name = tr.dataset.name, active = tr.dataset.active === "1";
    try {
      if (b.dataset.act === "rename") {
        const n = prompt(`${id} 的新姓名：`, name); if (!n) return;
        await api("POST", "/api/admin/user", { id, name: n, active });
      } else if (b.dataset.act === "reset") {
        if (!confirm(`確定重設 ${id} ${name} 的密碼？`)) return;
        await api("POST", "/api/admin/reset-password", { id });
      } else {
        if (active && !confirm(`停用 ${id} ${name}？停用後無法登入，也不會出現在排班表。`)) return;
        await api("POST", "/api/admin/user", { id, name, active: !active });
      }
      toast("已更新"); viewAdminUsers();
    } catch (e) { toast(e.message, true); }
  }));
}

boot().catch((e) => toast(e.message, true));
