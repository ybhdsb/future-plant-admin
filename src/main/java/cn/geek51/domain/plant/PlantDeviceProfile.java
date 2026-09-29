package cn.geek51.domain.plant;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "plant_device_profile", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plant_device_profile_key", columnNames = "device_key")
})
public class PlantDeviceProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_key", length = 64, nullable = false)
    private String deviceKey;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "firmware_version", length = 64)
    private String firmwareVersion;

    @Lob
    @Column(name = "config_json", columnDefinition = "LONGTEXT")
    private String configJson;

    @Column(name = "mock_enabled")
    private Boolean mockEnabled = true;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME(3)")
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", columnDefinition = "DATETIME(3)")
    private Date updatedAt;
}
