package cn.geek51.domain.plant;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * 植株档案：穴位/品种/生育期等，驱动数字化植株展示。
 */
@Getter
@Setter
@Entity
@Table(name = "plant_crop_specimen", indexes = {
        @Index(name = "idx_plant_crop_device", columnList = "device_key,plant_code", unique = true)
})
public class PlantCropSpecimen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_key", length = 64, nullable = false)
    private String deviceKey;

    @Column(name = "plant_code", length = 64, nullable = false)
    private String plantCode;

    @Column(name = "slot_code", length = 32)
    private String slotCode;

    @Column(name = "crop_type", length = 64)
    private String cropType;

    @Column(name = "variety", length = 128)
    private String variety;

    @Temporal(TemporalType.DATE)
    @Column(name = "sow_date")
    private Date sowDate;

    @Column(name = "growth_stage", length = 32)
    private String growthStage;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "pos_row")
    private Integer posRow;

    @Column(name = "pos_col")
    private Integer posCol;

    @Column(name = "remark", length = 255)
    private String remark;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME(3)")
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", columnDefinition = "DATETIME(3)")
    private Date updatedAt;
}
