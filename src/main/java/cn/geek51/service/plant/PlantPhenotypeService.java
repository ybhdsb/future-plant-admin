package cn.geek51.service.plant;

import cn.geek51.dao.plant.PlantAnalysisJobRepository;
import cn.geek51.dao.plant.PlantCropSpecimenRepository;
import cn.geek51.dao.plant.PlantMediaAssetRepository;
import cn.geek51.dao.plant.PlantPhenotypeMetricRepository;
import cn.geek51.domain.plant.PlantAnalysisJob;
import cn.geek51.domain.plant.PlantCropSpecimen;
import cn.geek51.domain.plant.PlantMediaAsset;
import cn.geek51.domain.plant.PlantPhenotypeMetric;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 作物表型：植株档案、指标、多模态影像关联、分析任务（模型未就绪时用 Mock）。
 */
@Service
public class PlantPhenotypeService {

    public static final String[] STAGES = {"VE", "V3", "V6", "V9", "VT", "R1"};

    private final PlantService plantService;
    private final PlantCropSpecimenRepository specimenRepository;
    private final PlantPhenotypeMetricRepository metricRepository;
    private final PlantAnalysisJobRepository jobRepository;
    private final PlantMediaAssetRepository mediaRepository;
    private final ObjectMapper objectMapper;

    public PlantPhenotypeService(PlantService plantService,
                                 PlantCropSpecimenRepository specimenRepository,
                                 PlantPhenotypeMetricRepository metricRepository,
                                 PlantAnalysisJobRepository jobRepository,
                                 PlantMediaAssetRepository mediaRepository,
                                 ObjectMapper objectMapper) {
        this.plantService = plantService;
        this.specimenRepository = specimenRepository;
        this.metricRepository = metricRepository;
        this.jobRepository = jobRepository;
        this.mediaRepository = mediaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ensureSeed(String deviceKey) {
        String key = plantService.resolveDeviceKey(deviceKey);
        plantService.ensureProfile(key);
        if (specimenRepository.countByDeviceKey(key) > 0) {
            return;
        }
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -28);
        Date sowBase = cal.getTime();

        Object[][] rows = {
                {"P-01", "A1", "V6", 1, 1, 42.5, 8, 12.4, 286.0, 41.2},
                {"P-02", "A2", "V6", 1, 2, 38.0, 7, 11.8, 252.0, 39.5},
                {"P-03", "A3", "V3", 1, 3, 22.0, 5, 8.6, 140.0, 36.0},
                {"P-04", "B1", "V9", 2, 1, 58.0, 10, 14.2, 410.0, 43.8},
                {"P-05", "B2", "V6", 2, 2, 45.5, 8, 12.9, 300.0, 40.1},
                {"P-06", "B3", "VT", 2, 3, 72.0, 12, 16.0, 520.0, 45.0},
                {"P-07", "C1", "V3", 3, 1, 18.5, 4, 7.9, 110.0, 34.2},
                {"P-08", "C2", "R1", 3, 2, 86.0, 14, 17.5, 610.0, 46.5}
        };

        List<PlantCropSpecimen> specimens = new ArrayList<>();
        for (Object[] r : rows) {
            PlantCropSpecimen s = new PlantCropSpecimen();
            s.setDeviceKey(key);
            s.setPlantCode((String) r[0]);
            s.setSlotCode((String) r[1]);
            s.setCropType("玉米");
            s.setVariety("郑单958（演示）");
            s.setSowDate(sowBase);
            s.setGrowthStage((String) r[2]);
            s.setStatus("GROWING");
            s.setPosRow((Integer) r[3]);
            s.setPosCol((Integer) r[4]);
            s.setRemark("Mock 植株，待实机采集替换");
            s.setCreatedAt(now);
            s.setUpdatedAt(now);
            specimens.add(specimenRepository.save(s));

            String plantCode = (String) r[0];
            seedMetric(key, plantCode, "height_cm", (Double) r[5], "cm", "MOCK", now, null);
            seedMetric(key, plantCode, "leaf_count", ((Number) r[6]).doubleValue(), "片", "MOCK", now, null);
            seedMetric(key, plantCode, "stem_diameter_mm", (Double) r[7], "mm", "MOCK", now, null);
            seedMetric(key, plantCode, "leaf_area_cm2", (Double) r[8], "cm²", "MOCK", now, null);
            seedMetric(key, plantCode, "spad", (Double) r[9], "", "MOCK", now, null);

            // 近 5 天简易生长曲线
            for (int d = 1; d <= 4; d++) {
                Calendar c = Calendar.getInstance();
                c.add(Calendar.DAY_OF_YEAR, -d);
                double h = (Double) r[5] - d * (1.2 + (plantCode.hashCode() % 7) * 0.15);
                seedMetric(key, plantCode, "height_cm", Math.max(5, h), "cm", "MOCK", c.getTime(), null);
            }
        }

        // 多模态影像占位（复用样例图 + meta）
        plantService.ensureSampleMedia(key);
        seedModalityMedia(key, "P-01", "RGB", now);
        seedModalityMedia(key, "P-01", "DEPTH", now);
        seedModalityMedia(key, "P-01", "IR", now);
        seedModalityMedia(key, "P-04", "RGB", now);
        seedModalityMedia(key, "P-06", "RGB", now);

        // 分析任务占位
        PlantAnalysisJob done = new PlantAnalysisJob();
        done.setDeviceKey(key);
        done.setPlantCode("P-01");
        done.setJobType("PHENOTYPE_EXTRACT");
        done.setStatus("SUCCESS");
        done.setInputMediaIdsJson("[\"mock-rgb\",\"mock-depth\"]");
        done.setResultJson("{\"height_cm\":42.5,\"leaf_count\":8,\"note\":\"mock inference\"}");
        done.setMessage("Mock 推理完成（模型未接入）");
        done.setCreatedAt(new Date(now.getTime() - 3600_000));
        done.setStartedAt(new Date(now.getTime() - 3500_000));
        done.setFinishedAt(new Date(now.getTime() - 3400_000));
        jobRepository.save(done);

        PlantAnalysisJob queued = new PlantAnalysisJob();
        queued.setDeviceKey(key);
        queued.setPlantCode("P-06");
        queued.setJobType("PHENOTYPE_EXTRACT");
        queued.setStatus("QUEUED");
        queued.setInputMediaIdsJson("[\"mock-rgb\"]");
        queued.setMessage("等待表型模型接入");
        queued.setCreatedAt(now);
        jobRepository.save(queued);

        plantService.recordEvent(key, "INFO", "PHENOTYPE_SEED",
                "已种子化 " + specimens.size() + " 株玉米表型演示数据");
    }

    private void seedMetric(String deviceKey, String plantCode, String metric, double value,
                            String unit, String source, Date sampledAt, Long jobId) {
        PlantPhenotypeMetric m = new PlantPhenotypeMetric();
        m.setDeviceKey(deviceKey);
        m.setPlantCode(plantCode);
        m.setMetric(metric);
        m.setValueNum(value);
        m.setUnit(unit);
        m.setSource(source);
        m.setJobId(jobId);
        m.setSampledAt(sampledAt);
        m.setCreatedAt(new Date());
        metricRepository.save(m);
    }

    private void seedModalityMedia(String deviceKey, String plantCode, String modality, Date now) {
        PlantMediaAsset a = new PlantMediaAsset();
        a.setDeviceKey(deviceKey);
        a.setMediaType(modality);
        a.setStoragePath("");
        a.setCapturedAt(new Date(now.getTime() - modality.hashCode() % 800_000));
        a.setCreatedAt(now);
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("plantCode", plantCode);
            meta.put("modality", modality);
            meta.put("label", plantCode + " · " + modalityLabel(modality));
            meta.put("mock", true);
            a.setMetaJson(objectMapper.writeValueAsString(meta));
        } catch (Exception e) {
            a.setMetaJson("{\"plantCode\":\"" + plantCode + "\",\"modality\":\"" + modality + "\"}");
        }
        mediaRepository.save(a);
    }

    private static String modalityLabel(String m) {
        if ("DEPTH".equalsIgnoreCase(m)) return "深度图";
        if ("IR".equalsIgnoreCase(m) || "INFRARED".equalsIgnoreCase(m)) return "红外";
        return "可见光 RGB";
    }

    public List<Map<String, Object>> listSpecimens(String deviceKey) {
        String key = plantService.resolveDeviceKey(deviceKey);
        ensureSeed(key);
        List<PlantCropSpecimen> list = specimenRepository.findByDeviceKeyOrderByPosRowAscPosColAsc(key);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlantCropSpecimen s : list) {
            Map<String, Object> m = toSpecimenMap(s);
            m.put("latest", latestMetricsMap(key, s.getPlantCode()));
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> getSpecimenDetail(String deviceKey, String plantCode) {
        String key = plantService.resolveDeviceKey(deviceKey);
        ensureSeed(key);
        PlantCropSpecimen s = specimenRepository.findByDeviceKeyAndPlantCode(key, plantCode)
                .orElseThrow(() -> new IllegalArgumentException("植株不存在: " + plantCode));
        Map<String, Object> m = toSpecimenMap(s);
        m.put("latest", latestMetricsMap(key, plantCode));
        m.put("history", queryMetrics(key, plantCode, "height_cm", null, null, 20));
        m.put("media", listPhenotypeMedia(key, plantCode, 12));
        return m;
    }

    public List<Map<String, Object>> queryMetrics(String deviceKey, String plantCode, String metric,
                                                  Date from, Date to, int limit) {
        String key = plantService.resolveDeviceKey(deviceKey);
        ensureSeed(key);
        List<PlantPhenotypeMetric> list = metricRepository.query(
                key,
                blankToNull(plantCode),
                blankToNull(metric),
                from, to,
                PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlantPhenotypeMetric x : list) {
            out.add(toMetricMap(x));
        }
        return out;
    }

    public List<Map<String, Object>> listJobs(String deviceKey, int limit) {
        String key = plantService.resolveDeviceKey(deviceKey);
        ensureSeed(key);
        List<PlantAnalysisJob> list = jobRepository.findByDeviceKeyOrderByCreatedAtDesc(
                key, PageRequest.of(0, Math.max(1, limit)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlantAnalysisJob j : list) {
            out.add(toJobMap(j));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> createJob(Map<String, Object> body) {
        String key = plantService.resolveDeviceKey(asString(body.get("deviceKey")));
        ensureSeed(key);
        String plantCode = asString(body.get("plantCode"));
        if (plantCode == null || plantCode.trim().isEmpty()) {
            throw new IllegalArgumentException("plantCode 必填");
        }
        specimenRepository.findByDeviceKeyAndPlantCode(key, plantCode)
                .orElseThrow(() -> new IllegalArgumentException("植株不存在: " + plantCode));

        Date now = new Date();
        PlantAnalysisJob job = new PlantAnalysisJob();
        job.setDeviceKey(key);
        job.setPlantCode(plantCode);
        job.setJobType(asString(body.get("jobType")) == null ? "PHENOTYPE_EXTRACT" : asString(body.get("jobType")));
        job.setStatus("QUEUED");
        try {
            Object mediaIds = body.get("mediaIds");
            job.setInputMediaIdsJson(mediaIds == null ? "[]" : objectMapper.writeValueAsString(mediaIds));
        } catch (Exception e) {
            job.setInputMediaIdsJson("[]");
        }
        job.setMessage("已入队（模型未接入，可一键 Mock 完成）");
        job.setCreatedAt(now);
        job = jobRepository.save(job);
        return toJobMap(job);
    }

    @Transactional
    public Map<String, Object> mockCompleteJob(Long id) {
        PlantAnalysisJob job = jobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        Date now = new Date();
        job.setStatus("SUCCESS");
        job.setStartedAt(job.getStartedAt() == null ? now : job.getStartedAt());
        job.setFinishedAt(now);
        job.setMessage("Mock 推理完成，已写回表型指标");

        Map<String, Object> latest = latestMetricsMap(job.getDeviceKey(), job.getPlantCode());
        double height = num(latest.get("height_cm"), 40) + 1.5;
        double leaves = num(latest.get("leaf_count"), 8) + 1;
        double stem = num(latest.get("stem_diameter_mm"), 12) + 0.3;
        double area = num(latest.get("leaf_area_cm2"), 280) + 18;
        double spad = num(latest.get("spad"), 40) + 0.8;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("height_cm", round1(height));
        result.put("leaf_count", (int) leaves);
        result.put("stem_diameter_mm", round1(stem));
        result.put("leaf_area_cm2", round1(area));
        result.put("spad", round1(spad));
        result.put("source", "MOCK_MODEL");
        try {
            job.setResultJson(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            job.setResultJson("{}");
        }
        jobRepository.save(job);

        seedMetric(job.getDeviceKey(), job.getPlantCode(), "height_cm", height, "cm", "MODEL_MOCK", now, job.getId());
        seedMetric(job.getDeviceKey(), job.getPlantCode(), "leaf_count", leaves, "片", "MODEL_MOCK", now, job.getId());
        seedMetric(job.getDeviceKey(), job.getPlantCode(), "stem_diameter_mm", stem, "mm", "MODEL_MOCK", now, job.getId());
        seedMetric(job.getDeviceKey(), job.getPlantCode(), "leaf_area_cm2", area, "cm²", "MODEL_MOCK", now, job.getId());
        seedMetric(job.getDeviceKey(), job.getPlantCode(), "spad", spad, "", "MODEL_MOCK", now, job.getId());

        // 同步生育期粗估
        specimenRepository.findByDeviceKeyAndPlantCode(job.getDeviceKey(), job.getPlantCode()).ifPresent(s -> {
            s.setGrowthStage(guessStage(height));
            s.setUpdatedAt(now);
            specimenRepository.save(s);
        });
        return toJobMap(job);
    }

    public List<Map<String, Object>> listPhenotypeMedia(String deviceKey, String plantCode, int limit) {
        String key = plantService.resolveDeviceKey(deviceKey);
        ensureSeed(key);
        List<PlantMediaAsset> list = mediaRepository.findByDeviceKeyOrderByCapturedAtDesc(
                key, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlantMediaAsset a : list) {
            Map<String, Object> meta = parseMeta(a.getMetaJson());
            String pc = asString(meta.get("plantCode"));
            if (plantCode != null && !plantCode.trim().isEmpty()
                    && pc != null && !plantCode.equals(pc)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("deviceKey", a.getDeviceKey());
            m.put("mediaType", a.getMediaType());
            m.put("modality", meta.get("modality") != null ? meta.get("modality") : a.getMediaType());
            m.put("plantCode", pc);
            m.put("label", meta.get("label") != null ? meta.get("label")
                    : (pc == null ? "采集图" : pc + " · " + modalityLabel(a.getMediaType())));
            boolean hasFile = a.getStoragePath() != null && !a.getStoragePath().trim().isEmpty();
            m.put("url", hasFile
                    ? ("/plant/api/media/" + a.getId() + "/file")
                    : "/static/images/plant/corn_plant_sample.png");
            m.put("capturedAt", a.getCapturedAt());
            m.put("mock", meta.get("mock"));
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> overview(String deviceKey) {
        String key = plantService.resolveDeviceKey(deviceKey);
        ensureSeed(key);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("specimenCount", specimenRepository.countByDeviceKey(key));
        m.put("metricCount", metricRepository.countByDeviceKey(key));
        m.put("jobCount", jobRepository.countByDeviceKey(key));
        m.put("specimens", listSpecimens(key));
        return m;
    }

    private Map<String, Object> latestMetricsMap(String deviceKey, String plantCode) {
        List<PlantPhenotypeMetric> list = metricRepository.findByDeviceKeyAndPlantCodeOrderBySampledAtDesc(
                deviceKey, plantCode, PageRequest.of(0, 40));
        Map<String, Object> latest = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (PlantPhenotypeMetric x : list) {
            if (seen.add(x.getMetric())) {
                latest.put(x.getMetric(), x.getValueNum());
                latest.put(x.getMetric() + "_unit", x.getUnit());
                latest.put(x.getMetric() + "_at", x.getSampledAt());
                latest.put(x.getMetric() + "_source", x.getSource());
            }
        }
        return latest;
    }

    private Map<String, Object> toSpecimenMap(PlantCropSpecimen s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("deviceKey", s.getDeviceKey());
        m.put("plantCode", s.getPlantCode());
        m.put("slotCode", s.getSlotCode());
        m.put("cropType", s.getCropType());
        m.put("variety", s.getVariety());
        m.put("sowDate", s.getSowDate());
        m.put("growthStage", s.getGrowthStage());
        m.put("status", s.getStatus());
        m.put("posRow", s.getPosRow());
        m.put("posCol", s.getPosCol());
        m.put("remark", s.getRemark());
        m.put("updatedAt", s.getUpdatedAt());
        return m;
    }

    private Map<String, Object> toMetricMap(PlantPhenotypeMetric x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", x.getId());
        m.put("deviceKey", x.getDeviceKey());
        m.put("plantCode", x.getPlantCode());
        m.put("metric", x.getMetric());
        m.put("value", x.getValueNum());
        m.put("unit", x.getUnit());
        m.put("source", x.getSource());
        m.put("jobId", x.getJobId());
        m.put("sampledAt", x.getSampledAt());
        return m;
    }

    private Map<String, Object> toJobMap(PlantAnalysisJob j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("deviceKey", j.getDeviceKey());
        m.put("plantCode", j.getPlantCode());
        m.put("jobType", j.getJobType());
        m.put("status", j.getStatus());
        m.put("inputMediaIdsJson", j.getInputMediaIdsJson());
        m.put("resultJson", j.getResultJson());
        m.put("message", j.getMessage());
        m.put("createdAt", j.getCreatedAt());
        m.put("startedAt", j.getStartedAt());
        m.put("finishedAt", j.getFinishedAt());
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMeta(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static String guessStage(double heightCm) {
        if (heightCm < 20) return "VE";
        if (heightCm < 30) return "V3";
        if (heightCm < 50) return "V6";
        if (heightCm < 65) return "V9";
        if (heightCm < 80) return "VT";
        return "R1";
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static double num(Object o, double def) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        return def;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
