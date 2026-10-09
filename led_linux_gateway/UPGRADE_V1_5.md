# LED 补光控制台 V1.5：广播后延迟回读与失败重试

## 一、适用版本

本次是 **V1.4 → V1.5** 的网页广播稳定性修复。保持已确认的设备/光谱映射：

| 配对驱动器 | CH1 | CH2 |
| --- | --- | --- |
| 0x96 / 0x9A | 660nm | 395nm |
| 0x97 / 0x9B | 450nm | 530nm |
| 0x98 / 0x9C | 630nm | 430nm |
| 0x99 / 0x9D | 730nm | 全光谱 |

每台驱动器都可单独控制 CH1/CH2，不会因配对而联动；共 8 台、16 通道。

## 二、本次修复

已验证硬件广播操作：执行 `/api/v1/led/broadcast/set` 指定 CH1=30、CH2=30 后，等待 3 秒再调用 `/api/v1/led/read-all`，八台设备均返回 30/30 与 `ok:true`。

V1.4 页面在 POST 广播后立即调用 `/read-all`，可能在设备刚执行广播时出现 RS485 超时、响应不完整和帧错位，导致网页出现“广播回读未完全确认”。V1.5 将页面改为：

1. 用户确认后 **只发送一次** `POST /api/v1/led/broadcast/set`。
2. 成功收到 HTTP 响应后，等待 **3000 毫秒**。
3. 通过原有 `GET /api/v1/led/read-all` 读取所有 8 台设备。
4. 对首次未读取成功、读回数值不一致或缺失的设备，延迟 **1500 毫秒**后逐台调用 `GET /api/v1/led/read`，最多 **2 轮重试读取**。
5. 页面分别展示“广播已发送”和“最终确认结果”，列出异常设备地址和读回数值/错误原因。重试 **绝不会重发广播**。
6. 即便首次批量读取发生 HTTP 500，页面也会尝试逐台读取；但 HTTP 401 会直接中止回读并提示密钥问题。

仅修改：
- `web/app.js`
- `web/index.html`（资源版本 `v=1.5`，防止浏览器缓存旧脚本）

**没有修改**：
- `server.py`、`light_modbus.py`
- RS485 串口参数/Modbus RTU 帧
- 五个原有 API 的 URL、返回协议、密钥策略

## 三、工控机上升级（推荐增量包）

将 `led_gateway_broadcast_fix_v1_5.zip` 放在工控机 `~/project/` 下。

1. 在运行 `server.py` 的终端按 **Ctrl+C** 停止服务。
2. 执行：

```bash
cd ~/project
cp -a led_linux_gateway "led_linux_gateway_backup_$(date +%Y%m%d_%H%M%S)"
unzip -o led_gateway_broadcast_fix_v1_5.zip -d led_linux_gateway
```

3. 使用以前的有效密钥重新启动（此方式不会把密钥直接写入命令历史）：

```bash
cd ~/project/led_linux_gateway
read -rsp "请输入 LED API Key: " LED_API_KEY
echo
export LED_API_KEY
LED_SERIAL_PORT=/dev/ttyUSB0 \
LED_HTTP_HOST=0.0.0.0 \
LED_HTTP_PORT=8090 \
python server.py
```

4. 本机浏览器打开 `http://192.168.123.103:8090/`，按 **Ctrl+F5** 强制刷新；如已存在浏览器网页标签，建议也刷新。
5. 确认 Network/网页源引用的是 `/assets/app.js?v=1.5`，登录 API Key，点击“读取全部”。
6. 在现场允许时，选用相对低亮度（例如 `30/30`）做一次网页全局广播。观察八灯是否变化；等待约 3 秒后确认页面显示 `8/8 台回读确认成功`。如少于 8/8，查看操作日志中具体失败地址，再到工控机日志排查。

## 四、关于 401 与跨电脑访问

- `HTTP 401` 表示请求没有携带有效 API Key，服务器不会执行该请求。
- 此前日志中 `192.168.123.109` 的请求曾返回 401，而 `192.168.123.34` 的请求返回 200；这属于不同客户端的认证问题，与广播后读取延迟属于两个独立问题。
- 网页密钥仅保存在当前标签页的内存里。新的客户端或重载网页后可能需要重新输入。
- 请不要把密钥写到日志、截图或聊天里；如果已经公开，尽快轮换密钥。

## 五、验证方式和注意事项

开发环境离线测试：

```bash
python -m pytest -q tests/test_gateway.py
node --check web/app.js
node tests/test_broadcast_web.js
```

重要：本次仅依赖你此前提供的 **一次成功的延迟 3 秒回读实验**。这支持修改网页时序，但无法保证所有现场 RS485 干扰都被解决。若依然频繁发生 CRC/帧错位/超时，继续排查串口和 RS485 布线、设备响应时间；不要盲目自动重复写入命令。

如想回滚，可恢复升级前的备份目录，重新启动原 `server.py`。
