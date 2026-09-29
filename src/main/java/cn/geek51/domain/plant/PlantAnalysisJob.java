package cn.geek51.domain.plant;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * 表型分析任务：影像 → 模型 → 指标（模型未就绪时可 Mock 完成）。
 */
@Getter
@Setter
@Entity
@Table(name = "plant_analysis_job", indexes = {
        @Index(name = "idx_plant_job_device", columnList = "device_key,created_at")
})
public class PlantAnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_key", length = 64, nullable = false)
    private String deviceKey;

    @Column(name = "plant_code", length = 64)
    private String plantCode;

    @Column(name = "job_type", length = 64, nullable = false)
    private String jobType;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Lob
    @Column(name = "input_media_ids_json", columnDefinition = "LONGTEXT")
    private String inputMediaIdsJson;

    @Lob
    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "message", length = 512)
    private String message;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME(3)")
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "started_at", columnDefinition = "DATETIME(3)")
    private Date startedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "finished_at", columnDefinition = "DATETIME(3)")
    private Date finishedAt;
}
