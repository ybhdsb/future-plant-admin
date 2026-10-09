/* 离线逻辑测试：通过真实 V1.5 网页广播函数，模拟 API 与 RS485 回读时序。 */
'use strict';
const fs = require('fs');
const vm = require('vm');
const assert = require('assert');
const path = require('path');

const source = fs.readFileSync(path.join(__dirname, '../web/app.js'), 'utf8');
const fragment = source.substring(
  source.indexOf('// 广播没有逐台应答'),
  source.indexOf('async function handleKeySubmit')
);
const addresses = Array.from({length: 8}, (_, i) => '0x' + (0x96 + i).toString(16).toUpperCase());

function makeScenario({ initialProblems = [], retryFailures = [], initialError = null, broadcastError = null } = {}) {
  const logs = [], toasts = [], delays = [], applied = [], paths = [];
  let posted = 0;
  let firstRead = true;
  const target = { ch1: 30, ch2: 30 };
  const mkDevice = (addr, ch1 = target.ch1, ch2 = target.ch2) =>
    ({ busAddress: addr, ch1, ch2, ok: true });
  const context = {
    DEVICE_LIST: addresses.map((address) => ({ address })),
    busy: false, apiAvailable: true, lastSync: '',
    window: { confirm: () => true },
    setBusy: (flag) => { context.busy = flag; },
    setSystemState: () => {},
    timeText: () => 'TEST',
    addLog: (message, type) => logs.push({ message, type }),
    toast: (message, type) => toasts.push({ message, type }),
    applyDeviceRead: (device) => applied.push(device),
    health: async () => ({}),
    setTimeout: (fn, ms) => { delays.push(ms); fn(); },
    api: async (url, opts = {}) => {
      paths.push(url);
      if (url === '/broadcast/set') {
        if (broadcastError) throw broadcastError;
        posted += 1;
        assert.deepStrictEqual(JSON.parse(JSON.stringify(opts.body)), target);
        return { ok: true };
      }
      if (url === '/read-all') {
        if (initialError) throw initialError;
        assert.strictEqual(firstRead, true);
        firstRead = false;
        return { devices: addresses.map((addr) => initialProblems.includes(addr)
          ? { busAddress: addr, ch1: null, ch2: null, ok: false, error: '模拟串口超时' }
          : mkDevice(addr)) };
      }
      if (url.startsWith('/read?busAddress=')) {
        const addr = decodeURIComponent(url.split('=')[1]);
        if (retryFailures.includes(addr)) {
          const err = new Error('模拟两轮串口超时'); err.status = 502; throw err;
        }
        return mkDevice(addr);
      }
      throw new Error('unexpected URL ' + url);
    },
  };
  vm.createContext(context);
  vm.runInContext(fragment, context);
  const broadcast = vm.runInContext('broadcast', context);
  return { context, broadcast, logs, toasts, delays, applied, paths, get posted() { return posted; } };
}

async function main() {
  const normal = makeScenario();
  await normal.broadcast(30, 30, '测试');
  assert.strictEqual(normal.posted, 1);
  assert.deepStrictEqual(normal.paths, ['/broadcast/set', '/read-all']);
  assert.deepStrictEqual(normal.delays, [3000]);
  assert.strictEqual(normal.applied.length, 8);
  assert(normal.toasts.some(x => x.message.includes('8/8')));
  assert.strictEqual(normal.context.busy, false);
  console.log('PASS: 单次广播、延迟 3 秒、8/8 首次确认');

  const transient = makeScenario({ initialProblems: ['0x96', '0x98'] });
  await transient.broadcast(30, 30, '测试');
  assert.strictEqual(transient.posted, 1);
  assert.deepStrictEqual(transient.paths, ['/broadcast/set', '/read-all',
    '/read?busAddress=0x96', '/read?busAddress=0x98']);
  assert.deepStrictEqual(transient.delays, [3000, 1500]);
  assert(transient.toasts.some(x => x.message.includes('8/8')));
  console.log('PASS: 2 台瞬时回读失败，仅重读异常设备，不重发广播');

  const persistent = makeScenario({ initialProblems: ['0x96'], retryFailures: ['0x96'] });
  await persistent.broadcast(30, 30, '测试');
  assert.strictEqual(persistent.posted, 1);
  assert.deepStrictEqual(persistent.paths, ['/broadcast/set', '/read-all',
    '/read?busAddress=0x96', '/read?busAddress=0x96']);
  assert.deepStrictEqual(persistent.delays, [3000, 1500, 1500]);
  assert(persistent.toasts.some(x => x.message.includes('7/8')));
  assert(persistent.logs.some(x => x.message.includes('0x96') && x.message.includes('模拟两轮串口超时')));
  console.log('PASS: 持续超时只重读 2 轮，显示 7/8 与失败地址');

  const fallback = makeScenario({ initialError: Object.assign(new Error('HTTP 500'), { status: 500 }) });
  await fallback.broadcast(30, 30, '测试');
  assert.strictEqual(fallback.posted, 1);
  assert.strictEqual(fallback.paths.filter(x => x.startsWith('/read?')).length, 8);
  assert(fallback.toasts.some(x => x.message.includes('8/8')));
  console.log('PASS: 批量回读 HTTP 500 时逐台重读，恢复后正确确认');

  const noSend = makeScenario({ broadcastError: Object.assign(new Error('HTTP 401'), { status: 401 }) });
  await noSend.broadcast(30, 30, '测试');
  assert.strictEqual(noSend.posted, 0);
  assert.strictEqual(noSend.paths.length, 1);
  assert(noSend.toasts.some(x => x.message.includes('广播请求失败')));
  console.log('PASS: POST 被拒绝时不回读、不误报广播确认成功');
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
