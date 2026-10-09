# LED 补光对接说明（多通道植物生长灯）

> 软件定平台接口与入库；LED 同学负责 Modbus 实控，并对外提供/对接 HTTP（与摄像头类似：平台后端调他）。  
> 逻辑机架键：`led-rack-01`（一套灯系统）  
> 平台基址示例：`http://<平台IP>:8708`  
> LED 网关基址示例：`http://<网关IP>:8090/api/v1`（端口与密钥联调时填写）

**实机一期范围（硬件同学已确认）：**

1. 控制单个设备 `0x9A`～`0x9D` 的 ch1 / ch2 光强  
2. 读取单个设备的 ch1 / ch2 光强  
3. 广播控制所有设备 ch1 / ch2 亮灭（或统一光强）  
4. 「读取全部」：一次拿到所有设备 ch1 / ch2 光强（内部可轮询 4 台，对外一个接口）

**设备与光谱对应（产品说明书；地址以实机 `0x9A`～`0x9D` 为准）：**

| busAddress | ch1 光谱 | ch2 光谱 |
|------------|----------|----------|
| `0x9A` | 730nm | Full Spectrum |
| `0x9B` | 630nm | 430nm |
| `0x9C` | 450nm | 530nm |
| `0x9D` | 660nm | 395nm |

→ 共 **4 设备 × 2 通道 = 8 路可独立调光**。  
光强协议值：**0～255**（`0`=灭，`255`=最亮）。Web 若用 0～100%，由平台换算：`level255 = round(percent * 255 / 100)`。

---

## 1. 两张表（软件侧 · LED 专用，不与传感/摄像头混表）

人话理解：

- **`plant_led_state`**：记「灯现在有多亮」——网页查状态看这张表（一共 8 行，一路光一行）。  
- **`plant_led_command_log`**：记「谁、什么时候、发过什么调灯命令」——流水账，方便对锅。  

两张表用同一个 **`rack_key`**（比如 `led-rack-01`）表示是同一套灯。

### 1.1 `plant_led_state`（当前亮度表）

| 字段 | 类型 | 说明（备注） |
|------|------|--------------|
| id | BIGINT PK | 自增编号，数据库自己用 |
| rack_key | VARCHAR(64) | 这套灯的名字，固定写成 `led-rack-01` 即可 |
| bus_address | VARCHAR(8) | 灯驱动器的门牌号：`0x9A` / `0x9B` / `0x9C` / `0x9D`（现场 4 台驱动器） |
| channel | TINYINT | 这一台驱动器上的第几路灯：只能是 `1`（ch1）或 `2`（ch2） |
| spectrum | VARCHAR(32) | 给人看的：这路是什么光，如 `730nm`；可空，不影响控制 |
| level | INT | **当前有多亮**：`0`=关，`255`=最亮，中间按比例 |
| online | TINYINT | 最近一次跟这路通信成没成功：`1`=通了，`0`=没通上 |
| source | VARCHAR(32) | 这条亮度是怎么来的：`READ`读回来的 / `SET`单台调的 / `BROADCAST`广播调的 / `MOCK`模拟的 |
| updated_at | DATETIME(3) | 这条记录最后一次改时间 |
| 唯一约束 | `(rack_key, bus_address, channel)` | 同一套灯 + 同一门牌 + 同一通道，只能有一行（避免重复） |

 8 行示例（4 台 × 每台 2 路）：

| rack_key | bus_address | channel | spectrum | level |
|----------|-------------|---------|----------|-------|
| led-rack-01 | 0x9A | 1 | 730nm | 0 |
| led-rack-01 | 0x9A | 2 | Full Spectrum | 0 |
| led-rack-01 | 0x9B | 1 | 630nm | 0 |
| … | … | … | … | … |
| led-rack-01 | 0x9D | 2 | 395nm | 0 |

### 1.2 `plant_led_command_log`（调灯操作记录表）

| 字段 | 类型 | 说明（备注） |
|------|------|--------------|
| id | BIGINT PK | 自增编号；对外可以说成命令号 `led-1`、`led-2`… |
| rack_key | VARCHAR(64) | 哪一套灯，与上面状态表同一个名字 |
| command_type | VARCHAR(64) | 干了哪一类事，取值见下面小表 |
| scope | VARCHAR(16) | 管一台还是管全部：`SINGLE`=单台，`BROADCAST`=广播全部 |
| bus_address | VARCHAR(8) | 单台操作时填门牌号（如 `0x9A`）；广播全部时可以不填 |
| payload_json | LONGTEXT | 当时发下去的参数原文（如 ch1/ch2 设成多少），方便以后回看 |
| status | VARCHAR(32) | 这条命令进行到哪：`PENDING`待发 / `SENT`已发出 / `ACKED`成功 / `FAILED`失败 |
| result_message | VARCHAR(512) | 一句话结果，成功或失败原因 |
| operator_name | VARCHAR(64) | 谁点的（登录用户名等），可空 |
| created_at | DATETIME(3) | 命令创建时间 |
| finished_at | DATETIME(3) | 命令结束时间（成功或失败时写入） |

| command_type | 对应功能（人话） |
|--------------|------------------|
| `LED_SET_CHANNEL` | ① 调某一台的 ch1/ch2 亮度 |
| `LED_READ_CHANNEL` | ② 读某一台的 ch1/ch2 亮度 |
| `LED_BROADCAST_SET` | ③ 一次调所有台的 ch1/ch2 |
| `LED_READ_ALL` | ④ 一次读所有台的亮度 |

建表 SQL 备份：

```sql
CREATE TABLE IF NOT EXISTS plant_led_state (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增编号',
    rack_key     VARCHAR(64)  NOT NULL COMMENT '一套灯的名字，如 led-rack-01',
    bus_address  VARCHAR(8)   NOT NULL COMMENT '驱动器门牌号：0x9A~0x9D',
    channel      TINYINT      NOT NULL COMMENT '通道：1=ch1，2=ch2',
    spectrum     VARCHAR(32)  NULL COMMENT '光谱名称展示用，可空',
    level        INT          NOT NULL DEFAULT 0 COMMENT '亮度0关~255最亮',
    online       TINYINT      NOT NULL DEFAULT 1 COMMENT '1通讯成功0失败',
    source       VARCHAR(32)  NULL COMMENT 'READ/SET/BROADCAST/MOCK',
    updated_at   DATETIME(3)  NULL COMMENT '最后更新时间',
    UNIQUE KEY uk_led_state (rack_key, bus_address, channel),
    KEY idx_led_state_rack (rack_key, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LED当前亮度（每路光一行）';

CREATE TABLE IF NOT EXISTS plant_led_command_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增编号',
    rack_key       VARCHAR(64)  NOT NULL COMMENT '一套灯的名字',
    command_type   VARCHAR(64)  NOT NULL COMMENT '命令种类，见文档小表',
    scope          VARCHAR(16)  NOT NULL COMMENT 'SINGLE单台/BROADCAST全部',
    bus_address    VARCHAR(8)   NULL COMMENT '单台时的门牌号，广播可空',
    payload_json   LONGTEXT     NULL COMMENT '当时下发的参数原文',
    status         VARCHAR(32)  NOT NULL COMMENT 'PENDING/SENT/ACKED/FAILED',
    result_message VARCHAR(512) NULL COMMENT '成功或失败说明',
    operator_name  VARCHAR(64)  NULL COMMENT '操作人',
    created_at     DATETIME(3)  NULL COMMENT '创建时间',
    finished_at    DATETIME(3)  NULL COMMENT '结束时间',
    KEY idx_led_cmd_rack (rack_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LED调灯操作流水账';
```

---

## 2. 功能清单（一期仅 4 个）

| # | 功能 | 我让 LED 侧干什么 | 我要拿到什么 |
|---|------|-------------------|--------------|
| 1 | 单设备调光 | 设置某地址 ch1/ch2 的 level | 成功/失败；建议回读后的光强 |
| 2 | 单设备读光强 | 读取某地址 ch1/ch2 | 两个 level（0～255） |
| 3 | 广播亮灭/调光 | 一次设置所有设备的 ch1/ch2 | 成功/失败（广播可能无逐台回包） |
| 4 | 读全部光强 | 返回 0x9A～0x9D 共 8 个值 | 数组；平台写入 `plant_led_state` |

本期不做：分组钟控、专家配方时段、改组号、保存 EEPROM（协议里有，二期再说）。

---

## 3. 通信方式

与摄像头相同：**平台后端主动调 LED 网关**；前端只调平台。

```text
前端
  → 接口① 平台 :8708/plant/api/led/...
  → 写 plant_led_command_log，更新/读取 plant_led_state
  → 接口② LED 网关（其内部再走 Modbus RTU）
  → 用响应更新库，返回前端
```

```text
接口①  前端 → 我的平台
接口②  我的平台 → LED 网关（他实现；Modbus 细节他内部消化）
```

---

## 4. 接口①：平台 API（给前端）

基址：`http://<平台IP>:8708`  
前缀：`/plant/api/led`

统一成功壳（与现有植物业务一致时可调整）：

```json
{ "code": 0, "message": "ok", "data": { } }
```

### 4.1 查询全部状态（页面刷新用）

| 项 | 约定 |
|----|------|
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

实现：可先读库；需要最新实况时平台再调接口②「读全部」后回写库。

---

### 4.2 单设备调光（功能 1）

| 项 | 约定 |
|----|------|
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
| rackKey | 是 | 机架键 |
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

| 项 | 约定 |
|----|------|
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

| 项 | 约定 |
|----|------|
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
- 也可两通道设不同统一值（所有设备同一 ch1、同一 ch2）

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

广播成功后建议平台再调一次「读全部」刷新 `plant_led_state`（因广播常无逐台应答）。

---

### 4.5 读取全部光强（功能 4）

| 项 | 约定 |
|----|------|
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

平台用该结果 upsert `plant_led_state`（8 行）。

---

## 5. 接口②：LED 网关 API（详细 · 我的后端调他）

> LED 同学对外提供下列 HTTP；内部用 Modbus RTU（9600 8N1 等按协议文档）访问灯具。  
> 基址示例：`http://<网关IP>:8090/api/v1`  
> 建议请求头：`X-API-Key: <密钥>`（与摄像头同样安全要求；无 Key 方案须限局域网）

### 5.0 通用

| 项 | 约定 |
|----|------|
| Content-Type | 有 JSON 正文时：`application/json` |
| 光强 | 整数 **0～255** |
| 地址 | 字符串 `"0x9A"`～`"0x9D"`（也可用十进制 154～157，双方选定一种） |
| 错误 | `{"detail":"..."}` 或与平台约定的 `{code,message}` |

---

### 5.1 总表（一期只要这些）

| 方法 | 路径 | 功能 | 对应 |
|------|------|------|------|
| POST | `/led/set` | 单设备写 ch1/ch2 | 功能 1 |
| GET | `/led/read` | 单设备读 ch1/ch2 | 功能 2 |
| POST | `/led/broadcast/set` | 广播写所有设备 ch1/ch2 | 功能 3 |
| GET | `/led/read-all` | 读全部 4 设备光强 | 功能 4 |
| GET | `/led/health` | 网关是否存活（可选） | 联调 |

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

**成功 200 示例：**

```json
{
  "ok": true,
  "busAddress": "0x9A",
  "ch1": 200,
  "ch2": 0
}
```

**他内部（参考协议，平台不管细节）：**  
对地址 `0x9A` 写保持寄存器 CH1=`0x01`、CH2=`0x02`（功能码 `0x06` 或 `0x10` 一次写两通道）。

---

### 5.3 `GET /led/read`（单设备读取）

```http
GET /api/v1/led/read?busAddress=0x9A
X-API-Key: YOUR_KEY
```

**成功 200：**

```json
{
  "busAddress": "0x9A",
  "ch1": 200,
  "ch2": 0
}
```

**他内部：** Modbus `0x03` 读该地址 CH1/CH2 寄存器。

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

**成功 200：**

```json
{
  "ok": true,
  "scope": "BROADCAST",
  "ch1": 255,
  "ch2": 0,
  "note": "broadcast may have no per-device ack"
}
```

**他内部：** Modbus 广播地址 `0x00` 写多寄存器（协议「多个通道同时控制」）；广播通常**无返回**，故 HTTP 以「指令已发出」为成功，真实亮度靠后续 `read-all` 确认。

亮灭约定：

| 意图 | ch1 | ch2 |
|------|-----|-----|
| 两通道全灭 | 0 | 0 |
| 两通道全亮 | 255 | 255 |
| 只亮 ch1 | 255 | 0 |

---

### 5.5 `GET /led/read-all`（读全部）

```http
GET /api/v1/led/read-all
X-API-Key: YOUR_KEY
```

**成功 200：**

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

**他内部：** **禁止依赖广播读**；应对 `0x9A`～`0x9D` **逐台** `0x03` 读取，汇总后返回。某台失败时该条 `ok:false`，其余仍返回。

---

## 6. 时序示例

### 6.1 网页调某一路亮度

```text
前端 --POST /plant/api/led/set--> 平台
平台 --写 plant_led_command_log--> 库
平台 --POST /api/v1/led/set--> LED网关 --Modbus写--> 灯
平台 --可选 GET read 校验--> 网关
平台 --upsert plant_led_state--> 库
平台 --code:0--> 前端
```

### 6.2 网页点「全部关闭」

```text
前端 --POST /plant/api/led/broadcast/set {ch1:0,ch2:0}--> 平台
平台 --POST /api/v1/led/broadcast/set--> 网关（广播写）
平台 --GET /api/v1/led/read-all--> 网关（逐台读）
平台 --刷新 8 行 plant_led_state--> 库
平台 --成功--> 前端
```

---

