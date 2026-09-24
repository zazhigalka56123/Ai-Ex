'use strict';

// Простой фронт для ai-ex: одна страница, hash-роутинг, без сборки и зависимостей.
// Текущий пользователь передаётся заголовком X-User-Id (лаб. 1, см. ADR-0003).

const API = '/api/v1';
const STORAGE_KEY = 'aiex.userId';

const DEMO_USERS = [
  { id: '00000000-0000-0000-0000-00000000c001', label: 'Клиент' },
  { id: '00000000-0000-0000-0000-00000000b001', label: 'Психолог Анна' },
  { id: '00000000-0000-0000-0000-00000000a001', label: 'Администратор' },
];

const LABELS = {
  personaStatus: { DRAFT: 'черновик', TRAINING: 'обучается', READY: 'готова', ARCHIVED: 'в архиве' },
  relationship: { EX_PARTNER: 'бывший партнёр', EX_CRUSH: 'бывшая симпатия', FRIEND: 'друг', OTHER: 'знакомый человек' },
  importStatus: { PENDING: 'в очереди', PARSING: 'разбирается', PARSED: 'разобран', FAILED: 'ошибка' },
  importSource: { TELEGRAM_JSON: 'Telegram JSON', WHATSAPP_TXT: 'WhatsApp TXT', PLAIN_TEXT: 'Текст «Имя: сообщение»' },
  sessionStatus: { REQUESTED: 'запрошена', CONFIRMED: 'подтверждена', DONE: 'проведена', CANCELLED: 'отменена' },
  flagStatus: { OPEN: 'новая', IN_REVIEW: 'на рассмотрении', RESOLVED: 'решена', REJECTED: 'отклонена' },
  flagReason: { ABUSE: 'оскорбления', SELF_HARM: 'самоповреждение', SPAM: 'спам', OTHER: 'другое' },
  flagSource: { USER: 'жалоба', GUARDRAIL: 'авто (guardrails)' },
  notification: {
    IMPORT_PARSED: 'Переписка разобрана',
    IMPORT_FAILED: 'Не удалось разобрать переписку',
    PERSONA_READY: 'Персона готова к общению',
    PERSONA_ARCHIVED: 'Персона архивирована',
    CONSULTATION_REQUESTED: 'Новая запись на консультацию',
    CONSULTATION_STATUS_CHANGED: 'Статус консультации изменился',
    FLAG_RESOLVED: 'Жалоба рассмотрена',
  },
  role: { USER: 'клиент', SPECIALIST: 'специалист', ADMIN: 'администратор' },
};

const TONE = {
  READY: 'ok', ACTIVE: 'ok', PARSED: 'ok', CONFIRMED: 'ok', DONE: 'ok', RESOLVED: 'ok',
  TRAINING: 'warn', PARSING: 'warn', PENDING: 'warn', REQUESTED: 'warn', OPEN: 'warn', IN_REVIEW: 'warn',
  FAILED: 'bad', CANCELLED: 'bad', BLOCKED: 'bad', ARCHIVED: 'bad', REJECTED: 'bad',
};

const state = { user: null, leave: [] }; // leave - что закрыть при уходе со страницы (сокеты)

// ---------- утилиты ----------

// Дети: узлы, строки, числа и вложенные массивы; null и false пропускаются.
function nodes(children) {
  return children.flat(Infinity)
    .filter((child) => child != null && child !== false)
    .map((child) => (child instanceof Node ? child : String(child)));
}

function mount(el, ...children) {
  el.replaceChildren(...nodes(children));
}

function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value == null || value === false) continue;
    if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
    else if (key === 'class') el.className = value;
    else if (key === 'value' || key === 'checked' || key === 'disabled') el[key] = value;
    else el.setAttribute(key, value === true ? '' : value);
  }
  el.append(...nodes(children));
  return el;
}

function badge(value, labels) {
  return h('span', { class: `badge ${TONE[value] || ''}` }, labels?.[value] || value);
}

function fmtDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function fmtTime(iso) {
  return new Date(iso).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
}

function pct(x) {
  return `${Math.round(x * 100)}%`;
}

function has(role) {
  return state.user?.roles.includes(role);
}

function go(path) {
  if (location.hash === `#${path}`) render();
  else location.hash = path;
}

function storage(action, value) {
  try {
    if (action === 'get') return localStorage.getItem(STORAGE_KEY);
    if (action === 'set') localStorage.setItem(STORAGE_KEY, value);
    if (action === 'remove') localStorage.removeItem(STORAGE_KEY);
  } catch {
    // приватный режим или запрещённое хранилище - просто не запоминаем
  }
  return null;
}

let toastTimer;
function toast(text, isError = false) {
  const el = document.getElementById('toast');
  el.textContent = text;
  el.className = isError ? 'error' : '';
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, isError ? 6000 : 3000);
}

// ---------- API ----------

class ApiError extends Error {
  constructor(status, problem) {
    const details = (problem?.errors || []).map((e) => `${e.field}: ${e.message}`);
    super([problem?.detail || problem?.title || `HTTP ${status}`, ...details].join('\n'));
    this.status = status;
    this.code = problem?.code;
    this.traceId = problem?.traceId;
  }
}

async function api(method, path, body) {
  const headers = { Accept: 'application/json' };
  if (state.user) headers['X-User-Id'] = state.user.id;
  let payload;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const res = await fetch(API + path, { method, headers, body: payload });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { detail: text.slice(0, 200) };
  }
  if (!res.ok) throw new ApiError(res.status, data);
  return { data, total: Number(res.headers.get('X-Total-Count') ?? NaN) };
}

const get = (path) => api('GET', path).then((r) => r.data);
const post = (path, body) => api('POST', path, body).then((r) => r.data);
const patch = (path, body) => api('PATCH', path, body).then((r) => r.data);
const del = (path) => api('DELETE', path);

function qs(params) {
  const search = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v != null && v !== '') search.set(k, v);
  const s = search.toString();
  return s ? `?${s}` : '';
}

// Выполнить действие с сообщением об ошибке и перерисовкой страницы после успеха.
async function act(fn, successText) {
  try {
    const result = await fn();
    if (successText) toast(successText);
    return result;
  } catch (e) {
    toast(e.message, true);
    return undefined;
  }
}

// ---------- диалог с формой ----------

// fields: [{ name, label, type, options: [[value, label]], value, required, placeholder }]
function formDialog(title, fields, submitLabel = 'Сохранить') {
  const dialog = document.getElementById('dialog');
  const form = document.getElementById('dialog-form');
  const inputs = {};
  mount(form,
    h('h3', null, title),
    fields.map((f) => {
      if (f.type === 'checkbox') {
        inputs[f.name] = h('input', { type: 'checkbox', checked: !!f.value });
        return h('label', { class: 'check' }, inputs[f.name], h('span', null, f.label));
      }
      if (f.type === 'checks') {
        const boxes = f.options.map(([value, label]) => {
          const box = h('input', { type: 'checkbox', value, checked: (f.value || []).includes(value) });
          return { box, el: h('label', { class: 'check' }, box, h('span', null, label)) };
        });
        inputs[f.name] = boxes.map((b) => b.box);
        return h('div', { style: 'margin-bottom:10px' }, h('div', { class: 'small muted' }, f.label), boxes.map((b) => b.el));
      }
      let input;
      if (f.type === 'select') {
        input = h('select', { required: f.required }, f.options.map(([value, label]) => h('option', { value, selected: value === f.value }, label)));
      } else if (f.type === 'textarea') {
        input = h('textarea', { rows: 4, required: f.required, placeholder: f.placeholder, maxlength: f.maxlength });
        input.value = f.value ?? '';
      } else {
        input = h('input', { type: f.type || 'text', required: f.required, placeholder: f.placeholder, min: f.min, max: f.max, step: f.step });
        input.value = f.value ?? '';
      }
      inputs[f.name] = input;
      return h('label', null, h('span', null, f.label), input);
    }),
    h('div', { class: 'row', style: 'justify-content:flex-end;margin-top:8px' },
      h('button', { value: 'cancel', formnovalidate: true }, 'Отмена'),
      h('button', { value: 'ok', class: 'primary' }, submitLabel)),
  );
  dialog.returnValue = '';
  dialog.showModal();
  return new Promise((resolve) => {
    dialog.addEventListener('close', () => {
      if (dialog.returnValue !== 'ok') return resolve(null);
      const values = {};
      for (const [name, input] of Object.entries(inputs)) {
        if (Array.isArray(input)) values[name] = input.filter((b) => b.checked).map((b) => b.value);
        else if (input.type === 'checkbox') values[name] = input.checked;
        else values[name] = input.value.trim();
      }
      resolve(values);
    }, { once: true });
  });
}

// ---------- каркас: навигация, вход ----------

const NAV = [
  { role: 'USER', path: '/personas', label: 'Персоны' },
  { role: 'USER', path: '/chats', label: 'Беседы' },
  { role: 'USER', path: '/specialists', label: 'Специалисты' },
  { role: 'SPECIALIST', path: '/my-profile', label: 'Мой профиль' },
  { role: ['USER', 'SPECIALIST'], path: '/consultations', label: 'Консультации' },
  { role: 'ADMIN', path: '/admin/flags', label: 'Модерация' },
  { role: 'ADMIN', path: '/admin/users', label: 'Пользователи' },
  { role: 'ADMIN', path: '/admin/dictionaries', label: 'Справочники' },
  { role: 'ADMIN', path: '/admin/metrics', label: 'Метрики' },
  { role: null, path: '/notifications', label: 'Уведомления' },
];

function renderChrome(path) {
  const nav = document.getElementById('nav');
  const who = document.getElementById('who');
  if (!state.user) {
    mount(nav);
    mount(who);
    return;
  }
  const items = NAV.filter((item) => item.role == null || [item.role].flat().some(has));
  mount(nav, ...items.map((item) =>
    h('a', { href: `#${item.path}`, class: path.startsWith(item.path) ? 'active' : '' }, item.label)));
  mount(who,
    h('span', null, `${state.user.displayName} · ${state.user.roles.map((r) => LABELS.role[r]).join(', ')}`),
    h('button', { class: 'link', onclick: logout }, 'Выйти'),
  );
}

async function login(userId) {
  state.user = { id: userId, roles: [] };
  try {
    state.user = await get(`/users/${userId}`);
    storage('set', userId);
    go('/');
  } catch (e) {
    state.user = null;
    storage('remove');
    toast(e.message, true);
  }
}

function logout() {
  state.user = null;
  document.getElementById('toast').hidden = true;
  storage('remove');
  go('/login');
}

function homePath() {
  if (has('ADMIN')) return '/admin/flags';
  if (has('SPECIALIST')) return '/my-profile';
  if (has('USER')) return '/personas';
  return '/notifications';
}

async function loginPage(view) {
  const uuid = h('input', { placeholder: 'UUID пользователя', style: 'flex:1;min-width:240px' });
  const email = h('input', { type: 'email', required: true, placeholder: 'masha@example.com' });
  const name = h('input', { required: true, placeholder: 'Маша' });
  const roleBoxes = ['USER', 'SPECIALIST'].map((r) => h('input', { type: 'checkbox', value: r, checked: r === 'USER' }));

  mount(view,
    h('h1', null, 'Вход'),
    h('p', { class: 'muted' }, 'Авторизации в лаб. 1 нет: выберите пользователя, его id уйдёт в заголовке X-User-Id.'),
    h('div', { class: 'card' },
      h('h3', null, 'Демо-пользователи'),
      h('div', { class: 'row' }, DEMO_USERS.map((u) => h('button', { onclick: () => login(u.id) }, u.label)))),
    h('div', { class: 'card' },
      h('h3', null, 'По id'),
      h('form', { class: 'row', onsubmit: (e) => { e.preventDefault(); if (uuid.value.trim()) login(uuid.value.trim()); } },
        uuid, h('button', { class: 'primary' }, 'Войти'))),
    h('div', { class: 'card' },
      h('h3', null, 'Регистрация'),
      h('form', {
        class: 'stack',
        onsubmit: async (e) => {
          e.preventDefault();
          const roles = roleBoxes.filter((b) => b.checked).map((b) => b.value);
          const user = await act(() => post('/users', { email: email.value.trim(), displayName: name.value.trim(), roles }));
          if (user) login(user.id);
        },
      },
      h('label', null, h('span', null, 'Email'), email),
      h('label', null, h('span', null, 'Имя'), name),
      h('div', null, roleBoxes.map((b) => h('label', { class: 'check' }, b, h('span', null, LABELS.role[b.value])))),
      h('button', { class: 'primary' }, 'Создать и войти'))),
  );
}

// ---------- персоны ----------

async function loadTags() {
  return get('/tags?size=50');
}

async function personasPage(view) {
  const [personas, tags] = await Promise.all([get('/personas?size=50'), loadTags()]);

  async function create() {
    const values = await formDialog('Новая персона', [
      { name: 'name', label: 'Имя', required: true, placeholder: 'Маша' },
      { name: 'relationshipKind', label: 'Кем был человек', type: 'select', options: Object.entries(LABELS.relationship) },
      { name: 'description', label: 'Описание', type: 'textarea', maxlength: 500 },
      { name: 'tagCodes', label: 'Теги', type: 'checks', options: tags.map((t) => [t.code, t.title]) },
    ], 'Создать');
    if (!values) return;
    const persona = await act(() => post('/personas', { ...values, description: values.description || null }));
    if (persona) go(`/personas/${persona.id}`);
  }

  mount(view,
    h('div', { class: 'row spread' }, h('h1', null, 'Мои персоны'), h('button', { class: 'primary', onclick: create }, '+ Новая персона')),
    personas.length === 0
      ? h('div', { class: 'card empty' }, 'Персон пока нет. Создайте персону и загрузите переписку.')
      : h('div', { class: 'grid' }, personas.map((p) =>
        h('a', { class: 'card', href: `#/personas/${p.id}`, style: 'color:inherit;text-decoration:none' },
          h('div', { class: 'row spread' }, h('h3', null, p.name), badge(p.status, LABELS.personaStatus)),
          h('div', { class: 'muted small' }, LABELS.relationship[p.relationshipKind]),
          p.description && h('p', { class: 'small' }, p.description)))),
  );
}

async function personaPage(view, id) {
  const persona = await get(`/personas/${id}`);
  const imports = await get(`/personas/${id}/imports?size=10`);
  const profile = persona.activeProfile ? await get(`/personas/${id}/profile`).catch(() => null) : null;
  const archived = persona.status === 'ARCHIVED';

  async function startChat() {
    const conversation = await act(() => post('/conversations', { personaId: id }));
    if (conversation) go(`/chat/${conversation.id}`);
  }

  async function archive() {
    if (!confirm(`Архивировать «${persona.name}»? Профиль отключится, беседы закроются, загруженные сообщения удалятся.`)) return;
    if (await act(() => del(`/personas/${id}`), 'Персона архивирована')) go('/personas');
  }

  async function rebuild() {
    if (await act(() => post(`/personas/${id}/profile:rebuild`), 'Профиль пересобран')) render();
  }

  async function edit() {
    const values = await formDialog('Изменить персону', [
      { name: 'name', label: 'Имя', required: true, value: persona.name },
      { name: 'relationshipKind', label: 'Кем был человек', type: 'select', options: Object.entries(LABELS.relationship), value: persona.relationshipKind },
      { name: 'description', label: 'Описание', type: 'textarea', value: persona.description || '', maxlength: 500 },
    ]);
    if (values && await act(() => patch(`/personas/${id}`, values), 'Сохранено')) render();
  }

  const fileInput = h('input', { type: 'file', required: true, accept: '.json,.txt,text/plain,application/json' });
  const theirName = h('input', { placeholder: 'Как она/он подписан(а) в выгрузке (необязательно)', style: 'flex:1;min-width:200px' });
  const source = h('select', null, h('option', { value: '' }, 'Формат: автоопределение'),
    Object.entries(LABELS.importSource).map(([v, l]) => h('option', { value: v }, l)));
  const uploadStatus = h('div', { class: 'small muted' });

  async function upload(e) {
    e.preventDefault();
    const file = fileInput.files[0];
    if (!file) return;
    const data = new FormData();
    data.append('file', file);
    const query = qs({ theirName: theirName.value.trim(), source: source.value });
    uploadStatus.textContent = 'Загружаю…';
    let chatImport = await act(() => post(`/personas/${id}/imports${query}`, data));
    if (!chatImport) { uploadStatus.textContent = ''; return; }
    while (chatImport.status === 'PENDING' || chatImport.status === 'PARSING') {
      uploadStatus.textContent = `Импорт: ${LABELS.importStatus[chatImport.status]}…`;
      await new Promise((r) => setTimeout(r, 1000));
      chatImport = await get(`/imports/${chatImport.id}`);
    }
    if (chatImport.status === 'FAILED') toast(`Импорт не удался: ${chatImport.errorMessage || chatImport.errorCode}`, true);
    else toast(`Разобрано ${chatImport.messageCount} сообщений, из них её/его: ${chatImport.theirMessageCount}`);
    render();
  }

  mount(view,
    h('p', null, h('a', { href: '#/personas' }, '← Персоны')),
    h('div', { class: 'row spread' },
      h('div', { class: 'row' }, h('h1', { style: 'margin:0' }, persona.name), badge(persona.status, LABELS.personaStatus)),
      h('div', { class: 'row' },
        persona.status === 'READY' && h('button', { class: 'primary', onclick: startChat }, 'Начать беседу'),
        !archived && h('button', { onclick: edit }, 'Изменить'),
        !archived && imports.length > 0 && h('button', { onclick: rebuild }, 'Пересобрать профиль'),
        !archived && h('button', { class: 'danger', onclick: archive }, 'Архивировать'))),
    h('p', { class: 'muted' }, LABELS.relationship[persona.relationshipKind], persona.description && ` · ${persona.description}`),
    persona.tags.length > 0 && h('div', { class: 'phrases' },
      persona.tags.map((t) => h('span', { title: `${t.source === 'AUTO' ? 'автотег' : 'ручной'}, вес ${t.weight}` }, t.title))),

    !archived && h('div', { class: 'card', style: 'margin-top:16px' },
      h('h3', null, 'Загрузить переписку'),
      h('p', { class: 'small muted' }, 'result.json из Telegram Desktop, .txt из WhatsApp или текст вида «Имя: сообщение». Пример: docs/demo/telegram-masha.json'),
      h('form', { class: 'row', onsubmit: upload }, fileInput, source, theirName, h('button', { class: 'primary' }, 'Загрузить')),
      uploadStatus),

    profile && profileSection(profile),

    h('h2', null, 'Импорты'),
    imports.length === 0
      ? h('div', { class: 'muted' }, 'Ещё ничего не загружено.')
      : h('div', { class: 'card table-wrap' }, h('table', null,
        h('tr', null, h('th', null, 'Файл'), h('th', null, 'Статус'), h('th', null, 'Сообщений'), h('th', null, 'Когда')),
        imports.map((i) => h('tr', null,
          h('td', null, i.originalFilename, i.errorMessage && h('div', { class: 'small', style: 'color:var(--danger)' }, i.errorMessage)),
          h('td', null, badge(i.status, LABELS.importStatus)),
          h('td', null, `${i.messageCount} (её/его: ${i.theirMessageCount})`),
          h('td', { class: 'small muted' }, fmtDate(i.createdAt)))))),
  );
}

function profileSection(profile) {
  const s = profile.style;
  const c = profile.corpusStats;
  return h('div', null,
    h('h2', null, `Профиль v${profile.versionNo}`),
    h('div', { class: 'grid' },
      h('div', { class: 'card' },
        h('h3', null, 'Стиль'),
        h('table', null,
          h('tr', null, h('td', null, 'Средняя длина'), h('td', null, `${Math.round(s.avgMessageLength)} симв.`)),
          h('tr', null, h('td', null, 'Эмодзи на сообщение'), h('td', null, s.emojiPerMessage.toFixed(2), ' ', s.topEmojis.join(' '))),
          h('tr', null, h('td', null, 'С маленькой буквы'), h('td', null, pct(s.lowercaseStartShare))),
          h('tr', null, h('td', null, 'Капс'), h('td', null, pct(s.capsShare))),
          h('tr', null, h('td', null, 'Скорость ответа'), h('td', null, s.replySpeed)),
          h('tr', null, h('td', null, 'Ночью'), h('td', null, pct(c.nightShare))),
          h('tr', null, h('td', null, 'Ревность / нежность'), h('td', null, `${c.jealousyMarkers} / ${c.affectionMarkers}`)))),
      h('div', { class: 'card' },
        h('h3', null, 'Черты'),
        h('table', null, profile.traits.map((t) =>
          h('tr', null, h('td', null, t.key), h('td', null, t.value), h('td', { class: 'small muted' }, t.source === 'AUTO' ? 'авто' : 'вручную')))))),
    s.samplePhrases.length > 0 && h('div', { class: 'card' },
      h('h3', null, 'Характерные фразы'),
      h('div', { class: 'phrases' }, s.samplePhrases.map((p) => h('span', null, p)))),
    h('details', { class: 'card' }, h('summary', null, 'Системный промпт'), h('pre', null, profile.systemPrompt)),
  );
}

// ---------- беседы ----------

async function chatsPage(view) {
  const [conversations, personas] = await Promise.all([get('/conversations?size=50'), get('/personas?size=50')]);
  const personaName = Object.fromEntries(personas.map((p) => [p.id, p.name]));
  const ready = personas.filter((p) => p.status === 'READY');

  async function create() {
    if (ready.length === 0) return toast('Нет готовых персон: сначала загрузите переписку', true);
    const values = await formDialog('Новая беседа', [
      { name: 'personaId', label: 'С кем', type: 'select', options: ready.map((p) => [p.id, p.name]) },
      { name: 'title', label: 'Название (необязательно)', placeholder: 'Три часа ночи' },
    ], 'Начать');
    if (!values) return;
    const conversation = await act(() => post('/conversations', { personaId: values.personaId, title: values.title || null }));
    if (conversation) go(`/chat/${conversation.id}`);
  }

  mount(view,
    h('div', { class: 'row spread' }, h('h1', null, 'Беседы'), h('button', { class: 'primary', onclick: create }, '+ Новая беседа')),
    conversations.length === 0
      ? h('div', { class: 'card empty' }, 'Бесед пока нет.')
      : conversations.map((c) => h('a', { class: 'card', href: `#/chat/${c.id}`, style: 'display:block;color:inherit;text-decoration:none' },
        h('div', { class: 'row spread' },
          h('h3', null, c.title),
          c.status === 'ARCHIVED' && badge(c.status, { ARCHIVED: 'в архиве' })),
        h('div', { class: 'small muted' },
          `${personaName[c.personaId] || 'персона'} · ${c.messageCount} сообщ. · ${c.lastMessageAt ? fmtDate(c.lastMessageAt) : 'пока пусто'}`))),
  );
}

async function reportMessage(message, onDone) {
  const values = await formDialog('Пожаловаться на сообщение', [
    { name: 'reason', label: 'Причина', type: 'select', options: Object.entries(LABELS.flagReason) },
    { name: 'comment', label: 'Комментарий', type: 'textarea', maxlength: 500 },
  ], 'Отправить');
  if (!values) return;
  const flag = await act(() => post('/moderation/flags', { messageId: message.id, reason: values.reason, comment: values.comment || null }),
    'Жалоба отправлена администратору');
  if (flag) onDone();
}

// Живой чат беседы: /api/v1/conversations/{id}/ws. Браузер не ставит заголовки на WebSocket,
// поэтому пользователь уходит в ?userId= (лаб. 1). Сокет переподключается сам, пока страница открыта.
function openChatSocket(conversationId, { onEvent, onStatus, onReconnect }) {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  const url = `${scheme}://${location.host}${API}/conversations/${conversationId}/ws${qs({ userId: state.user.id })}`;
  let socket = null;
  let closedByUs = false;
  let attempt = 0;
  let everOpened = false;
  let timer = null;

  function connect() {
    onStatus(attempt === 0 ? 'connecting' : 'reconnecting');
    socket = new WebSocket(url);
    socket.onopen = () => {
      attempt = 0;
      onStatus('online');
      if (everOpened) onReconnect();
      everOpened = true;
    };
    socket.onmessage = (e) => {
      try {
        onEvent(JSON.parse(e.data));
      } catch {
        // не JSON - не наше событие
      }
    };
    socket.onclose = () => {
      if (closedByUs) return;
      attempt += 1;
      if (attempt > 6) { onStatus('offline'); return; }
      onStatus('reconnecting');
      timer = setTimeout(connect, Math.min(1000 * 2 ** (attempt - 1), 15000));
    };
  }

  connect();
  return {
    get isOpen() { return socket?.readyState === WebSocket.OPEN; },
    send(command) { socket.send(JSON.stringify(command)); },
    close() {
      closedByUs = true;
      clearTimeout(timer);
      socket?.close(1000, 'leave');
    },
  };
}

async function chatPage(view, id, readOnly) {
  const conversation = await get(`/conversations/${id}`).catch(() => null);
  const canWrite = !readOnly && conversation?.status === 'ACTIVE';
  const canReport = has('USER') || has('SPECIALIST');
  const live = canWrite || (readOnly && has('SPECIALIST') && conversation != null);

  let messages = []; // от старых к новым, без повторов по id
  let cursor = null;
  let typing = false;
  let inFlight = null; // { requestId, text, accepted } - своё сообщение, ответ на которое ещё идёт
  const log = h('div', { class: 'chat-log' });
  const errorLine = h('div', { class: 'chat-error' });
  const statusLine = h('span', { class: 'badge' });
  const olderButton = h('button', { class: 'link', style: 'align-self:center', onclick: () => loadOlder() }, 'Загрузить ранние сообщения');
  const input = h('textarea', { rows: 1, maxlength: 2000, placeholder: 'Сообщение… (Enter - отправить, Shift+Enter - новая строка)' });
  const sendButton = h('button', { class: 'primary' }, 'Отправить');

  function upsert(message) {
    const i = messages.findIndex((m) => m.id === message.id);
    if (i >= 0) {
      messages[i] = message;
      return;
    }
    messages.push(message);
    messages.sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id));
  }

  function bubble(m, extraClass = '') {
    const classes = ['msg', m.sender === 'USER' ? 'user' : 'persona', m.flagged ? 'flagged' : '', extraClass].join(' ');
    return h('div', { class: classes },
      m.text,
      m.createdAt && h('div', { class: 'meta' },
        h('span', null, fmtTime(m.createdAt)),
        m.flagged && h('span', null, '⚑ на модерации'),
        canReport && !m.flagged && m.id && (readOnly || m.sender === 'PERSONA') && h('button', {
          class: 'link',
          style: 'color:inherit',
          onclick: () => reportMessage(m, () => { m.flagged = true; draw(); }),
        }, 'пожаловаться')));
  }

  function draw() {
    const nearBottom = log.scrollHeight - log.scrollTop - log.clientHeight < 80;
    const pending = inFlight && !inFlight.accepted;
    mount(log,
      cursor && olderButton,
      messages.length === 0 && !pending && h('div', { class: 'empty' }, readOnly ? 'Нет доступных сообщений.' : 'Напишите первое сообщение, например «привет, спишь?»'),
      messages.map((m) => bubble(m)),
      pending && bubble({ sender: 'USER', text: inFlight.text }, 'typing'),
      typing && bubble({ sender: 'PERSONA', text: 'печатает…' }, 'typing'),
    );
    sendButton.disabled = inFlight != null;
    if (nearBottom) log.scrollTop = log.scrollHeight;
  }

  // Первая порция задаёт курсор; повторная (после переподключения) только досыпает пропущенное.
  async function loadLatest(initial = false) {
    const page = await get(`/conversations/${id}/messages?limit=30`);
    page.items.forEach(upsert);
    if (initial) cursor = page.nextCursor;
    draw();
  }

  async function loadOlder() {
    const page = await act(() => get(`/conversations/${id}/messages${qs({ limit: 30, cursor })}`));
    if (!page) return;
    const before = log.scrollHeight;
    page.items.forEach(upsert);
    cursor = page.nextCursor;
    draw();
    log.scrollTop = log.scrollHeight - before;
  }

  function finish(errorText, restoreInput) {
    if (restoreInput && inFlight && !input.value) input.value = inFlight.text;
    inFlight = null;
    errorLine.textContent = errorText || '';
    draw();
    input.focus();
  }

  function onEvent(event) {
    if (event.type === 'message') {
      upsert(event.message);
      if (inFlight && event.requestId === inFlight.requestId) inFlight.accepted = true;
    } else if (event.type === 'typing') {
      typing = event.active;
      if (!event.active && inFlight?.accepted) return finish();
    } else if (event.type === 'error' && inFlight && event.requestId === inFlight.requestId) {
      return finish(event.code === 'LLM_UNAVAILABLE' ? event.detail : `Ошибка: ${event.detail}`, !inFlight.accepted);
    } else if (event.type === 'error') {
      errorLine.textContent = `Ошибка: ${event.detail}`;
    }
    draw();
    return undefined;
  }

  const socket = live && openChatSocket(id, {
    onEvent,
    onStatus(status) {
      const labels = { connecting: 'подключение…', reconnecting: 'переподключение…', online: '● онлайн', offline: 'офлайн' };
      statusLine.textContent = labels[status];
      statusLine.className = `badge ${status === 'online' ? 'ok' : status === 'offline' ? 'bad' : 'warn'}`;
      // Пока сокет лежит, ответа на своё сообщение по нему уже не придёт.
      if (status !== 'online' && inFlight) {
        typing = false;
        finish('Связь прервалась. Сообщение могло сохраниться - история обновится после переподключения.', false);
      }
    },
    onReconnect: () => loadLatest().catch(() => {}),
  });
  if (socket) state.leave.push(() => socket.close());

  // Без сокета (не поднялся или браузер без WebSocket) остаётся обычный REST.
  async function sendViaRest(text) {
    try {
      const exchange = await post(`/conversations/${id}/messages`, { text });
      upsert(exchange.userMessage);
      upsert(exchange.reply);
      finish();
    } catch (err) {
      await loadLatest().catch(() => {});
      finish(err.status === 503 ? err.message : `Ошибка: ${err.message}`, err.status !== 503);
    }
  }

  function send(e) {
    e?.preventDefault();
    const text = input.value.trim();
    if (!text || inFlight) return;
    inFlight = { requestId: crypto.randomUUID?.() ?? String(Date.now()), text, accepted: false };
    errorLine.textContent = '';
    input.value = '';
    draw();
    log.scrollTop = log.scrollHeight;
    if (socket?.isOpen) socket.send({ type: 'send', text, requestId: inFlight.requestId });
    else sendViaRest(text);
  }

  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) send(e);
  });

  async function archive() {
    if (!confirm('Архивировать беседу? История останется, но писать будет нельзя.')) return;
    if (await act(() => del(`/conversations/${id}`), 'Беседа архивирована')) render();
  }

  const back = readOnly
    ? (has('ADMIN') ? h('a', { href: '#/admin/flags' }, '← Модерация') : h('a', { href: '#/consultations' }, '← Консультации'))
    : h('a', { href: '#/chats' }, '← Беседы');

  mount(view,
    h('div', { class: 'row spread', style: 'margin-bottom:10px' },
      h('div', { class: 'row' }, back, h('strong', null, conversation?.title || 'Беседа'),
        readOnly && h('span', { class: 'badge' }, has('ADMIN') ? 'только помеченные сообщения' : 'только чтение'),
        conversation?.status === 'ARCHIVED' && badge('ARCHIVED', { ARCHIVED: 'в архиве' }),
        live && statusLine),
      canWrite && h('button', { class: 'link', onclick: archive }, 'Архивировать беседу')),
    h('div', { class: 'chat' },
      log,
      errorLine,
      canWrite && h('form', { class: 'chat-form', onsubmit: send }, input, sendButton)),
    canWrite && h('p', { class: 'small muted' }, 'Для демонстрации сбоя добавьте в текст [[llm:down]] или [[llm:timeout]].'),
  );
  await loadLatest(true);
  log.scrollTop = log.scrollHeight;
  if (canWrite) input.focus();
}

// ---------- специалисты и консультации ----------

function specialistCard(s, withLink = true) {
  return h(withLink ? 'a' : 'div', { class: 'card', href: withLink ? `#/specialists/${s.id}` : null, style: 'display:block;color:inherit;text-decoration:none' },
    h('div', { class: 'row spread' }, h('h3', null, s.displayName || 'Специалист'), h('strong', null, `${Number(s.pricePerHour).toLocaleString('ru-RU')} ₽/ч`)),
    h('div', null, s.headline),
    h('div', { class: 'phrases', style: 'margin-top:6px' }, s.specializations.map((sp) => h('span', null, sp.title))),
    !withLink && h('p', { class: 'small', style: 'white-space:pre-wrap' }, s.bio),
    s.status === 'INACTIVE' && badge('INACTIVE', { INACTIVE: 'скрыт из каталога' }));
}

async function specialistsPage(view) {
  const specializations = await get('/specializations?size=50');
  const list = h('div', { class: 'grid' });
  const filter = h('select', { onchange: () => load() },
    h('option', { value: '' }, 'Все специализации'),
    specializations.map((s) => h('option', { value: s.code }, s.title)));

  async function load() {
    const specialists = await act(() => get(`/specialists${qs({ size: 50, specialization: filter.value })}`));
    if (!specialists) return;
    mount(list, ...(specialists.length ? specialists.map((s) => specialistCard(s)) : [h('div', { class: 'muted' }, 'Никого не нашлось.')]));
  }

  mount(view,
    h('div', { class: 'row spread' }, h('h1', null, 'Специалисты'), filter),
    h('p', { class: 'muted' }, 'Если тяжело - запишитесь к психологу. При записи можно открыть специалисту беседу с персоной.'),
    list,
  );
  await load();
}

async function specialistPage(view, id) {
  const [specialist, slots] = await Promise.all([get(`/specialists/${id}`), get(`/specialists/${id}/slots?size=50`)]);

  async function book(slot) {
    const conversations = has('USER') ? await get('/conversations?size=50&status=ACTIVE') : [];
    const values = await formDialog(`Запись на ${fmtDate(slot.startsAt)}`, [
      {
        name: 'shared',
        label: 'Показать специалисту беседу',
        type: 'select',
        options: [['', 'Не показывать'], ...conversations.map((c) => [c.id, c.title])],
      },
    ], 'Записаться');
    if (!values) return;
    const session = await act(() => post('/consultations', { slotId: slot.id, sharedConversationId: values.shared || null }), 'Вы записаны');
    if (session) go('/consultations');
  }

  mount(view,
    h('p', null, h('a', { href: '#/specialists' }, '← Специалисты')),
    specialistCard(specialist, false),
    h('h2', null, 'Свободные слоты'),
    slots.length === 0
      ? h('div', { class: 'muted' }, 'Свободных слотов нет.')
      : h('div', { class: 'grid' }, slots.map((slot) => h('div', { class: 'card row spread' },
        h('div', null, h('strong', null, fmtDate(slot.startsAt)), h('div', { class: 'small muted' }, `${slot.durationMin} мин`)),
        has('USER') && h('button', { class: 'primary', onclick: () => book(slot) }, 'Записаться')))),
  );
}

async function consultationsPage(view) {
  const [sessions, specialists] = await Promise.all([get('/consultations?size=50'), get('/specialists?size=50')]);
  const specialistName = Object.fromEntries(specialists.map((s) => [s.id, s.displayName]));

  async function change(session, body, text) {
    if (await act(() => patch(`/consultations/${session.id}`, body), text)) render();
  }

  async function cancel(session) {
    const values = await formDialog('Отменить консультацию', [{ name: 'cancelReason', label: 'Причина', type: 'textarea', maxlength: 500 }], 'Отменить запись');
    if (values) change(session, { status: 'CANCELLED', cancelReason: values.cancelReason || null }, 'Консультация отменена');
  }

  async function rate(session) {
    const values = await formDialog('Оценить консультацию', [
      { name: 'rating', label: 'Оценка', type: 'select', options: [['5', '5 - отлично'], ['4', '4'], ['3', '3'], ['2', '2'], ['1', '1']] },
    ], 'Оценить');
    if (values) change(session, { rating: Number(values.rating) }, 'Спасибо за оценку');
  }

  async function summary(session) {
    const values = await formDialog('Резюме консультации', [
      { name: 'summary', label: 'Резюме', type: 'textarea', value: session.summary || '' },
      { name: 'recommendations', label: 'Рекомендации', type: 'textarea', value: session.recommendations || '' },
    ]);
    if (values) change(session, { summary: values.summary || null, recommendations: values.recommendations || null }, 'Сохранено');
  }

  function actions(s) {
    const active = s.status === 'REQUESTED' || s.status === 'CONFIRMED';
    if (s.clientId === state.user.id) {
      return [
        s.sharedConversationId && h('a', { class: 'btn', href: `#/chat/${s.sharedConversationId}` }, 'Открытая беседа'),
        active && h('button', { class: 'danger', onclick: () => cancel(s) }, 'Отменить'),
        s.status === 'DONE' && s.rating == null && h('button', { class: 'primary', onclick: () => rate(s) }, 'Оценить'),
      ];
    }
    return [
      s.sharedConversationId && active && h('a', { class: 'btn', href: `#/view/${s.sharedConversationId}` }, 'Беседа клиента'),
      s.status === 'REQUESTED' && h('button', { class: 'primary', onclick: () => change(s, { status: 'CONFIRMED' }, 'Подтверждено') }, 'Подтвердить'),
      s.status === 'CONFIRMED' && h('button', { class: 'primary', onclick: () => change(s, { status: 'DONE' }, 'Отмечено как проведённая') }, 'Проведена'),
      (s.status === 'CONFIRMED' || s.status === 'DONE') && h('button', { onclick: () => summary(s) }, 'Резюме'),
      active && h('button', { class: 'danger', onclick: () => cancel(s) }, 'Отменить'),
    ];
  }

  mount(view,
    h('h1', null, 'Консультации'),
    sessions.length === 0
      ? h('div', { class: 'card empty' }, has('USER') ? h('span', null, 'Записей нет. ', h('a', { href: '#/specialists' }, 'Найти специалиста')) : 'Записей нет.')
      : sessions.map((s) => h('div', { class: 'card' },
        h('div', { class: 'row spread' },
          h('div', null,
            h('h3', null, fmtDate(s.startsAt), ` · ${s.durationMin} мин`),
            h('div', { class: 'small muted' }, s.clientId === state.user.id
              ? `Специалист: ${specialistName[s.specialistId] || s.specialistId}`
              : `Клиент: ${s.clientId}`)),
          badge(s.status, LABELS.sessionStatus)),
        s.summary && h('p', null, h('strong', null, 'Резюме: '), s.summary),
        s.recommendations && h('p', null, h('strong', null, 'Рекомендации: '), s.recommendations),
        s.cancelReason && h('p', { class: 'muted' }, `Причина отмены: ${s.cancelReason}`),
        s.rating != null && h('p', null, `Оценка: ${'★'.repeat(s.rating)}${'☆'.repeat(5 - s.rating)}`),
        h('div', { class: 'row' }, actions(s)))),
  );
}

async function mySpecialistPage(view) {
  const [specialists, specializations] = await Promise.all([get('/specialists?size=50'), get('/specializations?size=50')]);
  const mine = specialists.find((s) => s.userId === state.user.id);
  const specOptions = specializations.map((s) => [s.code, s.title]);

  if (!mine) {
    async function create() {
      const values = await formDialog('Профиль специалиста', [
        { name: 'headline', label: 'Заголовок', required: true, placeholder: 'Психолог, помогаю пережить расставание' },
        { name: 'bio', label: 'О себе', type: 'textarea', required: true },
        { name: 'pricePerHour', label: 'Цена за час, ₽', type: 'number', required: true, min: 0, step: '0.01', value: '2500' },
        { name: 'specializationCodes', label: 'Специализации', type: 'checks', options: specOptions },
      ], 'Создать');
      if (!values) return;
      if (await act(() => post('/specialists', values), 'Профиль создан')) render();
    }
    mount(view,
      h('h1', null, 'Мой профиль специалиста'),
      h('div', { class: 'card empty' }, h('p', null, 'Профиль ещё не создан (или скрыт из каталога).'), h('button', { class: 'primary', onclick: create }, 'Создать профиль')),
    );
    return;
  }

  const slots = await get(`/specialists/${mine.id}/slots?size=50`);

  async function edit() {
    const values = await formDialog('Изменить профиль', [
      { name: 'headline', label: 'Заголовок', required: true, value: mine.headline },
      { name: 'bio', label: 'О себе', type: 'textarea', required: true, value: mine.bio },
      { name: 'pricePerHour', label: 'Цена за час, ₽', type: 'number', required: true, min: 0, step: '0.01', value: mine.pricePerHour },
      { name: 'status', label: 'Статус', type: 'select', options: [['ACTIVE', 'Виден в каталоге'], ['INACTIVE', 'Скрыт']], value: mine.status },
      { name: 'specializationCodes', label: 'Специализации', type: 'checks', options: specOptions, value: mine.specializations.map((s) => s.code) },
    ]);
    if (values && await act(() => patch(`/specialists/${mine.id}`, values), 'Сохранено')) render();
  }

  async function addSlot() {
    const tomorrow = new Date(Date.now() + 24 * 3600 * 1000);
    tomorrow.setMinutes(0, 0, 0);
    const local = new Date(tomorrow.getTime() - tomorrow.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    const values = await formDialog('Новый слот', [
      { name: 'startsAt', label: 'Начало', type: 'datetime-local', required: true, value: local },
      { name: 'durationMin', label: 'Длительность, мин (15–240)', type: 'number', required: true, min: 15, max: 240, value: '60' },
    ], 'Опубликовать');
    if (!values) return;
    const body = { startsAt: new Date(values.startsAt).toISOString(), durationMin: Number(values.durationMin) };
    if (await act(() => post(`/specialists/${mine.id}/slots`, body), 'Слот опубликован')) render();
  }

  mount(view,
    h('div', { class: 'row spread' }, h('h1', null, 'Мой профиль специалиста'), h('button', { onclick: edit }, 'Изменить')),
    specialistCard(mine, false),
    h('div', { class: 'row spread' }, h('h2', null, 'Свободные слоты'), h('button', { class: 'primary', onclick: addSlot }, '+ Слот')),
    slots.length === 0
      ? h('div', { class: 'muted' }, 'Свободных слотов нет.')
      : h('div', { class: 'grid' }, slots.map((slot) => h('div', { class: 'card' },
        h('strong', null, fmtDate(slot.startsAt)), h('div', { class: 'small muted' }, `${slot.durationMin} мин`)))),
    h('p', { style: 'margin-top:20px' }, h('a', { href: '#/consultations' }, 'Записи клиентов →')),
  );
}

// ---------- уведомления ----------

async function notificationsPage(view) {
  const notifications = await get('/notifications?size=50');
  mount(view,
    h('h1', null, 'Уведомления'),
    notifications.length === 0
      ? h('div', { class: 'card empty' }, 'Уведомлений нет.')
      : notifications.map((n) => h('div', { class: 'card' },
        h('div', { class: 'row spread' }, h('strong', null, LABELS.notification[n.type] || n.type), h('span', { class: 'small muted' }, fmtDate(n.createdAt))),
        h('div', { class: 'small muted' }, Object.entries(n.payload).map(([k, v]) => {
          if (k === 'personaId' && has('USER')) return h('div', null, h('a', { href: `#/personas/${v}` }, 'Открыть персону'));
          return h('div', null, `${k}: ${typeof v === 'object' ? JSON.stringify(v) : v}`);
        })))),
  );
}

// ---------- администратор ----------

async function flagsPage(view) {
  const list = h('div');
  const counter = h('span', { class: 'muted' });
  const filter = h('select', { onchange: () => load() },
    h('option', { value: '' }, 'Все'),
    Object.entries(LABELS.flagStatus).map(([v, l]) => h('option', { value: v, selected: v === 'OPEN' }, l)));

  async function review(flag) {
    const statuses = (flag.status === 'OPEN' ? ['IN_REVIEW', 'RESOLVED', 'REJECTED'] : ['RESOLVED', 'REJECTED'])
      .map((s) => [s, LABELS.flagStatus[s]]);
    const values = await formDialog('Вердикт по жалобе', [
      { name: 'status', label: 'Статус', type: 'select', options: statuses, value: 'RESOLVED' },
      { name: 'resolution', label: 'Решение', type: 'textarea' },
      { name: 'archivePersona', label: 'Архивировать персону (только с «решена»)', type: 'checkbox' },
    ], 'Применить');
    if (!values) return;
    const result = await act(() => patch(`/moderation/flags/${flag.id}`, {
      status: values.status,
      resolution: values.resolution || null,
      archivePersona: values.archivePersona,
    }), 'Вердикт сохранён');
    if (result) load();
  }

  async function load() {
    const res = await act(() => api('GET', `/moderation/flags${qs({ size: 50, status: filter.value })}`));
    if (!res) return;
    counter.textContent = Number.isNaN(res.total) ? '' : `всего: ${res.total}`;
    mount(list, ...(res.data.length === 0
      ? [h('div', { class: 'card empty' }, 'Очередь пуста.')]
      : res.data.map((f) => h('div', { class: 'card' },
        h('div', { class: 'row spread' },
          h('div', { class: 'row' }, badge(f.status, LABELS.flagStatus), h('strong', null, LABELS.flagReason[f.reason]), h('span', { class: 'small muted' }, LABELS.flagSource[f.source])),
          h('span', { class: 'small muted' }, fmtDate(f.createdAt))),
        f.message && h('div', { class: `msg ${f.message.sender === 'USER' ? 'user' : 'persona'}`, style: 'margin:10px 0;max-width:100%' },
          h('div', { class: 'small', style: 'opacity:.7' }, f.message.sender === 'USER' ? 'Клиент:' : 'Персона:'), f.message.body),
        f.comment && h('p', null, h('strong', null, 'Комментарий: '), f.comment),
        f.resolution && h('p', null, h('strong', null, 'Решение: '), f.resolution),
        h('div', { class: 'row' },
          h('a', { class: 'btn', href: `#/view/${f.conversationId}` }, 'Помеченные в беседе'),
          (f.status === 'OPEN' || f.status === 'IN_REVIEW') && h('button', { class: 'primary', onclick: () => review(f) }, 'Разобрать'))))));
  }

  mount(view, h('div', { class: 'row spread' }, h('h1', null, 'Модерация'), h('div', { class: 'row' }, counter, filter)), list);
  await load();
}

async function usersPage(view) {
  const users = await get('/users?size=50');

  async function toggle(user) {
    const status = user.status === 'ACTIVE' ? 'BLOCKED' : 'ACTIVE';
    if (await act(() => patch(`/users/${user.id}`, { status }), status === 'BLOCKED' ? 'Пользователь заблокирован' : 'Пользователь разблокирован')) render();
  }

  mount(view,
    h('h1', null, 'Пользователи'),
    h('div', { class: 'card table-wrap' }, h('table', null,
      h('tr', null, h('th', null, 'Имя'), h('th', null, 'Email'), h('th', null, 'Роли'), h('th', null, 'Статус'), h('th', null, 'id'), h('th')),
      users.map((u) => h('tr', null,
        h('td', null, u.displayName),
        h('td', null, u.email),
        h('td', null, u.roles.map((r) => LABELS.role[r]).join(', ')),
        h('td', null, badge(u.status, { ACTIVE: 'активен', BLOCKED: 'заблокирован' })),
        h('td', { class: 'small muted' }, h('code', null, u.id)),
        h('td', null, u.id !== state.user.id && h('button', { class: u.status === 'ACTIVE' ? 'danger' : '', onclick: () => toggle(u) },
          u.status === 'ACTIVE' ? 'Заблокировать' : 'Разблокировать')))))),
  );
}

async function dictionariesPage(view) {
  const [tags, specializations] = await Promise.all([get('/tags?size=50'), get('/specializations?size=50')]);

  function section(title, path, entries) {
    async function add() {
      const values = await formDialog(`${title}: добавить`, [
        { name: 'code', label: 'Код (латиница, цифры, дефис)', required: true },
        { name: 'title', label: 'Название', required: true },
      ], 'Добавить');
      if (values && await act(() => post(path, values), 'Добавлено')) render();
    }
    async function rename(entry) {
      const values = await formDialog(`Переименовать «${entry.code}»`, [{ name: 'title', label: 'Название', required: true, value: entry.title }]);
      if (values && await act(() => patch(`${path}/${entry.id}`, values), 'Сохранено')) render();
    }
    async function remove(entry) {
      if (confirm(`Удалить «${entry.title}»?`) && await act(() => del(`${path}/${entry.id}`), 'Удалено')) render();
    }
    return h('div', null,
      h('div', { class: 'row spread' }, h('h2', null, title), h('button', { onclick: add }, '+ Добавить')),
      h('div', { class: 'card table-wrap' }, h('table', null,
        entries.map((e) => h('tr', null,
          h('td', null, h('code', null, e.code)),
          h('td', null, e.title),
          h('td', { style: 'text-align:right' },
            h('button', { class: 'link', onclick: () => rename(e) }, 'переименовать'), ' ',
            h('button', { class: 'link', style: 'color:var(--danger)', onclick: () => remove(e) }, 'удалить')))))));
  }

  mount(view, h('h1', null, 'Справочники'), section('Теги', '/tags', tags), section('Специализации', '/specializations', specializations));
}

async function metricsPage(view) {
  const metrics = await get('/admin/metrics');
  mount(view,
    h('div', { class: 'row spread' }, h('h1', null, 'Метрики'), h('span', { class: 'small muted' }, `на ${fmtDate(metrics.generatedAt)}`)),
    h('div', { class: 'card table-wrap' }, h('table', null,
      Object.entries(metrics.metrics).map(([k, v]) => h('tr', null, h('td', null, h('code', null, k)), h('td', { style: 'text-align:right' }, v))))),
  );
}

// ---------- роутинг ----------

const ROUTES = [
  [/^\/login$/, loginPage],
  [/^\/personas$/, personasPage],
  [/^\/personas\/([\w-]+)$/, personaPage],
  [/^\/chats$/, chatsPage],
  [/^\/chat\/([\w-]+)$/, (view, id) => chatPage(view, id, false)],
  [/^\/view\/([\w-]+)$/, (view, id) => chatPage(view, id, true)],
  [/^\/specialists$/, specialistsPage],
  [/^\/specialists\/([\w-]+)$/, specialistPage],
  [/^\/consultations$/, consultationsPage],
  [/^\/my-profile$/, mySpecialistPage],
  [/^\/notifications$/, notificationsPage],
  [/^\/admin\/flags$/, flagsPage],
  [/^\/admin\/users$/, usersPage],
  [/^\/admin\/dictionaries$/, dictionariesPage],
  [/^\/admin\/metrics$/, metricsPage],
];

async function render() {
  const path = location.hash.slice(1) || '/';
  if (!state.user && path !== '/login') return go('/login');
  if (state.user && (path === '/' || path === '/login')) return go(homePath());

  state.leave.splice(0).forEach((fn) => fn());
  renderChrome(path);
  const app = document.getElementById('app');
  const view = h('div', null, h('div', { class: 'muted' }, 'Загрузка…'));
  mount(app, view);

  const route = ROUTES.find(([pattern]) => pattern.test(path));
  if (!route) {
    mount(view, h('div', { class: 'card empty' }, 'Страница не найдена. ', h('a', { href: '#/' }, 'На главную')));
    return undefined;
  }
  try {
    await route[1](view, ...path.match(route[0]).slice(1));
  } catch (e) {
    mount(view, h('div', { class: 'card' },
      h('h3', null, 'Не получилось загрузить'),
      h('p', { style: 'white-space:pre-line' }, e.message),
      e.traceId && h('p', { class: 'small muted' }, `traceId: ${e.traceId}`)));
  }
  return undefined;
}

async function boot() {
  const saved = storage('get');
  if (saved) {
    state.user = { id: saved, roles: [] };
    try {
      state.user = await get(`/users/${saved}`);
    } catch {
      state.user = null;
      storage('remove');
    }
  }
  window.addEventListener('hashchange', render);
  render();
}

boot();
