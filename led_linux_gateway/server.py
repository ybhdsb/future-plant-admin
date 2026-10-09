#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""工控机 LED 网关：HTTP API -> 本机 RS485 Modbus RTU。

运行: python server.py
配置: LED_SERIAL_PORT, LED_API_KEY, LED_HTTP_HOST, LED_HTTP_PORT 等环境变量。
"""

import logging
import os
import secrets
from pathlib import Path
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Query
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, StrictInt

from light_modbus import (
    DEVICE_ADDRESSES, LEDCommunicationError, LEDSerialError,
    LightController, parse_address,
)

log = logging.getLogger('led_gateway')


class DeviceSet(BaseModel):
    busAddress: str
    ch1: StrictInt = Field(ge=0, le=255)
    ch2: StrictInt = Field(ge=0, le=255)


class BroadcastSet(BaseModel):
    ch1: StrictInt = Field(ge=0, le=255)
    ch2: StrictInt = Field(ge=0, le=255)


def validate_address(bus_address: str) -> int:
    try:
        return parse_address(bus_address)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


def guarded_call(callable_):
    try:
        return callable_()
    except LEDSerialError as exc:
        log.warning('串口不可用: %s', exc)
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except LEDCommunicationError as exc:
        log.warning('Modbus 通信失败: %s', exc)
        raise HTTPException(status_code=502, detail=str(exc)) from exc


def create_app(controller: Optional[LightController] = None,
               api_key: Optional[str] = None) -> FastAPI:
    """controller 可注入模拟设备，方便不接硬件测试。"""
    if controller is None:
        controller = LightController(
            port=os.getenv('LED_SERIAL_PORT', '/dev/ttyUSB0'),
            timeout=float(os.getenv('LED_SERIAL_TIMEOUT', '0.7')),
            write_timeout=float(os.getenv('LED_SERIAL_WRITE_TIMEOUT', '1.0')),
        )
    if api_key is None:
        api_key = os.getenv('LED_API_KEY', '')

    app = FastAPI(title='LED RS485 Gateway', version='1.2.0')
    app.state.controller = controller

    # 本机/局域网可访问的轻量网页，不改变文档第五部分的网关 API。
    # HTML/CSS/JS 均由网关本地提供，浏览器不会访问第三方服务。
    web_dir = Path(__file__).resolve().parent / 'web'
    app.mount('/assets', StaticFiles(directory=str(web_dir)), name='led-web-assets')

    @app.get('/', include_in_schema=False)
    def dashboard():
        return FileResponse(
            web_dir / 'index.html',
            media_type='text/html; charset=utf-8',
            headers={
                'Cache-Control': 'no-store',
                'X-Content-Type-Options': 'nosniff',
                'X-Frame-Options': 'DENY',
                'Referrer-Policy': 'no-referrer',
                'Content-Security-Policy': (
                    "default-src 'none'; script-src 'self'; style-src 'self'; "
                    "connect-src 'self'; img-src 'self' data:; "
                    "base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
                ),
            },
        )

    def verify_api_key(x_api_key: Optional[str] = Header(default=None)):
        if api_key and (not x_api_key or not secrets.compare_digest(x_api_key, api_key)):
            raise HTTPException(status_code=401, detail='X-API-Key 无效或缺失')

    auth = [Depends(verify_api_key)]

    @app.post('/api/v1/led/set', dependencies=auth)
    def set_device(payload: DeviceSet):
        addr = validate_address(payload.busAddress)
        guarded_call(lambda: controller.set_two_channels(addr, payload.ch1, payload.ch2))
        return {'ok': True, 'busAddress': f'0x{addr:02X}',
                'ch1': payload.ch1, 'ch2': payload.ch2}

    @app.get('/api/v1/led/read', dependencies=auth)
    def read_device(busAddress: str = Query(...)):
        addr = validate_address(busAddress)
        ch1, ch2 = guarded_call(lambda: controller.read_two_channels(addr))
        return {'busAddress': f'0x{addr:02X}', 'ch1': ch1, 'ch2': ch2}

    @app.post('/api/v1/led/broadcast/set', dependencies=auth)
    def broadcast_set(payload: BroadcastSet):
        guarded_call(lambda: controller.broadcast_set_two_channels(payload.ch1, payload.ch2))
        return {
            'ok': True, 'scope': 'BROADCAST',
            'ch1': payload.ch1, 'ch2': payload.ch2,
            'note': 'broadcast may have no per-device ack',
        }

    @app.get('/api/v1/led/read-all', dependencies=auth)
    def read_all():
        devices = []
        # 不广播读取，单台出错不妨碍下一台继续读取。
        for addr in DEVICE_ADDRESSES:
            record = {'busAddress': f'0x{addr:02X}'}
            try:
                ch1, ch2 = controller.read_two_channels(addr)
                record.update(ch1=ch1, ch2=ch2, ok=True)
            except LEDCommunicationError as exc:
                log.warning('设备 0x%02X 读取失败: %s', addr, exc)
                record.update(ch1=None, ch2=None, ok=False, error=str(exc))
            devices.append(record)
        return {'devices': devices}

    @app.get('/api/v1/led/health', dependencies=auth)
    def health():
        # 仅代表 HTTP 服务存活和串口是否已经打开，不代表每个驱动器在线。
        return {'ok': True, 'serialConnected': controller.is_open}

    return app


def main():
    import uvicorn
    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
    host = os.getenv('LED_HTTP_HOST', '127.0.0.1')
    port = int(os.getenv('LED_HTTP_PORT', '8090'))
    key = os.getenv('LED_API_KEY', '')
    if host not in ('127.0.0.1', '::1', 'localhost') and (
        not key or key == 'PLEASE_CHANGE_TO_A_LONG_RANDOM_SECRET'
    ):
        raise SystemExit('拒绝启动：对外监听必须配置真正的 LED_API_KEY；或将 LED_HTTP_HOST=127.0.0.1')
    app = create_app()
    uvicorn.run(app, host=host, port=port, workers=1)


if __name__ == '__main__':
    main()
