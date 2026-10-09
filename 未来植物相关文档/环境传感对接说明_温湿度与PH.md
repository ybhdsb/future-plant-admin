# 未来植物原型系统 · 环境传感子系统对接说明（温湿度 / pH）

| 项目 | 内容 |
|------|------|
| 文档性质 | 软件平台与边缘传感节点之间的接口与数据约定 |
| 适用对象 | 环境传感开发同学；软件侧联调与验收 |
| 默认设备标识 `deviceKey` | `plant-ctrl-01` |
| 平台服务基址（示例） | `http://<平台主机IP>:8708` |
| 版本说明 | 一期仅约定温湿度与营养液 pH 的采集与上报 |

**分工原则：** 软件侧负责接口定义、数据入库与展示；传感侧负责传感器驱动、采样与按约定格式上报。传感硬件内部实现细节不在本文范围内。

---

## 1. 数据表设计（软件侧）

传感侧无需维护业务库。平台侧以 MySQL 存储测点时序与采集策略，两表通过字段 **`device_key`** 关联。

### 1.1 `plant_sensor_reading`（环境测点时序表）

用于保存每一次采样得到的温湿度、pH 数值。三种测点共用一张表，以字段 `metric` 区分类型。一次同时上报三项指标时，对应写入三行记录。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT，主键，自增 | 记录编号 |
| device_key | VARCHAR(64)，非空 | 边缘控制器唯一标识，须与上报 JSON 中的 `deviceKey` 一致 |
| metric | VARCHAR(64)，非空 | 测点键名，取值见下表 |
| value_num | DOUBLE | 测点数值（不含单位字符串） |
| value_text | VARCHAR(255) | 文本型测点预留字段，本期环境传感一般不使用 |
| unit | VARCHAR(32) | 物理单位，如 `°C`、`%RH` |
| quality | VARCHAR(32) | 数据质量：`GOOD`（正常）/ `BAD`（读取失败等） |
| sampled_at | DATETIME(3) | 边缘侧采样时间 |
| received_at | DATETIME(3) | 平台侧入库时间 |
| raw_json | LONGTEXT | 原始测点对象 JSON，便于排错 |

**本期约定的测点键（`metric` 必须使用下列名称，不得自行命名）：**

| metric | 含义 | unit |
|--------|------|------|
| `air.temperature` | 空气温度 | `°C` |
| `air.humidity` | 空气相对湿度 | `%RH` |
| `nutrient.ph` | 营养液酸碱度 | 空字符串 `""` |

### 1.2 `plant_strategy`（采集策略表）

用于保存采样周期等配置。与读数表分离：读数表存“采到了什么”，策略表存“按什么规则去采”。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT，主键 | 策略编号 |
| device_key | VARCHAR(64) | 设备标识，与读数表对应 |
| name | VARCHAR(128) | 策略名称，便于管理端展示 |
| strategy_type | VARCHAR(32) | 本期固定为 `SAMPLE_INTERVAL`（按固定间隔采样） |
| target | VARCHAR(64) | 本期固定为 `sensor` |
| enabled | TINYINT | `1` 启用定时采集；`0` 停用 |
| config_json | LONGTEXT | 策略参数 JSON，见下方示例 |
| remark | VARCHAR(255) | 备注 |
| created_at / updated_at | DATETIME(3) | 创建与更新时间 |

**`SAMPLE_INTERVAL` 的 `config_json` 示例：**

```json
{
  "intervalSec": 15,
  "metrics": ["air.temperature", "air.humidity", "nutrient.ph"]
}
```

其中 `intervalSec` 表示采样并上报的周期（秒），建议默认值为 15。

> 说明：当前平台亦支持将采样间隔写入设备配置（`plant_device_profile.config_json`），并通过 `GET /plant/api/strategy` 对外提供；表结构与接口语义保持一致即可。

---

## 2. 功能需求（一期）

| 功能 | 平台对传感侧的要求 | 传感侧须回传的内容 |
|------|--------------------|--------------------|
| 立即读取 | 立即完成一次温湿度与 pH 采样 | 三个测点的当前数值 |
| 定时读取 | 按策略中的 `intervalSec` 周期性采样 | 每一周期均上报三个测点数值 |

---

## 3. 接口总体说明

本期约定两类交互方向：

```text
接口①  平台 → 传感侧    下发控制指令（立即采样 / 设置定时）
接口②  传感侧 → 平台    上报测点数据（遥测）
```

传感侧如何连接传感器芯片、如何实现定时线程等，由硬件自行实现；软件侧仅校验并存储符合约定的数据。

**典型时序（定时采集）：**

```text
传感侧轮询待执行指令 ──► 获得 SENSOR_SAMPLE_SCHEDULE
传感侧按 intervalSec 循环采样
传感侧 POST /plant/api/telemetry ──► 平台写入 plant_sensor_reading
管理端通过看板 / 历史曲线读取入库结果
```

---

## 4. 接口①：指令下发（平台 → 传感侧）

### 4.1 平台登记指令

| 项目 | 约定 |
|------|------|
| 方法 | `POST` |
| 路径 | `/plant/api/commands` |
| 说明 | 管理端或联调工具调用；指令写入平台指令日志后，由传感侧主动拉取 |

### 4.2 传感侧拉取待执行指令

因边缘节点通常不主动监听平台推送，一期采用 **HTTP 轮询**：

| 项目 | 约定 |
|------|------|
| 方法 | `GET` |
| 路径 | `/plant/api/commands/pending?deviceKey=plant-ctrl-01` |
| 建议轮询周期 | 1～3 秒 |
| 无待执行指令时 | `data` 为空数组 `[]` |

**响应示例：**

```json
{
  "code": 0,
  "data": [
    {
      "commandId": "c-1001",
      "commandType": "SENSOR_SAMPLE_ONCE",
      "payload": {
        "metrics": ["air.temperature", "air.humidity", "nutrient.ph"]
      }
    }
  ]
}
```

### 4.3 指令类型与载荷

#### （1）立即采样 `SENSOR_SAMPLE_ONCE`

```json
{
  "deviceKey": "plant-ctrl-01",
  "commandType": "SENSOR_SAMPLE_ONCE",
  "payload": {
    "metrics": ["air.temperature", "air.humidity", "nutrient.ph"]
  }
}
```

传感侧收到后：立即采样，并调用接口②上报。

#### （2）定时采样策略 `SENSOR_SAMPLE_SCHEDULE`

```json
{
  "deviceKey": "plant-ctrl-01",
  "commandType": "SENSOR_SAMPLE_SCHEDULE",
  "payload": {
    "enabled": true,
    "intervalSec": 15,
    "metrics": ["air.temperature", "air.humidity", "nutrient.ph"]
  }
}
```

| 字段 | 含义 |
|------|------|
| `enabled=true` | 按 `intervalSec` 启动或更新本地定时采集 |
| `enabled=false` | 停止定时采集（仍可响应立即采样指令） |

亦可通过 `GET /plant/api/strategy?deviceKey=plant-ctrl-01` 拉取当前采样策略（与上表语义一致）。

---

## 5. 接口②：遥测上报（传感侧 → 平台）

无论数据来源于“立即采样”还是“定时采样”，统一使用本接口上报。

| 项目 | 约定 |
|------|------|
| 方法 | `POST` |
| 路径 | `/plant/api/telemetry` |
| Content-Type | `application/json` |
| 鉴权 | 本期边缘上报免登录（内网联调） |

**请求体示例：**

```json
{
  "deviceKey": "plant-ctrl-01",
  "sampledAt": "2026-10-08 20:00:00.000",
  "metrics": [
    { "metric": "air.temperature", "value": 26.5, "unit": "°C", "quality": "GOOD" },
    { "metric": "air.humidity", "value": 62.0, "unit": "%RH", "quality": "GOOD" },
    { "metric": "nutrient.ph", "value": 6.2, "unit": "", "quality": "GOOD" }
  ]
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| deviceKey | 是 | 须与约定设备标识一致 |
| sampledAt | 否 | 边缘采样时间；缺省时平台以接收时间为准 |
| metrics | 是 | 测点数组；未采到的项可省略 |
| metrics[].metric | 是 | 必须为第 1.1 节约定名称 |
| metrics[].value | 建议 | 数值；失败时可省略并置 `quality=BAD` |
| metrics[].unit | 否 | 建议携带 |
| metrics[].quality | 否 | 缺省按 `GOOD` 处理 |

**成功响应示例：**

```json
{
  "code": 0,
  "message": "ok",
  "data": "accepted"
}
```

**异常约定：** 单次传感器读取失败时，仍应上报，并将对应测点的 `quality` 置为 `BAD`；定时任务不应因此中断。

---

## 6. 验收标准

| 序号 | 验收项 | 通过条件 |
|------|--------|----------|
| 1 | 上报格式 | 调用 `POST /plant/api/telemetry` 后平台返回成功 |
| 2 | 三项测点 | `air.temperature`、`air.humidity`、`nutrient.ph` 均可入库 |
| 3 | 定时采集 | 按约定周期持续产生新的 `sampled_at` 记录 |
| 4 | 失败可辨 | `quality=BAD` 可上报且定时不中断 |
| 5 | 策略生效 | 修改采样周期后，传感侧在下一轮拉取/执行后跟随新周期 |

---

## 7. 本期范围外事项

- 传感器校准工艺、电路设计细节  
- MQTT 作为必选通道（HTTP 打通即可；MQTT 可作为后续增强）  
- 断网本地缓存与补发（可列为二期）

---

## 8. 联调参数登记表

| 参数项 | 填写值 |
|--------|--------|
| 平台地址 | `http://____:8708` |
| deviceKey | `plant-ctrl-01` |
| 初始 intervalSec | `15` |
| 软件侧联系人 | |
| 传感侧联系人 | |
