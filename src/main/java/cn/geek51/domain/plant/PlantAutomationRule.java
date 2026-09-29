package cn.geek51.domain.plant;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * 通用执行器自动化规则（泵/氧泵/风机/继电器/LED 等均可挂接）。
 *
 * ruleType:
 *   DAILY_WINDOW   — 每天固定时段开启
 *   EVERY_N_DAYS   — 每隔 N 天，在固定时刻开启一段时间
 *   INTERVAL       — 开 X 分钟 / 关 Y 分钟循环
 *   SENSOR_THRESHOLD — 传感器条件触发（如溶氧偏低立即开氧泵）
 */
@Getter
@Setter
@Entity
@Table(name = "plant_automation_rule", indexes = {
        @Index(name = "idx_plant_auto_device", columnList = "device_key,enabled"),
        @Index(name = "idx_plant_auto_actuator", columnList = "actuator_id,enabled")
})
public class PlantAutomationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_key", length = 64, nullable = false)
    private String deviceKey;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "actuator_id", length = 64, nullable = false)
    private String actuatorId;

    @Column(name = "rule_type", length = 32, nullable = false)
    private String ruleType;

    /** ON / OFF；LED 可用 SET_BRIGHTNESS（brightness 字段） */
    @Column(name = "action", length = 32, nullable = false)
    private String action = "ON";

    @Column(name = "brightness")
    private Integer brightness;

    /** DAILY_WINDOW / EVERY_N_DAYS: HH:mm */
    @Column(name = "on_time", length = 16)
    private String onTime;

    /** DAILY_WINDOW: HH:mm */
    @Column(name = "off_time", length = 16)
    private String offTime;

    /** EVERY_N_DAYS: 每隔几天 */
    @Column(name = "every_n_days")
    private Integer everyNDays;

    /** EVERY_N_DAYS: 单次开启持续分钟；也可用于 SENSOR 触发后保持开启时长 */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /** INTERVAL */
    @Column(name = "on_minutes")
    private Integer onMinutes;

    @Column(name = "off_minutes")
    private Integer offMinutes;

    /** SENSOR_THRESHOLD */
    @Column(name = "metric", length = 64)
    private String metric;

    /** LT / LTE / GT / GTE / EQ */
    @Column(name = "operator", length = 16)
    private String operatorName;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    /** 传感触发冷却，避免抖动频繁开关（分钟） */
    @Column(name = "cooldown_minutes")
    private Integer cooldownMinutes;

    /** EVERY_N_DAYS 锚点日期（yyyy-MM-dd），为空则以创建日为锚点 */
    @Column(name = "anchor_date", length = 16)
    private String anchorDate;

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "priority")
    private Integer priority = 100;

    @Column(name = "remark", length = 255)
    private String remark;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_triggered_at", columnDefinition = "DATETIME(3)")
    private Date lastTriggeredAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_applied_at", columnDefinition = "DATETIME(3)")
    private Date lastAppliedAt;

    @Column(name = "last_applied_action", length = 64)
    private String lastAppliedAction;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME(3)")
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", columnDefinition = "DATETIME(3)")
    private Date updatedAt;
}
