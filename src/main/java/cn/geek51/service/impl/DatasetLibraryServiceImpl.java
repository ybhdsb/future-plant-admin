package cn.geek51.service.impl;

import cn.geek51.dao.DatasetLibraryRepository;
import cn.geek51.dao.ModelLibraryRepository;
import cn.geek51.domain.DatasetLibrary;
import cn.geek51.domain.ModelLibrary;
import cn.geek51.service.DatasetLibraryService;
import cn.geek51.service.LibraryStorageHelper;
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
public class DatasetLibraryServiceImpl implements DatasetLibraryService {

    @Autowired
    private DatasetLibraryRepository repository;

    @Autowired
    private ModelLibraryRepository modelRepository;

    @Value("${library.storage-root:uploads/library}")
    private String storageRoot;

    @Override
    public List<DatasetLibrary> listAll(String keyword, String datasetType) {
        if (datasetType != null && !datasetType.trim().isEmpty()
                && !"all".equalsIgnoreCase(datasetType.trim())) {
            return repository.findByDatasetType(normalizeType(datasetType));
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String key = keyword.trim();
            return repository.findByNameContainingIgnoreCaseOrOwnerNameContainingIgnoreCase(key, key);
        }
        return repository.findAll();
    }

    @Override
    public DatasetLibrary getById(Long id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public DatasetLibrary create(String name, String ownerName, String datasetType, String category,
                                 String description, Integer sampleCount, MultipartFile[] files) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("数据集名称不能为空");
        }
        if (ownerName == null || ownerName.trim().isEmpty()) {
            throw new IllegalArgumentException("归属人不能为空");
        }
        try {
            LibraryStorageHelper.SaveResult saved = LibraryStorageHelper.saveFolder(storageRoot, "datasets", files);
            DatasetLibrary dataset = new DatasetLibrary();
            dataset.setName(name.trim());
            dataset.setOwnerName(ownerName.trim());
            dataset.setDatasetType(normalizeType(datasetType));
            dataset.setCategory(blankToNull(category));
            dataset.setDescription(blankToNull(description));
            dataset.setSampleCount(sampleCount);
            dataset.setFamilyKey(slugify(name.trim()));
            dataset.setStatus("ready");
            dataset.setLabelFormat("folder");
            dataset.setFolderName(saved.folderName);
            dataset.setStoragePath(saved.storagePath);
            dataset.setFileCount(saved.fileCount);
            dataset.setTotalSizeBytes(saved.totalSizeBytes);
            Date now = new Date();
            dataset.setCreatedTime(now);
            dataset.setUpdatedTime(now);
            return repository.save(dataset);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("保存数据集文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public DatasetLibrary updateMeta(Long id, String name, String ownerName, String datasetType,
                                     String category, String description, Integer sampleCount) {
        DatasetLibrary dataset = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("数据集不存在"));
        if (name != null && !name.trim().isEmpty()) {
            dataset.setName(name.trim());
        }
        if (ownerName != null && !ownerName.trim().isEmpty()) {
            dataset.setOwnerName(ownerName.trim());
        }
        if (datasetType != null && !datasetType.trim().isEmpty()) {
            dataset.setDatasetType(normalizeType(datasetType));
        }
        if (category != null) {
            dataset.setCategory(blankToNull(category));
        }
        if (description != null) {
            dataset.setDescription(blankToNull(description));
        }
        if (sampleCount != null) {
            dataset.setSampleCount(sampleCount);
        }
        dataset.setUpdatedTime(new Date());
        return repository.save(dataset);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DatasetLibrary dataset = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("数据集不存在"));
        LibraryStorageHelper.deleteQuietly(storageRoot, dataset.getStoragePath());
        repository.deleteById(id);
    }

    @Override
    public Map<String, Object> toView(DatasetLibrary dataset) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", dataset.getId());
        map.put("name", dataset.getName());
        map.put("ownerName", dataset.getOwnerName());
        map.put("datasetType", dataset.getDatasetType());
        map.put("datasetTypeText", "public".equalsIgnoreCase(dataset.getDatasetType()) ? "公共数据集" : "自建数据集");
        map.put("category", dataset.getCategory());
        map.put("flKey", dataset.getFlKey());
        map.put("familyKey", dataset.getFamilyKey());
        map.put("parentDatasetId", dataset.getParentDatasetId());
        map.put("labelFormat", dataset.getLabelFormat());
        map.put("classCount", dataset.getClassCount());
        map.put("status", dataset.getStatus());
        map.put("statusText", statusText(dataset.getStatus()));
        map.put("metricsJson", dataset.getMetricsJson());
        map.put("metrics", parseMetrics(dataset.getMetricsJson()));
        map.put("downloadUrl", "/library/files/datasets/" + dataset.getId() + ".zip");
        map.put("detailUrl", "/datasets/" + dataset.getId());
        map.put("description", dataset.getDescription());
        map.put("sampleCount", dataset.getSampleCount());
        map.put("folderName", dataset.getFolderName());
        map.put("storagePath", dataset.getStoragePath());
        map.put("fileCount", dataset.getFileCount());
        map.put("totalSizeBytes", dataset.getTotalSizeBytes());
        map.put("totalSizeText", LibraryStorageHelper.formatSize(dataset.getTotalSizeBytes()));
        map.put("createdTime", dataset.getCreatedTime());
        map.put("updatedTime", dataset.getUpdatedTime());
        return map;
    }

    @Override
    public Map<String, Object> getDetail(Long id) {
        DatasetLibrary dataset = getById(id);
        if (dataset == null) {
            return null;
        }
        Map<String, Object> detail = new HashMap<>(toView(dataset));
        Map<String, Object> inspect = LibraryStorageHelper.inspectStorage(storageRoot, dataset.getStoragePath());
        detail.put("storage", inspect);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> previews = (List<Map<String, Object>>) inspect.get("previews");
        if (previews != null) {
            for (Map<String, Object> preview : previews) {
                String path = String.valueOf(preview.get("path"));
                preview.put("url", "/library/files/datasets/" + id + "/preview?path="
                        + encodePath(path));
            }
        }

        List<Map<String, Object>> versions = new ArrayList<>();
        String family = dataset.getFamilyKey();
        if (family != null && family.trim().length() > 0) {
            for (DatasetLibrary item : repository.findByFamilyKeyOrderByCreatedTimeAsc(family)) {
                Map<String, Object> v = new HashMap<>();
                v.put("id", item.getId());
                v.put("name", item.getName());
                v.put("versionLabel", versionLabel(item));
                v.put("sampleCount", item.getSampleCount());
                v.put("createdTime", item.getCreatedTime());
                v.put("current", item.getId().equals(id));
                v.put("detailUrl", "/datasets/" + item.getId());
                versions.add(v);
            }
        } else {
            Map<String, Object> v = new HashMap<>();
            v.put("id", dataset.getId());
            v.put("name", dataset.getName());
            v.put("versionLabel", versionLabel(dataset));
            v.put("sampleCount", dataset.getSampleCount());
            v.put("createdTime", dataset.getCreatedTime());
            v.put("current", true);
            v.put("detailUrl", "/datasets/" + dataset.getId());
            versions.add(v);
        }
        detail.put("versions", versions);

        List<Map<String, Object>> lineage = new ArrayList<>();
        lineage.add(lineageNode("dataset", dataset.getName(),
                "数据集入库 · " + ("public".equalsIgnoreCase(dataset.getDatasetType()) ? "公共" : "自建"),
                dataset.getCreatedTime(), "/datasets/" + dataset.getId(), true));

        if (dataset.getParentDatasetId() != null) {
            DatasetLibrary parent = getById(dataset.getParentDatasetId());
            if (parent != null) {
                lineage.add(0, lineageNode("dataset", parent.getName(),
                        "上一版本", parent.getCreatedTime(), "/datasets/" + parent.getId(), false));
            }
        }

        List<ModelLibrary> derived = modelRepository.findBySourceDatasetIdOrderByCreatedTimeAsc(id);
        for (ModelLibrary model : derived) {
            String subtitle = "模型产出";
            if (model.getTrainingMethod() != null && model.getTrainingMethod().trim().length() > 0) {
                subtitle = model.getTrainingMethod() + " 训练产出 · " + nullToDash(model.getVersion());
            } else if (model.getVersion() != null) {
                subtitle = "版本 " + model.getVersion();
            }
            lineage.add(lineageNode("model", model.getName(), subtitle,
                    model.getCreatedTime(), "/model_library/" + model.getId(), false));
        }
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

    private String versionLabel(DatasetLibrary dataset) {
        if (dataset.getMetricsJson() != null && dataset.getMetricsJson().contains("version")) {
            Map<String, Object> metrics = parseMetrics(dataset.getMetricsJson());
            if (metrics.get("version") != null) {
                return String.valueOf(metrics.get("version"));
            }
        }
        if (dataset.getFlKey() != null) {
            return dataset.getFlKey();
        }
        return "v1";
    }

    private Map<String, Object> parseMetrics(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        String text = json.trim();
        // 轻量解析简单 {"k":v,"k2":"v2"}，避免额外依赖
        text = text.replace("{", "").replace("}", "").trim();
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
        if ("draft".equalsIgnoreCase(status)) {
            return "草稿";
        }
        if ("ready".equalsIgnoreCase(status)) {
            return "可用";
        }
        return status == null ? "可用" : status;
    }

    private String encodePath(String path) {
        try {
            return java.net.URLEncoder.encode(path, "UTF-8");
        } catch (Exception e) {
            return path;
        }
    }

    private String slugify(String name) {
        String s = name.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-");
        s = s.replaceAll("^-|-$", "");
        if (s.isEmpty()) {
            return "dataset-" + System.currentTimeMillis();
        }
        return s.length() > 48 ? s.substring(0, 48) : s;
    }

    private String nullToDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private String normalizeType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "custom";
        }
        String t = type.trim().toLowerCase();
        if ("public".equals(t) || "公共".equals(type.trim()) || "公共数据集".equals(type.trim())) {
            return "public";
        }
        return "custom";
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
