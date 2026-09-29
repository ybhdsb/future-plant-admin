package cn.geek51.domain.plant;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "plant_media_asset", indexes = {
        @Index(name = "idx_plant_media_device", columnList = "device_key,captured_at")
})
public class PlantMediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_key", length = 64, nullable = false)
    private String deviceKey;

    @Column(name = "media_type", length = 32, nullable = false)
    private String mediaType;

    @Column(name = "storage_path", length = 512)
    private String storagePath;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "captured_at", columnDefinition = "DATETIME(3)")
    private Date capturedAt;

    @Lob
    @Column(name = "meta_json", columnDefinition = "LONGTEXT")
    private String metaJson;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME(3)")
    private Date createdAt;
}
