-- 未来植物 · 环境传感（温湿度 / pH）唯一必建表
-- 三种测点合表，用 metric 区分：air.temperature / air.humidity / nutrient.ph

CREATE TABLE IF NOT EXISTS plant_sensor_reading (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_key   VARCHAR(64)  NOT NULL COMMENT '设备键，如 plant-ctrl-01',
    metric       VARCHAR(64)  NOT NULL COMMENT 'air.temperature / air.humidity / nutrient.ph',
    value_num    DOUBLE       NULL COMMENT '数值',
    value_text   VARCHAR(255) NULL COMMENT '预留，一般不用',
    unit         VARCHAR(32)  NULL COMMENT '°C / %RH / 空(pH)',
    quality      VARCHAR(32)  NULL COMMENT 'GOOD | BAD | CALIB | STALE',
    sampled_at   DATETIME(3)  NULL COMMENT '边缘采样时间',
    received_at  DATETIME(3)  NULL COMMENT '平台入库时间',
    raw_json     LONGTEXT     NULL COMMENT '原始 JSON',
    KEY idx_plant_reading_query (device_key, metric, sampled_at),
    KEY idx_plant_reading_received (device_key, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='环境传感时序读数';
