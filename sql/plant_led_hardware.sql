-- LED 专用表（与对接文档一致；JPA ddl-auto=update 也会自动建）
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
    command_type   VARCHAR(64)  NOT NULL COMMENT '命令种类',
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
