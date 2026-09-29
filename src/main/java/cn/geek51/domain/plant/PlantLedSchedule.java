package cn.geek51.domain.plant;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * LED 定时策略。
 * scheduleType:
 *   DAILY_WINDOW — 每天固定时段开灯（onTime~offTime）
 *   INTERVAL     — 开灯 onMinutes 分钟，关灯 offMinutes 分钟，循环
 */
@Getter
@Setter
@Entity
@Table(name = "plant_led_schedule", indexes = {
        @Index(name = "idx_plant_led_sched_device", columnList = "device_key,enabled")
})
public class PlantLedSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_key", length = 64, nullable = false)
    private String deviceKey;

    @Column(name = "name", length = 128)
    private String name;

    /** DAILY_WINDOW / INTERVAL */
    @Column(name = "schedule_type", length = 32, nullable = false)
    private String scheduleType;

    /** 开灯亮度 0-100 */
    @Column(name = "brightness")
    private Integer brightness = 80;

    /** DAILY_WINDOW: HH:mm */
    @Column(name = "on_time", length = 16)
    private String onTime;

    /** DAILY_WINDOW: HH:mm */
    @Column(name = "off_time", length = 16)
    private String offTime;

    /** INTERVAL: 开灯持续分钟 */
    @Column(name = "on_minutes")
    private Integer onMinutes;

    /** INTERVAL: 关灯持续分钟 */
    @Column(name = "off_minutes")
    private Integer offMinutes;

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "remark", length = 255)
    private String remark;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_applied_at", columnDefinition = "DATETIME(3)")
    private Date lastAppliedAt;

    @Column(name = "last_applied_brightness")
    private Integer lastAppliedBrightness;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME(3)")
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", columnDefinition = "DATETIME(3)")
    private Date updatedAt;
}
