package cn.geek51.domain.plant;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "plant_led_command_log", indexes = {
        @Index(name = "idx_led_cmd_rack", columnList = "rack_key,created_at")
})
public class PlantLedCommandLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rack_key", length = 64, nullable = false)
    private String rackKey;

    @Column(name = "command_type", length = 64, nullable = false)
    private String commandType;

    @Column(name = "scope", length = 16, nullable = false)
    private String scope;

    @Column(name = "bus_address", length = 8)
    private String busAddress;

    @Lob
    @Column(name = "payload_json", columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "result_message", length = 512)
    private String resultMessage;

    @Column(name = "operator_name", length = 64)
    private String operatorName;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME(3)")
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "finished_at", columnDefinition = "DATETIME(3)")
    private Date finishedAt;
}
