'use strict';
/*
 * CyberClub — веб-интерфейс КР3 (без сборки и библиотек): кабинет клиента и рабочее место сотрудников.
 * Все данные — только через REST API с JWT: access-токен хранится в памяти вкладки,
 * refresh-токен — в sessionStorage (живёт до закрытия вкладки). Права проверяет сервер;
 * интерфейс лишь скрывает кнопки, недоступные роли.
 */

const API = '/api';
const REFRESH_KEY = 'cyberclub.refresh';
const THEME_KEY = 'cyberclub.theme';

const ROLE_TITLES = { VIEWER: 'Просмотр', OPERATOR: 'Администратор зала', ADMIN: 'Администратор системы', CLIENT: 'Клиент клуба' };
const STATUS_TITLES = { CREATED: 'Создано', CONFIRMED: 'Подтверждено', ACTIVE: 'Активно', COMPLETED: 'Завершено', CANCELLED: 'Отменено' };
const STATUS_NEXT = { CREATED: ['CONFIRMED', 'CANCELLED'], CONFIRMED: ['ACTIVE', 'CANCELLED'], ACTIVE: ['COMPLETED'], COMPLETED: [], CANCELLED: [] };
const RISK_TITLES = { NONE: 'Нет', LOW: 'Низкий', MEDIUM: 'Средний', HIGH: 'Высокий' };
const STATION_TYPES = { STANDARD: 'Стандарт', VIP: 'VIP', BOOTCAMP: 'Буткемп', CONSOLE: 'Консоль' };
const AUDIT_TYPES = ['LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGIN_LOCKED', 'LOGOUT', 'PASSWORD_CHANGED', 'REFRESH_REUSE',
  'BOOKING_CREATED', 'BOOKING_UPDATED', 'BOOKING_STATUS', 'BOOKING_ARCHIVED', 'BOOKING_RESTORED', 'BOOKING_DELETED',
  'BOOKING_TAG_ADDED', 'BOOKING_TAG_REMOVED', 'BOOKING_PAID', 'BOOKING_BULK', 'BOOKING_IMPORT', 'BOOKING_EXPORT',
  'NOTE_CREATED', 'NOTE_UPDATED', 'NOTE_DELETED', 'STATION_CREATED', 'STATION_UPDATED', 'STATION_ENABLED',
  'STATION_DISABLED', 'CLIENT_REGISTERED', 'CLIENT_CREATED', 'CLIENT_UPDATED', 'CLIENT_DELETED', 'USER_CREATED', 'USER_UPDATED',
  'USER_ROLES', 'USER_ENABLED', 'USER_DISABLED', 'USER_PASSWORD_RESET', 'USER_UNLOCKED'];

const state = {
  access: null,
  accessExpiresAt: 0,
  refresh: safeStorage(() => sessionStorage.getItem(REFRESH_KEY)),
  user: null,
  view: 'bookings',
  stations: [],
  clients: [],
};

// ===================================================================== утилиты

function safeStorage(fn) {
  try { return fn(); } catch (e) { return null; }
}

/** Создание элемента без innerHTML: текст всегда вставляется как текст (защита от XSS). */
function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key === 'class') el.className = value;
    else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2), value);
    else if (key === 'value') el.value = value;
    else if (key === 'checked') el.checked = Boolean(value);
    else if (value === true) el.setAttribute(key, '');
    else el.setAttribute(key, value);
  }
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

const $ = (sel, root = document) => root.querySelector(sel);

function option(value, title, selected) {
  return h('option', { value, selected: String(value) === String(selected ?? '') }, title);
}

function fmtMoney(value) {
  if (value === null || value === undefined) return '—';
  return Number(value).toLocaleString('ru-RU', { style: 'currency', currency: 'RUB', maximumFractionDigits: 2 });
}

/** LocalDateTime сервера ("2026-11-13T12:00:00") — время клуба, без перевода часовых поясов. */
function fmtLocal(value) {
  if (!value) return '—';
  const [date, time = ''] = value.split('T');
  const [y, m, d] = date.split('-');
  return `${d}.${m}.${y} ${time.slice(0, 5)}`;
}

/** Instant сервера (UTC) — показываем в часовом поясе браузера. */
function fmtInstant(value) {
  return value ? new Date(value).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' }) : '—';
}

function fmtDuration(minutes) {
  const hrs = Math.floor(minutes / 60);
  const min = minutes % 60;
  return (hrs ? `${hrs} ч` : '') + (min ? ` ${min} мин` : '');
}

function toast(message, isError = false) {
  const el = h('div', { class: 'toast' + (isError ? ' error' : '') }, message);
  document.body.append(el);
  setTimeout(() => el.remove(), isError ? 6000 : 3000);
}

function statusBadge(status) {
  return h('span', { class: `badge ${status}` }, STATUS_TITLES[status] || status);
}

function riskBadge(risk) {
  return h('span', { class: `badge risk ${risk}`, title: 'Риск неоплаты — считает сервер' }, RISK_TITLES[risk] || risk);
}

function tagsView(tags) {
  return (tags || []).map(t => h('span', { class: 'tag' }, t));
}

function hasRole(role) {
  return Boolean(state.user && state.user.roles.includes(role));
}
const isAdmin = () => hasRole('ADMIN');
const canEdit = () => hasRole('ADMIN') || hasRole('OPERATOR');
const isClientOnly = () => Boolean(state.user && state.user.roles.length === 1 && state.user.roles[0] === 'CLIENT');

function mainRole(user) {
  for (const role of ['ADMIN', 'OPERATOR', 'VIEWER', 'CLIENT']) {
    if (user.roles.includes(role)) return ROLE_TITLES[role];
  }
  return '';
}

// ===================================================================== API

class ApiError extends Error {
  constructor(status, body) {
    super((body && body.message) || `Ошибка ${status}`);
    this.status = status;
    this.code = body && body.code;
    this.fields = (body && body.validationErrors) || {};
    this.correlationId = body && body.correlationId;
  }

  describe() {
    const fields = Object.entries(this.fields).map(([f, m]) => `• ${f}: ${m}`);
    return [this.message, ...fields].join('\n');
  }
}

function saveTokens(tokens) {
  state.access = tokens.accessToken;
  state.accessExpiresAt = Date.parse(tokens.accessExpiresAt);
  state.refresh = tokens.refreshToken;
  state.user = tokens.user;
  safeStorage(() => sessionStorage.setItem(REFRESH_KEY, tokens.refreshToken));
}

function clearTokens() {
  state.access = null;
  state.refresh = null;
  state.user = null;
  safeStorage(() => sessionStorage.removeItem(REFRESH_KEY));
}

let refreshing = null;

/** Обмен refresh-токена на новую пару; параллельные запросы ждут один и тот же обмен. */
async function refreshTokens() {
  if (!state.refresh) throw new ApiError(401, { message: 'Сессия закончилась. Войдите заново.' });
  if (!refreshing) {
    refreshing = (async () => {
      const res = await fetch(`${API}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: state.refresh }),
      });
      const body = await res.json().catch(() => null);
      if (!res.ok) {
        clearTokens();
        throw new ApiError(res.status, body);
      }
      saveTokens(body);
    })().finally(() => { refreshing = null; });
  }
  return refreshing;
}

/**
 * Запрос к API. JSON-тело сериализуется, FormData уходит как есть; ответ разбирается как JSON
 * (или возвращается Response при raw). 401 — одна попытка обновить токен, затем экран входа.
 */
async function api(path, { method = 'GET', body, params, raw = false, headers = {}, retry = true } = {}) {
  if (state.access && Date.now() > state.accessExpiresAt - 30_000) {
    await refreshTokens().catch(() => {});
  }
  let url = `${API}${path}`;
  if (params) {
    const query = new URLSearchParams();
    for (const [k, v] of Object.entries(params)) {
      if (v !== null && v !== undefined && v !== '') query.append(k, v);
    }
    if ([...query].length) url += `?${query}`;
  }
  const init = { method, headers: { ...headers } };
  if (state.access) init.headers.Authorization = `Bearer ${state.access}`;
  if (body instanceof FormData) init.body = body;
  else if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  const res = await fetch(url, init);
  if (res.status === 401 && retry && state.refresh) {
    try {
      await refreshTokens();
      return api(path, { method, body, params, raw, headers, retry: false });
    } catch (e) {
      showLogin('Сессия закончилась. Войдите заново.');
      throw e;
    }
  }
  if (!res.ok) {
    const err = new ApiError(res.status, await res.json().catch(() => null));
    if (err.code === 'PASSWORD_CHANGE_REQUIRED') showPasswordScreen();
    if (res.status === 401) showLogin(err.message);
    throw err;
  }
  if (raw) return res;
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

/** Ошибка API — в рамку формы или во всплывающее сообщение. */
function showError(err, box) {
  const text = err instanceof ApiError ? err.describe() : (err.message || String(err));
  if (box) {
    box.textContent = text;
    box.classList.remove('hidden');
  } else {
    toast(text, true);
  }
}

// ===================================================================== вход и пароль

function showScreen(id) {
  for (const s of ['login-screen', 'register-screen', 'password-screen', 'app']) $(`#${s}`).classList.toggle('hidden', s !== id);
}

function showLogin(message) {
  clearTokens();
  closeAllDialogs();
  showScreen('login-screen');
  const box = $('#login-error');
  if (message) {
    box.textContent = message;
    box.classList.remove('hidden');
  } else {
    box.classList.add('hidden');
  }
  checkHealth($('#login-health'));
}

function showPasswordScreen() {
  closeAllDialogs();
  $('#password-form').reset();
  $('#password-error').classList.add('hidden');
  showScreen('password-screen');
}

$('#login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.target;
  const box = $('#login-error');
  box.classList.add('hidden');
  try {
    const res = await fetch(`${API}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: form.email.value.trim(), password: form.password.value }),
    });
    const body = await res.json().catch(() => null);
    if (!res.ok) throw new ApiError(res.status, body);
    saveTokens(body);
    form.password.value = '';
    afterLogin();
  } catch (err) {
    showError(err, box);
  }
});

$('#show-register').addEventListener('click', () => {
  $('#register-form').reset();
  $('#register-error').classList.add('hidden');
  showScreen('register-screen');
});
$('#show-login').addEventListener('click', () => showLogin());

$('#register-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  const box = $('#register-error');
  box.classList.add('hidden');
  if (f.password.value !== f.repeat.value) {
    showError(new Error('Пароль и повтор не совпадают.'), box);
    return;
  }
  try {
    const res = await fetch(`${API}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nickname: f.nickname.value.trim(), fullName: f.fullName.value.trim(), phone: f.phone.value.trim(),
        email: f.email.value.trim(), birthDate: f.birthDate.value, password: f.password.value,
      }),
    });
    const body = await res.json().catch(() => null);
    if (!res.ok) throw new ApiError(res.status, body);
    saveTokens(body);
    toast('Добро пожаловать в CyberClub!');
    afterLogin();
  } catch (err) {
    showError(err, box);
  }
});

async function changePassword(current, next, repeat, box) {
  if (next !== repeat) {
    showError(new Error('Новый пароль и повтор не совпадают.'), box);
    return false;
  }
  try {
    await api('/auth/password', { method: 'POST', body: { currentPassword: current, newPassword: next } });
    return true;
  } catch (err) {
    showError(err, box);
    return false;
  }
}

$('#password-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  const email = state.user && state.user.email;
  if (await changePassword(f.currentPassword.value, f.newPassword.value, f.repeat.value, $('#password-error'))) {
    // сервер закрыл все сессии — входим заново уже с новым паролем
    const password = f.newPassword.value;
    clearTokens();
    try {
      const res = await fetch(`${API}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      const body = await res.json();
      if (!res.ok) throw new ApiError(res.status, body);
      saveTokens(body);
      toast('Пароль изменён');
      afterLogin();
    } catch (err) {
      showLogin('Пароль изменён. Войдите с новым паролем.');
    }
  }
});

$('#password-cancel').addEventListener('click', () => logout());

async function logout() {
  const refresh = state.refresh;
  clearTokens();
  if (refresh) {
    await fetch(`${API}/auth/logout`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: refresh }),
    }).catch(() => {});
  }
  showLogin();
}

function afterLogin() {
  const user = state.user;
  if (user.mustChangePassword) {
    showPasswordScreen();
    return;
  }
  $('#user-name').textContent = user.displayName;
  $('#user-role').textContent = `${mainRole(user)} · ${user.email}`;
  showScreen('app');
  const allowed = visibleViews().map(v => v.id);
  openView(allowed.includes(state.view) ? state.view : allowed[0]);
  checkHealth($('#health'));
}

$('#logout-btn').addEventListener('click', () => logout());

$('#change-password-btn').addEventListener('click', () => {
  const form = h('form', { class: 'stack' },
    h('label', {}, 'Текущий пароль', h('input', { name: 'current', type: 'password', required: true, autocomplete: 'current-password' })),
    h('label', {}, 'Новый пароль', h('input', { name: 'next', type: 'password', required: true, autocomplete: 'new-password' })),
    h('label', {}, 'Повтор', h('input', { name: 'repeat', type: 'password', required: true, autocomplete: 'new-password' })),
    h('p', { class: 'small muted' }, 'От 8 символов, буквы и цифры. После смены все ваши сессии закрываются — войдите заново.'));
  const box = h('div', { class: 'error-box hidden' });
  const dlg = dialog('Смена пароля', [form, box], [
    h('button', { type: 'button', onclick: () => dlg.close() }, 'Отмена'),
    h('button', {
      class: 'primary', type: 'button', onclick: async () => {
        if (!form.reportValidity()) return;
        if (await changePassword(form.current.value, form.next.value, form.repeat.value, box)) {
          dlg.close();
          showLogin('Пароль изменён. Войдите с новым паролем.');
        }
      },
    }, 'Сменить'),
  ]);
});

async function checkHealth(target) {
  try {
    const res = await fetch('/actuator/health');
    const body = await res.json();
    target.textContent = body.status === 'UP' ? 'сервер работает' : `сервер: ${body.status}`;
  } catch (e) {
    target.textContent = 'сервер недоступен';
  }
}

// ===================================================================== тема

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  safeStorage(() => localStorage.setItem(THEME_KEY, theme));
}

$('#theme-toggle').addEventListener('click', () => {
  applyTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark');
});

// ===================================================================== диалоги

const openDialogs = new Set();

function dialog(title, content, buttons, wide = false) {
  const card = h('div', { class: `card dialog${wide ? ' wide' : ''}`, role: 'dialog', 'aria-modal': 'true' },
    h('div', { class: 'row' }, h('h2', { class: 'grow' }, title), h('button', { class: 'icon', type: 'button', 'aria-label': 'Закрыть', onclick: () => api_.close() }, '✕')),
    content,
    buttons && buttons.length ? h('div', { class: 'actions' }, buttons) : null);
  const overlay = h('div', { class: 'overlay', onclick: (e) => { if (e.target === overlay) api_.close(); } }, card);
  const onKey = (e) => { if (e.key === 'Escape') api_.close(); };
  const api_ = {
    root: card,
    close() {
      overlay.remove();
      document.removeEventListener('keydown', onKey);
      openDialogs.delete(api_);
    },
  };
  document.addEventListener('keydown', onKey);
  $('#overlay-root').append(overlay);
  openDialogs.add(api_);
  const focus = card.querySelector('input, select, textarea');
  if (focus) focus.focus();
  return api_;
}

function closeAllDialogs() {
  [...openDialogs].forEach(d => d.close());
}

function confirmDialog(title, text, okText = 'Да', danger = false) {
  return new Promise(resolve => {
    const dlg = dialog(title, h('p', {}, text), [
      h('button', { type: 'button', onclick: () => { dlg.close(); resolve(false); } }, 'Отмена'),
      h('button', { type: 'button', class: danger ? 'danger' : 'primary', onclick: () => { dlg.close(); resolve(true); } }, okText),
    ]);
  });
}

// ===================================================================== навигация

const CLIENT_VIEWS = [
  { id: 'my', title: 'Мои брони', render: renderMyBookings },
];

const VIEWS = [
  { id: 'bookings', title: 'Брони', render: renderBookings },
  { id: 'stations', title: 'Места', render: renderStations },
  { id: 'analytics', title: 'Аналитика', render: renderAnalytics },
  { id: 'users', title: 'Пользователи', render: renderUsers, admin: true },
  { id: 'audit', title: 'Журнал аудита', render: renderAudit, admin: true },
];

function visibleViews() {
  return isClientOnly() ? CLIENT_VIEWS : VIEWS.filter(v => !v.admin || isAdmin());
}

function renderNav() {
  const nav = $('#nav');
  nav.replaceChildren(...visibleViews().map(v =>
    h('button', { type: 'button', class: state.view === v.id ? 'active' : '', onclick: () => openView(v.id) }, v.title)));
}

function openView(id) {
  state.view = id;
  renderNav();
  const view = visibleViews().find(v => v.id === id) || visibleViews()[0];
  state.view = view.id;
  const root = $('#view');
  root.replaceChildren();
  view.render(root);
}

async function loadReferences() {
  const [stations, clients] = await Promise.all([api('/stations'), api('/clients')]);
  state.stations = stations;
  state.clients = clients;
}

function pager(page, onPage) {
  return h('div', { class: 'pager' },
    h('span', { class: 'muted' }, `Всего: ${page.totalElements} · стр. ${page.totalPages ? page.page + 1 : 0} из ${page.totalPages}`),
    h('button', { type: 'button', disabled: page.first, onclick: () => onPage(page.page - 1) }, '‹ Назад'),
    h('button', { type: 'button', disabled: page.last, onclick: () => onPage(page.page + 1) }, 'Вперёд ›'));
}

// ===================================================================== брони

const bookingsState = {
  filters: { q: '', stationId: '', status: '', paid: '', riskLevel: '', tag: '', archive: 'ACTIVE', priceOp: '', price: '', from: '', to: '' },
  sort: 'start,desc',
  page: 0,
  size: 25,
  selected: new Set(),
  lastPage: null,
};

async function renderBookings(root) {
  try {
    await loadReferences();
  } catch (err) {
    showError(err);
  }
  const f = bookingsState.filters;
  const input = (name, attrs = {}) => h('input', { name, value: f[name], ...attrs });
  const filterForm = h('form', { class: 'toolbar card', onsubmit: (e) => { e.preventDefault(); applyFilters(); } },
    h('label', { style: 'min-width:220px' }, 'Поиск (№, ник, ФИО, место, комментарий)', input('q', { placeholder: '#12 или frost' })),
    h('label', {}, 'Место', h('select', { name: 'stationId' }, option('', 'Все'), state.stations.map(s => option(s.id, s.name, f.stationId)))),
    h('label', {}, 'Статус', h('select', { name: 'status' }, option('', 'Все'), Object.entries(STATUS_TITLES).map(([k, v]) => option(k, v, f.status)))),
    h('label', {}, 'Оплата', h('select', { name: 'paid' }, option('', 'Все'), option('true', 'Оплачено', f.paid), option('false', 'Не оплачено', f.paid))),
    h('label', {}, 'Риск неоплаты', h('select', { name: 'riskLevel' }, option('', 'Все'), Object.entries(RISK_TITLES).map(([k, v]) => option(k, v, f.riskLevel)))),
    h('label', {}, 'Тег', input('tag', { placeholder: 'турнир', size: 10 })),
    h('label', {}, 'Архив', h('select', { name: 'archive' }, option('ACTIVE', 'Действующие', f.archive), option('ARCHIVED', 'Архив', f.archive), option('ALL', 'Все', f.archive))),
    h('label', {}, 'Стоимость', h('div', { class: 'row' },
      h('select', { name: 'priceOp' }, option('', '—'), ['EQ', 'GT', 'GTE', 'LT', 'LTE'].map(op => option(op, { EQ: '=', GT: '>', GTE: '≥', LT: '<', LTE: '≤' }[op], f.priceOp))),
      input('price', { type: 'number', min: 0, step: '0.01', style: 'width:90px' }))),
    h('label', {}, 'С', input('from', { type: 'date' })),
    h('label', {}, 'По', input('to', { type: 'date' })),
    h('label', {}, 'Сортировка', h('select', { name: 'sort' },
      [['start,desc', 'Начало ↓'], ['start,asc', 'Начало ↑'], ['number,desc', 'Номер ↓'], ['number,asc', 'Номер ↑'],
        ['price,desc', 'Стоимость ↓'], ['price,asc', 'Стоимость ↑'], ['status,asc', 'Статус'], ['client,asc', 'Клиент'],
        ['station,asc', 'Место'], ['created,desc', 'Создано ↓'], ['updated,desc', 'Изменено ↓']].map(([v, t]) => option(v, t, bookingsState.sort)))),
    h('label', {}, 'На странице', h('select', { name: 'size' }, [10, 25, 50, 100].map(n => option(n, n, bookingsState.size)))),
    h('div', { class: 'row' },
      h('button', { class: 'primary', type: 'submit' }, 'Найти'),
      h('button', { type: 'button', onclick: resetFilters }, 'Сбросить')));

  const actions = h('div', { class: 'row', style: 'margin-bottom:12px' },
    h('h2', { class: 'grow' }, 'Бронирования'),
    canEdit() ? h('button', { class: 'primary', type: 'button', onclick: () => bookingForm(null) }, '＋ Новая бронь') : null,
    h('button', { type: 'button', onclick: exportCsv }, 'Экспорт CSV'),
    canEdit() ? h('button', { type: 'button', onclick: importDialog }, 'Импорт CSV') : null);

  const bulkBar = h('div', { id: 'bulk-bar', class: 'bulk-bar hidden' });
  const tableHost = h('div', { id: 'bookings-table' }, h('p', { class: 'muted' }, 'Загрузка…'));
  root.append(actions, filterForm, bulkBar, tableHost);

  function applyFilters() {
    const data = new FormData(filterForm);
    for (const key of Object.keys(f)) f[key] = (data.get(key) || '').toString().trim();
    bookingsState.sort = data.get('sort');
    bookingsState.size = Number(data.get('size'));
    bookingsState.page = 0;
    loadBookings();
  }

  function resetFilters() {
    Object.assign(f, { q: '', stationId: '', status: '', paid: '', riskLevel: '', tag: '', archive: 'ACTIVE', priceOp: '', price: '', from: '', to: '' });
    bookingsState.sort = 'start,desc';
    bookingsState.page = 0;
    openView('bookings');
  }

  loadBookings();
}

function bookingParams() {
  return { ...bookingsState.filters };
}

async function loadBookings() {
  const host = $('#bookings-table');
  if (!host) return;
  try {
    const page = await api('/bookings', {
      params: { ...bookingParams(), sort: bookingsState.sort, page: bookingsState.page, size: bookingsState.size },
    });
    bookingsState.lastPage = page;
    const visible = new Set(page.content.map(b => b.id));
    for (const id of [...bookingsState.selected]) if (!visible.has(id)) bookingsState.selected.delete(id);
    host.replaceChildren(bookingsTable(page));
    renderBulkBar();
  } catch (err) {
    host.replaceChildren(h('div', { class: 'error-box' }, err instanceof ApiError ? err.describe() : err.message));
  }
}

function bookingsTable(page) {
  const all = page.content.length > 0 && page.content.every(b => bookingsState.selected.has(b.id));
  const head = h('tr', {},
    canEdit() ? h('th', {}, h('input', {
      type: 'checkbox', checked: all, title: 'Выбрать все на странице',
      onclick: (e) => { e.stopPropagation(); page.content.forEach(b => e.target.checked ? bookingsState.selected.add(b.id) : bookingsState.selected.delete(b.id)); loadBookings(); },
    })) : null,
    ['№', 'Клиент', 'Место', 'Начало', 'Длит.', 'Статус', 'Стоимость', 'Оплата', 'Риск', 'Теги'].map(t => h('th', {}, t)));
  const rows = page.content.map(b => h('tr', {
    class: (b.archived ? 'archived ' : '') + (bookingsState.selected.has(b.id) ? 'selected' : ''),
    onclick: () => bookingDetails(b.id),
  },
  canEdit() ? h('td', { onclick: (e) => e.stopPropagation() }, h('input', {
    type: 'checkbox', checked: bookingsState.selected.has(b.id),
    onchange: (e) => { e.target.checked ? bookingsState.selected.add(b.id) : bookingsState.selected.delete(b.id); renderBulkBar(); e.target.closest('tr').classList.toggle('selected', e.target.checked); },
  })) : null,
  h('td', { class: 'mono' }, `#${b.number}`),
  h('td', {}, b.clientNickname, h('div', { class: 'small muted' }, b.clientFullName)),
  h('td', {}, b.stationName),
  h('td', { class: 'nowrap' }, fmtLocal(b.startTime)),
  h('td', { class: 'nowrap' }, fmtDuration(b.durationMinutes)),
  h('td', {}, statusBadge(b.status), b.archived ? h('div', { class: 'small muted' }, 'в архиве') : null),
  h('td', { class: 'right nowrap' }, fmtMoney(b.totalPrice)),
  h('td', {}, b.paid ? 'да' : 'нет'),
  h('td', {}, riskBadge(b.riskLevel)),
  h('td', {}, tagsView(b.tags))));
  return h('div', {},
    h('div', { class: 'table-wrap card', style: 'padding:0' },
      h('table', {}, h('thead', {}, head), h('tbody', {}, rows.length ? rows : h('tr', {}, h('td', { colspan: 11, class: 'muted' }, 'Ничего не найдено'))))),
    pager(page, (p) => { bookingsState.page = p; loadBookings(); }));
}

function renderBulkBar() {
  const bar = $('#bulk-bar');
  if (!bar) return;
  const count = bookingsState.selected.size;
  bar.classList.toggle('hidden', !canEdit() || count === 0);
  if (!count) return;
  const statusSelect = h('select', {}, Object.entries(STATUS_TITLES).map(([k, v]) => option(k, v)));
  const tagInput = h('input', { placeholder: 'тег', size: 10 });
  bar.replaceChildren(
    h('strong', {}, `Выбрано: ${count}`),
    h('button', { type: 'button', onclick: () => runBulk({ action: 'ARCHIVE' }) }, 'В архив'),
    h('button', { type: 'button', onclick: () => runBulk({ action: 'RESTORE' }) }, 'Восстановить'),
    h('button', { type: 'button', onclick: () => runBulk({ action: 'MARK_PAID' }) }, 'Оплата получена'),
    h('span', { class: 'muted' }, '·'), statusSelect,
    h('button', { type: 'button', onclick: () => runBulk({ action: 'SET_STATUS', status: statusSelect.value }) }, 'Сменить статус'),
    h('span', { class: 'muted' }, '·'), tagInput,
    h('button', { type: 'button', onclick: () => runBulk({ action: 'ADD_TAG', tag: tagInput.value }) }, 'Добавить тег'),
    h('button', { type: 'button', onclick: () => runBulk({ action: 'REMOVE_TAG', tag: tagInput.value }) }, 'Снять тег'),
    h('button', { type: 'button', class: 'link', onclick: () => { bookingsState.selected.clear(); loadBookings(); } }, 'снять выделение'));
}

async function runBulk(request) {
  const numbers = new Map((bookingsState.lastPage?.content || []).map(b => [b.id, b.number]));
  try {
    const result = await api('/bookings/bulk', { method: 'POST', body: { ...request, ids: [...bookingsState.selected] } });
    const failures = result.failures.map(f => h('li', {}, `#${numbers.get(f.id) ?? f.id}: ${f.message}`));
    dialog('Результат массовой операции', h('div', { class: 'stack' },
      h('div', { class: 'ok-box' }, `Успешно: ${result.succeeded} из ${result.requested}`),
      failures.length ? h('div', {}, h('h3', {}, `Ошибки (${failures.length})`), h('ul', {}, failures)) : null), []);
    bookingsState.selected = new Set(result.failures.map(f => f.id));
    loadBookings();
  } catch (err) {
    showError(err);
  }
}

// ---------- форма брони

function bookingForm(booking) {
  const isNew = !booking;
  const stations = state.stations.filter(s => s.active || (booking && s.id === booking.stationId));
  const form = h('form', { class: 'stack' },
    h('div', { class: 'grid2' },
      h('label', {}, 'Клиент *', h('select', { name: 'clientId', required: true, disabled: !isNew },
        option('', '— выберите —'), state.clients.map(c => option(c.id, `${c.nickname} — ${c.fullName}`, booking?.clientId)))),
      h('label', {}, 'Игровое место *', h('select', { name: 'stationId', required: true },
        option('', '— выберите —'), stations.map(s => option(s.id, `${s.name} (${s.typeTitle}, ${fmtMoney(s.hourlyRate)}/ч)`, booking?.stationId)))),
      h('label', {}, 'Начало *', h('input', { name: 'startTime', type: 'datetime-local', required: true, value: booking ? booking.startTime.slice(0, 16) : '' })),
      h('label', {}, 'Окончание *', h('input', { name: 'endTime', type: 'datetime-local', required: true, value: booking ? booking.endTime.slice(0, 16) : '' }))),
    h('label', {}, 'Комментарий', h('textarea', { name: 'comment', rows: 2, maxlength: 255 }, booking?.comment || '')),
    h('label', {}, 'Теги (через запятую)', h('input', { name: 'tags', value: (booking?.tags || []).join(', '), placeholder: 'турнир, постоянный' })),
    h('label', { class: 'inline' }, h('input', { name: 'paid', type: 'checkbox', checked: booking?.paid }), 'Оплачено'),
    h('p', { class: 'small muted' }, 'Стоимость и риск неоплаты считает сервер. Длительность — от 30 минут до 12 часов; VIP, буткемп и ночь — только 18+.'));
  const box = h('div', { class: 'error-box hidden' });
  const dlg = dialog(isNew ? 'Новая бронь' : `Бронь #${booking.number}`, [form, box], [
    h('button', { type: 'button', onclick: () => dlg.close() }, 'Отмена'),
    h('button', {
      class: 'primary', type: 'button', onclick: async () => {
        if (!form.reportValidity()) return;
        const body = {
          clientId: Number(isNew ? form.clientId.value : booking.clientId),
          stationId: Number(form.stationId.value),
          startTime: form.startTime.value,
          endTime: form.endTime.value,
          comment: form.comment.value.trim() || null,
          paid: form.paid.checked,
          tags: form.tags.value.split(',').map(t => t.trim()).filter(Boolean),
        };
        try {
          if (isNew) {
            const created = await api('/bookings', { method: 'POST', body });
            toast(`Создана бронь #${created.number}`);
          } else {
            await api(`/bookings/${booking.id}`, { method: 'PUT', body: { ...body, version: booking.version } });
            toast(`Бронь #${booking.number} сохранена`);
          }
          dlg.close();
          closeAllDialogs();
          loadBookings();
        } catch (err) {
          showError(err, box);
        }
      },
    }, isNew ? 'Создать' : 'Сохранить'),
  ]);
}

// ---------- карточка брони и заметки

async function bookingDetails(id) {
  let b;
  try {
    b = await api(`/bookings/${id}`);
  } catch (err) {
    showError(err);
    return;
  }
  const notesHost = h('div', {}, h('p', { class: 'muted' }, 'Загрузка заметок…'));
  const buttons = [];
  if (canEdit() && !b.archived && !b.paid && b.status !== 'CANCELLED') {
    buttons.push(h('button', {
      type: 'button', class: 'primary', onclick: async () => {
        try {
          await api(`/bookings/${b.id}/confirm-payment`, { method: 'POST', body: { version: b.version } });
          toast(`Оплата брони #${b.number} подтверждена`);
          dlg.close();
          loadBookings();
          bookingDetails(b.id);
        } catch (err) { showError(err); }
      },
    }, '₽ Подтвердить оплату'));
  }
  if (canEdit() && !b.archived) {
    if (['CREATED', 'CONFIRMED'].includes(b.status)) {
      buttons.push(h('button', { type: 'button', onclick: () => bookingForm(b) }, 'Изменить'));
    }
    for (const next of STATUS_NEXT[b.status]) {
      buttons.push(h('button', {
        type: 'button', class: next === 'CANCELLED' ? 'danger' : '',
        onclick: async () => {
          try {
            await api(`/bookings/${b.id}/status`, { method: 'POST', body: { status: next, version: b.version } });
            toast(`Статус: ${STATUS_TITLES[next]}`);
            dlg.close();
            loadBookings();
            bookingDetails(b.id);
          } catch (err) { showError(err); }
        },
      }, `→ ${STATUS_TITLES[next]}`));
    }
    buttons.push(h('button', {
      type: 'button', onclick: async () => {
        try {
          await api(`/bookings/${b.id}`, { method: 'DELETE' });
          toast(`Бронь #${b.number} в архиве`);
          dlg.close();
          loadBookings();
        } catch (err) { showError(err); }
      },
    }, 'В архив'));
  }
  if (canEdit() && b.archived) {
    buttons.push(h('button', {
      type: 'button', onclick: async () => {
        try {
          await api(`/bookings/${b.id}/restore`, { method: 'POST' });
          toast(`Бронь #${b.number} восстановлена`);
          dlg.close();
          loadBookings();
        } catch (err) { showError(err); }
      },
    }, 'Восстановить'));
  }
  if (isAdmin()) {
    buttons.push(h('button', {
      type: 'button', class: 'danger', onclick: async () => {
        if (!await confirmDialog('Удалить окончательно?', `Бронь #${b.number} и её заметки будут удалены без возможности восстановления.`, 'Удалить', true)) return;
        try {
          await api(`/bookings/${b.id}/permanent`, { method: 'DELETE', headers: { 'X-Confirm-Delete': 'PERMANENTLY_DELETE' } });
          toast(`Бронь #${b.number} удалена`);
          dlg.close();
          loadBookings();
        } catch (err) { showError(err); }
      },
    }, 'Удалить навсегда'));
  }

  const info = h('div', { class: 'grid2' },
    h('div', { class: 'stack' },
      h('div', {}, h('span', { class: 'muted' }, 'Клиент: '), `${b.clientNickname} (${b.clientFullName})`),
      h('div', {}, h('span', { class: 'muted' }, 'Место: '), `${b.stationName} · ${STATION_TYPES[b.stationType] || b.stationType}`),
      h('div', {}, h('span', { class: 'muted' }, 'Время: '), `${fmtLocal(b.startTime)} — ${fmtLocal(b.endTime)} (${fmtDuration(b.durationMinutes)})`),
      h('div', {}, h('span', { class: 'muted' }, 'Комментарий: '), b.comment || '—')),
    h('div', { class: 'stack' },
      h('div', {}, statusBadge(b.status), ' ', riskBadge(b.riskLevel), b.archived ? h('span', { class: 'muted' }, ' · в архиве') : null),
      h('div', {}, h('span', { class: 'muted' }, 'Стоимость: '), fmtMoney(b.totalPrice), b.paid ? ' · оплачено' : ' · не оплачено'),
      h('div', {}, h('span', { class: 'muted' }, 'Теги: '), b.tags.length ? tagsView(b.tags) : '—'),
      h('div', { class: 'small muted' }, `Создано ${fmtInstant(b.createdAt)} · изменено ${fmtInstant(b.updatedAt)} · версия ${b.version}`)));

  const dlg = dialog(`Бронь #${b.number}`, [info, h('div', { class: 'row', style: 'margin:14px 0' }, buttons), h('h3', {}, 'Заметки'), notesHost], [], true);
  loadNotes(b, notesHost);
}

async function loadNotes(booking, host) {
  let notes;
  try {
    notes = await api(`/bookings/${booking.id}/notes`);
  } catch (err) {
    host.replaceChildren(h('div', { class: 'error-box' }, err.message));
    return;
  }
  const list = notes.map(n => {
    const body = h('div', { style: 'white-space:pre-wrap' }, n.body);
    return h('div', { class: `note${n.pinned ? ' pinned' : ''}` },
      h('div', { class: 'meta' },
        n.pinned ? h('strong', {}, '📌 закреплено') : null,
        h('span', {}, n.authorName),
        h('span', {}, fmtInstant(n.createdAt)),
        n.updatedAt !== n.createdAt ? h('span', {}, `(изменено ${fmtInstant(n.updatedAt)})`) : null,
        h('span', { class: 'grow' }),
        n.editable ? h('button', { type: 'button', class: 'link', onclick: () => noteForm(booking, n, host) }, 'изменить') : null,
        n.editable ? h('button', {
          type: 'button', class: 'link', onclick: async () => {
            if (!await confirmDialog('Удалить заметку?', n.body.slice(0, 120), 'Удалить', true)) return;
            try {
              await api(`/bookings/${booking.id}/notes/${n.id}`, { method: 'DELETE' });
              loadNotes(booking, host);
            } catch (err) { showError(err); }
          },
        }, 'удалить') : null),
      body);
  });
  host.replaceChildren(
    list.length ? h('div', {}, list) : h('p', { class: 'muted' }, 'Заметок пока нет.'),
    canEdit() ? h('button', { type: 'button', onclick: () => noteForm(booking, null, host) }, '＋ Заметка') : null);
}

function noteForm(booking, note, host) {
  const form = h('form', { class: 'stack' },
    h('label', {}, 'Текст *', h('textarea', { name: 'body', rows: 4, required: true, maxlength: 2000 }, note?.body || '')),
    h('label', { class: 'inline' }, h('input', { name: 'pinned', type: 'checkbox', checked: note?.pinned }), 'Закрепить'));
  const box = h('div', { class: 'error-box hidden' });
  const dlg = dialog(note ? 'Изменить заметку' : 'Новая заметка', [form, box], [
    h('button', { type: 'button', onclick: () => dlg.close() }, 'Отмена'),
    h('button', {
      class: 'primary', type: 'button', onclick: async () => {
        if (!form.reportValidity()) return;
        const body = { body: form.body.value, pinned: form.pinned.checked };
        try {
          if (note) await api(`/bookings/${booking.id}/notes/${note.id}`, { method: 'PUT', body: { ...body, version: note.version } });
          else await api(`/bookings/${booking.id}/notes`, { method: 'POST', body });
          dlg.close();
          loadNotes(booking, host);
        } catch (err) { showError(err, box); }
      },
    }, 'Сохранить'),
  ]);
}

// ---------- CSV

async function exportCsv() {
  try {
    const res = await api('/bookings/export', { params: bookingParams(), raw: true });
    const blob = await res.blob();
    const name = (res.headers.get('Content-Disposition') || '').match(/filename="([^"]+)"/)?.[1] || 'bookings.csv';
    const link = h('a', { href: URL.createObjectURL(blob), download: name });
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(link.href), 1000);
  } catch (err) {
    showError(err);
  }
}

function importDialog() {
  const fileInput = h('input', { type: 'file', accept: '.csv,text/csv', required: true });
  const modeSelect = h('select', {},
    option('CREATE_ONLY', 'Только новые брони'),
    option('UPSERT_BY_NUMBER', 'Обновлять брони по номеру, без номера — создавать'));
  const result = h('div', { class: 'stack' });
  const applyBtn = h('button', { class: 'primary', type: 'button', disabled: true }, 'Применить');
  const checkBtn = h('button', { type: 'button' }, 'Проверить');
  const help = h('p', { class: 'small muted' },
    'Колонки: client (ник), station (название), start, end — обязательные; number, paid, comment, tags (через |) — необязательные. ',
    'Даты: ГГГГ-ММ-ДД ЧЧ:ММ или ДД.ММ.ГГГГ ЧЧ:ММ. Разделитель «;» или «,». Подходит файл, полученный экспортом.');

  function formData() {
    const data = new FormData();
    data.append('file', fileInput.files[0]);
    return data;
  }

  function issuesTable(issues) {
    if (!issues.length) return null;
    return h('div', { class: 'table-wrap' }, h('table', {},
      h('thead', {}, h('tr', {}, h('th', {}, 'Строка'), h('th', {}, 'Поле'), h('th', {}, 'Ошибка'))),
      h('tbody', {}, issues.map(i => h('tr', {}, h('td', {}, i.row), h('td', {}, i.field || '—'), h('td', {}, i.message))))));
  }

  checkBtn.addEventListener('click', async () => {
    if (!fileInput.files.length) { fileInput.reportValidity(); return; }
    applyBtn.disabled = true;
    try {
      const p = await api('/bookings/import/preview', { method: 'POST', body: formData(), params: { mode: modeSelect.value } });
      result.replaceChildren(
        h('div', { class: p.invalidRows ? 'error-box' : 'ok-box' },
          `Строк: ${p.totalRows} · корректных: ${p.validRows} (создать ${p.toCreate}, обновить ${p.toUpdate}) · с ошибками: ${p.invalidRows}`),
        issuesTable(p.issues),
        h('p', { class: 'small muted' }, 'Это проверка: в базе ничего не изменилось. «Применить» сохранит корректные строки, ошибочные будут пропущены.'));
      applyBtn.disabled = p.validRows === 0;
    } catch (err) {
      result.replaceChildren(h('div', { class: 'error-box' }, err instanceof ApiError ? err.describe() : err.message));
    }
  });
  applyBtn.addEventListener('click', async () => {
    try {
      const r = await api('/bookings/import/apply', { method: 'POST', body: formData(), params: { mode: modeSelect.value } });
      result.replaceChildren(
        h('div', { class: 'ok-box' }, `Создано: ${r.created} · обновлено: ${r.updated} · пропущено: ${r.skipped} из ${r.totalRows}`),
        issuesTable(r.issues));
      applyBtn.disabled = true;
      loadBookings();
    } catch (err) {
      showError(err, null);
    }
  });
  fileInput.addEventListener('change', () => { applyBtn.disabled = true; result.replaceChildren(); });
  modeSelect.addEventListener('change', () => { applyBtn.disabled = true; result.replaceChildren(); });

  const dlg = dialog('Импорт броней из CSV', h('div', { class: 'stack' },
    h('label', {}, 'Файл CSV', fileInput), h('label', {}, 'Режим', modeSelect), help, result), [
    h('button', { type: 'button', onclick: () => dlg.close() }, 'Закрыть'), checkBtn, applyBtn,
  ], true);
}

// ===================================================================== места

async function renderStations(root) {
  root.append(h('div', { class: 'row', style: 'margin-bottom:12px' },
    h('h2', { class: 'grow' }, 'Игровые места'),
    isAdmin() ? h('button', { class: 'primary', type: 'button', onclick: () => stationForm(null) }, '＋ Место') : null));
  const host = h('div', {}, h('p', { class: 'muted' }, 'Загрузка…'));
  root.append(host);
  try {
    const stations = await api('/stations');
    state.stations = stations;
    host.replaceChildren(h('div', { class: 'card table-wrap', style: 'padding:0' }, h('table', {},
      h('thead', {}, h('tr', {}, ['Название', 'Зона', 'Тариф', 'Характеристики', 'Состояние', ''].map(t => h('th', {}, t)))),
      h('tbody', {}, stations.map(s => h('tr', { class: s.active ? '' : 'archived' },
        h('td', { class: 'mono' }, s.name),
        h('td', {}, s.typeTitle),
        h('td', { class: 'nowrap' }, `${fmtMoney(s.hourlyRate)}/ч`),
        h('td', {}, s.specs || '—'),
        h('td', {}, s.active ? 'доступно' : 'обслуживание'),
        h('td', { class: 'nowrap', onclick: (e) => e.stopPropagation() }, isAdmin() ? [
          h('button', { type: 'button', onclick: () => stationForm(s) }, 'Изменить'), ' ',
          h('button', {
            type: 'button', onclick: async () => {
              try {
                await api(`/stations/${s.id}/active`, { method: 'POST', body: { active: !s.active } });
                openView('stations');
              } catch (err) { showError(err); }
            },
          }, s.active ? 'Отключить' : 'Включить'),
        ] : null)))))));
  } catch (err) {
    host.replaceChildren(h('div', { class: 'error-box' }, err.message));
  }
}

function stationForm(station) {
  const form = h('form', { class: 'stack' },
    h('div', { class: 'grid2' },
      h('label', {}, 'Название * (PC-01)', h('input', { name: 'name', required: true, pattern: '[A-Z0-9-]{2,20}', value: station?.name || '' })),
      h('label', {}, 'Зона *', h('select', { name: 'type' }, Object.entries(STATION_TYPES).map(([k, v]) => option(k, v, station?.type)))),
      h('label', {}, 'Тариф, руб./ч *', h('input', { name: 'hourlyRate', type: 'number', min: '0.01', max: '100000', step: '0.01', required: true, value: station?.hourlyRate ?? '' }))),
    h('label', {}, 'Характеристики', h('input', { name: 'specs', maxlength: 255, value: station?.specs || '' })));
  const box = h('div', { class: 'error-box hidden' });
  const dlg = dialog(station ? `Место ${station.name}` : 'Новое место', [form, box], [
    h('button', { type: 'button', onclick: () => dlg.close() }, 'Отмена'),
    h('button', {
      class: 'primary', type: 'button', onclick: async () => {
        if (!form.reportValidity()) return;
        const body = { name: form.name.value.trim(), type: form.type.value, hourlyRate: Number(form.hourlyRate.value), specs: form.specs.value.trim() || null };
        try {
          if (station) await api(`/stations/${station.id}`, { method: 'PUT', body: { ...body, version: station.version } });
          else await api('/stations', { method: 'POST', body });
          dlg.close();
          openView('stations');
        } catch (err) { showError(err, box); }
      },
    }, 'Сохранить'),
  ]);
}

// ===================================================================== аналитика

async function renderAnalytics(root) {
  root.append(h('h2', {}, 'Аналитика'));
  try {
    const a = await api('/analytics/summary');
    const stat = (value, label) => h('div', { class: 'card stat' }, h('div', { class: 'value' }, value), h('div', { class: 'label' }, label));
    root.append(h('div', { class: 'stats' },
      stat(a.totalBookings, 'всего броней'),
      stat(a.activeBookings, 'действующих'),
      stat(a.archivedBookings, 'в архиве'),
      stat(a.paidBookings, 'оплачено (действующие)'),
      stat(fmtMoney(a.averagePrice), 'средняя стоимость'),
      stat(fmtMoney(a.completedRevenue), 'выручка (завершённые)')));
    const group = (title, data, titles) => {
      const max = Math.max(1, ...Object.values(data));
      return h('div', { class: 'card' }, h('h3', {}, title), Object.entries(data).map(([k, v]) =>
        h('div', { class: 'bar-row' },
          h('span', {}, titles ? (titles[k] || k) : k),
          h('div', { class: 'bar-track' }, h('div', { class: 'bar-fill', style: `width:${(v / max) * 100}%` })),
          h('span', { class: 'right' }, v))));
    };
    root.append(h('div', { class: 'bars' },
      group('По статусу', a.byStatus, STATUS_TITLES),
      group('По риску неоплаты', a.byRiskLevel, RISK_TITLES),
      group('По игровым местам', a.byStation),
      group('По зонам', a.byStationType, STATION_TYPES)),
    h('p', { class: 'small muted' }, 'Группировки — по действующим (неархивным) броням.'));
  } catch (err) {
    root.append(h('div', { class: 'error-box' }, err.message));
  }
}

// ===================================================================== пользователи

const usersState = { q: '', page: 0, size: 25, sort: 'email,asc' };

function renderUsers(root) {
  const form = h('form', { class: 'toolbar card', onsubmit: (e) => { e.preventDefault(); usersState.q = form.q.value.trim(); usersState.sort = form.sort.value; usersState.page = 0; load(); } },
    h('label', { style: 'min-width:240px' }, 'Поиск (email или имя)', h('input', { name: 'q', value: usersState.q })),
    h('label', {}, 'Сортировка', h('select', { name: 'sort' },
      [['email,asc', 'Email'], ['name,asc', 'Имя'], ['created,desc', 'Созданы ↓'], ['lastLogin,desc', 'Последний вход ↓']].map(([v, t]) => option(v, t, usersState.sort)))),
    h('button', { class: 'primary', type: 'submit' }, 'Найти'));
  const host = h('div', {});
  root.append(h('div', { class: 'row', style: 'margin-bottom:12px' },
    h('h2', { class: 'grow' }, 'Пользователи'),
    h('button', { class: 'primary', type: 'button', onclick: createUserDialog }, '＋ Пользователь')), form, host);

  async function load() {
    try {
      const page = await api('/admin/users', { params: { q: usersState.q, page: usersState.page, size: usersState.size, sort: usersState.sort } });
      host.replaceChildren(h('div', { class: 'card table-wrap', style: 'padding:0' }, h('table', {},
        h('thead', {}, h('tr', {}, ['Email', 'Имя', 'Роли', 'Состояние', 'Последний вход', ''].map(t => h('th', {}, t)))),
        h('tbody', {}, page.content.map(u => h('tr', { class: u.enabled ? '' : 'archived', onclick: () => editUserDialog(u, load) },
          h('td', {}, u.email),
          h('td', {}, u.displayName),
          h('td', {}, u.roles.map(r => ROLE_TITLES[r] || r).join(', ')),
          h('td', {}, !u.enabled ? 'отключён' : u.locked ? 'вход заблокирован' : 'активен',
            u.mustChangePassword ? h('div', { class: 'small muted' }, 'временный пароль') : null),
          h('td', { class: 'nowrap' }, fmtInstant(u.lastLoginAt)),
          h('td', { class: 'nowrap', onclick: (e) => e.stopPropagation() },
            h('button', { type: 'button', onclick: () => resetPassword(u, load) }, 'Сбросить пароль'), ' ',
            u.locked ? h('button', { type: 'button', onclick: async () => { try { await api(`/admin/users/${u.id}/unlock`, { method: 'POST' }); load(); } catch (err) { showError(err); } } }, 'Разблокировать') : null)))))),
      pager(page, (p) => { usersState.page = p; load(); }));
    } catch (err) {
      host.replaceChildren(h('div', { class: 'error-box' }, err.message));
    }
  }
  load();
}

function rolesFieldset(selected, disabled) {
  return h('fieldset', { class: 'row', style: 'border:1px solid var(--border);border-radius:8px' },
    h('legend', { class: 'small muted' }, 'Роли'),
    ['VIEWER', 'OPERATOR', 'ADMIN'].map(r => h('label', { class: 'inline' },
      h('input', { type: 'checkbox', name: 'roles', value: r, checked: selected.includes(r), disabled }), ROLE_TITLES[r])));
}

function createUserDialog() {
  const form = h('form', { class: 'stack' },
    h('div', { class: 'grid2' },
      h('label', {}, 'Email (логин) *', h('input', { name: 'email', type: 'email', required: true, maxlength: 100 })),
      h('label', {}, 'Имя *', h('input', { name: 'displayName', required: true, minlength: 2, maxlength: 100 }))),
    rolesFieldset(['OPERATOR'], false),
    h('label', {}, 'Временный пароль *', h('div', { class: 'row' },
      h('input', { name: 'password', required: true, minlength: 8, maxlength: 128, class: 'grow mono' }),
      h('button', { type: 'button', onclick: () => { form.password.value = randomPassword(); } }, 'Сгенерировать'))),
    h('p', { class: 'small muted' }, 'При первом входе пользователь обязан сменить пароль. Передайте его лично.'));
  const box = h('div', { class: 'error-box hidden' });
  const dlg = dialog('Новый пользователь', [form, box], [
    h('button', { type: 'button', onclick: () => dlg.close() }, 'Отмена'),
    h('button', {
      class: 'primary', type: 'button', onclick: async () => {
        if (!form.reportValidity()) return;
        const roles = [...form.querySelectorAll('input[name=roles]:checked')].map(i => i.value);
        try {
          await api('/admin/users', { method: 'POST', body: { email: form.email.value.trim(), displayName: form.displayName.value.trim(), password: form.password.value, roles } });
          toast('Пользователь зарегистрирован');
          dlg.close();
          openView('users');
        } catch (err) { showError(err, box); }
      },
    }, 'Зарегистрировать'),
  ]);
}

function editUserDialog(user, reload) {
  const isClient = user.roles.includes('CLIENT');
  const form = h('form', { class: 'stack' },
    h('div', { class: 'grid2' },
      h('label', {}, 'Email (логин) *', h('input', { name: 'email', type: 'email', required: true, value: user.email, disabled: isClient })),
      h('label', {}, 'Имя *', h('input', { name: 'displayName', required: true, minlength: 2, value: user.displayName }))),
    isClient ? h('p', { class: 'muted' }, 'Учётная запись клиента клуба: роль и логин не меняются.') : rolesFieldset(user.roles, false),
    h('label', { class: 'inline' }, h('input', { name: 'enabled', type: 'checkbox', checked: user.enabled }), 'Учётная запись активна'),
    h('p', { class: 'small muted' }, `Создан ${fmtInstant(user.createdAt)} · последний вход ${fmtInstant(user.lastLoginAt)}`));
  const box = h('div', { class: 'error-box hidden' });
  const dlg = dialog(user.email, [form, box], [
    h('button', { type: 'button', onclick: () => dlg.close() }, 'Отмена'),
    h('button', {
      class: 'primary', type: 'button', onclick: async () => {
        if (!form.reportValidity()) return;
        const roles = isClient ? ['CLIENT'] : [...form.querySelectorAll('input[name=roles]:checked')].map(i => i.value);
        try {
          await api(`/admin/users/${user.id}`, {
            method: 'PUT',
            body: { email: isClient ? user.email : form.email.value.trim(), displayName: form.displayName.value.trim(), roles, enabled: form.enabled.checked, version: user.version },
          });
          toast('Сохранено');
          dlg.close();
          reload();
        } catch (err) { showError(err, box); }
      },
    }, 'Сохранить'),
  ]);
}

async function resetPassword(user, reload) {
  if (!await confirmDialog('Сбросить пароль?', `Для ${user.email} будет создан временный пароль; все его сессии закроются.`, 'Сбросить')) return;
  try {
    const r = await api(`/admin/users/${user.id}/reset-password`, { method: 'POST' });
    dialog('Временный пароль', h('div', { class: 'stack' },
      h('p', {}, `Передайте пароль пользователю ${user.email}. Он показан один раз; при входе его нужно сменить.`),
      h('div', { class: 'ok-box mono', style: 'font-size:20px;text-align:center' }, r.temporaryPassword)), []);
    reload();
  } catch (err) { showError(err); }
}

function randomPassword() {
  const letters = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz';
  const digits = '23456789';
  const pick = (set, n) => Array.from(crypto.getRandomValues(new Uint32Array(n)), x => set[x % set.length]).join('');
  return pick(letters, 9) + pick(digits, 3);
}

// ===================================================================== аудит

const auditState = { q: '', type: '', page: 0, size: 25, sort: 'occurred,desc' };

function renderAudit(root) {
  const form = h('form', { class: 'toolbar card', onsubmit: (e) => { e.preventDefault(); auditState.q = form.q.value.trim(); auditState.type = form.type.value; auditState.sort = form.sort.value; auditState.page = 0; load(); } },
    h('label', { style: 'min-width:260px' }, 'Поиск (email, описание, id, correlation ID)', h('input', { name: 'q', value: auditState.q })),
    h('label', {}, 'Тип события', h('select', { name: 'type' }, option('', 'Все'), AUDIT_TYPES.map(t => option(t, t, auditState.type)))),
    h('label', {}, 'Сортировка', h('select', { name: 'sort' },
      [['occurred,desc', 'Сначала новые'], ['occurred,asc', 'Сначала старые'], ['type,asc', 'Тип'], ['actor,asc', 'Пользователь']].map(([v, t]) => option(v, t, auditState.sort)))),
    h('button', { class: 'primary', type: 'submit' }, 'Найти'));
  const host = h('div', {});
  root.append(h('h2', {}, 'Журнал аудита'), form, host);

  async function load() {
    try {
      const page = await api('/admin/audit', { params: { q: auditState.q, type: auditState.type, page: auditState.page, size: auditState.size, sort: auditState.sort } });
      host.replaceChildren(h('div', { class: 'card table-wrap', style: 'padding:0' }, h('table', {},
        h('thead', {}, h('tr', {}, ['Время', 'Пользователь', 'Событие', 'Объект', 'Описание', 'Correlation ID / IP'].map(t => h('th', {}, t)))),
        h('tbody', {}, page.content.map(e => h('tr', {},
          h('td', { class: 'nowrap' }, fmtInstant(e.occurredAt)),
          h('td', {}, e.actorEmail || '—'),
          h('td', { class: 'mono small' }, e.eventType),
          h('td', { class: 'small' }, e.entityType || '—', e.entityId ? h('div', { class: 'mono muted' }, e.entityId) : null),
          h('td', {}, e.summary),
          h('td', { class: 'mono small muted' }, e.correlationId || '—', h('div', {}, e.ipAddress || ''))))))),
      pager(page, (p) => { auditState.page = p; load(); }));
    } catch (err) {
      host.replaceChildren(h('div', { class: 'error-box' }, err.message));
    }
  }
  load();
}

// ===================================================================== кабинет клиента

const myState = { archive: 'ACTIVE', page: 0 };

async function renderMyBookings(root) {
  let card;
  let stations;
  try {
    [card, stations] = await Promise.all([api('/me/client'), api('/me/stations')]);
  } catch (err) {
    root.append(h('div', { class: 'error-box' }, err.message));
    return;
  }
  const today = new Date();
  const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  const durations = [1, 1.5, 2, 3, 4, 5, 6, 8, 10, 12].map(hrs => option(hrs * 60, fmtDuration(hrs * 60), 120));
  const form = h('form', { class: 'stack' },
    h('div', { class: 'grid2' },
      h('label', {}, 'Игровое место *', h('select', { name: 'stationId', required: true },
        option('', '— выберите —'),
        stations.map(s => option(s.id, `${s.name} · ${s.typeTitle} · ${fmtMoney(s.hourlyRate)}/ч${s.type === 'VIP' || s.type === 'BOOTCAMP' ? ' · 18+' : ''}`)))),
      h('label', {}, 'Дата *', h('input', { name: 'date', type: 'date', required: true, min: iso(today), value: iso(today) })),
      h('label', {}, 'Начало *', h('input', { name: 'time', type: 'time', required: true, step: 1800, value: '18:00' })),
      h('label', {}, 'Длительность *', h('select', { name: 'minutes' }, durations))),
    h('label', {}, 'Пожелания', h('input', { name: 'comment', maxlength: 255, placeholder: 'например, место у окна' })));
  const busyHost = h('div', { class: 'small muted' }, 'Выберите место и дату — покажем занятое время.');
  const priceHost = h('div', {});
  const box = h('div', { class: 'error-box hidden' });

  function selectedStation() {
    return stations.find(s => String(s.id) === form.stationId.value);
  }

  function updatePrice() {
    const st = selectedStation();
    const minutes = Number(form.minutes.value);
    if (!st) { priceHost.replaceChildren(); return; }
    const price = Number(st.hourlyRate) * Math.ceil(minutes / 30) / 2;
    priceHost.replaceChildren(h('strong', {}, `Стоимость: ${fmtMoney(price)}`), h('span', { class: 'muted' }, ' — оплата на стойке клуба'));
  }

  async function updateBusy() {
    updatePrice();
    if (!form.stationId.value || !form.date.value) return;
    try {
      const slots = await api('/me/busy', { params: { stationId: form.stationId.value, date: form.date.value } });
      busyHost.replaceChildren(slots.length
        ? h('span', {}, 'Занято: ', slots.map(sl => h('span', { class: 'tag' }, `${fmtLocal(sl.startTime).slice(11)}–${fmtLocal(sl.endTime).slice(11)}`)))
        : h('span', {}, 'На этот день место свободно.'));
    } catch (err) {
      busyHost.replaceChildren(h('span', {}, err.message));
    }
  }
  form.stationId.addEventListener('change', updateBusy);
  form.date.addEventListener('change', updateBusy);
  form.minutes.addEventListener('change', updatePrice);

  const submit = h('button', {
    class: 'primary', type: 'button', onclick: async () => {
      box.classList.add('hidden');
      if (!form.reportValidity()) return;
      const start = `${form.date.value}T${form.time.value}`;
      const end = new Date(new Date(`${start}:00`).getTime() + Number(form.minutes.value) * 60000);
      const pad = (n) => String(n).padStart(2, '0');
      const endText = `${end.getFullYear()}-${pad(end.getMonth() + 1)}-${pad(end.getDate())}T${pad(end.getHours())}:${pad(end.getMinutes())}`;
      try {
        const b = await api('/me/bookings', {
          method: 'POST',
          body: { stationId: Number(form.stationId.value), startTime: start, endTime: endText, comment: form.comment.value.trim() || null },
        });
        toast(`Бронь #${b.number} создана. Оплатите ${fmtMoney(b.totalPrice)} на стойке клуба.`);
        loadMy();
        updateBusy();
      } catch (err) {
        showError(err, box);
      }
    },
  }, 'Забронировать');

  const listHost = h('div', {});
  root.append(
    h('div', { class: 'row', style: 'margin-bottom:12px' },
      h('h2', { class: 'grow' }, `Привет, ${card.nickname}!`),
      h('span', { class: 'muted' }, `${card.fullName} · ${card.phone} · ${card.email}`)),
    h('div', { class: 'card stack', style: 'margin-bottom:16px' },
      h('h3', {}, 'Новая бронь'), form, busyHost, priceHost, box, h('div', {}, submit),
      h('p', { class: 'small muted' }, 'От 30 минут до 12 часов. Бронь появится со статусом «Создано»; администратор подтвердит её и отметит оплату, когда вы заплатите на стойке.')),
    h('div', { class: 'row', style: 'margin-bottom:8px' },
      h('h3', { class: 'grow' }, 'Мои брони'),
      h('select', { onchange: (e) => { myState.archive = e.target.value; myState.page = 0; loadMy(); } },
        option('ACTIVE', 'Действующие', myState.archive), option('ALL', 'Все, включая архив', myState.archive))),
    listHost);

  async function loadMy() {
    try {
      const page = await api('/me/bookings', { params: { archive: myState.archive, page: myState.page, size: 10 } });
      const now = Date.now();
      const rows = page.content.map(b => h('tr', { class: b.archived ? 'archived' : '' },
        h('td', { class: 'mono' }, `#${b.number}`),
        h('td', {}, b.stationName),
        h('td', { class: 'nowrap' }, fmtLocal(b.startTime)),
        h('td', { class: 'nowrap' }, fmtDuration(b.durationMinutes)),
        h('td', {}, statusBadge(b.status)),
        h('td', { class: 'right nowrap' }, fmtMoney(b.totalPrice)),
        h('td', {}, b.paid ? h('span', { class: 'badge risk NONE' }, 'оплачено')
          : b.status === 'CANCELLED' ? '—' : h('span', { class: 'badge risk MEDIUM' }, 'ожидает оплаты')),
        h('td', {}, ['CREATED', 'CONFIRMED'].includes(b.status) && !b.archived && new Date(b.startTime).getTime() > now
          ? h('button', {
            type: 'button', class: 'danger', onclick: async () => {
              if (!await confirmDialog('Отменить бронь?', `Бронь #${b.number}: ${b.stationName}, ${fmtLocal(b.startTime)}.`, 'Отменить бронь', true)) return;
              try {
                await api(`/me/bookings/${b.id}/cancel`, { method: 'POST' });
                toast(`Бронь #${b.number} отменена`);
                loadMy();
                updateBusy();
              } catch (err) { showError(err); }
            },
          }, 'Отменить') : null)));
      listHost.replaceChildren(h('div', { class: 'card table-wrap', style: 'padding:0' }, h('table', {},
        h('thead', {}, h('tr', {}, ['№', 'Место', 'Начало', 'Длит.', 'Статус', 'Стоимость', 'Оплата', ''].map(t => h('th', {}, t)))),
        h('tbody', {}, rows.length ? rows : h('tr', {}, h('td', { colspan: 8, class: 'muted' }, 'Броней пока нет — самое время забронировать!'))))),
      pager(page, (p) => { myState.page = p; loadMy(); }));
    } catch (err) {
      listHost.replaceChildren(h('div', { class: 'error-box' }, err.message));
    }
  }
  loadMy();
}

// ===================================================================== запуск

(async function start() {
  applyTheme(safeStorage(() => localStorage.getItem(THEME_KEY)) || 'light');
  if (state.refresh) {
    try {
      await refreshTokens();
      afterLogin();
      return;
    } catch (e) {
      // refresh-токен устарел — обычный вход
    }
  }
  showLogin();
})();
