'use strict';

/* 独立 Web 控制台：调用原有五个 HTTP API，不包含模拟数据，也不在本地保存密钥。 */
/* 物理分组：第一列 0x96～0x99 为组 1，第二列 0x9A～0x9D 为组 2（不再按光谱两两配对成 4 组）。 */

const DEVICE_SPECTRA = {
  '0x96': { ch1: '660nm', ch2: '395nm', colors: ['#b45458', '#9585ba'] },
  '0x97': { ch1: '450nm', ch2: '530nm', colors: ['#6688da', '#6ba887'] },
  '0x98': { ch1: '630nm', ch2: '430nm', colors: ['#c15d59', '#687fd5'] },
  '0x99': { ch1: '730nm', ch2: '全光谱', colors: ['#c47b63', '#7faf8f'] },
  '0x9A': { ch1: '660nm', ch2: '395nm', colors: ['#b45458', '#9585ba'] },
  '0x9B': { ch1: '450nm', ch2: '530nm', colors: ['#6688da', '#6ba887'] },
  '0x9C': { ch1: '630nm', ch2: '430nm', colors: ['#c15d59', '#687fd5'] },
  '0x9D': { ch1: '730nm', ch2: '全光谱', colors: ['#c47b63', '#7faf8f'] },
};

/** 两列物理组：左列 4 台、右列 4 台 */
const RACK_GROUPS = [
  {
    group: 1,
    label: '第一组 · 左列',
    subtitle: '驱动器 0x96～0x99 · 同列独立控制',
    addresses: ['0x96', '0x97', '0x98', '0x99'],
  },
  {
    group: 2,
    label: '第二组 · 右列',
    subtitle: '驱动器 0x9A～0x9D · 同列独立控制',
    addresses: ['0x9A', '0x9B', '0x9C', '0x9D'],
  },
];

const DEVICE_LIST = RACK_GROUPS.flatMap((group) =>
  group.addresses.map((address) => ({
    address,
    group: group.group,
    ch1: DEVICE_SPECTRA[address].ch1,
    ch2: DEVICE_SPECTRA[address].ch2,
    colors: DEVICE_SPECTRA[address].colors,
  })),
);

const $ = (id) => document.getElementById(id);
const live = new Map(DEVICE_LIST.map((d) => [d.address, { ok: false, seen: false, confirmed: [null, null], draft: [null, null], error: '' }]));
let apiKey = '';
let busy = false;
let apiAvailable = false;
let serialConnected = null;
let lastSync = '';

class RequestFailure extends Error {
  constructor(message, status = 0) { super(message); this.status = status; }
}

function validLevel(value) {
  if (value === '' || value === null || value === undefined) return null;
  const valueStr = String(value).trim();
  if (!/^\d{1,3}$/.test(valueStr)) return null;
  const n = Number(valueStr);
  return n >= 0 && n <= 255 ? n : null;
}

function timestamp() { return new Date().toLocaleTimeString('zh-CN', { hour12: false }); }
function timeText() { return new Date().toLocaleString('zh-CN', { hour12: false }); }
function pct(n) { return n == null ? '—' : `${Math.round(n * 100 / 255)}%`; }
function entryDirty(entry) { return entry.seen && entry.ok && entry.draft.some((v, i) => v !== entry.confirmed[i]); }
function anyDirty() { return [...live.values()].some((entry) => entryDirty(entry)); }

function addLog(message, kind = 'success') {
  const list = $('activityList');
  const empty = list.querySelector('.activity-empty');
  if (empty) empty.remove();
  const li = document.createElement('li');
  const body = document.createElement('span');
  body.className = 'activity-copy';
  const dot = document.createElement('span');
  dot.className = `activity-icon ${kind === 'success' ? '' : kind}`;
  body.append(dot, document.createTextNode(message));
  const time = document.createElement('time');
  time.textContent = timestamp();
  li.append(body, time);
  list.prepend(li);
  while (list.children.length > 15) list.lastElementChild.remove();
}

function toast(message, kind = 'success') {
  const item = document.createElement('div');
  item.className = `toast ${kind === 'success' ? '' : kind}`;
  item.textContent = message;
  $('toastStack').append(item);
  setTimeout(() => item.remove(), 5800);
}

async function api(path, options = {}) {
  const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}) };
  if (apiKey) headers['X-API-Key'] = apiKey;
  let response;
  try {
    response = await fetch(`/api/v1/led${path}`, {
      method: options.method || 'GET',
      headers,
      body: options.body ? JSON.stringify(options.body) : undefined,
      credentials: 'omit', cache: 'no-store',
    });
  } catch (_) {
    throw new RequestFailure('无法连接 LED 网关，请确认 Python 服务正在运行。');
  }
  let data = {};
  try { data = await response.json(); } catch (_) { /* unexpected non-JSON */ }
  if (!response.ok) {
    const details = typeof data.detail === 'string' ? data.detail : JSON.stringify(data.detail || {});
    if (response.status === 401) {
      openKeyDialog('API 密钥无效或缺失，请输入正确密钥。');
    }
    throw new RequestFailure(`HTTP ${response.status}：${details || '接口请求失败'}`, response.status);
  }
  return data;
}

function openKeyDialog(message = '') {
  const dlg = $('keyDialog');
  $('keyError').textContent = message;
  $('keyError').hidden = !message;
  if (!dlg.open) dlg.showModal();
}

function setBusy(flag) {
  busy = flag;
  for (const el of document.querySelectorAll('.command-button, #refreshBtn, #broadcast1, #broadcast2')) {
    el.disabled = flag || (el.dataset.needsOnline === 'true' && live.get(el.dataset.address)?.ok !== true);
  }
  for (const config of DEVICE_LIST) updateDevice(config.address);
  $('refreshBtn').textContent = flag ? '正在通信…' : '↻ 读取全部设备';
}

function setSystemState() {
  $('apiStatus').textContent = apiAvailable ? '运行正常' : '连接异常';
  $('apiDetail').textContent = apiAvailable ? 'HTTP 网关响应正常' : '请检查端口 / 密钥';
  $('serialStatus').textContent = serialConnected === true ? '已连接' : serialConnected === false ? '尚未打开' : '待检查';
  $('serialDetail').textContent = serialConnected === true ? 'RS485 串口已经打开' : '串口将在首次读写时打开';
  const online = [...live.values()].filter((x) => x.seen && x.ok).length;
  const known = [...live.values()].some((x) => x.seen);
  $('onlineCount').textContent = known ? String(online) : '—';
  $('onlineDetail').textContent = known ? '依据最近一次逐台读取' : '等待第一次读取';
  $('lastSync').textContent = lastSync ? `最近读取：${lastSync}` : '尚未读取设备';
}

function makeRackGroup(group) {
  const section = document.createElement('section');
  section.className = 'spectrum-group';
  section.setAttribute('aria-label', `${group.label}`);
  section.innerHTML = `
    <div class="spectrum-group-heading">
      <div class="spectrum-group-title"><span class="group-number">组 ${group.group}</span><div>
        <h3>${group.label}</h3>
        <p>${group.subtitle}</p>
      </div></div>
      <span class="group-pair">${group.addresses.join(' · ')}</span>
    </div>
    <div id="device-pair-${group.group}" class="device-pair-grid"></div>`;
  $('deviceGrid').append(section);
}

function makeDeviceCard(config, idx) {
  const article = document.createElement('article');
  article.className = 'device-card';
  article.id = `device-${idx}`;
  article.innerHTML = `
    <div class="device-head">
      <div class="device-identity"><div class="device-idx">${String(idx+1).padStart(2,'0')}</div><div>
        <h3 class="device-name">驱动器 ${config.address}</h3><div class="device-sub">CH1 ${config.ch1} · CH2 ${config.ch2} · 本设备独立调光</div>
      </div></div><span class="state-pill" id="pill-${idx}">尚未读取</span>
    </div>
    ${[config.ch1, config.ch2].map((spectrum, ch) => `
      <div class="channel">
        <div class="channel-label"><span><i class="spectrum-dot"></i> CH${ch+1} · ${spectrum}</span><span id="pct-${idx}-${ch}" class="level-pct">—</span></div>
        <div class="channel-controls">
          <input id="range-${idx}-${ch}" class="level-range" type="range" min="0" max="255" value="0" aria-label="${config.address} CH${ch+1} ${spectrum} 光强滑块" disabled>
          <input id="number-${idx}-${ch}" class="level-number" type="number" min="0" max="255" step="1" inputmode="numeric" aria-label="${config.address} CH${ch+1} 光强数值" placeholder="—" disabled>
        </div>
        <div id="saved-${idx}-${ch}" class="channel-sub">等待读取实际光强</div>
      </div>`).join('')}
    <div class="device-footer"><span id="device-msg-${idx}" class="device-message">请先读取设备</span>
      <div class="device-actions"><button type="button" class="button button-read command-button" data-read="${config.address}">回读</button><button type="button" class="button button-dark command-button" data-set="${config.address}" data-address="${config.address}" data-needs-online="true" disabled>应用设置</button></div>
    </div>`;
  $(`device-pair-${config.group}`).append(article);
  article.querySelectorAll('.channel').forEach((el, ch) => el.style.setProperty('--spectrum', config.colors[ch]));
  [0, 1].forEach((ch) => {
    const range = $(`range-${idx}-${ch}`);
    const number = $(`number-${idx}-${ch}`);
    range.addEventListener('input', () => setDraft(config.address, ch, range.value));
    number.addEventListener('input', () => setDraft(config.address, ch, number.value));
    number.addEventListener('change', () => { if (validLevel(number.value) != null) number.value = String(Number(number.value)); });
  });
  article.querySelector('[data-read]').addEventListener('click', () => readOne(config.address));
  article.querySelector('[data-set]').addEventListener('click', () => setOne(config.address));
  updateDevice(config.address);
}

function setDraft(address, ch, raw) {
  const level = validLevel(raw);
  const entry = live.get(address);
  entry.draft[ch] = level;
  const idx = DEVICE_LIST.findIndex((d) => d.address === address);
  const range = $(`range-${idx}-${ch}`);
  const number = $(`number-${idx}-${ch}`);
  if (level != null) {
    range.value = String(level);
    number.value = String(level);
  } else {
    if (document.activeElement !== number) number.value = raw;
  }
  updateDevice(address);
}

function updateDevice(address) {
  const idx = DEVICE_LIST.findIndex((d) => d.address === address);
  const entry = live.get(address);
  const card = $(`device-${idx}`);
  const pill = $(`pill-${idx}`);
  if (!card) return;
  pill.className = `state-pill ${!entry.seen ? '' : entry.ok ? 'online' : 'offline'}`;
  pill.textContent = !entry.seen ? '尚未读取' : entry.ok ? '通信正常' : '读取失败';
  card.classList.toggle('is-dirty', entryDirty(entry));
  for (let ch = 0; ch < 2; ch++) {
    const range = $(`range-${idx}-${ch}`);
    const number = $(`number-${idx}-${ch}`);
    const level = entry.draft[ch];
    range.disabled = busy || !entry.ok;
    number.disabled = busy || !entry.ok;
    if (level != null) {
      if (document.activeElement !== number) number.value = String(level);
      range.value = String(level);
    } else if (document.activeElement !== number) number.value = '';
    range.style.setProperty('--fill', `${level == null ? 0 : 100 * level / 255}%`);
    $(`pct-${idx}-${ch}`).textContent = pct(level);
    $(`saved-${idx}-${ch}`).textContent = entry.confirmed[ch] == null
      ? '暂无已确认数据'
      : `${entry.ok ? '设备回读' : '上次确认'}：${entry.confirmed[ch]} / 255${level !== entry.confirmed[ch] ? ' · 有待应用修改' : ''}`;
  }
  const msg = $(`device-msg-${idx}`);
  msg.textContent = !entry.seen ? '请先读取设备' : !entry.ok ? '设备未能回读，请检查连接' : entryDirty(entry) ? '已修改预设，尚未发送' : '已与设备读取值同步';
  const set = card.querySelector('[data-set]');
  set.disabled = busy || !entry.ok || !entryDirty(entry) || entry.draft.some((v) => v == null);
  const read = card.querySelector('[data-read]');
  read.disabled = busy;
}

function applyDeviceRead(device) {
  const entry = live.get(device.busAddress);
  if (!entry) return;
  entry.seen = true;
  entry.ok = device.ok !== false && Number.isInteger(device.ch1) && Number.isInteger(device.ch2) &&
    validLevel(device.ch1) != null && validLevel(device.ch2) != null;
  entry.error = device.error || '';
  if (entry.ok) {
    entry.confirmed = [device.ch1, device.ch2];
    entry.draft = [device.ch1, device.ch2];
  }
  updateDevice(device.busAddress);
}

async function health() {
  const info = await api('/health');
  apiAvailable = true;
  serialConnected = Boolean(info.serialConnected);
  setSystemState();
}

async function refreshAll(force = false) {
  if (busy) return;
  if (!force && anyDirty() && !window.confirm('当前有尚未应用的调光预设，读取全部将覆盖这些预设。确定继续吗？')) return;
  setBusy(true);
  try {
    await health();
    const result = await api('/read-all');
    if (!Array.isArray(result.devices)) throw new Error('网关响应缺少 devices 数据');
    for (const dev of result.devices) applyDeviceRead(dev);
    lastSync = timeText();
    await health();
    const okCount = [...live.values()].filter((v) => v.ok && v.seen).length;
    const total = DEVICE_LIST.length;
    addLog(`读取全部完成：${okCount}/${total} 台成功`, okCount === total ? 'success' : 'warning');
    if (okCount < total) toast(`读取完成，但 ${total - okCount} 台设备未能确认`, 'warning');
  } catch (err) {
    apiAvailable = err.status === 401 || err.status === 0 ? false : apiAvailable;
    toast(`读取失败：${err.message}`, 'error');
    addLog(`读取全部失败：${err.message}`, 'error');
  } finally {
    setBusy(false);
    setSystemState();
  }
}

async function readOne(address) {
  if (busy) return;
  if (entryDirty(live.get(address)) && !window.confirm(`${address} 有尚未应用的修改，回读将覆盖预设。确定继续吗？`)) return;
  setBusy(true);
  try {
    const dev = await api(`/read?busAddress=${encodeURIComponent(address)}`);
    applyDeviceRead({ ...dev, ok: true });
    lastSync = timeText();
    await health();
    addLog(`${address} 回读成功：CH1=${dev.ch1}，CH2=${dev.ch2}`);
  } catch (err) {
    const entry = live.get(address);
    if (err.status === 0 || err.status === 401) apiAvailable = false;
    entry.seen = true;
    entry.ok = false;
    entry.error = err.message;
    toast(`${address} 回读失败：${err.message}`, 'error');
    addLog(`${address} 回读失败：${err.message}`, 'error');
  } finally {
    setBusy(false);
    setSystemState();
  }
}

async function setOne(address) {
  if (busy) return;
  const entry = live.get(address);
  if (!entry.ok) { toast('请先成功读取这台设备，再设置光强', 'warning'); return; }
  const [ch1, ch2] = entry.draft;
  if (ch1 == null || ch2 == null) { toast('请填写 0–255 的整数光强', 'warning'); return; }
  if (!entryDirty(entry)) { toast('当前没有待应用的修改', 'warning'); return; }
  setBusy(true);
  try {
    await api('/set', { method: 'POST', body: { busAddress: address, ch1, ch2 } });
    addLog(`${address} 写入已确认，正在回读 CH1=${ch1} / CH2=${ch2}`);
    try {
      const data = await api(`/read?busAddress=${encodeURIComponent(address)}`);
      applyDeviceRead({ ...data, ok: true });
      lastSync = timeText();
      const match = data.ch1 === ch1 && data.ch2 === ch2;
      toast(match ? `${address} 设置成功，回读与目标值一致` : `${address} 写入成功，但回读值与设定不一致，请检查设备`, match ? 'success' : 'warning');
      addLog(`${address} 回读 CH1=${data.ch1} / CH2=${data.ch2}${match ? '，已确认' : '，与设定不一致'}`, match ? 'success' : 'warning');
    } catch (err) {
      entry.ok = false; entry.seen = true;
      toast(`${address} 指令已确认写入，但回读失败：${err.message}`, 'warning');
      addLog(`${address} 写入已确认但无法回读，状态未知`, 'warning');
    }
    try { await health(); } catch (_) { /* avoid overriding previous result */ }
  } catch (err) {
    toast(`${address} 设置失败：${err.message}`, 'error');
    addLog(`${address} 设置失败：${err.message}`, 'error');
  } finally { setBusy(false); setSystemState(); }
}

// 广播没有逐台应答，因此“写入完成”和“回读确认”必须分别报告。
const BROADCAST_SETTLE_MS = 3000;
const BROADCAST_RETRY_WAIT_MS = 1500;
const BROADCAST_RETRY_ROUNDS = 2;
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function matchesBroadcast(device, ch1, ch2) {
  return device && device.ok === true && device.ch1 === ch1 && device.ch2 === ch2;
}

function broadcastMismatch(address, device, ch1, ch2) {
  if (!device) return `${address} 未返回读取结果`;
  if (device.ok !== true) return `${address} 读取失败：${device.error || '设备没有应答'}`;
  return `${address} 实际 CH1=${device.ch1} / CH2=${device.ch2}，目标 ${ch1}/${ch2}`;
}

async function verifyBroadcast(ch1, ch2) {
  addLog(`已发送广播；等待 ${BROADCAST_SETTLE_MS / 1000} 秒后逐台回读`, 'warning');
  await wait(BROADCAST_SETTLE_MS);

  const readings = new Map();
  try {
    const result = await api('/read-all');
    if (!Array.isArray(result.devices)) throw new Error('网关回读响应缺少 devices 数组');
    for (const dev of result.devices) {
      if (DEVICE_LIST.some(({ address }) => address === dev.busAddress)) {
        readings.set(dev.busAddress, dev);
      }
    }
  } catch (err) {
    if (err.status === 401) throw err;
    addLog(`首次批量回读异常：${err.message}，将尝试逐台读取`, 'warning');
  }

  for (let round = 1; round <= BROADCAST_RETRY_ROUNDS; round++) {
    const pending = DEVICE_LIST.filter(({ address }) =>
      !matchesBroadcast(readings.get(address), ch1, ch2));
    if (pending.length === 0) break;

    addLog(`回读第 ${round} 轮重试：${pending.map((d) => d.address).join('、')}`, 'warning');
    await wait(BROADCAST_RETRY_WAIT_MS);
    for (const { address } of pending) {
      try {
        const result = await api(`/read?busAddress=${encodeURIComponent(address)}`);
        readings.set(address, { ...result, busAddress: address, ok: true });
      } catch (err) {
        if (err.status === 401) throw err;
        readings.set(address, { busAddress: address, ch1: null, ch2: null,
          ok: false, error: err.message });
      }
    }
  }

  for (const { address } of DEVICE_LIST) {
    applyDeviceRead(readings.get(address) || {
      busAddress: address, ch1: null, ch2: null,
      ok: false, error: '本轮没有获得设备回读数据',
    });
  }
  lastSync = timeText();
  const problems = DEVICE_LIST
    .filter(({ address }) => !matchesBroadcast(readings.get(address), ch1, ch2))
    .map(({ address }) => broadcastMismatch(address, readings.get(address), ch1, ch2));
  return { matched: DEVICE_LIST.length - problems.length,
    total: DEVICE_LIST.length, problems };
}

async function broadcast(ch1, ch2, label) {
  if (busy) return;
  if (ch1 == null || ch2 == null) { toast('广播光强必须为 0–255 的整数', 'warning'); return; }
  if (!window.confirm(`确认${label}吗？\n总线上的所有广播接收设备都可能受到影响（当前列管 ${DEVICE_LIST.length} 台）。\nCH1 将设置为 ${ch1}，CH2 将设置为 ${ch2}。\n此操作会立即改变真实 LED 光强。`)) return;
  setBusy(true);
  try {
    await api('/broadcast/set', { method: 'POST', body: { ch1, ch2 } });
    addLog(`广播发送成功：CH1=${ch1} / CH2=${ch2}；准备延迟回读`, 'success');

    try {
      const { matched, total, problems } = await verifyBroadcast(ch1, ch2);
      if (matched === total) {
        toast(`广播已发送，${total}/${total} 台回读确认成功`, 'success');
        addLog(`广播确认完成：${total}/${total} 台光强一致`, 'success');
      } else {
        toast(`广播已发送，${matched}/${total} 台确认成功；其余设备请查看操作记录`, 'warning');
        addLog(`广播已发送但未全部确认：${matched}/${total} 台一致`, 'warning');
        for (const problem of problems) addLog(`未确认：${problem}`, 'warning');
      }
    } catch (err) {
      if (err.status === 401 || err.status === 0) apiAvailable = false;
      toast(`广播已经发送，但后续回读中断：${err.message}`, 'warning');
      addLog(`广播已发送，回读中断：${err.message}`, 'warning');
    }
    try { await health(); } catch (_) { /* 设备确认结果不受状态 API 影响 */ }
  } catch (err) {
    toast(`广播请求失败：${err.message}；请先回读确认，不要立即重复广播`, 'error');
    addLog(`广播请求失败，现场执行状态未确认：${err.message}`, 'error');
  } finally {
    setBusy(false);
    setSystemState();
  }
}

async function handleKeySubmit(event) {
  event.preventDefault();
  if (busy) return;
  const next = $('keyInput').value.trim();
  apiKey = next;
  $('keyError').hidden = true;
  try {
    await health();
    $('keyDialog').close();
    $('keyInput').value = '';
    toast('网关验证成功');
    await refreshAll(true);
  } catch (err) {
    $('keyError').hidden = false;
    $('keyError').textContent = err.message;
  }
}

function init() {
  $('serverAddress').textContent = window.location.host;
  RACK_GROUPS.forEach(makeRackGroup);
  DEVICE_LIST.forEach(makeDeviceCard);
  $('keyBtn').addEventListener('click', () => openKeyDialog());
  $('closeKey').addEventListener('click', () => $('keyDialog').close());
  $('keyForm').addEventListener('submit', handleKeySubmit);
  $('refreshBtn').addEventListener('click', () => refreshAll());
  $('broadcastOff').addEventListener('click', () => broadcast(0, 0, '关闭所有灯'));
  $('broadcastOn').addEventListener('click', () => broadcast(255, 255, '将所有灯设置为最亮'));
  $('broadcastCustom').addEventListener('click', () => broadcast(validLevel($('broadcast1').value), validLevel($('broadcast2').value), '广播指定光强'));
  ['broadcastOff', 'broadcastOn', 'broadcastCustom'].forEach((id) => $(id).classList.add('command-button'));
  $('clearLog').addEventListener('click', () => { $('activityList').replaceChildren(); const li = document.createElement('li'); li.className = 'activity-empty'; li.textContent = '尚无操作记录'; $('activityList').append(li); });
  setSystemState();
  refreshAll(true);
}

init();
