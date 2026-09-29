package cn.geek51.domain;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.persistence.*;
import java.util.Date;

/**
 * @author lzh
 * @since 2025-02-23
 */
@Getter
@Setter
@Entity
@Table(name = "devices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_devices_device_key", columnNames = "device_key")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class Devices {

    /**
     * 主键，自增长
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonAlias({"device_id", "id"})
    private Long deviceId;

    /**
     * 稳定设备身份，换 IP / 换 Wi-Fi 后不变
     */
    @Column(name = "device_key", length = 64)
    @JsonAlias({"device_key", "deviceKey", "client_id", "clientId"})
    private String deviceKey;

    @JsonAlias({"device_name", "name"})
    private String deviceName;

    /**
     * jetson / raspberry / robot / phone / device
     */
    @Column(name = "device_type", length = 64)
    @JsonAlias({"device_type", "deviceType", "type"})
    private String deviceType;

    @JsonAlias({"ip_address", "ip"})
    private String ipAddress;

    /**
     * SSH 登录用户名（用于网页添加后自动部署心跳）
     */
    @Column(name = "ssh_username", length = 64)
    @JsonAlias({"ssh_username", "sshUsername", "username"})
    private String sshUsername;

    /**
     * SSH 密码（实验室设备常用；列表接口不返回）
     */
    @Column(name = "ssh_password", length = 128)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @JsonAlias({"ssh_password", "sshPassword", "password"})
    private String sshPassword;

    private String status;
    @JsonAlias({"latitude", "lat", "latituded"})
    private String latituded;
    @JsonAlias({"longitude", "lng", "longituded"})
    private String longituded;

    @Temporal(TemporalType.TIMESTAMP)
    @JsonAlias({"last_heartbeat_time", "lastHeartbeat", "last_report"})
    private Date lastHeartbeatTime;

    /** 心跳上报的运行态指标，不落库，用于能力画像更新 */
    @Transient
    @JsonAlias({"cpu_load", "cpuLoad"})
    private Double cpuLoad;

    @Transient
    @JsonAlias({"mem_pct", "memPct", "memory_pct"})
    private Double memPct;

    @Transient
    @JsonAlias({"gpu_load", "gpuLoad"})
    private Double gpuLoad;

    @Transient
    @JsonAlias({"battery_pct", "batteryPct", "battery"})
    private Double batteryPct;

    @Transient
    @JsonAlias({"rtt_ms", "rttMs"})
    private Double rttMs;

    @Transient
    @JsonAlias({"packet_loss_pct", "packetLossPct", "loss_pct"})
    private Double packetLossPct;

    @Transient
    @JsonAlias({"temperature_c", "temperatureC", "temp_c"})
    private Double temperatureC;

    @Transient
    @JsonAlias({"scene_tag", "sceneTag"})
    private String sceneTag;

    public Devices() {
    }

    public Devices(Long deviceId, String deviceName, String status, String latituded, String longituded) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.status = status;
        this.latituded = latituded;
        this.longituded = longituded;
    }
}
