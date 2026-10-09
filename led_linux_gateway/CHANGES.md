# 本次迁移记录

源文件：`legacy/smart_light_controller_original.py`（用户提供的 `smart_light_controller(2).py`，原样备份）

| 旧程序 | 新程序 |
| --- | --- |
| Windows/Linux 桌面 PyQt5 串口操作界面 | `server.py` 无界面 HTTP 服务，适合 Linux 工控机开机自启 |
| `LightController.set_two_channels` | `light_modbus.LightController.set_two_channels`；沿用 0x10 功能码/寄存器 0x0001、0x0002 |
| `read_channel` 分两次查询 | `read_two_channels` 单次 0x03 查询两个保持寄存器 |
| `broadcast_set_two_channels` | 保留功能，0x00 广播无回包 |
| `ser.read(256)` | 精确读取 RTU 响应长度、异常帧，并验证 CRC/地址/功能码/写入回显 |
| PyQt5 `Worker` 后台线程 | FastAPI 处理 HTTP；线程锁串行化单端口 Modbus 请求 |
| GUI 通信日志 | 标准 Python 日志由 systemd journal 管理 |
| 手工选择串口 | `LED_SERIAL_PORT` 环境变量；建议持久 `/dev/serial/by-id/...` |

HTTP API 定义遵循 `docs/LED补光对接说明.md` 的第 5 节，非平台 API 第 4 节。本项目不处理数据库和前端。对实际 Modbus 寄存器/通信方式的使用依据是**用户提交的原代码**，并非经过硬件现场验证。

## 网页控制台升级（V1.1）

- 新增 `GET /` 的浏览器控制台及 `web/` 下 HTML、CSS、JavaScript 静态资源（完全离线、无需外部 CDN）。
- 支持四台设备的双通道预设、点击按钮后下发、设置后自动回读、读取全部、单台回读、广播亮灭及自定义光强、页面通信状态和临时操作日志。
- 浏览器 API Key 仅保存在当前页面内存，HTTP 接口既有 `X-API-Key` 鉴权和 Linux RS485 通信代码未改。
- 保留原第五部分全部 API 请求/响应结构；无数据库依赖，无需额外 Python 包。

## 设备扩容（V1.2）

- 支持 0x96、0x97、0x98、0x99 新增设备，保留 0x9A～0x9D；八台设备每台 CH1/CH2，共 16 路。
- `light_modbus.DEVICE_ADDRESSES` 更新为 0x96～0x9D；地址校验、读全部、单台设置/读取自动覆盖新地址。
- 网页新增四张设备卡片，汇总、广播确认与设备数量动态匹配 8 台；密钥验证策略不变。
- 新增四台的波长信息尚未提供，网页标记“光谱待确认”，不假设波长。
- 说明文档 `docs/LED补光对接说明.md` 是原四台设备的历史对接基准，未擅自改写；对接外部平台时需同步更新其设备清单/数据库配置。

## 光谱对应关系修正（V1.3）

- 现场确认 0x96 ↔ 0x9A（730nm / 全光谱）、0x97 ↔ 0x9B（630nm / 430nm）、0x98 ↔ 0x9C（450nm / 530nm）、0x99 ↔ 0x9D（660nm / 395nm）。
- 网页按四组排列，每组包含两台光谱相同但**控制独立**的驱动器。
- 后端设备清单、RS485 协议、HTTP 接口以及认证/广播代码完全保持 V1.2 不变。
- 具体增量安装见 `SPECTRUM_PAIRING_V1_3.md`。


## 最终现场光谱顺序修正（V1.4）

- **用现场复核的新映射取代 V1.3 旧映射**：0x96↔0x9A = CH1 660nm / CH2 395nm；0x97↔0x9B = CH1 450nm / CH2 530nm；0x98↔0x9C = CH1 630nm / CH2 430nm；0x99↔0x9D = CH1 730nm / CH2 全光谱。
- 仅修正 `web/app.js` 的光谱文本及指示颜色，`web/index.html` 的缓存版本设为 `v=1.4`；配对地址保持不变，各设备仍独立控制。
- `server.py`、`light_modbus.py`、API Key、串口参数、读写协议及全局广播逻辑与 V1.3 完全相同。
- `docs/LED补光对接说明.md` 和 `SPECTRUM_PAIRING_V1_3.md` 是旧版历史材料，存在过时的光谱顺序；以 `SPECTRUM_MAPPING_V1_4.md` 为准。


## 广播延迟校验修复（V1.5）

- 现场验证：广播 CH1=30/CH2=30 后等待 3 秒，0x96～0x9D 全部 8/8 回读成功。
- 网页 `web/app.js` 广播 POST 成功后等待 3000ms，调用原有 `/read-all`。
- 对读取异常或光强不一致的设备，最多再等待 1500ms 并通过 `/read?busAddress=...` 重读两轮，不重新发送广播。
- 回读结果和发送结果分别标记；未确认设备记录具体地址、读回值或错误。
- HTTP 接口、`server.py`、`light_modbus.py`、API Key、八台设备光谱对应完全保持 V1.4 不变。
