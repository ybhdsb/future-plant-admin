package cn.geek51.domain.plant;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "plant_runtime_event", indexes = {
        @Index(name = "idx_plant_event_device", columnList = "device_key,created_at")
})
public class PlantRuntimeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_key", length = 64, nullable = false)
    private String deviceKey;

    @Column(name = "level_name", length = 16, nullable = false)
    private String level;

    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "message", length = 512)
    private String message;

    @Lob
    @Column(name = "context_json", columnDefinition = "LONGTEXT")
    private String contextJson;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME(3)")
    private Date createdAt;
}
