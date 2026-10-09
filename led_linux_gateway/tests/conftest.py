"""离线模拟串口，测试不依赖 RS485 实机或 pyserial 安装。"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
