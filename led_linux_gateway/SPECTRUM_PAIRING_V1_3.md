> ⚠️ 历史 V1.3 说明（光谱顺序已过时）。请以 `SPECTRUM_MAPPING_V1_4.md` 的现场最新映射为准。

# LED 网关 V1.3：光谱配对与网页显示修正

## 现场已确认的映射

| 组别 | 前段地址 | 后段地址 | CH1 | CH2 |
| --- | --- | --- | --- | --- |
| 1 | 0x96 | 0x9A | 730nm | 全光谱 |
| 2 | 0x97 | 0x9B | 630nm | 430nm |
| 3 | 0x98 | 0x9C | 450nm | 530nm |
| 4 | 0x99 | 0x9D | 660nm | 395nm |

这里的“配对”只说明两台设备 CH1 光谱相同、CH2 光谱相同，并不表示两台设备互相联动。每个 Modbus 地址仍独立控制 CH1、CH2；灯具实体数量与 Modbus 地址数量不是同一个概念。

## 本次改动范围

- `web/app.js`：用四组光谱数据生成八台独立设备；修正 0x96～0x99 的 CH1/CH2 标签与颜色，并标出每台设备的配对地址。
- `web/index.html`：修改说明文案，静态资源缓存版本更新为 `v=1.3`。
- `web/style.css`：增加四组分区，每组并列两台驱动器卡片；窄屏自动改为纵向。
- **不修改** `server.py`、`light_modbus.py`、API Key 验证、通信协议、读写指令、设备地址白名单以及广播行为。

## 在现有 V1.2 工程上升级（推荐）

1. 工控机上对原项目先备份，然后在运行 server.py 的终端按 Ctrl+C 停止。
2. 下载 `led_gateway_spectrum_pairing_upgrade_v1_3.zip`，传到工控机的 `~/project/` 目录。
3. 进入 `~/project`，执行：

```bash
cp -a led_linux_gateway "led_linux_gateway_backup_$(date +%Y%m%d_%H%M%S)"
unzip -o led_gateway_spectrum_pairing_upgrade_v1_3.zip -d led_linux_gateway
```

4. 保持原有串口配置，在**同一个终端**安全输入 API Key 并启动：

```bash
cd ~/project/led_linux_gateway
read -rsp '请输入 LED API Key: ' LED_API_KEY; echo; export LED_API_KEY
LED_SERIAL_PORT=/dev/ttyUSB0 LED_HTTP_HOST=0.0.0.0 LED_HTTP_PORT=8090 python server.py
```

5. 在同一 WiFi 的电脑打开 `http://192.168.123.103:8090/`，输入 API Key；如仍看到旧页面，按 Ctrl+F5 强制刷新。
6. 检查四组配对名称是否正确，先点击“读取全部设备”，确认 8/8；分别小幅调节一组内的两台设备，验证它们互不影响。

广播仍然作用于总线上所有能接收广播的设备（不是只对某一个光谱组），网页会在广播后逐台回读。请确保操作前现场允许更改灯光。

## 完整工程

如需重新安装，可使用 `led_linux_gateway_web_console_v1_3.zip`。完整包顶层含 `led_linux_gateway/` 目录，不要误将其解压到已有的 `led_linux_gateway/` 里面造成二层嵌套。已运行正常的系统优先使用增量包。
