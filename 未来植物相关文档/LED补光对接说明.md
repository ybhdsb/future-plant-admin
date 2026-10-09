# 未来植物原型系统 · LED 补光子系统对接说明（多通道植物生长灯）

| 项目 | 内容 |
|------|------|
| 文档性质 | 软件平台与 LED 局域网网关之间的接口、数据入库与联调约定 |
| 适用对象 | LED 网关 / Modbus 开发同学；软件侧后端联调与验收；指导教师审阅 |
| 默认机架标识 `rackKey` | `led-rack-01`（一套 LED 补光系统） |
| 平台服务基址（示例） | `http://<平台主机IP>:8708` |
| LED 网关基址（示例） | `http://<网关主机IP>:8090/api/v1`（端口与密钥于联调时确认） |
| 版本说明 | 一期约定 4 台驱动器（`0x9A`～`0x9D`）× 2 通道共 8 路独立调光 |

**分工原则：** 软件侧负责平台接口定义、亮度状态与指令流水入库、前端展示；LED 侧负责 Modbus RTU 实际控制，并对外提供 HTTP 网关供平台调用。Modbus 帧格式、串口参数等硬件细节由网关侧内部消化，不在本文展开。

**实机一期范围（以硬件侧最新确认为准）：**

1. 控制单个设备 `0x9A`～`0x9D` 的 ch1 / ch2 光强
2. 读取单个设备的 ch1 / ch2 光强
3. 广播控制所有设备 ch1 / ch2 亮灭（或统一光强）
4. 「读取全部」：一次返回所有设备 ch1 / ch2 光强（网关内部可轮询 4 台，对外统一为一个接口）

**设备与光谱对应（产品说明书；总线地址以实机 `0x9A`～`0x9D` 为准）：**

| busAddress | ch1 光谱 | ch2 光谱 |
|------------|----------|----------|
| `0x9A` | 730nm | Full Spectrum |
| `0x9B` | 630nm | 430nm |
| `0x9C` | 450nm | 530nm |
| `0x9D` | 660nm | 395nm |

共 **4 设备 × 2 通道 = 8 路可独立调光**。  
光强协议值：**0～255**（`0` = 熄灭，`255` = 最亮）。Web 界面若使用 0～100% 百分比，由平台换算：`level255 = round(percent * 255 / 100)`。

---

## 1. 数据表设计（软件侧 · LED 专用）

LED 业务使用独立数据表，不与环境传感、摄像头等业务混表。两表通过 **`rack_key`** 关联同一套补光系统。

**表职责说明：**

- **`plant_led_state`（当前亮度状态表）**：记录每一路光的 **最新亮度与在线状态**，供管理端页面查询展示。一套系统固定 8 行（4 台驱动器 × 每台 2 通道）。
- **`plant_led_command_log`（调光指令流水表）**：记录每一次调光、读光强等操作的完整过程，便于审计、排错与指令状态追踪。

### 1.1 `plant_led_state`（当前亮度状态表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT，主键，自增 | 记录编号 |
| rack_key | VARCHAR(64)，非空 | 机架逻辑标识，本期固定为 `led-rack-01` |
| bus_address | VARCHAR(8)，非空 | Modbus 从站地址：`0x9A` / `0x9B` / `0x9C` / `0x9D`（现场 4 台驱动器） |
| channel | TINYINT，非空 | 通道编号：仅允许 `1`（ch1）或 `2`（ch2） |
| spectrum | VARCHAR(32) | 光谱名称，供界面展示，如 `730nm`；可空，不影响控制逻辑 |
| level | INT，非空 | 当前亮度：`0` = 熄灭，`255` = 最亮 |
| online | TINYINT，非空 | 最近一次通信结果：`1` = 成功，`0` = 失败 |
| source | VARCHAR(32) | 亮度数据来源：`READ`（读回）/ `SET`（单台设置）/ `BROADCAST`（广播设置）/ `MOCK`（模拟） |
| updated_at | DATETIME(3) | 本行记录最后更新时间 |
| 唯一约束 | `(rack_key, bus_address, channel)` | 同一机架、同一地址、同一通道仅允许一行，避免重复 |

**初始数据示例（4 台 × 每台 2 路，共 8 行）：**

| rack_key | bus_address | channel | spectrum | level |
|----------|-------------|---------|----------|-------|
| led-rack-01 | 0x9A | 1 | 730nm | 0 |
| led-rack-01 | 0x9A | 2 | Full Spectrum | 0 |
| led-rack-01 | 0x9B | 1 | 630nm | 0 |
| led-rack-01 | 0x9B | 2 | 430nm | 0 |
| led-rack-01 | 0x9C | 1 | 450nm | 0 |
| led-rack-01 | 0x9C | 2 | 530nm | 0 |
| led-rack-01 | 0x9D | 1 | 660nm | 0 |
| led-rack-01 | 0x9D | 2 | 395nm | 0 |

### 1.2 `plant_led_command_log`（调光指令流水表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT，主键，自增 | 记录编号；对外可表示为 `led-{id}` |
| rack_key | VARCHAR(64)，非空 | 机架逻辑标识，与状态表一致 |
| command_type | VARCHAR(64)，非空 | 指令类型，取值见下表 |
| scope | VARCHAR(16)，非空 | 作用范围：`SINGLE`（单台）/ `BROADCAST`（广播全部） |
| bus_address | VARCHAR(8) | 单台操作时填写从站地址（如 `0x9A`）；广播时可空 |
| payload_json | LONGTEXT | 下发参数 JSON 原文，便于事后追溯 |
| status | VARCHAR(32)，非空 | 执行状态：`PENDING` / `SENT` / `ACKED` / `FAILED` |
| result_message | VARCHAR(512) | 执行结果说明或错误摘要 |
| operator_name | VARCHAR(64) | 操作者用户名，可空 |
| created_at | DATETIME(3) | 指令创建时间 |
| finished_at | DATETIME(3) | 指令结束时间 |

**本期约定的指令类型（`command_type`）：**

| command_type | 对应功能 |
|--------------|----------|
| `LED_SET_CHANNEL` | 设置单台驱动器 ch1 / ch2 亮度 |
| `LED_READ_CHANNEL` | 读取单台驱动器 ch1 / ch2 亮度 |
| `LED_BROADCAST_SET` | 广播设置所有设备 ch1 / ch2 亮度 |
| `LED_READ_ALL` | 一次读取所有设备亮度 |

**建表 SQL（备份）：**

```sql
CREATE TABLE IF NOT EXISTS plant_led_state (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录编号，自增主键',
    rack_key     VARCHAR(64)  NOT NULL COMMENT '机架逻辑标识，如 led-rack-01',
    bus_address  VARCHAR(8)   NOT NULL COMMENT 'Modbus 从站地址：0x9A~0x9D',
    channel      TINYINT      NOT NULL COMMENT '通道编号：1=ch1，2=ch2',
    spectrum     VARCHAR(32)  NULL COMMENT '光谱名称，供界面展示，可空',
    level        INT          NOT NULL DEFAULT 0 COMMENT '当前亮度：0=熄灭，255=最亮',
    online       TINYINT      NOT NULL DEFAULT 1 COMMENT '通信状态：1=成功，0=失败',
    source       VARCHAR(32)  NULL COMMENT '数据来源：READ/SET/BROADCAST/MOCK',
    updated_at   DATETIME(3)  NULL COMMENT '记录最后更新时间',
    UNIQUE KEY uk_led_state (rack_key, bus_address, channel),
    KEY idx_led_state_rack (rack_key, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LED 当前亮度状态表（每路光一行，共 8 行）';

CREATE TABLE IF NOT EXISTS plant_led_command_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录编号，自增主键',
    rack_key       VARCHAR(64)  NOT NULL COMMENT '机架逻辑标识',
    command_type   VARCHAR(64)  NOT NULL COMMENT '指令类型，见文档 command_type 表',
    scope          VARCHAR(16)  NOT NULL COMMENT '作用范围：SINGLE=单台，BROADCAST=广播',
    bus_address    VARCHAR(8)   NULL COMMENT '单台操作时的从站地址，广播时可空',
    payload_json   LONGTEXT     NULL COMMENT '下发参数 JSON 原文',
    status         VARCHAR(32)  NOT NULL COMMENT '执行状态：PENDING/SENT/ACKED/FAILED',
    result_message VARCHAR(512) NULL COMMENT '执行结果说明或错误摘要',
    operator_name  VARCHAR(64)  NULL COMMENT '操作者用户名',
    created_at     DATETIME(3)  NULL COMMENT '指令创建时间',
    finished_at    DATETIME(3)  NULL COMMENT '指令结束时间',
    KEY idx_led_cmd_rack (rack_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LED 调光指令流水表';
```

---

## 2. 功能需求（一期）

| 序号 | 功能 | 平台对 LED 侧的要求 | 平台须获得的结果 |
|------|------|---------------------|------------------|
| 1 | 单设备调光 | 设置指定地址 ch1 / ch2 的 level | 成功或失败；建议回读校验后的光强 |
| 2 | 单设备读光强 | 读取指定地址 ch1 / ch2 | 两个 level 值（0～255） |
| 3 | 广播亮灭 / 调光 | 一次设置所有设备 ch1 / ch2 | 成功或失败（广播可能无逐台应答） |
| 4 | 读全部光强 | 返回 `0x9A`～`0x9D` 共 8 个值 | 数组；平台 upsert 至 `plant_led_state` |

**本期范围外：** 分组钟控、专家配方时段、修改组号、保存 EEPROM 等（协议虽支持，留待二期）。

---

## 3. 通信架构

与摄像头子系统相同：**平台后端主动调用 LED 网关**；前端仅调用平台 API。网关收到 HTTP 请求后，内部通过 **Modbus RTU**（如 9600 8N1，具体以协议文档为准）访问灯具驱动器。

```text
管理端前端发起操作
  → 调用接口① 平台 API（:8708/plant/api/led/...）
  → 平台写入 plant_led_command_log，读取或更新 plant_led_state
  → 平台调用接口② LED 网关 API（网关内部执行 Modbus 读写）
  → 平台根据响应更新数据库，并返回前端
```

**接口分层：**

```text
接口①  前端 → 平台       表达业务意图（平台 REST API）
接口②  平台 → LED 网关   执行真实设备控制（网关 REST API；Modbus 细节由网关封装）
```

**安全约束：** 前端 **禁止** 直连 LED 网关。API 密钥与 Modbus 总线均须经平台后端统一访问。

---

## 4. 接口①：平台 API（供前端与管理端调用）

基址：`http://<平台主机IP>:8708`  
路径前缀：`/plant/api/led`

统一成功响应格式（与现有植物业务一致时可调整）：

```json
{ "code": 0, "message": "ok", "data": { } }
```

### 4.1 查询全部状态（页面刷新用）

| 项目 | 约定 |
|------|------|
| 方法 | `GET` |
| 路径 | `/plant/api/led/state?rackKey=led-rack-01` |

**响应 `data` 示例：**

```json
{
  "rackKey": "led-rack-01",
  "channels": [
    { "busAddress": "0x9A", "channel": 1, "spectrum": "730nm", "level": 128, "online": true, "updatedAt": "2026-10-09 11:00:00.000" },
    { "busAddress": "0x9A", "channel": 2, "spectrum": "Full Spectrum", "level": 0, "online": true, "updatedAt": "2026-10-09 11:00:00.000" }
  ]
}
```

实现说明：可先读取数据库缓存；需要最新实况时，平台再调用接口②「读全部」后回写 `plant_led_state`。

---

### 4.2 单设备调光（功能 1）

| 项目 | 约定 |
|------|------|
| 方法 | `POST` |
| 路径 | `/plant/api/led/set` |

```json
{
  "rackKey": "led-rack-01",
  "busAddress": "0x9A",
  "ch1": 200,
  "ch2": 0
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| rackKey | 是 | 机架逻辑标识 |
| busAddress | 是 | `0x9A`～`0x9D` |
| ch1 | 是 | 0～255 |
| ch2 | 是 | 0～255 |

**响应 `data`：**

```json
{
  "commandId": "led-1001",
  "status": "ACKED",
  "busAddress": "0x9A",
  "ch1": 200,
  "ch2": 0
}
```

---

### 4.3 单设备读光强（功能 2）

| 项目 | 约定 |
|------|------|
| 方法 | `GET` |
| 路径 | `/plant/api/led/read?rackKey=led-rack-01&busAddress=0x9A` |

**响应 `data`：**

```json
{
  "busAddress": "0x9A",
  "ch1": 200,
  "ch2": 0,
  "readAt": "2026-10-09 11:01:00.000"
}
```

---

### 4.4 广播设置全部（功能 3）

| 项目 | 约定 |
|------|------|
| 方法 | `POST` |
| 路径 | `/plant/api/led/broadcast/set` |

```json
{
  "rackKey": "led-rack-01",
  "ch1": 255,
  "ch2": 0
}
```

说明：

- 「全亮 / 全灭」：`ch1=255,ch2=255` 或 `ch1=0,ch2=0`
- 亦可两通道设为不同统一值（所有设备共享同一 ch1、同一 ch2）

**响应 `data`：**

```json
{
  "commandId": "led-1002",
  "status": "ACKED",
  "scope": "BROADCAST",
  "ch1": 255,
  "ch2": 0
}
```

广播成功后，建议平台再调用一次「读全部」刷新 `plant_led_state`（因 Modbus 广播通常无逐台应答）。

---

### 4.5 读取全部光强（功能 4）

| 项目 | 约定 |
|------|------|
| 方法 | `GET` |
| 路径 | `/plant/api/led/read-all?rackKey=led-rack-01` |

**响应 `data`：**

```json
{
  "rackKey": "led-rack-01",
  "devices": [
    { "busAddress": "0x9A", "ch1": 200, "ch2": 0 },
    { "busAddress": "0x9B", "ch1": 0, "ch2": 128 },
    { "busAddress": "0x9C", "ch1": 0, "ch2": 0 },
    { "busAddress": "0x9D", "ch1": 50, "ch2": 50 }
  ],
  "readAt": "2026-10-09 11:02:00.000"
}
```

平台须用该结果 upsert `plant_led_state`（8 行）。

---

## 5. 接口②：LED 网关 API（供平台后端调用）

> LED 侧对外提供下列 HTTP 接口；内部通过 Modbus RTU 访问灯具驱动器。  
> 基址示例：`http://<网关主机IP>:8090/api/v1`  
> 建议请求头：`X-API-Key: <密钥>`（安全要求同摄像头子系统；若无密钥方案，须限制为局域网访问）

### 5.0 通用约定

| 项目 | 约定 |
|------|------|
| Content-Type | 有 JSON 正文时：`application/json` |
| 光强 | 整数 **0～255** |
| 地址 | 字符串 `"0x9A"`～`"0x9D"`（亦可用十进制 154～157，双方选定一种并保持一致） |
| 错误响应 | `{"detail":"..."}` 或与平台约定的 `{code,message}` |

---

### 5.1 本期可用接口总表

| 方法 | 路径 | 功能 | 对应平台功能 |
|------|------|------|--------------|
| POST | `/led/set` | 单设备写 ch1 / ch2 | 功能 1 |
| GET | `/led/read` | 单设备读 ch1 / ch2 | 功能 2 |
| POST | `/led/broadcast/set` | 广播写所有设备 ch1 / ch2 | 功能 3 |
| GET | `/led/read-all` | 读全部 4 设备光强 | 功能 4 |
| GET | `/led/health` | 网关存活探测（可选） | 联调 |

---

### 5.2 `POST /led/set`（单设备控制）

```http
POST /api/v1/led/set
X-API-Key: YOUR_KEY
Content-Type: application/json

{
  "busAddress": "0x9A",
  "ch1": 200,
  "ch2": 0
}
```

**成功 HTTP 200 示例：**

```json
{
  "ok": true,
  "busAddress": "0x9A",
  "ch1": 200,
  "ch2": 0
}
```

**网关内部实现（参考协议，平台侧无需关注细节）：**  
对地址 `0x9A` 写保持寄存器 CH1=`0x01`、CH2=`0x02`（功能码 `0x06` 或 `0x10` 一次写两通道）。

---

### 5.3 `GET /led/read`（单设备读取）

```http
GET /api/v1/led/read?busAddress=0x9A
X-API-Key: YOUR_KEY
```

**成功 HTTP 200：**

```json
{
  "busAddress": "0x9A",
  "ch1": 200,
  "ch2": 0
}
```

**网关内部实现：** Modbus 功能码 `0x03` 读取该地址 CH1 / CH2 寄存器。

---

### 5.4 `POST /led/broadcast/set`（广播控制）

```http
POST /api/v1/led/broadcast/set
X-API-Key: YOUR_KEY
Content-Type: application/json

{
  "ch1": 255,
  "ch2": 0
}
```

**成功 HTTP 200：**

```json
{
  "ok": true,
  "scope": "BROADCAST",
  "ch1": 255,
  "ch2": 0,
  "note": "broadcast may have no per-device ack"
}
```

**网关内部实现：** Modbus 广播地址 `0x00` 写多寄存器（协议「多个通道同时控制」）；广播通常 **无从站返回**，故 HTTP 层以「指令已发出」为成功，真实亮度须通过后续 `read-all` 确认。

亮灭约定：

| 意图 | ch1 | ch2 |
|------|-----|-----|
| 两通道全灭 | 0 | 0 |
| 两通道全亮 | 255 | 255 |
| 仅 ch1 全亮 | 255 | 0 |

---

### 5.5 `GET /led/read-all`（读全部）

```http
GET /api/v1/led/read-all
X-API-Key: YOUR_KEY
```

**成功 HTTP 200：**

```json
{
  "devices": [
    { "busAddress": "0x9A", "ch1": 200, "ch2": 0, "ok": true },
    { "busAddress": "0x9B", "ch1": 0, "ch2": 128, "ok": true },
    { "busAddress": "0x9C", "ch1": 0, "ch2": 0, "ok": true },
    { "busAddress": "0x9D", "ch1": 50, "ch2": 50, "ok": false, "error": "timeout" }
  ]
}
```

**网关内部实现：** **禁止依赖 Modbus 广播读**；应对 `0x9A`～`0x9D` **逐台** 以功能码 `0x03` 读取，汇总后返回。某台失败时该条标记 `ok:false`，其余设备仍正常返回。

---

## 6. 典型时序

### 6.1 管理端调节单路亮度

```text
前端 --POST /plant/api/led/set--> 平台
平台 --写入 plant_led_command_log--> 数据库
平台 --POST /api/v1/led/set--> LED 网关 --Modbus 写--> 驱动器
平台 --可选 GET /led/read 校验--> 网关
平台 --upsert plant_led_state--> 数据库
平台 --code:0--> 前端
```

### 6.2 管理端「全部关闭」

```text
前端 --POST /plant/api/led/broadcast/set {ch1:0,ch2:0}--> 平台
平台 --POST /api/v1/led/broadcast/set--> 网关（Modbus 广播写）
平台 --GET /api/v1/led/read-all--> 网关（逐台读）
平台 --刷新 8 行 plant_led_state--> 数据库
平台 --成功响应--> 前端
```

---

## 7. 验收标准

| 序号 | 验收项 | 通过条件 |
|------|--------|----------|
| 1 | 通信分层 | 前端仅调用平台 `:8708`；平台后端调用网关 `:8090` |
| 2 | 8 路调光 | `0x9A`～`0x9D` 各 ch1 / ch2 可独立设置与读取 |
| 3 | 广播控制 | 全亮 / 全灭广播可执行；读全部可刷新状态表 |
| 4 | 数据入库 | `plant_led_state` 保持 8 行；`plant_led_command_log` 可追溯 |
| 5 | Modbus 封装 | 平台侧无 Modbus 依赖；网关内部完成 RTU 通信 |

---

## 8. 本期范围外事项

- 分组钟控、专家配方、EEPROM 持久化
- 前端直连 LED 网关或 Modbus 总线
- 超过 4 台驱动器的扩展（协议支持，留待二期）

---

## 9. 联调参数登记表

| 参数项 | 填写值 |
|--------|--------|
| 平台地址 | `http://____:8708` |
| 网关地址 | `http://____:8090/api/v1` |
| rackKey | `led-rack-01` |
| 软件侧联系人 | |
| LED 侧联系人 | |
