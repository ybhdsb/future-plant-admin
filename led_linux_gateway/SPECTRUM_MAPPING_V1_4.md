# LED 网关 V1.4：最终现场确认的光谱映射

## 正确对应关系（覆盖 V1.3 顺序）

| 光谱组 | 设备地址 1 | 设备地址 2 | CH1 | CH2 |
| --- | --- | --- | --- | --- |
| 第 1 组 | `0x96` | `0x9A` | **660nm** | **395nm** |
| 第 2 组 | `0x97` | `0x9B` | **450nm** | **530nm** |
| 第 3 组 | `0x98` | `0x9C` | **630nm** | **430nm** |
| 第 4 组 | `0x99` | `0x9D` | **730nm** | **全光谱** |

> 两台配对驱动器的 CH1 光谱相同、CH2 光谱相同，但不自动联动。每台设备的 CH1/CH2 均独立调光。这里的光谱仅用于网页标识，不改变 Modbus 实际控制地址。

## 修改范围

- `web/app.js`：四组光谱标签与颜色按最新现场数据修正。
- `web/index.html`：静态资源缓存版本更新为 `v=1.4`，避免浏览器加载 V1.3 缓存。
- 增量升级不触及后端通信部分：`server.py`、`light_modbus.py`、密钥验证、广播与读写 API 均保留。
- 本完整包的 README、网页操作手册与自动化测试已同步；旧历史文件没有直接改写其原始光谱表，只添加过时提示。

## 在工控机安装（建议用增量包）

先停止正在运行的 `server.py`（Ctrl+C），然后：

```bash
cd ~/project
cp -a led_linux_gateway "led_linux_gateway_backup_$(date +%Y%m%d_%H%M%S)"
unzip -o led_gateway_spectrum_fix_v1_4.zip -d led_linux_gateway
```

这个增量包只含 `web/app.js`、`web/index.html` 和本说明文件。**不需要重新安装 Python 依赖，也不需要更改 API Key。**

使用原来的安全密钥启动方式（不建议在 shell 命令中明文粘贴密钥）：

```bash
cd ~/project/led_linux_gateway
read -rsp '请输入 LED API Key: ' LED_API_KEY; echo; export LED_API_KEY
LED_SERIAL_PORT=/dev/ttyUSB0 LED_HTTP_HOST=0.0.0.0 LED_HTTP_PORT=8090 python server.py
```

在同 WiFi 的本机打开 `http://192.168.123.103:8090/`，按 **Ctrl+F5** 强制刷新并检查四组光谱顺序，然后点击“读取全部设备”确认 8/8。若后端已由 systemd 管理，应重启现有服务，而非再同时手工启动第二个进程。

## 注意

- 修改只是网页显示映射，不会改变物理光源的波长。
- 广播操作仍然影响总线上支持广播的全部灯，操作前应确认现场允许改变光强。
- 原有 `docs/LED补光对接说明.md` 及 V1.3 配对说明存在旧光谱信息，供历史追踪；后续管理平台的设备光谱数据库也需要据此同步修正。
