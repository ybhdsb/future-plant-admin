# LED 补光网关 V1.2：8 台设备 / 16 通道升级指南

## 变更范围

在 **V1.1（已带网页）** 的基础上，新增设备 `0x96、0x97、0x98、0x99`，与原有 `0x9A、0x9B、0x9C、0x9D` 一起作为 8 台设备轮询。每台双通道，值为 0～255。

- `light_modbus.py`：更新地址白名单为 `0x96～0x9D`；底层 Modbus 功能码、寄存器、串口通信参数保持不变。
- `server.py`：接口保持原样，仅版本号更新；`read-all` 通过地址白名单自动读取全部 8 台。
- `web/app.js`：新增 4 台双通道卡片，设备汇总和广播核验由固定 4 台改为 8 台。
- `web/index.html`：设备数量、通道数量更新；资源版本标签避免浏览器误用旧脚本。
- `web/style.css`：沿用原有样式。
- 新增 4 台设备的**波长数据未提供**，网页统一标注“光谱待确认”，等待实际光谱清单后再修改。

> 说明：原 `docs/LED补光对接说明.md` 描述的是 **4 台设备的早期接口规格**，未修改历史文档。网关 HTTP 接口协议不变，但 `/read-all` 现在返回 **8** 条；如果外部植物平台有固定地址枚举、数据库记录或设备数量检查，平台端还需要同步增加 0x96～0x99 四台。

## 在 Linux 工控机升级（推荐增量 ZIP）

**1. 关闭旧服务并备份**

如果使用终端运行，先在运行 `python server.py` 的终端按 Ctrl+C。如果以 systemd 运行，则 `sudo systemctl stop led-gateway`。

```bash
cd ~/project
cp -a led_linux_gateway "led_linux_gateway_backup_$(date +%Y%m%d_%H%M%S)"
```

**2. 把 `led_gateway_upgrade_v1_2.zip` 上传到工控机的 `~/project/` 目录，再执行：**

```bash
cd ~/project
unzip -o led_gateway_upgrade_v1_2.zip -d led_linux_gateway
```

会覆盖 `light_modbus.py`、`server.py`、`web/app.js`、`web/index.html`、`web/style.css`，以及安装说明；不会更改实际密钥、串口设备或其他现有配置。新版本的 `server.py` 仍要求对外监听时设置真实 API Key。

**3. 启动（工控机 IP：192.168.123.103）**

```bash
cd ~/project/led_linux_gateway
LED_SERIAL_PORT=/dev/ttyUSB0 \
LED_HTTP_HOST=0.0.0.0 \
LED_HTTP_PORT=8090 \
LED_API_KEY='替换为你已有的真实密钥' \
python server.py
```

**4. 用本机浏览器访问：**

`http://192.168.123.103:8090/`

按 `Ctrl+F5` 强制刷新，输入网关 API Key。应出现 `0x96`～`0x9D` 共 **8 张设备卡片**。

**5. 验证（先读取，不改变实际光强）**

从工控机新开一个终端：

```bash
export LED_KEY='替换为你已有的真实密钥'
curl -sS -H "X-API-Key: $LED_KEY" http://127.0.0.1:8090/api/v1/led/read-all
```

期望返回 `devices` 数组共 8 条，包含 0x96、0x97、0x98、0x99、0x9A、0x9B、0x9C、0x9D；若某条 `ok:false`，说明该台通信尚未成功，请检查实际设备地址、接线和供电。

**6. 调光验证（现场允许时）**

先读取新设备（示例为 0x96）：

```bash
curl -sS -H "X-API-Key: $LED_KEY" 'http://127.0.0.1:8090/api/v1/led/read?busAddress=0x96'
```

确认原始数值后，在网页对 `0x96` 小幅调整并点击“应用设置”，自动回读验证；完成后恢复原始设置。切勿在新设备尚未确认时直接使用“全部全亮”广播按钮。

## 注意事项

- `0x00` 广播可能影响 **RS485 总线上所有支持广播的设备**，而不仅仅是网页当前列出的 8 台；回读只是核验这 8 台。广播前确认现场允许。
- 原来的每台两通道仍使用 Modbus `0x03` 读取、`0x10` 写两个保持寄存器（起始地址 0x0001），没有改变波特率（9600，8N1）。
- 本 ZIP 的测试使用模拟串口，不能替代你在真实新增设备上的连通性、端口冲突、光强效果测试。
- 如果想在工控机本机测试而不开启远程访问，可使用 `LED_HTTP_HOST=127.0.0.1`，不设置 `LED_API_KEY`；外网/局域网对外监听仍必须设置真实密钥。
