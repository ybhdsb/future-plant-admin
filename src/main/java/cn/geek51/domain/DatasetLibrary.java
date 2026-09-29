package cn.geek51.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * 数据集条目
 */
@Getter
@Setter
@Entity
@Table(name = "dataset_library")
public class DatasetLibrary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 数据集名称，如 CIFAR-10 / 生猪音频 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 归属人 */
    @Column(name = "owner_name", length = 64)
    private String ownerName;

    /**
     * 类型：public=公共数据集，custom=自建数据集
     */
    @Column(name = "dataset_type", length = 32)
    private String datasetType;

    /** 分类，如 图像 / 音频 / 文本 */
    @Column(length = 64)
    private String category;

    /** 联邦训练使用的数据集名，如 mnist / cifar10 */
    @Column(name = "fl_key", length = 64)
    private String flKey;

    @Column(length = 512)
    private String description;

    /** 样本数量（可选） */
    @Column(name = "sample_count")
    private Integer sampleCount;

    /** 同一资产的版本族，如 pig-behavior */
    @Column(name = "family_key", length = 64)
    private String familyKey;

    /** 上一版本数据集 ID */
    @Column(name = "parent_dataset_id")
    private Long parentDatasetId;

    /** 标注格式：folder / YOLO / COCO / CSV */
    @Column(name = "label_format", length = 32)
    private String labelFormat;

    /** 类别数 */
    @Column(name = "class_count")
    private Integer classCount;

    /** 状态：draft / ready */
    @Column(length = 32)
    private String status;

    /** 质量/统计指标 JSON */
    @Column(name = "metrics_json", length = 1024)
    private String metricsJson;

    /** 用户选择的本地文件夹名 */
    @Column(name = "folder_name", length = 255)
    private String folderName;

    /** 服务器存储相对路径 */
    @Column(name = "storage_path", length = 512)
    private String storagePath;

    @Column(name = "file_count")
    private Integer fileCount;

    @Column(name = "total_size_bytes")
    private Long totalSizeBytes;

    @Temporal(TemporalType.TIMESTAMP)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "created_time")
    private Date createdTime;

    @Temporal(TemporalType.TIMESTAMP)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "updated_time")
    private Date updatedTime;
}
