package cn.geek51.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * 模型库条目
 */
@Getter
@Setter
@Entity
@Table(name = "model_library")
public class ModelLibrary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模型名称 */
    @Column(nullable = false, length = 128)
    private String name;

    /** 归属人 */
    @Column(name = "owner_name", length = 64)
    private String ownerName;

    /** 框架：PyTorch / TensorFlow / ONNX / Other */
    @Column(length = 64)
    private String framework;

    /** 版本号 */
    @Column(length = 64)
    private String version;

    /** 分类标签，如 图像分类 / 目标检测 / 音频 */
    @Column(length = 64)
    private String category;

    /** public=公共模型，custom=自建模型 */
    @Column(name = "library_type", length = 32)
    private String libraryType;

    /** 联邦训练使用的模型名，如 lenet5 / resnet18 */
    @Column(name = "fl_key", length = 64)
    private String flKey;

    @Column(length = 512)
    private String description;

    /** 同一模型的版本族，如 pig-behavior-cnn */
    @Column(name = "family_key", length = 64)
    private String familyKey;

    /** 上一版本模型 ID */
    @Column(name = "parent_model_id")
    private Long parentModelId;

    /** 训练来源数据集 ID */
    @Column(name = "source_dataset_id")
    private Long sourceDatasetId;

    /** 训练算法，如 FedBuff */
    @Column(name = "training_method", length = 64)
    private String trainingMethod;

    /** 输入形状，如 3x224x224 */
    @Column(name = "input_shape", length = 64)
    private String inputShape;

    /** 输出类别数 */
    @Column(name = "output_classes")
    private Integer outputClasses;

    /** 状态：experimental / deployable / deployed */
    @Column(length = 32)
    private String status;

    /** 指标 JSON，如 {"accuracy":0.92,"f1":0.90} */
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
