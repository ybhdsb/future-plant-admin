#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
智能灯具控制器 (双通道版)
协议依据：《智能灯具通讯协议精减版》

Modbus RTU 参数：
    波特率：9600   数据位：8   停止位：1   校验位：无

设备结构：
    4 个双通道智能驱动器：0x9A, 0x9B, 0x9C, 0x9D
    每个驱动器有 CH1、CH2 两个通道（光强 0~255）
"""

import sys
from typing import Optional

import serial
from serial.tools import list_ports
from PyQt5.QtCore import Qt, QThread, pyqtSignal
from PyQt5.QtGui import QFont
from PyQt5.QtWidgets import (
    QApplication, QComboBox, QGridLayout, QGroupBox, QHBoxLayout, QLabel,
    QLineEdit, QMainWindow, QMessageBox, QPushButton, QSlider, QSpinBox,
    QStatusBar, QTextEdit, QVBoxLayout, QWidget
)

# ===== 设备地址列表：4 个双通道驱动器 =====
DEVICE_ADDRESSES = [0x9A, 0x9B, 0x9C, 0x9D]


# =========================
# Modbus CRC16
# =========================

def modbus_crc(data: bytes) -> bytes:
    """返回 Modbus RTU CRC，低字节在前，高字节在后。"""
    crc = 0xFFFF
    for byte in data:
        crc ^= byte
        for _ in range(8):
            if crc & 0x0001:
                crc = (crc >> 1) ^ 0xA001
            else:
                crc >>= 1
    return bytes((crc & 0xFF, (crc >> 8) & 0xFF))


def build_frame(payload: bytes) -> bytes:
    return payload + modbus_crc(payload)


def hex_string(data: bytes) -> str:
    return " ".join(f"{b:02X}" for b in data)


# =========================
# 灯具通信类（多设备版）
# =========================

class LightController:
    """每个方法显式传入设备地址 addr，支持多设备控制。"""

    def __init__(self, port: str, timeout: float = 0.5):
        self.port_name = port
        self.timeout = timeout
        self.ser: Optional[serial.Serial] = None

    def connect(self):
        self.ser = serial.Serial(
            port=self.port_name,
            baudrate=9600,
            bytesize=serial.EIGHTBITS,
            parity=serial.PARITY_NONE,
            stopbits=serial.STOPBITS_ONE,
            timeout=self.timeout,
        )

    def close(self):
        if self.ser and self.ser.is_open:
            self.ser.close()

    @property
    def is_open(self) -> bool:
        return self.ser is not None and self.ser.is_open

    def _check_open(self):
        if not self.is_open:
            raise RuntimeError("串口尚未连接")

    def _send_and_receive(self, frame: bytes, expected_min_len: int = 0,
                          response: bool = True) -> bytes:
        self._check_open()
        self.ser.reset_input_buffer()
        self.ser.write(frame)

        if not response:
            return b""

        raw = self.ser.read(256)
        if len(raw) < 5:
            raise TimeoutError(
                f"未收到完整响应，实际收到 {len(raw)} 字节：{hex_string(raw)}"
            )

        body, crc = raw[:-2], raw[-2:]
        if modbus_crc(body) != crc:
            raise ValueError(f"CRC 校验失败，收到：{hex_string(raw)}")

        if expected_min_len and len(raw) < expected_min_len:
            raise ValueError(f"响应长度异常：{len(raw)} 字节")

        if len(raw) >= 2 and (raw[1] & 0x80):
            code = raw[2] if len(raw) > 2 else None
            raise RuntimeError(
                f"设备返回 Modbus 异常，功能码=0x{raw[1]:02X}，异常码={code}"
            )

        return raw

    # -------- 参数校验 --------
    @staticmethod
    def _check_channel(channel: int):
        if not 1 <= channel <= 2:
            raise ValueError("通道只能是 1~2")

    @staticmethod
    def _check_intensity(intensity: int):
        if not 0 <= intensity <= 255:
            raise ValueError("光强必须在 0~255")

    # -------- 单通道设置 (功能码 0x06) --------
    def set_channel(self, addr: int, channel: int, intensity: int) -> bytes:
        self._check_channel(channel)
        self._check_intensity(intensity)
        payload = bytes([addr, 0x06, 0x00, channel, 0x00, intensity])
        return self._send_and_receive(build_frame(payload), expected_min_len=8)

    # -------- 读单通道 (功能码 0x03) --------
    def read_channel(self, addr: int, channel: int) -> int:
        self._check_channel(channel)
        payload = bytes([addr, 0x03, 0x00, channel, 0x00, 0x01])
        response = self._send_and_receive(build_frame(payload), expected_min_len=7)
        if response[0] != addr or response[1] != 0x03:
            raise ValueError(f"响应头错误：{hex_string(response)}")
        if response[2] != 0x02:
            raise ValueError(f"响应字节数错误：{hex_string(response)}")
        return response[4]

    # -------- 一次写两通道 (功能码 0x10) --------
    def set_two_channels(self, addr: int, v1: int, v2: int) -> bytes:
        self._check_intensity(v1)
        self._check_intensity(v2)
        payload = bytes([
            addr, 0x10,
            0x00, 0x01,     # 起始寄存器
            0x00, 0x02,     # 写 2 个寄存器
            0x04,           # 4 字节数据
            0x00, v1,
            0x00, v2,
        ])
        return self._send_and_receive(build_frame(payload), expected_min_len=8)

    # -------- 保存到 EEPROM --------
    def save_to_eeprom(self, addr: int) -> bytes:
        payload = bytes([addr, 0x06, 0x00, 0x10, 0x53, 0x65])
        return self._send_and_receive(build_frame(payload), expected_min_len=8)

    # -------- 广播操作 --------
    def broadcast_set_channel(self, channel: int, intensity: int):
        self._check_channel(channel)
        self._check_intensity(intensity)
        payload = bytes([0x00, 0x06, 0x00, channel, 0x00, intensity])
        self._send_and_receive(build_frame(payload), response=False)

    def broadcast_set_two_channels(self, v1: int, v2: int):
        self._check_intensity(v1)
        self._check_intensity(v2)
        payload = bytes([
            0x00, 0x10,
            0x00, 0x01,
            0x00, 0x02,
            0x04,
            0x00, v1,
            0x00, v2,
        ])
        self._send_and_receive(build_frame(payload), response=False)

    def broadcast_save(self):
        payload = bytes([0x00, 0x06, 0x00, 0x10, 0x53, 0x65])
        self._send_and_receive(build_frame(payload), response=False)

    def send_broadcast(self, frame: bytes):
        self._send_and_receive(frame, response=False)

    # -------- 组控制 --------
    @staticmethod
    def build_group_match(group_bytes: bytes) -> bytes:
        if len(group_bytes) != 4:
            raise ValueError("组号必须是 4 字节")
        payload = bytes([0x00, 0x10, 0x00, 0x09, 0x00, 0x02, 0x04]) + group_bytes
        return build_frame(payload)

    def match_group(self, group_hex: str):
        group_hex = group_hex.strip().replace(" ", "")
        if len(group_hex) != 8:
            raise ValueError("组号必须输入 8 个十六进制字符，例如 11223344")
        try:
            group_bytes = bytes.fromhex(group_hex)
        except ValueError as exc:
            raise ValueError("组号不是合法十六进制") from exc
        self._send_and_receive(self.build_group_match(group_bytes), response=False)

    def build_group_set_one(self, channel: int, intensity: int) -> bytes:
        self._check_channel(channel)
        self._check_intensity(intensity)
        payload = bytes([
            0x00, 0x10,
            0x00, 0x0B,
            0x00, 0x02,
            0x04,
            0x00, channel,
            0x00, intensity,
        ])
        return build_frame(payload)


# =========================
# 后台线程
# =========================

class Worker(QThread):
    success = pyqtSignal(object, str)
    error = pyqtSignal(str)

    def __init__(self, task, description=""):
        super().__init__()
        self.task = task
        self.description = description

    def run(self):
        try:
            result = self.task()
            self.success.emit(result, self.description)
        except Exception as e:
            self.error.emit(f"{type(e).__name__}: {e}")


# =========================
# 主界面
# =========================

class MainWindow(QMainWindow):

    def __init__(self):
        super().__init__()
        self.controller: Optional[LightController] = None
        self.worker: Optional[Worker] = None
        self.device_widgets = []       # 每个设备一行，保存控件引用

        self.setWindowTitle("智能灯具控制器 (双通道版) - Modbus RTU")
        self.resize(1180, 860)

        self.init_ui()
        self.refresh_ports()

    # ============ 界面 ============
    def init_ui(self):
        central = QWidget()
        self.setCentralWidget(central)
        main_layout = QVBoxLayout(central)

        # ---- 串口设置 ----
        serial_group = QGroupBox("串口设置")
        sg = QGridLayout(serial_group)
        sg.addWidget(QLabel("串口："), 0, 0)
        self.port_combo = QComboBox()
        self.port_combo.setMinimumWidth(230)
        sg.addWidget(self.port_combo, 0, 1)

        self.refresh_btn = QPushButton("刷新串口")
        self.refresh_btn.clicked.connect(self.refresh_ports)
        sg.addWidget(self.refresh_btn, 0, 2)

        sg.addWidget(QLabel("波特率："), 0, 3)
        sg.addWidget(QLabel("9600"), 0, 4)
        sg.addWidget(QLabel("格式："), 0, 5)
        sg.addWidget(QLabel("8 / N / 1"), 0, 6)

        self.connect_btn = QPushButton("连接串口")
        self.connect_btn.clicked.connect(self.toggle_connection)
        self.connect_btn.setMinimumHeight(32)
        sg.addWidget(self.connect_btn, 0, 7)
        main_layout.addWidget(serial_group)

        # ---- 多设备控制表 ----
        device_group = QGroupBox(
            f"多设备控制 ({len(DEVICE_ADDRESSES)} 个双通道驱动器)"
        )
        dg = QGridLayout(device_group)
        dg.setHorizontalSpacing(8)
        dg.setVerticalSpacing(6)

        headers = ["设备", "CH1 光强", "CH1值", "CH1设置",
                   "CH2 光强", "CH2值", "CH2设置", "整机操作"]
        for col, h in enumerate(headers):
            lbl = QLabel(h)
            lbl.setStyleSheet("font-weight: bold;")
            dg.addWidget(lbl, 0, col)

        for row_idx, addr in enumerate(DEVICE_ADDRESSES, start=1):
            # 设备地址
            addr_label = QLabel(f"0x{addr:02X}")
            addr_label.setStyleSheet(
                "font-weight: bold; color: #1976D2; font-size: 14px;")
            addr_label.setMinimumWidth(60)
            dg.addWidget(addr_label, row_idx, 0)

            # CH1
            slider1 = QSlider(Qt.Horizontal); slider1.setRange(0, 255)
            slider1.setMinimumWidth(160)
            spin1 = QSpinBox(); spin1.setRange(0, 255); spin1.setMinimumWidth(70)
            set1 = QPushButton("设置CH1"); set1.setMaximumWidth(85)

            # CH2
            slider2 = QSlider(Qt.Horizontal); slider2.setRange(0, 255)
            slider2.setMinimumWidth(160)
            spin2 = QSpinBox(); spin2.setRange(0, 255); spin2.setMinimumWidth(70)
            set2 = QPushButton("设置CH2"); set2.setMaximumWidth(85)

            # 整机按钮
            read_btn = QPushButton("读取"); read_btn.setMaximumWidth(55)
            save_btn = QPushButton("保存"); save_btn.setMaximumWidth(55)
            on_btn = QPushButton("全亮"); on_btn.setMaximumWidth(55)
            off_btn = QPushButton("全灭"); off_btn.setMaximumWidth(55)

            # 双向同步
            slider1.valueChanged.connect(lambda v, sp=spin1: self._sync(sp, v))
            spin1.valueChanged.connect(lambda v, sl=slider1: self._sync(sl, v))
            slider2.valueChanged.connect(lambda v, sp=spin2: self._sync(sp, v))
            spin2.valueChanged.connect(lambda v, sl=slider2: self._sync(sl, v))

            # 按钮
            set1.clicked.connect(
                lambda _, a=addr, s=spin1: self.set_single(a, 1, s.value()))
            set2.clicked.connect(
                lambda _, a=addr, s=spin2: self.set_single(a, 2, s.value()))
            read_btn.clicked.connect(
                lambda _, a=addr: self.read_device(a))
            save_btn.clicked.connect(
                lambda _, a=addr: self.save_device(a))
            on_btn.clicked.connect(
                lambda _, a=addr, w=self.device_widgets: None)  # 占位，稍后重连
            off_btn.clicked.connect(
                lambda _, a=addr, w=self.device_widgets: None)

            dg.addWidget(slider1, row_idx, 1)
            dg.addWidget(spin1, row_idx, 2)
            dg.addWidget(set1, row_idx, 3)
            dg.addWidget(slider2, row_idx, 4)
            dg.addWidget(spin2, row_idx, 5)
            dg.addWidget(set2, row_idx, 6)

            action_box = QHBoxLayout()
            action_box.setSpacing(3)
            action_box.addWidget(read_btn)
            action_box.addWidget(save_btn)
            action_box.addWidget(on_btn)
            action_box.addWidget(off_btn)
            dg.addLayout(action_box, row_idx, 7)

            widget_dict = {
                "addr": addr,
                "slider1": slider1, "spin1": spin1, "set1": set1,
                "slider2": slider2, "spin2": spin2, "set2": set2,
                "read_btn": read_btn, "save_btn": save_btn,
                "on_btn": on_btn, "off_btn": off_btn,
            }
            self.device_widgets.append(widget_dict)

            # 现在可以连接"全亮/全灭"了（拿到自身 widget 引用）
            on_btn.clicked.disconnect()
            off_btn.clicked.disconnect()
            on_btn.clicked.connect(
                lambda _, a=addr, w=widget_dict: self.set_device_all(a, 255, w))
            off_btn.clicked.connect(
                lambda _, a=addr, w=widget_dict: self.set_device_all(a, 0, w))

        main_layout.addWidget(device_group)

        # ---- 全局快捷 ----
        quick_group = QGroupBox("全局快捷操作 (广播, 所有设备)")
        qg = QHBoxLayout(quick_group)

        b_all_ch1_on = QPushButton("所有CH1亮")
        b_all_ch1_off = QPushButton("所有CH1灭")
        b_all_ch2_on = QPushButton("所有CH2亮")
        b_all_ch2_off = QPushButton("所有CH2灭")
        b_all_on = QPushButton("全部亮")
        b_all_off = QPushButton("全部灭")
        b_save_all = QPushButton("广播保存")

        b_all_ch1_on.clicked.connect(lambda: self.broadcast_set(1, 255))
        b_all_ch1_off.clicked.connect(lambda: self.broadcast_set(1, 0))
        b_all_ch2_on.clicked.connect(lambda: self.broadcast_set(2, 255))
        b_all_ch2_off.clicked.connect(lambda: self.broadcast_set(2, 0))
        b_all_on.clicked.connect(lambda: self.broadcast_set_all(255))
        b_all_off.clicked.connect(lambda: self.broadcast_set_all(0))
        b_save_all.clicked.connect(self.broadcast_save)

        for b in (b_all_ch1_on, b_all_ch1_off, b_all_ch2_on, b_all_ch2_off,
                  b_all_on, b_all_off, b_save_all):
            qg.addWidget(b)

        main_layout.addWidget(quick_group)

        # ---- 组控制 ----
        group_group = QGroupBox("组控制（协议支持）")
        gg = QGridLayout(group_group)
        gg.addWidget(QLabel("组号(HEX)："), 0, 0)
        self.group_edit = QLineEdit("11223344")
        self.group_edit.setMaximumWidth(120)
        gg.addWidget(self.group_edit, 0, 1)

        self.match_group_btn = QPushButton("广播匹配组")
        self.match_group_btn.clicked.connect(self.match_group)
        gg.addWidget(self.match_group_btn, 0, 2)

        gg.addWidget(QLabel("通道："), 0, 3)
        self.group_ch = QSpinBox(); self.group_ch.setRange(1, 2)
        gg.addWidget(self.group_ch, 0, 4)

        gg.addWidget(QLabel("光强："), 0, 5)
        self.group_val = QSpinBox(); self.group_val.setRange(0, 255)
        gg.addWidget(self.group_val, 0, 6)

        self.group_set_btn = QPushButton("广播设置组内通道")
        self.group_set_btn.clicked.connect(self.set_group_channel)
        gg.addWidget(self.group_set_btn, 0, 7)

        main_layout.addWidget(group_group)

        # ---- 日志 ----
        log_group = QGroupBox("通信日志")
        lg = QVBoxLayout(log_group)
        self.log_text = QTextEdit()
        self.log_text.setReadOnly(True)
        self.log_text.setFont(QFont("Consolas", 10))
        lg.addWidget(self.log_text)
        clear_btn = QPushButton("清空日志")
        clear_btn.clicked.connect(self.log_text.clear)
        lg.addWidget(clear_btn)
        main_layout.addWidget(log_group, 1)

        # ---- 状态栏 ----
        self.status = QStatusBar()
        self.setStatusBar(self.status)
        self.status.showMessage("未连接")

        self.set_controls_enabled(False)

    # ============ 辅助 ============
    def _sync(self, target, value):
        if target.value() != value:
            target.blockSignals(True)
            target.setValue(value)
            target.blockSignals(False)

    def log(self, text: str):
        self.log_text.append(text)

    def set_controls_enabled(self, enabled: bool):
        for w in self.device_widgets:
            for key in ("slider1", "spin1", "set1",
                        "slider2", "spin2", "set2",
                        "read_btn", "save_btn", "on_btn", "off_btn"):
                w[key].setEnabled(enabled)
        for w in (self.match_group_btn, self.group_set_btn,
                  self.group_edit, self.group_ch, self.group_val):
            w.setEnabled(enabled)

    def ensure_controller(self):
        if self.controller is None or not self.controller.is_open:
            raise RuntimeError("请先连接串口")

    def start_task(self, task, description=""):
        if self.worker and self.worker.isRunning():
            QMessageBox.warning(self, "提示",
                                "上一条命令仍在执行，请稍后再试。")
            return
        self.worker = Worker(task, description)
        self.worker.success.connect(self.task_success)
        self.worker.error.connect(self.task_error)
        self.worker.finished.connect(self.task_finished)
        self.worker.start()

    def task_success(self, result, description):
        # 单个设备读取返回: (addr, v1, v2)
        if isinstance(result, tuple) and len(result) == 3:
            addr, v1, v2 = result
            for w in self.device_widgets:
                if w["addr"] == addr:
                    for sl, sp, v in ((w["slider1"], w["spin1"], v1),
                                      (w["slider2"], w["spin2"], v2)):
                        sl.blockSignals(True); sp.blockSignals(True)
                        sl.setValue(v); sp.setValue(v)
                        sl.blockSignals(False); sp.blockSignals(False)
                    break
            self.log(f"[OK] 0x{addr:02X}: CH1={v1}, CH2={v2}")
        elif isinstance(result, bytes) and result:
            self.log(f"[RECV] {hex_string(result)}")

        if description:
            self.log(f"[OK] {description}")
            self.status.showMessage(description)

    def task_error(self, message):
        self.log(f"[ERROR] {message}")
        self.status.showMessage("操作失败")
        QMessageBox.critical(self, "通信错误", message)

    def task_finished(self):
        if self.worker:
            self.worker.deleteLater()
            self.worker = None

    # ============ 串口 ============
    def refresh_ports(self):
        current = self.port_combo.currentData()
        self.port_combo.clear()
        ports = list(list_ports.comports())
        for p in ports:
            self.port_combo.addItem(f"{p.device} - {p.description}", p.device)
        if not ports:
            self.port_combo.addItem("未发现串口", None)
        if current:
            for i in range(self.port_combo.count()):
                if self.port_combo.itemData(i) == current:
                    self.port_combo.setCurrentIndex(i)
                    break

    def toggle_connection(self):
        if self.controller and self.controller.is_open:
            self.controller.close()
            self.controller = None
            self.connect_btn.setText("连接串口")
            self.set_controls_enabled(False)
            self.status.showMessage("未连接")
            self.log("[INFO] 串口已断开")
            return

        port = self.port_combo.currentData()
        if not port:
            QMessageBox.warning(self, "提示", "没有可用串口")
            return

        try:
            self.controller = LightController(port)
            self.controller.connect()
            self.connect_btn.setText("断开串口")
            self.set_controls_enabled(True)
            self.status.showMessage(f"已连接 {port}")
            self.log(f"[INFO] 已连接 {port}, 9600 8N1")
        except Exception as e:
            self.controller = None
            QMessageBox.critical(self, "连接失败", str(e))

    # ============ 单设备操作 ============
    def set_single(self, addr, channel, value):
        try:
            self.ensure_controller()
        except Exception as e:
            QMessageBox.warning(self, "提示", str(e))
            return

        def do():
            return self.controller.set_channel(addr, channel, value)

        payload = bytes([addr, 0x06, 0x00, channel, 0x00, value])
        self.log(f"[SEND] 0x{addr:02X} CH{channel}={value}  →  "
                 f"{hex_string(build_frame(payload))}")
        self.start_task(do, f"0x{addr:02X} CH{channel} 设置为 {value}")

    def read_device(self, addr):
        try:
            self.ensure_controller()
        except Exception as e:
            QMessageBox.warning(self, "提示", str(e))
            return

        def do():
            v1 = self.controller.read_channel(addr, 1)
            v2 = self.controller.read_channel(addr, 2)
            return (addr, v1, v2)

        self.start_task(do, f"读取 0x{addr:02X}")

    def save_device(self, addr):
        try:
            self.ensure_controller()
        except Exception as e:
            QMessageBox.warning(self, "提示", str(e))
            return

        def do():
            return self.controller.save_to_eeprom(addr)

        payload = bytes([addr, 0x06, 0x00, 0x10, 0x53, 0x65])
        self.log(f"[SEND] 0x{addr:02X} 保存  →  "
                 f"{hex_string(build_frame(payload))}")
        self.start_task(do, f"0x{addr:02X} 已保存到 EEPROM")

    def set_device_all(self, addr, value, widget_dict):
        """该设备两个通道一起设置为 value"""
        try:
            self.ensure_controller()
        except Exception as e:
            QMessageBox.warning(self, "提示", str(e))
            return

        def do():
            return self.controller.set_two_channels(addr, value, value)

        payload = bytes([
            addr, 0x10, 0x00, 0x01, 0x00, 0x02, 0x04,
            0x00, value, 0x00, value,
        ])
        self.log(f"[SEND] 0x{addr:02X} CH1+CH2={value}  →  "
                 f"{hex_string(build_frame(payload))}")
        self.start_task(do, f"0x{addr:02X} CH1+CH2 设置为 {value}")

        # 同步滑条
        for sl, sp in ((widget_dict["slider1"], widget_dict["spin1"]),
                       (widget_dict["slider2"], widget_dict["spin2"])):
            sl.blockSignals(True); sp.blockSignals(True)
            sl.setValue(value); sp.setValue(value)
            sl.blockSignals(False); sp.blockSignals(False)

    # ============ 全局广播 ============
    def broadcast_set(self, channel, intensity):
        try:
            self.ensure_controller()
        except Exception as e:
            QMessageBox.warning(self, "提示", str(e))
            return

        def do():
            self.controller.broadcast_set_channel(channel, intensity)
            return b""

        payload = bytes([0x00, 0x06, 0x00, channel, 0x00, intensity])
        self.log(f"[SEND] 广播 CH{channel}={intensity}  →  "
                 f"{hex_string(build_frame(payload))}")
        self.start_task(do, f"广播: 所有设备 CH{channel} = {intensity}")

        # 同步对应滑条
        for w in self.device_widgets:
            sl = w[f"slider{channel}"]
            sp = w[f"spin{channel}"]
            sl.blockSignals(True); sp.blockSignals(True)
            sl.setValue(intensity); sp.setValue(intensity)
            sl.blockSignals(False); sp.blockSignals(False)

    def broadcast_set_all(self, value):
        try:
            self.ensure_controller()
        except Exception as e:
            QMessageBox.warning(self, "提示", str(e))
            return

        def do():
            self.controller.broadcast_set_two_channels(value, value)
            return b""

        payload = bytes([
            0x00, 0x10, 0x00, 0x01, 0x00, 0x02, 0x04,
            0x00, value, 0x00, value,
        ])
        self.log(f"[SEND] 广播 全部={value}  →  "
                 f"{hex_string(build_frame(payload))}")
        self.start_task(do, f"广播: 所有设备 CH1+CH2 = {value}")

        # 同步所有滑条
        for w in self.device_widgets:
            for sl, sp in ((w["slider1"], w["spin1"]),
                           (w["slider2"], w["spin2"])):
                sl.blockSignals(True); sp.blockSignals(True)
                sl.setValue(value); sp.setValue(value)
                sl.blockSignals(False); sp.blockSignals(False)

    def broadcast_save(self):
        try:
            self.ensure_controller()
        except Exception as e:
            QMessageBox.warning(self, "提示", str(e))
            return

        def do():
            self.controller.broadcast_save()
            return b""

        payload = bytes([0x00, 0x06, 0x00, 0x10, 0x53, 0x65])
        self.log(f"[SEND] 广播保存  →  {hex_string(build_frame(payload))}")
        self.start_task(do, "广播: 所有设备保存到 EEPROM")

    # ============ 组控制 ============
    def match_group(self):
        try:
            self.ensure_controller()
        except Exception as e:
            QMessageBox.warning(self, "提示", str(e))
            return

        group_hex = self.group_edit.text().strip().replace(" ", "")
        try:
            group_bytes = bytes.fromhex(group_hex)
            if len(group_bytes) != 4:
                raise ValueError
        except Exception:
            QMessageBox.warning(self, "提示", "组号必须是 8 位十六进制")
            return

        def do():
            self.controller.match_group(group_hex)
            return b""

        frame = LightController.build_group_match(group_bytes)
        self.log(f"[SEND] 广播匹配组 {group_hex}  →  {hex_string(frame)}")
        self.start_task(do, f"广播匹配组 {group_hex}")

    def set_group_channel(self):
        try:
            self.ensure_controller()
        except Exception as e:
            QMessageBox.warning(self, "提示", str(e))
            return

        ch = self.group_ch.value()
        val = self.group_val.value()

        def do():
            frame = self.controller.build_group_set_one(ch, val)
            self.controller.send_broadcast(frame)
            return b""

        frame = self.controller.build_group_set_one(ch, val)
        self.log(f"[SEND] 广播 组内 CH{ch}={val}  →  {hex_string(frame)}")
        self.start_task(do, f"广播: 组内 CH{ch} = {val}")

    # ============ 关闭 ============
    def closeEvent(self, event):
        if self.controller:
            self.controller.close()
        event.accept()


def main():
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()