/* 简记 · 网页版便签 —— 全部逻辑都在浏览器里，数据只写进本机 localStorage。
   顺带在页面加载时问一下 GitHub，把下载按钮换成最新的发行版 APK。 */

const STORAGE_KEY = 'jianji.web.notes.v1';
const REPO = 'yuedaochangmendian/jianji-note';
/* 五种便签底色，对应 App 里的强调色（琥珀 / 靛蓝 / 青绿 / 紫 / 玫红） */
const PALETTE = ['#fff4d6', '#e8eaf6', '#e0f2f1', '#ede7f6', '#fce4ec'];
const WELCOME = '欢迎来到网页版简记 —— 点一下这句话就能改，鼠标移上去可以置顶或删掉。手机上装 App 后，笔记会存进你自己的设备。';

const boardEl = document.getElementById('board');
const formEl = document.getElementById('composer');
const inputEl = document.getElementById('note-input');
const addBtn = document.getElementById('add-btn');
const clearBtn = document.getElementById('clear-all');
const yearEl = document.getElementById('year');
const toastEl = document.getElementById('toast');
const toastTextEl = document.getElementById('toast-text');
const toastUndoEl = document.getElementById('toast-undo');

let notes = load();
let undoState = null;
let toastTimer = null;
let boardReady = false;
const animatedNotes = new Set();

/* ----------------------------------------------------------- 存储 */

function load() {
  let raw = null;
  try {
    raw = localStorage.getItem(STORAGE_KEY);
  } catch (err) {
    return [];
  }
  if (raw === null) return [makeNote(WELCOME, PALETTE[0])];
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((note) => note && typeof note.text === 'string')
      .map((note) => ({
        id: String(note.id || uid()),
        text: note.text,
        color: typeof note.color === 'string' ? note.color : PALETTE[0],
        tilt: Number.isFinite(note.tilt) ? note.tilt : 0,
        created: Number.isFinite(note.created) ? note.created : Date.now(),
        pinned: Boolean(note.pinned),
      }));
  } catch (err) {
    return [];
  }
}

function save() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(notes));
  } catch (err) {
    /* 隐私模式或配额满：页面照常能用，只是不持久化 */
  }
}

/* ----------------------------------------------------------- 小工具 */

function uid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

function makeNote(text, color) {
  return {
    id: uid(),
    text: text,
    color: color || PALETTE[0],
    tilt: Number((Math.random() * 2.4 - 1.2).toFixed(2)),
    created: Date.now(),
    pinned: false,
  };
}

/* 总挑当前用得最少的颜色，便签墙看起来才不会一片黄 */
function nextColor() {
  const used = new Map(PALETTE.map((color) => [color, 0]));
  notes.forEach((note) => {
    if (used.has(note.color)) used.set(note.color, used.get(note.color) + 1);
  });
  let best = PALETTE[0];
  let least = Infinity;
  used.forEach((count, color) => {
    if (count < least) {
      least = count;
      best = color;
    }
  });
  return best;
}

function fmtTime(ts) {
  const date = new Date(ts);
  const now = new Date();
  const hh = String(date.getHours()).padStart(2, '0');
  const mm = String(date.getMinutes()).padStart(2, '0');
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();
  if (sameDay) return '今天 ' + hh + ':' + mm;
  return date.getMonth() + 1 + '月' + date.getDate() + '日 ' + hh + ':' + mm;
}

function svgIcon(path, filled) {
  const el = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  el.setAttribute('viewBox', '0 0 20 20');
  el.setAttribute('aria-hidden', 'true');
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', path);
  p.setAttribute('fill', filled ? 'currentColor' : 'none');
  p.setAttribute('stroke', 'currentColor');
  p.setAttribute('stroke-width', '1.5');
  p.setAttribute('stroke-linecap', 'round');
  p.setAttribute('stroke-linejoin', 'round');
  el.append(p);
  return el;
}

const ICON_PIN = 'M10 3.4 12.1 8l4.9.5-3.7 3.3 1 4.8-4.3-2.5-4.3 2.5 1-4.8L3 8.5 7.9 8z';
const ICON_TRASH = 'M4.6 6.4h10.8M8.2 6.4V4.6h3.6v1.8M6.6 6.4l.6 9h5.6l.6-9';

function iconButton(label, icon, onClick) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'icon-btn';
  btn.title = label;
  btn.setAttribute('aria-label', label);
  btn.append(icon);
  btn.addEventListener('click', (event) => {
    event.stopPropagation();
    onClick();
  });
  return btn;
}

/* ----------------------------------------------------------- 渲染 */

function render() {
  const ordered = notes.slice().sort((a, b) => {
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
    return b.created - a.created;
  });

  const frag = document.createDocumentFragment();

  if (!ordered.length) {
    const empty = document.createElement('div');
    empty.className = 'board-empty';
    const title = document.createElement('strong');
    title.textContent = '还没有便签';
    const hint = document.createElement('span');
    hint.textContent = '在上面写一句，它就会出现在这里。';
    empty.append(title, hint);
    boardEl.replaceChildren(empty);
    return;
  }

  ordered.forEach((note, index) => frag.append(noteCard(note, index)));
  boardEl.replaceChildren(frag);
}

function noteCard(note, index) {
  const card = document.createElement('article');
  card.className = 'note' + (note.pinned ? ' is-pinned' : '');
  card.dataset.id = note.id;
  card.style.setProperty('--note-bg', note.color);
  card.style.setProperty('--tilt', note.tilt + 'deg');

  if (!animatedNotes.has(note.id)) {
    animatedNotes.add(note.id);
    if (boardReady) {
      card.classList.add('note-enter');
    } else {
      card.classList.add('note-initial');
      card.style.setProperty('--i', index);
    }
  }

  const body = document.createElement('div');
  body.className = 'note-text';
  body.textContent = note.text;
  body.tabIndex = 0;
  body.setAttribute('role', 'button');
  body.setAttribute('aria-label', '编辑这张便签');
  body.addEventListener('click', () => startEdit(card, note));
  body.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      startEdit(card, note);
    }
  });

  const foot = document.createElement('div');
  foot.className = 'note-foot';

  const time = document.createElement('time');
  time.dateTime = new Date(note.created).toISOString();
  time.textContent = fmtTime(note.created);

  const actions = document.createElement('div');
  actions.className = 'note-actions';

  const pinBtn = iconButton(
    note.pinned ? '取消置顶' : '置顶',
    svgIcon(ICON_PIN, note.pinned),
    () => togglePin(note.id)
  );
  if (note.pinned) pinBtn.classList.add('is-on');

  const delBtn = iconButton('删除', svgIcon(ICON_TRASH, false), () => removeNote(note.id));

  actions.append(pinBtn, delBtn);
  foot.append(time, actions);
  card.append(body, foot);
  return card;
}

/* ----------------------------------------------------------- 增删改 */

function addNote(text) {
  const value = text.replace(/\s+$/, '');
  if (!value.trim()) return;
  notes.push(makeNote(value, nextColor()));
  save();
  render();
  boardEl.lastElementChild?.scrollIntoView({ block: 'nearest' });
}

function togglePin(id) {
  const note = notes.find((item) => item.id === id);
  if (!note) return;
  note.pinned = !note.pinned;
  save();
  render();
  showToast(note.pinned ? '已置顶' : '已取消置顶');
}

function removeNote(id) {
  const index = notes.findIndex((item) => item.id === id);
  if (index < 0) return;
  const [removed] = notes.splice(index, 1);
  save();
  render();
  showToast('便签已删除', () => {
    notes.splice(Math.min(index, notes.length), 0, removed);
    save();
    render();
  });
}

function startEdit(card, note) {
  if (card.querySelector('.note-edit')) return;
  const body = card.querySelector('.note-text');
  if (!body) return;

  const editor = document.createElement('textarea');
  editor.className = 'note-edit';
  editor.value = note.text;
  editor.rows = Math.max(2, note.text.split('\n').length);
  body.replaceWith(editor);
  editor.focus();
  editor.setSelectionRange(editor.value.length, editor.value.length);

  let done = false;
  const commit = (keep) => {
    if (done) return;
    done = true;
    if (keep) {
      const value = editor.value.replace(/\s+$/, '');
      if (value.trim()) {
        note.text = value;
      } else {
        notes = notes.filter((item) => item.id !== note.id);
      }
      save();
    }
    render();
  };

  editor.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      commit(false);
    } else if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      commit(true);
    }
  });
  editor.addEventListener('blur', () => commit(true));
}

/* ----------------------------------------------------------- 提示条 */

function showToast(message, undo) {
  toastTextEl.textContent = message;
  undoState = undo || null;
  toastUndoEl.hidden = !undoState;
  toastEl.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(hideToast, 4200);
}

function hideToast() {
  toastEl.hidden = true;
  undoState = null;
}

toastUndoEl.addEventListener('click', () => {
  const undo = undoState;
  hideToast();
  if (undo) undo();
});

/* ----------------------------------------------------------- 输入区 */

function autoGrow() {
  inputEl.style.height = 'auto';
  inputEl.style.height = Math.min(inputEl.scrollHeight, 190) + 'px';
  addBtn.disabled = !inputEl.value.trim();
}

inputEl.addEventListener('input', autoGrow);

inputEl.addEventListener('keydown', (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    formEl.requestSubmit();
  }
});

formEl.addEventListener('submit', (event) => {
  event.preventDefault();
  const value = inputEl.value;
  inputEl.value = '';
  autoGrow();
  addNote(value);
  inputEl.focus();
});

document.querySelectorAll('.chip').forEach((chip) => {
  chip.addEventListener('click', () => {
    inputEl.value = chip.dataset.text || '';
    autoGrow();
    inputEl.focus();
    inputEl.setSelectionRange(inputEl.value.length, inputEl.value.length);
  });
});

clearBtn.addEventListener('click', () => {
  if (!notes.length) {
    showToast('本来就是空的');
    return;
  }
  if (!window.confirm('清空全部 ' + notes.length + ' 张便签？此操作不能撤销。')) return;
  const removed = notes;
  notes = [];
  save();
  render();
  showToast('已清空', () => {
    notes = removed;
    save();
    render();
  });
});

/* 同一个浏览器的另一个标签页改了便签，这边跟着更新 */
window.addEventListener('storage', (event) => {
  if (event.key !== STORAGE_KEY) return;
  notes = load();
  render();
});

/* ----------------------------------------------------------- 启动 */

/* ----------------------------------------------------------- 动效 */

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/* 滚动到哪，哪一块才升起来 */
function initMotion() {
  const targets = document.querySelectorAll('[data-reveal]');
  if (!targets.length) return;
  if (prefersReducedMotion() || !('IntersectionObserver' in window)) {
    targets.forEach((el) => el.classList.add('is-in'));
    return;
  }
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-in');
        observer.unobserve(entry.target);
      });
    },
    { rootMargin: '0px 0px -6% 0px', threshold: 0.1 }
  );
  targets.forEach((el) => observer.observe(el));
}

/* 顶部栏滚动后浮起来（毛玻璃 + 分隔线） */
function initNavState() {
  const bar = document.getElementById('topbar');
  if (!bar) return;
  const update = () => bar.classList.toggle('is-scrolled', window.scrollY > 8);
  update();
  window.addEventListener('scroll', update, { passive: true });
}

/* 输入框获得焦点时，整条轻轻抬起 */
function initComposer() {
  const composer = document.querySelector('.composer');
  if (!composer) return;
  composer.addEventListener('focusin', () => composer.classList.add('is-active'));
  composer.addEventListener('focusout', () => composer.classList.remove('is-active'));
}

/* 右上角那颗星：拿不到（离线、限流）就干脆不显示 */
async function initStars() {
  const el = document.querySelector('[data-stars]');
  if (!el) return;
  try {
    const res = await fetch('https://api.github.com/repos/' + REPO, {
      headers: { Accept: 'application/vnd.github+json' },
    });
    if (!res.ok) return;
    const data = await res.json();
    const count = data.stargazers_count;
    if (typeof count !== 'number' || count <= 0) return;
    el.textContent =
      '★ ' + (count >= 10000 ? (count / 10000).toFixed(1).replace(/\.0$/, '') + ' 万' : String(count));
    el.hidden = false;
  } catch (err) {
    /* 静默失败：不显示星数 */
  }
}

if (yearEl) yearEl.textContent = String(new Date().getFullYear());
render();
boardReady = true;
autoGrow();
initMotion();
initNavState();
initComposer();
initStars();

/* 下载按钮指向"最新版"：页面里写死的是 v1.5.0，加载后再跟 GitHub 对一次表。
   拿不到（离线、被限流）就沿用写死的值，不影响页面。 */
async function syncLatestRelease() {
  try {
    const res = await fetch('https://api.github.com/repos/' + REPO + '/releases/latest', {
      headers: { Accept: 'application/vnd.github+json' },
    });
    if (!res.ok) return;
    const release = await res.json();
    const assets = Array.isArray(release.assets) ? release.assets : [];
    const apk =
      assets.find((item) => /release\.apk$/i.test(item.name)) ||
      assets.find((item) => /\.apk$/i.test(item.name));
    if (!apk) return;

    document.querySelectorAll('[data-apk-link]').forEach((el) => {
      el.setAttribute('href', apk.browser_download_url);
    });
    document.querySelectorAll('[data-apk-version]').forEach((el) => {
      if (release.tag_name) el.textContent = release.tag_name;
    });
    document.querySelectorAll('[data-apk-size]').forEach((el) => {
      el.textContent = (apk.size / 1048576).toFixed(1) + ' MB';
    });
  } catch (err) {
    /* 保持页面里写死的版本信息 */
  }
}

syncLatestRelease();
