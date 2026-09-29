package cn.geek51.domain.plant;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * 表型指标时序：可由 Mock / 手工 / 模型分析写入。
 */
@Getter
@Setter
@Entity
@Table(name = "plant_phenotype_metric", indexes = {
        @Index(name = "idx_plant_pheno_query", columnList = "device_key,plant_code,metric,sampled_at")
})
public class PlantPhenotypeMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_key", length = 64, nullable = false)
    private String deviceKey;

    @Column(name = "plant_code", length = 64, nullable = false)
    private String plantCode;

    @Column(name = "metric", length = 64, nullable = false)
    private String metric;

    @Column(name = "value_num")
    private Double valueNum;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "source", length = 32)
    private String source;

    @Column(name = "job_id")
    private Long jobId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "sampled_at", columnDefinition = "DATETIME(3)")
    private Date sampledAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "DATETIME(3)")
    private Date createdAt;
}
