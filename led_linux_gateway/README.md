# LED 补光网关 —— Linux 工控机 RS485 版

由用户提供的 `smart_light_controller(2).py` 迁移。工控机**直接通过 RS485** 连接 8 台 LED 驱动器，并向平台提供《LED 补光对接说明》第 5 部分约定的 HTTP API。无需桌面环境/PyQt5，不需要另外部署 LED 网关。

```text
网页 -> 平台后端 (例如 :8708) -> HTTP -> Linux 工控机 (:8090) -> 本机 RS485 -> 8 台 LED 驱动器
```

## 光谱配对（V1.4 现场最终修正）

| 配对地址 | CH1 | CH2 |
| --- | --- | --- |
| 0x96 / 0x9A | 660nm | 395nm |
| 0x97 / 0x9B | 450nm | 530nm |
| 0x98 / 0x9C | 630nm | 430nm |
| 0x99 / 0x9D | 730nm | 全光谱 |

**配对仅用于显示和管理，每个地址的两个通道仍独立调光；广播影响总线全部设备。** 该表是现场最新核对结果，覆盖本仓库 V1.3 和 `docs/LED补光对接说明.md` 中旧的光谱顺序；旧文档作为历史资料保留，不作为现行接线依据。

## 1. 接口

统一前缀 `http://<工控机IP>:8090/api/v1`。携带 `X-API-Key: <你的密钥>`。

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| POST | `/led/set` | `{"busAddress":"0x9A","ch1":200,"ch2":0}` | 0x10 一次设置两个通道，等待 RTU 确认帧 |
| GET | `/led/read?busAddress=0x9A` | URL 查询 | 0x03 一次读取 CH1/CH2 |
| POST | `/led/broadcast/set` | `{"ch1":255,"ch2":0}` | 0x00 广播写；**没有逐台确认** |
| GET | `/led/read-all` | 无 | 依次读取 0x96~0x9D 共 8 台；允许部分失败 |
| GET | `/led/health` | 无 | HTTP 进程存活、串口当前是否打开（并非设备在线检测） |

地址只接受 0x96~0x9D（大小写均可），亮度必须为 JSON 整数 0~255。请求体中不需要 `rackKey`，该字段由平台后端维护。成功输出字段与说明第五部分一致。

`read-all` 某设备失败时，该设备返回 `{"busAddress":"0x9D","ch1":null,"ch2":null,"ok":false,"error":"..."}`；**不虚构失败设备亮度**。平台应将该设备置离线，保留上次已验证的亮度；不要把 null 写入 level。通信错误返回 `{"detail":"..."}`，设备响应错误一般 502，串口打不开为 503，参数不合法为 422，无效密钥为 401。

## 2. 工控机安装（推荐 Python 3.9+，Debian/Ubuntu 示例）

先查 RS485 设备文件，优先使用稳定的 `/dev/serial/by-id/xxx` 路径：

```bash
ls -l /dev/serial/by-id/ 2>/dev/null
ls -l /dev/ttyUSB* /dev/ttyACM* /dev/ttyS* 2>/dev/null
```

若 USB 转 RS485 则一般是 `/dev/ttyUSB0`；板载串口可能是 `/dev/ttyS1`、`/dev/ttyAMA0` 等，**以实际枚举为准**。确认使用的是 RS485 通信口且接线 A/B、地线、终端电阻/偏置按硬件要求配置。当前程序适用自动收发方向的 USB-RS485/设备驱动；如果硬件需要 GPIO 手动切换 DE/RE，要额外适配。

将本目录复制到 `/opt/led-gateway` 后执行：

```bash
sudo apt update
sudo apt install -y python3 python3-venv python3-pip
cd /opt/led-gateway
sudo python3 -m venv .venv
sudo .venv/bin/python -m pip install -r requirements.txt
```

创建独立服务账号和环境配置（如果账号已存在，跳过 `useradd`）：

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin ledgateway
sudo usermod -aG dialout ledgateway
sudo mkdir -p /etc/led-gateway
sudo cp led-gateway.env.example /etc/led-gateway/led-gateway.env
sudo nano /etc/led-gateway/led-gateway.env
sudo chown root:ledgateway /etc/led-gateway/led-gateway.env
sudo chmod 640 /etc/led-gateway/led-gateway.env
sudo chmod -R a+rX /opt/led-gateway
```

编辑 `LED_SERIAL_PORT`、`LED_API_KEY`（必须替换示例占位密钥），根据是否需要其他主机访问调整 `LED_HTTP_HOST`。平台访问工控机需要 `0.0.0.0` 或指定网卡监听 IP；**对外监听时程序要求有 API Key**。在防火墙限制仅允许平台 IP 访问 TCP 8090，尽量只在隔离的工控局域网部署。HTTP 本身不加密，跨不可信网络请配 HTTPS 反向代理/VPN。

随后安装开机自启：

```bash
sudo cp led-gateway.service /etc/systemd/system/led-gateway.service
sudo systemctl daemon-reload
sudo systemctl enable --now led-gateway
sudo systemctl status led-gateway
sudo journalctl -u led-gateway -f
```

如果系统中串口设备组不是 `dialout`，改成当地使用的组（例如 `uucp`）并修改 systemd 服务文件；确保运行账号有串口读写权限。**切勿用 `chmod 777 /dev/ttyUSB0` 作为长期解决方案。**

只在本机临时调试时，也可使用下面命令（默认只监听 127.0.0.1）：

```bash
LED_SERIAL_PORT=/dev/ttyUSB0 LED_HTTP_HOST=127.0.0.1 .venv/bin/python server.py
```

## 3. 接口联调

将 `IP`、`KEY` 替换成实际配置：

```bash
IP=192.168.1.100
KEY='你的LED_API_KEY'

curl -sS -H "X-API-Key: $KEY" "http://$IP:8090/api/v1/led/health"
curl -sS -H "X-API-Key: $KEY" "http://$IP:8090/api/v1/led/read?busAddress=0x9A"
curl -sS -H "X-API-Key: $KEY" "http://$IP:8090/api/v1/led/read-all"
# 以下两项会真实修改光照！操作前确认现场允许。
curl -sS -X POST "http://$IP:8090/api/v1/led/set" \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"busAddress":"0x9A","ch1":200,"ch2":0}'
curl -sS -X POST "http://$IP:8090/api/v1/led/broadcast/set" \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"ch1":0,"ch2":0}'
```

推荐先测试 `health`、`read` 和 `read-all`，检查端口和接线，再尝试单台调光，**广播全灭等命令最后测试**。广播 HTTP 返回 `ok:true` 仅代表成功写入串口，必须再调用 `read-all` 确认实际状态。API 文档（开放本机/局域网访问后）可查看 `http://<工控机IP>:8090/docs`，注意此页面默认不做密钥校验，请仅限可信网段访问。

## 4. 与原代码的变化

- 复用原项目 CRC16、保持寄存器 `0x0001/0x0002`、功能码 0x03/0x10 及广播 `0x00`，串口固定 9600/8N1。
- 去掉 PyQt5 GUI，新增 FastAPI + Uvicorn 无界面服务，支持 systemd 后台启动。
- 修复原来的 `read(256)` 会等待串口超时才返回、无法精确区分 RTU 帧的问题：现在按长度读完整帧、CRC 和响应地址/功能码/写入确认严格校验。
- 同一串口加互斥锁，防止多个 HTTP 请求造成串口报文交错。
- `read-all` **逐台**采集，即使其中一台离线也会返回其他设备。
- 本期仅提供文档要求的 4 个核心接口和可选健康检查；组控制、EEPROM 保存没有开放到 HTTP。
- 由平台管理 `plant_led_state`、`plant_led_command_log` 两张表；本网关只执行 RS485 设备读写，**不连接平台数据库**。

## 5. 联调说明与尚需现场确认

1. 现有代码假设灯具寄存器为 0x0001/0x0002，`0x10` 可一次写 2 个寄存器；硬件协议具体实现仍需要现场核实。
2. 设置单设备获得了 Modbus 确认帧，但**不等于光强已由传感器实测**；若需要严格校验，平台后端在 set 后再 read。
3. Linux 串口权限、实际设备名、RS485 电气方式以及硬件现场通信均无法在开发环境直接验证，需要在工控机部署后测试。
4. 不能开多个服务进程操作同一串口。部署使用 Uvicorn 单 worker；如果原 PyQt 程序仍在运行，须退出以避免串口被占用。
5. 串口断线后下一次请求会尝试重新打开；本程序**不会对写操作进行自动重发**（避免不确定情况下重复控制）。

## 6. 离线单元测试

在有 `pytest` 和 `httpx` 的开发机上运行 `python -m pytest -q`，测试使用模拟串口，不会向真实灯具发送指令。实机通信仍需在现场单独验证。

## 7. 新增：浏览器图形控制台（不需要 PyQt5）

升级版在**原有网关服务**中内置了一个本地静态网页，继续复用第 1 节中的 5 个 API。
无需 Node、Nginx、独立前端服务或额外 Python 依赖。

网页地址：`http://127.0.0.1:8090/`（工控机本机浏览器）；FastAPI API 文档仍是 `/docs`。
如果已按第 2 节配置 API Key 及局域网监听，其他电脑可通过 `http://工控机IP:8090/` 访问网页。

### 使用已经联调成功的 Conda base 环境（工控机现场方案）

```bash
cd ~/project/led_linux_gateway
# 如果正在运行旧版，先在旧程序窗口按 Ctrl+C。不要同时运行两个进程抢占 RS485 串口。
LED_SERIAL_PORT=/dev/ttyUSB0 LED_HTTP_HOST=127.0.0.1 LED_HTTP_PORT=8090 python server.py
```

随后打开本机浏览器访问 `http://127.0.0.1:8090/`。
`/api/v1/led/...` 接口完全沿用旧版，无需更改平台后端。

网页提供：

1. 自动读取八台驱动器全部 16 路光强，显示最近读取状态；也可逐台回读。
2. 每个设备 CH1/CH2 都能用滑条和数字框分别预设 0～255，**点击“应用设置”后才下发**。网页使用原有 `/led/set` 一次写两通道，未改的通道取最近回读值，因此建议在每次手动调整前刷新状态；平台与网页并行改灯时也建议先回读。
3. 单设备写入成功后立即回读；只有确认回读值一致才报告设置验证成功。若读取失败，不把目标值当成真实当前值。
4. 支持广播自定义两通道光强、全部全亮、全部关闭。**发送广播前弹出确认框；发送后自动读取全部设备**，按逐台回读确认状态，不把广播的串口发送成功视为每台已执行。
5. 显示临时操作记录，仅在网页打开期间保留；不持久化、不替代平台的 `plant_led_command_log`。

### 局域网访问与 API Key

要让同一局域网电脑/手机访问，在**工控机**上使用实际随机密钥（请不要直接复制示例占位文本）：

```bash
LED_SERIAL_PORT=/dev/ttyUSB0 \
LED_HTTP_HOST=0.0.0.0 \
LED_HTTP_PORT=8090 \
LED_API_KEY='你生成的长随机密钥' \
python server.py
```

浏览器打开 `http://工控机IP:8090/`，页面会提示输入 API Key；用户手工输入后，前端在 HTTP 请求头传输 `X-API-Key`。**密钥仅保存在当前网页内存，不写入 localStorage、cookie 或 URL；网页刷新需重新输入。** 非 HTTPS 的局域网连接中密钥仍为明文传输，必须限制可信网络或启用 HTTPS/VPN；不要直接公开到互联网。请用防火墙只放行有权限的电脑访问 8090 端口。

### 更新已有项目时需要替换哪些文件？

只需更新 `server.py`，并在同级目录新增 `web/index.html`、`web/style.css`、`web/app.js`。已通过实机联调的 `light_modbus.py` 不需要修改，Python 依赖版本也不用增加。

```text
~/project/led_linux_gateway/
  server.py         <-- 更新
  light_modbus.py   <-- 原样保留
  web/              <-- 新增
    index.html
    style.css
    app.js
```

可运行离线测试 `python -m pytest -q`。这些测试使用模拟设备，不会给实际 LED 下发命令；部署到工控机以后请重新检查设备读取和小幅调光，确认正常后再在网页中使用广播。
