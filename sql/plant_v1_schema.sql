-- 未来植物 V1 完整表结构
-- 说明：
-- 1) 应用 spring.jpa.hibernate.ddl-auto=update 时会自动建/改表
-- 2) 本脚本用于手工部署、验收对照
-- 3) 时间字段统一 DATETIME(3)，保留毫秒；业务上以「采样时间 sampled_at / 抓拍时间 captured_at」为准，
--    「入库时间 received_at / created_at」用于链路延迟与审计

CREATE TABLE IF NOT EXISTS plant_device_profile (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_key       VARCHAR(64)  NOT NULL COMMENT '控制器唯一键，对应 devices.device_key',
    display_name     VARCHAR(128) NULL,
    firmware_version VARCHAR(64)  NULL,
    config_json      LONGTEXT     NULL COMMENT '点位/通道/采集周期等配置',
    mock_enabled     TINYINT      NOT NULL DEFAULT 1,
    created_at       DATETIME(3)  NULL COMMENT '创建时间戳',
    updated_at       DATETIME(3)  NULL COMMENT '更新时间戳',
    UNIQUE KEY uk_plant_device_profile_key (device_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='未来植物设备画像/配置';

CREATE TABLE IF NOT EXISTS plant_sensor_reading (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_key   VARCHAR(64)  NOT NULL,
    metric       VARCHAR(64)  NOT NULL COMMENT '如 air.temperature / nutrient.ph',
    value_num    DOUBLE       NULL,
    value_text   VARCHAR(255) NULL,
    unit         VARCHAR(32)  NULL,
    quality      VARCHAR(32)  NULL COMMENT 'GOOD/BAD/CALIB/STALE',
    sampled_at   DATETIME(3)  NULL COMMENT '【关键】边缘采样时间戳',
    received_at  DATETIME(3)  NULL COMMENT '【关键】平台入库时间戳',
    raw_json     LONGTEXT     NULL,
    KEY idx_plant_reading_query (device_key, metric, sampled_at),
    KEY idx_plant_reading_received (device_key, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器时序测点';

CREATE TABLE IF NOT EXISTS plant_actuator_state (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_key   VARCHAR(64)  NOT NULL,
    actuator_id  VARCHAR(64)  NOT NULL COMMENT '如 led.ch1 / pump.water',
    state_json   LONGTEXT     NULL,
    source       VARCHAR(32)  NULL COMMENT 'REPORT/COMMAND_OPTIMISTIC/ACK/MOCK',
    created_at   DATETIME(3)  NULL COMMENT '首次写入时间戳',
    updated_at   DATETIME(3)  NULL COMMENT '【关键】状态最近更新时间戳',
    UNIQUE KEY uk_plant_actuator (device_key, actuator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行器最新状态快照';

CREATE TABLE IF NOT EXISTS plant_command_log (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_key         VARCHAR(64)  NOT NULL,
    client_request_id  VARCHAR(64)  NULL,
    command_type       VARCHAR(64)  NOT NULL COMMENT 'LED_SET/PUMP_SET/...',
    payload_json       LONGTEXT     NULL,
    status             VARCHAR(32)  NOT NULL COMMENT 'PENDING/SENT/ACKED/FAILED/TIMEOUT',
    result_message     VARCHAR(512) NULL,
    operator_name      VARCHAR(64)  NULL,
    created_at         DATETIME(3)  NULL COMMENT '【关键】指令创建/下发时间戳',
    sent_at            DATETIME(3)  NULL COMMENT '实际发送到 MQTT 的时间戳',
    finished_at        DATETIME(3)  NULL COMMENT '【关键】ACK/失败完成时间戳',
    KEY idx_plant_cmd_device (device_key, created_at),
    KEY idx_plant_cmd_client (client_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制指令审计日志';

CREATE TABLE IF NOT EXISTS plant_media_asset (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_key    VARCHAR(64)  NOT NULL,
    media_type    VARCHAR(32)  NOT NULL COMMENT 'IMAGE/...',
    storage_path  VARCHAR(512) NULL,
    captured_at   DATETIME(3)  NULL COMMENT '【关键】相机抓拍时间戳',
    meta_json     LONGTEXT     NULL,
    created_at    DATETIME(3)  NULL COMMENT '入库时间戳',
    KEY idx_plant_media_device (device_key, captured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片/采集媒体';

CREATE TABLE IF NOT EXISTS plant_runtime_event (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_key    VARCHAR(64)  NOT NULL,
    level_name    VARCHAR(16)  NOT NULL COMMENT 'INFO/WARN/ERROR',
    code          VARCHAR(64)  NULL,
    message       VARCHAR(512) NULL,
    context_json  LONGTEXT     NULL,
    created_at    DATETIME(3)  NULL COMMENT '【关键】事件发生/记录时间戳',
    KEY idx_plant_event_device (device_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行/告警事件';

CREATE TABLE IF NOT EXISTS plant_led_schedule (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_key     VARCHAR(64)  NOT NULL,
    name           VARCHAR(128) NULL,
    schedule_type  VARCHAR(32)  NOT NULL COMMENT 'DAILY_WINDOW / INTERVAL',
    brightness     INT          NULL COMMENT '0-100',
    on_time        VARCHAR(16)  NULL COMMENT 'DAILY_WINDOW 开灯 HH:mm',
    off_time       VARCHAR(16)  NULL COMMENT 'DAILY_WINDOW 关灯 HH:mm',
    on_minutes     INT          NULL COMMENT 'INTERVAL 开灯分钟',
    off_minutes    INT          NULL COMMENT 'INTERVAL 关灯分钟',
    enabled        TINYINT      NULL DEFAULT 1,
    remark         VARCHAR(255) NULL,
    last_applied_at DATETIME(3) NULL COMMENT '最近一次自动执行时间戳',
    last_applied_brightness INT NULL COMMENT '最近一次自动执行亮度',
    created_at     DATETIME(3)  NULL COMMENT '创建时间戳',
    updated_at     DATETIME(3)  NULL COMMENT '更新时间戳',
    KEY idx_plant_led_sched_device (device_key, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LED 定时补光策略';

CREATE TABLE IF NOT EXISTS plant_automation_rule (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_key              VARCHAR(64)  NOT NULL,
    name                    VARCHAR(128) NULL,
    actuator_id             VARCHAR(64)  NOT NULL COMMENT 'pump.oxygen / fan.1 / ...',
    rule_type               VARCHAR(32)  NOT NULL COMMENT 'DAILY_WINDOW/EVERY_N_DAYS/INTERVAL/SENSOR_THRESHOLD',
    action                  VARCHAR(32)  NOT NULL DEFAULT 'ON',
    brightness              INT          NULL,
    on_time                 VARCHAR(16)  NULL,
    off_time                VARCHAR(16)  NULL,
    every_n_days            INT          NULL,
    duration_minutes        INT          NULL,
    on_minutes              INT          NULL,
    off_minutes             INT          NULL,
    metric                  VARCHAR(64)  NULL,
    operator                VARCHAR(16)  NULL,
    threshold_value         DOUBLE       NULL,
    cooldown_minutes        INT          NULL,
    anchor_date             VARCHAR(16)  NULL,
    enabled                 TINYINT      NULL DEFAULT 1,
    priority                INT          NULL DEFAULT 100,
    remark                  VARCHAR(255) NULL,
    last_triggered_at       DATETIME(3)  NULL COMMENT '传感触发时间戳',
    last_applied_at         DATETIME(3)  NULL COMMENT '最近执行时间戳',
    last_applied_action     VARCHAR(64)  NULL,
    created_at              DATETIME(3)  NULL,
    updated_at              DATETIME(3)  NULL,
    KEY idx_plant_auto_device (device_key, enabled),
    KEY idx_plant_auto_actuator (actuator_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用执行器自动化规则';

CREATE TABLE IF NOT EXISTS plant_crop_specimen (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_key    VARCHAR(64)  NOT NULL,
    plant_code    VARCHAR(64)  NOT NULL COMMENT '如 P-01',
    slot_code     VARCHAR(32)  NULL COMMENT '穴位 A1/B2',
    crop_type     VARCHAR(64)  NULL COMMENT '玉米',
    variety       VARCHAR(128) NULL,
    sow_date      DATE         NULL,
    growth_stage  VARCHAR(32)  NULL COMMENT 'VE/V3/V6/V9/VT/R1',
    status        VARCHAR(32)  NULL COMMENT 'GROWING/HARVESTED',
    pos_row       INT          NULL,
    pos_col       INT          NULL,
    remark        VARCHAR(255) NULL,
    created_at    DATETIME(3)  NULL,
    updated_at    DATETIME(3)  NULL,
    UNIQUE KEY uk_plant_crop_device_code (device_key, plant_code),
    KEY idx_plant_crop_device (device_key, plant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植株档案';

CREATE TABLE IF NOT EXISTS plant_phenotype_metric (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_key    VARCHAR(64)  NOT NULL,
    plant_code    VARCHAR(64)  NOT NULL,
    metric        VARCHAR(64)  NOT NULL COMMENT 'height_cm/leaf_count/...',
    value_num     DOUBLE       NULL,
    unit          VARCHAR(32)  NULL,
    source        VARCHAR(32)  NULL COMMENT 'MOCK/MANUAL/MODEL/MODEL_MOCK',
    job_id        BIGINT       NULL,
    sampled_at    DATETIME(3)  NULL COMMENT '【关键】表型采样时间',
    created_at    DATETIME(3)  NULL,
    KEY idx_plant_pheno_query (device_key, plant_code, metric, sampled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表型指标时序';

CREATE TABLE IF NOT EXISTS plant_analysis_job (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_key             VARCHAR(64)  NOT NULL,
    plant_code             VARCHAR(64)  NULL,
    job_type               VARCHAR(64)  NOT NULL COMMENT 'PHENOTYPE_EXTRACT',
    status                 VARCHAR(32)  NOT NULL COMMENT 'QUEUED/RUNNING/SUCCESS/FAILED',
    input_media_ids_json   LONGTEXT     NULL,
    result_json            LONGTEXT     NULL,
    message                VARCHAR(512) NULL,
    created_at             DATETIME(3)  NULL,
    started_at             DATETIME(3)  NULL,
    finished_at            DATETIME(3)  NULL,
    KEY idx_plant_job_device (device_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表型分析任务';
