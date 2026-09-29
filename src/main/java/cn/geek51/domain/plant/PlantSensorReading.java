package cn.geek51.domain.plant;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "plant_sensor_reading", indexes = {
        @Index(name = "idx_plant_reading_query", columnList = "device_key,metric,sampled_at")
})
public class PlantSensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_key", length = 64, nullable = false)
    private String deviceKey;

    @Column(name = "metric", length = 64, nullable = false)
    private String metric;

    @Column(name = "value_num")
    private Double valueNum;

    @Column(name = "value_text", length = 255)
    private String valueText;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "quality", length = 32)
    private String quality;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "sampled_at", columnDefinition = "DATETIME(3)")
    private Date sampledAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "received_at", columnDefinition = "DATETIME(3)")
    private Date receivedAt;

    @Lob
    @Column(name = "raw_json", columnDefinition = "LONGTEXT")
    private String rawJson;
}
