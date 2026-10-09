# LED 补光网页控制台 — 工控机升级操作

> 本版本在已联调成功的 `server.py` 上增加 `http://127.0.0.1:8090/` 可视化页面。保留原第五部分五个 HTTP 接口和 `light_modbus.py` 的串口通信实现。

## V1.4 现场确认的光谱配对（取代 V1.3）

网页按四组显示：0x96/0x9A（CH1=660nm, CH2=395nm），0x97/0x9B（CH1=450nm, CH2=530nm），0x98/0x9C（CH1=630nm, CH2=430nm），0x99/0x9D（CH1=730nm, CH2=全光谱）。同组仅光谱相同，各设备分别点击“应用设置”，不会自动互相联动。

## 1. 先备份已经能控制灯的工程

在你的 Linux 工控机终端执行：

```bash
cd ~/project
cp -a led_linux_gateway "led_linux_gateway_backup_$(date +%Y%m%d_%H%M%S)"
```

如果原程序还在运行，先回到启动 `python server.py` 的终端按 **Ctrl+C** 退出，避免新旧程序同时争用 `/dev/ttyUSB0`。若旧程序是 systemd 启动，则先使用 `sudo systemctl stop led-gateway`，后续按你已有服务配置重启。

## 2. 替换网页升级文件

从本次提供的 **完整 ZIP** 中找到以下 4 个文件/目录，复制到原目录，覆盖原 `server.py`：

```text
led_linux_gateway/server.py
led_linux_gateway/web/index.html
led_linux_gateway/web/style.css
led_linux_gateway/web/app.js
```

**不要删除已经在工控机上调通的 `light_modbus.py`、Conda 环境、密钥配置。** 本次只需替换 `server.py` 并新建 `web/`，无需重新安装额外依赖。

如果下载了“仅网页升级包”，可将其解压内容 `server.py`、`web/` 直接复制到 `~/project/led_linux_gateway`。

## 3. 使用你现有 Conda base 环境运行

```bash
cd ~/project/led_linux_gateway
LED_SERIAL_PORT=/dev/ttyUSB0 \
LED_HTTP_HOST=127.0.0.1 \
LED_HTTP_PORT=8090 \
python server.py
```

当看到 `Uvicorn running on http://127.0.0.1:8090` 时打开工控机浏览器：

**http://127.0.0.1:8090/**

页面会自动读取八台设备（0x96～0x9D）。若你为接口启用了 `LED_API_KEY`，在页面弹出的对话框中填写该密钥即可。打开 `/docs` 仍然可以使用原 Swagger 接口文档。

## 4. 如何控制灯

- **读取全部设备**：点击顶部“读取全部设备”，显示 0x96～0x9D 的 16 个光强；正常设备显示“通信正常”，读取失败则显示“读取失败”。
- **修改一台设备**：拖动 CH1 或 CH2 的滑块，或输入整数 0～255，**点击本设备“应用设置”**。网页仍调用 `/api/v1/led/set` 一次写入双通道；未修改的通道填的是最近一次回读值。因此如果外部平台可能同时改变灯光，请先刷新设备。
- **回读**：单台“回读”按钮从灯具读取实际寄存器值并覆盖未保存预设；光强写入后也会自动回读。
- **全部关闭 / 全部全亮**：在“全局广播控制”选择对应按钮并确认，程序立即广播，然后按 8 台设备逐台回读，只有回读一致时才显示“广播效果已确认”。
- **广播指定光强**：输入 CH1 / CH2 的值并点击“广播应用指定光强”，确认后统一下发。
- **操作记录**：右侧记录当前网页会话的操作/错误信息，关闭或刷新页面后会丢失；不会写数据库。

请在现场允许调光的情况下使用网页操作；广播全部关闭和全部全亮会影响总线上支持该广播的所有设备（当前列管 16 路光源）。

## 5. 让另一台电脑/手机访问工控机

`127.0.0.1` 只能在工控机本机访问。必须将监听地址修改为 `0.0.0.0`，并配置真实 `LED_API_KEY`：

```bash
cd ~/project/led_linux_gateway
LED_SERIAL_PORT=/dev/ttyUSB0 \
LED_HTTP_HOST=0.0.0.0 \
LED_HTTP_PORT=8090 \
LED_API_KEY='你的长随机密钥' \
python server.py
```

在同一可信局域网内访问 `http://工控机实际IP:8090/`，网页会要求输入密钥；密钥仅保存在当前网页内存中。不要把非 HTTPS 的服务直接暴露在公网上，建议防火墙只允许指定管理电脑或平台后端访问。

## 6. 排查

| 现象 | 处理 |
| --- | --- |
| 访问 `http://127.0.0.1:8090/` 提示无法连接 | 确认 `python server.py` 正在运行、8090 端口没有被另一个服务占用 |
| 页面打开但显示 API 连接失败 | F12 浏览器开发者工具检查请求，确认 API Key 是否输入正确 |
| 单个设备显示读取失败 | 看网关终端错误、RS485 A/B 接线、设备供电、地址 |
| 写入成功但回读不一致 | 不要认定设备已执行，检查接线、控制器协议以及是否有其他控制软件同时操作 |
| 广播后部分设备未确认 | 点击“读取全部设备”，逐台排查 `ok:false` 或回读不一致 |
| 网页没有更新但 API 能用 | 确认 `web/` 目录与新版 `server.py` 同级，浏览器强制刷新 Ctrl+F5 |
| 串口 Permission denied | `groups` 中确认当前用户有 `dialout` 权限，检查 `/dev/ttyUSB0` |

## 7. 对现有平台接口的影响

**没有变更。** 原有五个路由及请求格式不变：`/led/set`、`/led/read`、`/led/broadcast/set`、`/led/read-all`、`/led/health`。新增根路径 `/` 和 `/assets/...` 只用于现场人员的操作网页。网页不连接数据库，不修改平台业务代码。
