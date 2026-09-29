package cn.geek51.domain.plant;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "plant_actuator_state", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plant_actuator", columnNames = {"device_key", "actuator_id"})
})
public class PlantActuatorState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_key", length = 64, nullable = false)
    private String deviceKey;

    @Column(name = "actuator_id", length = 64, nullable = false)
    private String actuatorId;

    @Lob
    @Column(name = "state_json", columnDefinition = "LONGTEXT")
    private String stateJson;

    @Column(name = "source", length = 32)
    private String source;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME(3)")
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", columnDefinition = "DATETIME(3)")
    private Date updatedAt;
}
