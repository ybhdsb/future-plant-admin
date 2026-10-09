import threading
from concurrent.futures import ThreadPoolExecutor
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

import light_modbus
from light_modbus import (
    DEVICE_ADDRESSES, LEDCommunicationError, LEDProtocolError, LEDSerialError, LightController,
    build_frame, modbus_crc, parse_address,
)
from server import create_app


class SimulatedSerial:
    """仿真八台双通道灯，返回合法 RTU 帧，部分读取会拆成小块。"""

    def __init__(self, **kwargs):
        self.is_open = True
        self.kwargs = kwargs
        self.buff = bytearray()
        self.values = {addr: [0, 0] for addr in DEVICE_ADDRESSES}
        self.unavailable = set()
        self.bad_crc = set()
        self.written = []
        self.read_calls = 0
        self.thread_ids = set()

    def reset_input_buffer(self):
        self.buff.clear()

    def write(self, frame):
        self.thread_ids.add(threading.get_ident())
        assert modbus_crc(frame[:-2]) == frame[-2:]
        self.written.append(frame)
        addr, fc = frame[:2]
        if fc == 0x10:
            assert frame[2:7] == bytes((0, 1, 0, 2, 4))
            ch1, ch2 = frame[8], frame[10]
            if addr == 0:
                for a in self.values:
                    self.values[a][:] = [ch1, ch2]
                self.buff = bytearray()  # 广播必须不回包
            elif addr not in self.unavailable:
                self.values[addr][:] = [ch1, ch2]
                self.buff = bytearray(build_frame(frame[:6]))
        elif fc == 0x03:
            assert frame[2:6] == bytes((0, 1, 0, 2))
            if addr not in self.unavailable:
                v1, v2 = self.values[addr]
                payload = bytes((addr, 0x03, 4, 0, v1, 0, v2))
                self.buff = bytearray(build_frame(payload))
        else:
            raise AssertionError(f'Unexpected function {fc}')
        if addr in self.bad_crc and self.buff:
            self.buff[-1] ^= 0x10
        return len(frame)

    def read(self, n):
        self.read_calls += 1
        count = min(n, 2, len(self.buff))
        result = self.buff[:count]
        del self.buff[:count]
        return bytes(result)

    def flush(self):
        pass

    def close(self):
        self.is_open = False


@pytest.fixture
def fixture(monkeypatch):
    handles = []

    def serial_factory(**kwargs):
        handle = SimulatedSerial(**kwargs)
        handles.append(handle)
        return handle

    monkeypatch.setattr(light_modbus, 'serial', SimpleNamespace(
        Serial=serial_factory, EIGHTBITS=8, PARITY_NONE='N',
        STOPBITS_ONE=1, SerialException=OSError,
    ))
    controller = LightController('/dev/ttySIM0', timeout=0.1)
    app = create_app(controller=controller, api_key='test-secret')
    with TestClient(app) as client:
        yield controller, client, handles
    controller.close()


def headers():
    return {'X-API-Key': 'test-secret'}


def test_crc_address_and_bad_params():
    assert modbus_crc(bytes.fromhex('01 03 00 00 00 0A')) == bytes.fromhex('C5 CD')
    assert parse_address('0x9a') == 0x9A
    assert list(DEVICE_ADDRESSES) == list(range(0x96, 0x9E))
    for addr in DEVICE_ADDRESSES:
        assert parse_address(f'0x{addr:02X}') == addr
    with pytest.raises(ValueError):
        parse_address('0x10')
    with pytest.raises(ValueError):
        parse_address(154)  # 十进制本期不支持


def test_set_read_and_broadcast(fixture):
    controller, client, handles = fixture
    r = client.post('/api/v1/led/set', json={'busAddress': '0x9A', 'ch1': 200, 'ch2': 18}, headers=headers())
    assert r.status_code == 200, r.text
    assert r.json() == {'ok': True, 'busAddress': '0x9A', 'ch1': 200, 'ch2': 18}
    assert handles[0].kwargs['baudrate'] == 9600
    assert handles[0].kwargs['exclusive'] is True
    assert handles[0].written[0][0:2] == bytes((0x9A, 0x10))
    r = client.get('/api/v1/led/read?busAddress=0x9A', headers=headers())
    assert r.status_code == 200
    assert r.json() == {'busAddress': '0x9A', 'ch1': 200, 'ch2': 18}
    r = client.post('/api/v1/led/broadcast/set', json={'ch1': 255, 'ch2': 0}, headers=headers())
    assert r.status_code == 200
    assert r.json()['scope'] == 'BROADCAST'
    assert handles[0].written[-1][0:2] == bytes((0, 0x10))
    r = client.get('/api/v1/led/read-all', headers=headers())
    assert r.status_code == 200
    assert len(r.json()['devices']) == 8
    assert all(d['ch1'] == 255 and d['ch2'] == 0 and d['ok'] for d in r.json()['devices'])
    assert [d['busAddress'] for d in r.json()['devices']] == [f'0x{a:02X}' for a in DEVICE_ADDRESSES]
    assert all(f[1] == 0x03 for f in handles[0].written[-8:])


def test_auth_validation_and_health(fixture):
    _, client, handles = fixture
    assert client.get('/api/v1/led/read-all').status_code == 401
    assert client.get('/api/v1/led/health', headers=headers()).json() == {'ok': True, 'serialConnected': False}
    assert client.post('/api/v1/led/set', json={'busAddress': '0x9X', 'ch1': 1, 'ch2': 2}, headers=headers()).status_code == 422
    assert client.post('/api/v1/led/set', json={'busAddress': '0x9A', 'ch1': True, 'ch2': 2}, headers=headers()).status_code == 422
    assert client.post('/api/v1/led/set', json={'busAddress': '0x9A', 'ch1': 256, 'ch2': 2}, headers=headers()).status_code == 422
    assert client.post('/api/v1/led/broadcast/set', json={'ch1': 0, 'ch2': -1}, headers=headers()).status_code == 422
    assert not handles


def test_read_all_partial_error(fixture):
    _, client, handles = fixture
    client.get('/api/v1/led/read?busAddress=0x9A', headers=headers())
    handles[0].unavailable.add(0x9B)
    result = client.get('/api/v1/led/read-all', headers=headers())
    assert result.status_code == 200
    vals = result.json()['devices']
    assert vals[5]['busAddress'] == '0x9B'
    assert vals[5]['ok'] is False
    assert vals[5]['ch1'] is None and vals[5]['ch2'] is None
    assert '超时' in vals[5]['error']
    assert [v['ok'] for v in vals] == [True, True, True, True, True, False, True, True]
    assert client.get('/api/v1/led/read?busAddress=0x9B', headers=headers()).status_code == 502


def test_crc_fail_raises_502(fixture):
    _, client, handles = fixture
    client.get('/api/v1/led/read?busAddress=0x9A', headers=headers())
    handles[0].bad_crc.add(0x9A)
    r = client.get('/api/v1/led/read?busAddress=0x9A', headers=headers())
    assert r.status_code == 502
    assert 'CRC' in r.json()['detail']


def test_concurrent_calls_keep_serial_frames_separate(fixture):
    _, client, handles = fixture
    with ThreadPoolExecutor(max_workers=8) as executor:
        results = list(executor.map(
            lambda i: client.get('/api/v1/led/read?busAddress=0x9A', headers=headers()),
            range(16),
        ))
    assert all(r.status_code == 200 for r in results)
    assert len(handles) == 1  # 同一串口仅打开一次
    assert len(handles[0].written) == 16


def test_serial_open_failure_is_503(monkeypatch):
    def raise_serial(**kwargs):
        raise OSError('permission denied')
    monkeypatch.setattr(light_modbus, 'serial', SimpleNamespace(
        Serial=raise_serial, EIGHTBITS=8, PARITY_NONE='N',
        STOPBITS_ONE=1, SerialException=OSError,
    ))
    app = create_app(controller=LightController('/dev/ttyFAKE'), api_key='test-secret')
    with TestClient(app) as client:
        r = client.post('/api/v1/led/set', json={'busAddress':'0x9D','ch1':0,'ch2':0}, headers=headers())
        assert r.status_code == 503
        assert '无法打开串口' in r.json()['detail']


def test_web_dashboard_served_without_changing_api_auth(fixture):
    _, client, handles = fixture
    home = client.get('/')
    assert home.status_code == 200
    assert '植物补光控制' in home.text
    assert '/assets/app.js' in home.text
    assert home.headers['content-security-policy'].startswith("default-src 'none'")
    assert home.headers['cache-control'] == 'no-store'
    assert client.get('/assets/style.css').status_code == 200
    js = client.get('/assets/app.js')
    assert js.status_code == 200
    assert 'localStorage' not in js.text
    assert 'sessionStorage' not in js.text
    assert 'X-API-Key' in js.text
    assert client.get('/api/v1/led/read-all').status_code == 401
    assert not handles  # 单纯打开网页不触发串口读写（JS 在浏览器端运行）


def test_each_new_device_can_be_set_and_read(fixture):
    _, client, handles = fixture
    for i, addr in enumerate(range(0x96, 0x9A)):
        name = f'0x{addr:02X}'
        value = {'busAddress': name, 'ch1': 10 + i, 'ch2': 200 - i}
        response = client.post('/api/v1/led/set', json=value, headers=headers())
        assert response.status_code == 200, response.text
        assert response.json()['busAddress'] == name
        result = client.get(f'/api/v1/led/read?busAddress={name}', headers=headers())
        assert result.status_code == 200
        assert result.json() == value
    assert [frame[0] for frame in handles[0].written[::2]] == list(range(0x96, 0x9A))


def test_new_device_can_fail_without_hiding_others(fixture):
    _, client, handles = fixture
    client.get('/api/v1/led/read?busAddress=0x9A', headers=headers())
    handles[0].unavailable.add(0x97)
    devices = client.get('/api/v1/led/read-all', headers=headers()).json()['devices']
    assert len(devices) == 8
    assert devices[1]['busAddress'] == '0x97'
    assert devices[1]['ok'] is False
    assert sum(dev['ok'] for dev in devices) == 7
    assert all(dev['ok'] for dev in devices if dev['busAddress'] != '0x97')


def test_eight_cards_and_counts_are_present_in_web_assets(fixture):
    _, client, _ = fixture
    home = client.get('/')
    assert home.status_code == 200
    assert '8 台驱动器按相同光谱分为 4 组' in home.text
    assert '/ 8 台' in home.text
    js = client.get('/assets/app.js').text
    assert 'const SPECTRUM_GROUPS = [' in js
    pairs = [
        ('0x96', '0x9A', '660nm', '395nm'),
        ('0x97', '0x9B', '450nm', '530nm'),
        ('0x98', '0x9C', '630nm', '430nm'),
        ('0x99', '0x9D', '730nm', '全光谱'),
    ]
    for first, second, ch1, ch2 in pairs:
        assert f"first: '{first}', second: '{second}', ch1: '{ch1}', ch2: '{ch2}'" in js
    assert 'SPECTRUM_GROUPS.flatMap' in js
    assert 'makeSpectrumGroup' in js
    assert '与 ${config.partner} 光谱相同 · 本设备独立调光' in js
    assert 'const total = DEVICE_LIST.length' in js
    assert 'matched === total' in js
