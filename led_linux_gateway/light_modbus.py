#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""源自 smart_light_controller(2).py 的 Modbus RTU 控制逻辑。

Linux 工控机 -> RS485 -> 0x96 ~ 0x9D（8 台双通道设备）。
"""

import threading
import time
from typing import Optional, Tuple

try:
    import serial
except ImportError:  # 方便开发环境在未安装 pyserial 时进行纯模拟测试
    serial = None

DEVICE_ADDRESSES = tuple(range(0x96, 0x9E))  # 0x96~0x99 新增；0x9A~0x9D 原有


class LEDCommunicationError(Exception):
    """设备通信、协议或超时异常。"""


class LEDSerialError(LEDCommunicationError):
    """本机串口打不开或收发异常。"""


class LEDProtocolError(LEDCommunicationError):
    """CRC、地址、功能码或数据内容不正确。"""


def modbus_crc(data: bytes) -> bytes:
    """Modbus CRC16，低字节在前。保留原项目算法。"""
    crc = 0xFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = ((crc >> 1) ^ 0xA001) if (crc & 1) else (crc >> 1)
    return bytes((crc & 0xFF, (crc >> 8) & 0xFF))


def build_frame(payload: bytes) -> bytes:
    return payload + modbus_crc(payload)


def parse_address(value: str) -> int:
    """只接受 0x96~0x9D 的两位十六进制地址字符串（大小写均可）。"""
    error = 'busAddress 必须是 0x96～0x9D 的设备地址（例如 0x96 或 0x9A）'
    if not isinstance(value, str) or len(value) != 4 or value[:2].lower() != '0x':
        raise ValueError(error)
    try:
        address = int(value[2:], 16)
    except ValueError as exc:
        raise ValueError(error) from exc
    if address not in DEVICE_ADDRESSES:
        raise ValueError(error)
    return address


def check_level(value: int) -> None:
    if type(value) is not int or not 0 <= value <= 255:
        raise ValueError('ch1/ch2 必须为 0~255 的整数')


class LightController:
    """独占一个串口，串口交易加锁，避免并发 HTTP 请求交错写读。"""

    def __init__(self, port: str, timeout: float = 0.7, write_timeout: float = 1.0):
        self.port_name = port
        self.timeout = timeout
        self.write_timeout = write_timeout
        self.ser = None
        self._lock = threading.RLock()
        self._last_finish = 0.0
        # 9600 8N1 下 3.5 字符间隔约 3.65 ms，略留余量
        self._inter_frame_gap = 0.005

    @property
    def is_open(self) -> bool:
        with self._lock:
            return self.ser is not None and bool(self.ser.is_open)

    def connect(self) -> None:
        with self._lock:
            if self.ser is not None and self.ser.is_open:
                return
            if serial is None:
                raise LEDSerialError('缺少 pyserial，请先安装 requirements.txt 中的依赖')
            try:
                self.ser = serial.Serial(
                    port=self.port_name,
                    baudrate=9600,
                    bytesize=serial.EIGHTBITS,
                    parity=serial.PARITY_NONE,
                    stopbits=serial.STOPBITS_ONE,
                    timeout=self.timeout,
                    write_timeout=self.write_timeout,
                    exclusive=True,  # Linux 独占，避免别的进程抢占串口
                )
            except (OSError, serial.SerialException) as exc:
                self.ser = None
                raise LEDSerialError(f'无法打开串口 {self.port_name}: {exc}') from exc

    def close(self) -> None:
        with self._lock:
            if self.ser is not None:
                try:
                    self.ser.close()
                finally:
                    self.ser = None

    def _read_exact(self, length: int) -> bytes:
        result = bytearray()
        while len(result) < length:
            chunk = self.ser.read(length - len(result))
            if not chunk:
                raise LEDCommunicationError(
                    f'RS485 等待响应超时：需要 {length} 字节，收到 {len(result)} 字节'
                )
            result.extend(chunk)
        return bytes(result)

    @staticmethod
    def _verify_crc(frame: bytes) -> None:
        if len(frame) < 5 or modbus_crc(frame[:-2]) != frame[-2:]:
            raise LEDProtocolError(f'Modbus CRC 校验失败，报文={frame.hex(" ").upper()}')

    def _exchange(self, payload: bytes, response: bool = True) -> bytes:
        """按响应帧结构逐段读取；广播不等待回包。调用方必须拿到 lock。"""
        self.connect()
        now = time.monotonic()
        delay = self._inter_frame_gap - (now - self._last_finish)
        if delay > 0:
            time.sleep(delay)
        frame = build_frame(payload)
        try:
            self.ser.reset_input_buffer()
            written = self.ser.write(frame)
            if written != len(frame):
                raise LEDSerialError(f'RS485 写入不完整：{written}/{len(frame)}')
            self.ser.flush()
            if not response:
                return b''

            header = self._read_exact(2)
            if header[0] != payload[0]:
                raise LEDProtocolError(
                    f'响应地址错误：期望 0x{payload[0]:02X}，实际 0x{header[0]:02X}'
                )
            func = header[1]
            if func == (payload[1] | 0x80):
                raw = header + self._read_exact(3)
                self._verify_crc(raw)
                raise LEDProtocolError(
                    f'Modbus 设备异常：地址 0x{payload[0]:02X}，'
                    f'功能码 0x{payload[1]:02X}，异常码 0x{raw[2]:02X}'
                )
            if func != payload[1]:
                raise LEDProtocolError(f'响应功能码不匹配：0x{func:02X}')

            if func == 0x03:
                byte_count = self._read_exact(1)[0]
                expected_bytes = int.from_bytes(payload[4:6], 'big') * 2
                if byte_count != expected_bytes:
                    raise LEDProtocolError(
                        f'响应寄存器字节数错误：期望 {expected_bytes}，实际 {byte_count}'
                    )
                raw = header + bytes((byte_count,)) + self._read_exact(byte_count + 2)
            elif func in (0x06, 0x10):
                raw = header + self._read_exact(6)
                if raw[2:6] != payload[2:6]:
                    raise LEDProtocolError('写入响应未正确回显寄存器地址/数量')
            else:
                raise LEDProtocolError(f'不支持的响应功能码 0x{func:02X}')
            self._verify_crc(raw)
            return raw
        except (OSError, serial.SerialException if serial is not None else OSError) as exc:
            self.close()
            raise LEDSerialError(f'串口通信失败：{exc}') from exc
        finally:
            self._last_finish = time.monotonic()

    def set_two_channels(self, addr: int, ch1: int, ch2: int) -> None:
        if addr not in DEVICE_ADDRESSES:
            raise ValueError('不支持的设备地址')
        check_level(ch1)
        check_level(ch2)
        # 保留原程序 0x10，从保持寄存器 0x0001 起一次写两个通道。
        payload = bytes((addr, 0x10, 0, 1, 0, 2, 4, 0, ch1, 0, ch2))
        with self._lock:
            self._exchange(payload)

    def read_two_channels(self, addr: int) -> Tuple[int, int]:
        if addr not in DEVICE_ADDRESSES:
            raise ValueError('不支持的设备地址')
        # 一次 0x03 读寄存器 0x0001 与 0x0002，而非原来的两次单通道请求。
        payload = bytes((addr, 0x03, 0, 1, 0, 2))
        with self._lock:
            frame = self._exchange(payload)
            ch1 = int.from_bytes(frame[3:5], 'big')
            ch2 = int.from_bytes(frame[5:7], 'big')
            if not 0 <= ch1 <= 255 or not 0 <= ch2 <= 255:
                raise LEDProtocolError(f'设备返回光强超出范围：CH1={ch1}, CH2={ch2}')
            return ch1, ch2

    def broadcast_set_two_channels(self, ch1: int, ch2: int) -> None:
        check_level(ch1)
        check_level(ch2)
        # 地址 0x00 广播，Modbus RTU 广播无逐台应答。
        payload = bytes((0, 0x10, 0, 1, 0, 2, 4, 0, ch1, 0, ch2))
        with self._lock:
            self._exchange(payload, response=False)
