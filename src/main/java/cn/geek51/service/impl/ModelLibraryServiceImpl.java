package cn.geek51.service.impl;

import cn.geek51.dao.DatasetLibraryRepository;
import cn.geek51.dao.ModelLibraryRepository;
import cn.geek51.domain.DatasetLibrary;
import cn.geek51.domain.ModelLibrary;
import cn.geek51.service.LibraryStorageHelper;
import cn.geek51.service.ModelLibraryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModelLibraryServiceImpl implements ModelLibraryService {

    @Autowired
    private ModelLibraryRepository repository;

    @Autowired
    private DatasetLibraryRepository datasetRepository;

    @Value("${library.storage-root:uploads/library}")
    private String storageRoot;

    @Override
    public List<ModelLibrary> listAll(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            String key = keyword.trim();
            return repository.findByNameContainingIgnoreCaseOrOwnerNameContainingIgnoreCase(key, key);
        }
        return repository.findAll();
    }

    @Override
    public ModelLibrary getById(Long id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public ModelLibrary create(String name, String ownerName, String framework, String version,
                               String category, String description, MultipartFile[] files) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        if (ownerName == null || ownerName.trim().isEmpty()) {
            throw new IllegalArgumentException("归属人不能为空");
        }
        try {
            LibraryStorageHelper.SaveResult saved = LibraryStorageHelper.saveFolder(storageRoot, "models", files);
            ModelLibrary model = new ModelLibrary();
            model.setName(name.trim());
            model.setOwnerName(ownerName.trim());
            model.setFramework(blankToNull(framework));
            model.setVersion(blankToNull(version) == null ? "v1.0" : blankToNull(version));
            model.setCategory(blankToNull(category));
            model.setLibraryType("custom");
            model.setFamilyKey(slugify(name.trim()));
            model.setStatus("experimental");
            model.setDescription(blankToNull(description));
            model.setFolderName(saved.folderName);
            model.setStoragePath(saved.storagePath);
            model.setFileCount(saved.fileCount);
            model.setTotalSizeBytes(saved.totalSizeBytes);
            Date now = new Date();
            model.setCreatedTime(now);
            model.setUpdatedTime(now);
            return repository.save(model);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("保存模型文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ModelLibrary updateMeta(Long id, String name, String ownerName, String framework,
                                   String version, String category, String description) {
        ModelLibrary model = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模型不存在"));
        if (name != null && !name.trim().isEmpty()) {
            model.setName(name.trim());
        }
        if (ownerName != null && !ownerName.trim().isEmpty()) {
            model.setOwnerName(ownerName.trim());
        }
        if (framework != null) {
            model.setFramework(blankToNull(framework));
        }
        if (version != null) {
            model.setVersion(blankToNull(version));
        }
        if (category != null) {
            model.setCategory(blankToNull(category));
        }
        if (description != null) {
            model.setDescription(blankToNull(description));
        }
        model.setUpdatedTime(new Date());
        return repository.save(model);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ModelLibrary model = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模型不存在"));
        LibraryStorageHelper.deleteQuietly(storageRoot, model.getStoragePath());
        repository.deleteById(id);
    }

    @Override
    public Map<String, Object> toView(ModelLibrary model) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", model.getId());
        map.put("name", model.getName());
        map.put("ownerName", model.getOwnerName());
        map.put("framework", model.getFramework());
        map.put("version", model.getVersion());
        map.put("category", model.getCategory());
        map.put("libraryType", model.getLibraryType());
        map.put("libraryTypeText", "public".equalsIgnoreCase(model.getLibraryType()) ? "公共模型" : "自建模型");
        map.put("flKey", model.getFlKey());
        map.put("familyKey", model.getFamilyKey());
        map.put("parentModelId", model.getParentModelId());
        map.put("sourceDatasetId", model.getSourceDatasetId());
        map.put("trainingMethod", model.getTrainingMethod());
        map.put("inputShape", model.getInputShape());
        map.put("outputClasses", model.getOutputClasses());
        map.put("status", model.getStatus());
        map.put("statusText", statusText(model.getStatus()));
        map.put("metricsJson", model.getMetricsJson());
        map.put("metrics", parseMetrics(model.getMetricsJson()));
        map.put("downloadUrl", "/library/files/models/" + model.getId() + ".zip");
        map.put("detailUrl", "/model_library/" + model.getId());
        map.put("description", model.getDescription());
        map.put("folderName", model.getFolderName());
        map.put("storagePath", model.getStoragePath());
        map.put("fileCount", model.getFileCount());
        map.put("totalSizeBytes", model.getTotalSizeBytes());
        map.put("totalSizeText", LibraryStorageHelper.formatSize(model.getTotalSizeBytes()));
        map.put("createdTime", model.getCreatedTime());
        map.put("updatedTime", model.getUpdatedTime());

        if (model.getSourceDatasetId() != null) {
            DatasetLibrary ds = datasetRepository.findById(model.getSourceDatasetId()).orElse(null);
            if (ds != null) {
                map.put("sourceDatasetName", ds.getName());
                map.put("sourceDatasetUrl", "/datasets/" + ds.getId());
            }
        }
        return map;
    }

    @Override
    public Map<String, Object> getDetail(Long id) {
        ModelLibrary model = getById(id);
        if (model == null) {
            return null;
        }
        Map<String, Object> detail = new HashMap<>(toView(model));
        Map<String, Object> inspect = LibraryStorageHelper.inspectStorage(storageRoot, model.getStoragePath());
        detail.put("storage", inspect);

        List<Map<String, Object>> versions = new ArrayList<>();
        String family = model.getFamilyKey();
        if (family != null && family.trim().length() > 0) {
            for (ModelLibrary item : repository.findByFamilyKeyOrderByCreatedTimeAsc(family)) {
                Map<String, Object> v = new HashMap<>();
                v.put("id", item.getId());
                v.put("name", item.getName());
                v.put("version", item.getVersion());
                v.put("metrics", parseMetrics(item.getMetricsJson()));
                v.put("createdTime", item.getCreatedTime());
                v.put("current", item.getId().equals(id));
                v.put("detailUrl", "/model_library/" + item.getId());
                versions.add(v);
            }
        } else {
            Map<String, Object> v = new HashMap<>();
            v.put("id", model.getId());
            v.put("name", model.getName());
            v.put("version", model.getVersion());
            v.put("metrics", parseMetrics(model.getMetricsJson()));
            v.put("createdTime", model.getCreatedTime());
            v.put("current", true);
            v.put("detailUrl", "/model_library/" + model.getId());
            versions.add(v);
        }
        detail.put("versions", versions);

        List<Map<String, Object>> lineage = new ArrayList<>();
        if (model.getSourceDatasetId() != null) {
            DatasetLibrary ds = datasetRepository.findById(model.getSourceDatasetId()).orElse(null);
            if (ds != null) {
                lineage.add(lineageNode("dataset", ds.getName(),
                        "训练数据 · " + nullToDash(ds.getCategory()),
                        ds.getCreatedTime(), "/datasets/" + ds.getId(), false));
            }
        }
        if (model.getParentModelId() != null) {
            ModelLibrary parent = getById(model.getParentModelId());
            if (parent != null) {
                lineage.add(lineageNode("model", parent.getName() + " " + nullToDash(parent.getVersion()),
                        "上一版本", parent.getCreatedTime(), "/model_library/" + parent.getId(), false));
            }
        }
        String trainSub = "模型版本";
        if (model.getTrainingMethod() != null && model.getTrainingMethod().trim().length() > 0) {
            trainSub = model.getTrainingMethod() + " · " + nullToDash(model.getVersion());
        } else if (model.getVersion() != null) {
            trainSub = "版本 " + model.getVersion();
        }
        lineage.add(lineageNode("model", model.getName(), trainSub,
                model.getCreatedTime(), "/model_library/" + model.getId(), true));
        detail.put("lineage", lineage);
        return detail;
    }

    private Map<String, Object> lineageNode(String type, String title, String subtitle,
                                            Date time, String href, boolean current) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", type);
        node.put("title", title);
        node.put("subtitle", subtitle);
        node.put("time", time);
        node.put("href", href);
        node.put("current", current);
        return node;
    }

    private Map<String, Object> parseMetrics(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        String text = json.trim().replace("{", "").replace("}", "").trim();
        if (text.isEmpty()) {
            return map;
        }
        String[] parts = text.split(",");
        for (String part : parts) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim().replace("\"", "");
            String value = kv[1].trim().replace("\"", "");
            if (value.matches("-?\\d+(\\.\\d+)?")) {
                if (value.contains(".")) {
                    map.put(key, Double.valueOf(value));
                } else {
                    map.put(key, Long.valueOf(value));
                }
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    private String statusText(String status) {
        if ("experimental".equalsIgnoreCase(status)) {
            return "实验中";
        }
        if ("deployable".equalsIgnoreCase(status)) {
            return "可部署";
        }
        if ("deployed".equalsIgnoreCase(status)) {
            return "已下发";
        }
        return status == null ? "实验中" : status;
    }

    private String slugify(String name) {
        String s = name.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-");
        s = s.replaceAll("^-|-$", "");
        if (s.isEmpty()) {
            return "model-" + System.currentTimeMillis();
        }
        return s.length() > 48 ? s.substring(0, 48) : s;
    }

    private String nullToDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
