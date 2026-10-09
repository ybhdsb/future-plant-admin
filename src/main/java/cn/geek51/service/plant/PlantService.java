package cn.geek51.service.plant;

import cn.geek51.config.PlantGatewayProperties;
import cn.geek51.config.PlantWebSocket;
import cn.geek51.dao.DeviceJpaReposity;
import cn.geek51.dao.plant.*;
import cn.geek51.domain.Devices;
import cn.geek51.domain.UserAuth;
import cn.geek51.domain.plant.*;
import cn.geek51.service.MqttService;
import cn.geek51.util.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class PlantService {

    public static final String DEFAULT_DEVICE_KEY = "plant-ctrl-01";

    private final PlantDeviceProfileRepository profileRepository;
    private final PlantSensorReadingRepository readingRepository;
    private final PlantActuatorStateRepository actuatorRepository;
    private final PlantCommandLogRepository commandRepository;
    private final PlantMediaAssetRepository mediaRepository;
    private final PlantRuntimeEventRepository eventRepository;
    private final DeviceJpaReposity deviceJpaReposity;
    private final MqttService mqttService;
    private final PlantGatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PlantAutomationService automationService;
    private PlantCameraHardwareService cameraHardwareService;
    private PlantLedHardwareService ledHardwareService;

    @Autowired
    @Lazy
    public void setAutomationService(PlantAutomationService automationService) {
        this.automationService = automationService;
    }

    @Autowired
    @Lazy
    public void setCameraHardwareService(PlantCameraHardwareService cameraHardwareService) {
        this.cameraHardwareService = cameraHardwareService;
    }

    @Autowired
    @Lazy
    public void setLedHardwareService(PlantLedHardwareService ledHardwareService) {
        this.ledHardwareService = ledHardwareService;
    }

    @Value("${plant.default-device-key:plant-ctrl-01}")
    private String defaultDeviceKey;

    @Value("${plant.mock.enabled:false}")
    private boolean mockEnabledByDefault;

    @Value("${plant.media-dir:uploads/plant}")
    private String mediaDir;

    @Value("${plant.export-max-rows:100000}")
    private int exportMaxRows;

    @Value("${device.offline-timeout-seconds:30}")
    private int offlineTimeoutSeconds;

    public PlantService(PlantDeviceProfileRepository profileRepository,
                        PlantSensorReadingRepository readingRepository,
                        PlantActuatorStateRepository actuatorRepository,
                        PlantCommandLogRepository commandRepository,
                        PlantMediaAssetRepository mediaRepository,
                        PlantRuntimeEventRepository eventRepository,
                        DeviceJpaReposity deviceJpaReposity,
                        MqttService mqttService,
                        PlantGatewayProperties gatewayProperties) {
        this.profileRepository = profileRepository;
        this.readingRepository = readingRepository;
        this.actuatorRepository = actuatorRepository;
        this.commandRepository = commandRepository;
        this.mediaRepository = mediaRepository;
        this.eventRepository = eventRepository;
        this.deviceJpaReposity = deviceJpaReposity;
        this.mqttService = mqttService;
        this.gatewayProperties = gatewayProperties;
    }

    public String resolveDeviceKey(String deviceKey) {
        if (deviceKey == null || deviceKey.trim().isEmpty()) {
            return defaultDeviceKey;
        }
        return deviceKey.trim();
    }

    @Transactional
    public PlantDeviceProfile ensureProfile(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        return profileRepository.findByDeviceKey(key).orElseGet(() -> {
            PlantDeviceProfile p = new PlantDeviceProfile();
            p.setDeviceKey(key);
            p.setDisplayName("未来植物控制器");
            p.setFirmwareVersion("mock-1.0");
            // 新建画像默认跟随 plant.mock.enabled（默认为 false = 实机）
            p.setMockEnabled(mockEnabledByDefault);
            p.setConfigJson(defaultConfigJson());
            Date now = new Date();
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            return profileRepository.save(p);
        });
    }

    @Transactional
    public Map<String, Object> applyLedAllChannels(String deviceKey, int brightness, String reason) throws IOException {
        String key = resolveDeviceKey(deviceKey);
        int b = Math.max(0, Math.min(100, brightness));
        // 真机：统一亮度走网关广播（四台驱动器 ch1/ch2 同值）
        if (gatewayProperties.getLed().isEnabled() && ledHardwareService != null) {
            int level = (int) Math.round(b * 255.0 / 100.0);
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("rackKey", gatewayProperties.getLed().getRackKey());
            req.put("ch1", level);
            req.put("ch2", level);
            if (reason != null) {
                req.put("reason", reason);
            }
            Map<String, Object> result = ledHardwareService.broadcastSet(req);
            // 同步看板 actuator 乐观状态
            for (int i = 1; i <= 8; i++) {
                Map<String, Object> state = new LinkedHashMap<>();
                state.put("actuatorId", "led.ch" + i);
                state.put("brightness", (double) b);
                state.put("on", b > 0);
                upsertActuator(key, "led.ch" + i, state, "LED_GATEWAY");
            }
            result.put("via", "led-gateway-broadcast");
            result.put("deviceKey", key);
            return result;
        }
        List<Map<String, Object>> channels = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            Map<String, Object> ch = new LinkedHashMap<>();
            ch.put("actuatorId", "led.ch" + i);
            ch.put("brightness", b);
            channels.add(ch);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channels", channels);
        if (reason != null) {
            payload.put("reason", reason);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceKey", key);
        body.put("commandType", "LED_SET");
        body.put("clientRequestId", "sched-" + System.currentTimeMillis());
        body.put("payload", payload);
        return issueCommand(body);
    }

    public Map<String, Object> getDashboard(String deviceKey, Boolean forceMock) {
        String key = resolveDeviceKey(deviceKey);
        PlantDeviceProfile profile = ensureProfile(key);
        boolean useMock = forceMock != null ? forceMock : Boolean.TRUE.equals(profile.getMockEnabled());

        if (useMock) {
            ingestMockTelemetry(key);
        }

        Map<String, Object> metrics = latestMetrics(key);
        List<Map<String, Object>> actuators = latestActuators(key);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceKey", key);
        result.put("displayName", profile.getDisplayName());
        // 摄像头网关联调时自动关 Mock，避免页面仍显示 Mock / 假图
        if (gatewayProperties.getCamera().isEnabled() && Boolean.TRUE.equals(profile.getMockEnabled())) {
            profile.setMockEnabled(false);
            profile.setUpdatedAt(new Date());
            profileRepository.save(profile);
        }
        result.put("mockEnabled", Boolean.TRUE.equals(profile.getMockEnabled()));
        result.put("online", isOnline(key) || useMock);
        result.put("onlineSource", useMock ? "MOCK" : "HEARTBEAT");
        result.put("metrics", metrics);
        result.put("actuators", actuators);
        result.put("events", recentEvents(key, 12));
        result.put("recentCommands", recentCommands(key, 12));
        result.put("series", sparklineSeries(key, 36));
        result.put("summary", buildSummary(key, metrics, actuators));
        result.put("serverTime", new Date());
        return result;
    }

    public Map<String, Object> listReadings(String deviceKey, String metric, Date from, Date to, int page, int size) {
        String key = resolveDeviceKey(deviceKey);
        if (from == null) {
            from = new Date(System.currentTimeMillis() - 24L * 3600_000);
        }
        if (to == null) {
            to = new Date();
        }
        int safeSize = Math.min(Math.max(size, 1), 500);
        int safePage = Math.max(page, 0);
        List<PlantSensorReading> list = readingRepository.findForExport(
                key,
                (metric == null || metric.trim().isEmpty()) ? null : metric.trim(),
                from,
                to,
                PageRequest.of(safePage, safeSize)
        );
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlantSensorReading r : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("deviceKey", r.getDeviceKey());
            m.put("metric", r.getMetric());
            m.put("value", r.getValueNum());
            m.put("unit", r.getUnit());
            m.put("quality", r.getQuality());
            m.put("sampledAt", r.getSampledAt());
            m.put("receivedAt", r.getReceivedAt());
            rows.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("rows", rows);
        result.put("count", rows.size());
        return result;
    }

    public Map<String, Object> queryMultiMetrics(String deviceKey, String metricsCsv, Date from, Date to, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (metricsCsv == null || metricsCsv.trim().isEmpty()) {
            metricsCsv = "air.temperature,air.humidity,nutrient.ph,nutrient.ec";
        }
        for (String metric : metricsCsv.split(",")) {
            String m = metric.trim();
            if (m.isEmpty()) {
                continue;
            }
            result.put(m, queryMetrics(deviceKey, m, from, to, limit));
        }
        return result;
    }

    private Map<String, Object> sparklineSeries(String deviceKey, int points) {
        String[] metrics = {"air.temperature", "air.humidity", "nutrient.ph", "nutrient.ec"};
        Date to = new Date();
        Date from = new Date(to.getTime() - 6L * 3600_000);
        Map<String, Object> series = new LinkedHashMap<>();
        for (String metric : metrics) {
            series.put(metric, queryMetrics(deviceKey, metric, from, to, points));
        }
        return series;
    }

    private Map<String, Object> buildSummary(String deviceKey, Map<String, Object> metrics,
                                             List<Map<String, Object>> actuators) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("metricCount", metrics == null ? 0 : metrics.size());
        summary.put("actuatorCount", actuators == null ? 0 : actuators.size());
        int pumpOn = 0;
        int ledOn = 0;
        if (actuators != null) {
            for (Map<String, Object> a : actuators) {
                Object stateObj = a.get("state");
                if (!(stateObj instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> state = (Map<String, Object>) stateObj;
                boolean on = Boolean.TRUE.equals(state.get("on"));
                String id = String.valueOf(a.get("actuatorId"));
                if (id.startsWith("pump.") && on) {
                    pumpOn++;
                }
                if (id.startsWith("led.") && on) {
                    ledOn++;
                }
            }
        }
        summary.put("pumpOnCount", pumpOn);
        summary.put("ledOnCount", ledOn);
        summary.put("commandCount", recentCommands(deviceKey, 100).size());
        summary.put("eventCount", recentEvents(deviceKey, 100).size());
        summary.put("mediaCount", listMedia(deviceKey, 100).size());
        Date lastSample = null;
        if (metrics != null) {
            for (Object v : metrics.values()) {
                if (!(v instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) v;
                Object sampledAt = m.get("sampledAt");
                if (sampledAt instanceof Date) {
                    Date d = (Date) sampledAt;
                    if (lastSample == null || d.after(lastSample)) {
                        lastSample = d;
                    }
                }
            }
        }
        summary.put("lastSampleAt", lastSample);
        return summary;
    }

    public Map<String, Object> getState(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        ensureProfile(key);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceKey", key);
        result.put("online", isOnline(key));
        result.put("metrics", latestMetrics(key));
        result.put("actuators", latestActuators(key));
        return result;
    }

    @Transactional
    public Map<String, Object> setMockEnabled(String deviceKey, boolean enabled) {
        PlantDeviceProfile profile = ensureProfile(deviceKey);
        profile.setMockEnabled(enabled);
        profile.setUpdatedAt(new Date());
        profileRepository.save(profile);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceKey", profile.getDeviceKey());
        result.put("mockEnabled", enabled);
        return result;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public void ingestTelemetryJson(String payload) throws IOException {
        Map<String, Object> map = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        String deviceKey = resolveDeviceKey(asString(map.get("deviceKey")));
        ensureProfile(deviceKey);
        Date receivedAt = new Date();
        Date sampledAt = parseDate(map.get("sampledAt"));
        if (sampledAt == null) {
            sampledAt = receivedAt;
        }

        Object metricsObj = map.get("metrics");
        if (metricsObj instanceof List) {
            for (Object item : (List<Object>) metricsObj) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> m = (Map<String, Object>) item;
                String metric = asString(m.get("metric"));
                if (metric == null || metric.isEmpty()) {
                    continue;
                }
                PlantSensorReading reading = new PlantSensorReading();
                reading.setDeviceKey(deviceKey);
                reading.setMetric(metric);
                reading.setValueNum(asDouble(m.get("value")));
                reading.setUnit(asString(m.get("unit")));
                reading.setQuality(asString(m.get("quality")) == null ? "GOOD" : asString(m.get("quality")));
                reading.setSampledAt(sampledAt);
                reading.setReceivedAt(receivedAt);
                reading.setRawJson(objectMapper.writeValueAsString(m));
                readingRepository.save(reading);
            }
        }

        Object actuatorsObj = map.get("actuators");
        if (actuatorsObj instanceof List) {
            for (Object item : (List<Object>) actuatorsObj) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> a = (Map<String, Object>) item;
                String actuatorId = asString(a.get("actuatorId"));
                if (actuatorId == null || actuatorId.isEmpty()) {
                    continue;
                }
                upsertActuator(deviceKey, actuatorId, a, "REPORT");
            }
        }

        broadcast("telemetry", deviceKey, latestMetrics(deviceKey));
        if (automationService != null) {
            try {
                automationService.evaluateSensorRules(deviceKey);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Transactional
    public void ingestAckJson(String payload) throws IOException {
        Map<String, Object> map = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        String commandId = asString(map.get("commandId"));
        if (commandId == null || !commandId.startsWith("c-")) {
            return;
        }
        Long id;
        try {
            id = Long.parseLong(commandId.substring(2));
        } catch (NumberFormatException e) {
            return;
        }
        Optional<PlantCommandLog> opt = commandRepository.findById(id);
        if (!opt.isPresent()) {
            return;
        }
        PlantCommandLog log = opt.get();
        String status = asString(map.get("status"));
        log.setStatus(status == null ? "ACKED" : status);
        log.setResultMessage(asString(map.get("message")));
        log.setFinishedAt(new Date());
        commandRepository.save(log);

        Object stateAfter = map.get("stateAfter");
        if (stateAfter instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> state = (Map<String, Object>) stateAfter;
            String actuatorId = asString(state.get("actuatorId"));
            if (actuatorId != null) {
                upsertActuator(log.getDeviceKey(), actuatorId, state, "ACK");
            }
        }
        broadcast("command_status", log.getDeviceKey(), toCommandMap(log));
    }

    @Transactional
    public Map<String, Object> issueCommand(Map<String, Object> body) throws IOException {
        String deviceKey = resolveDeviceKey(asString(body.get("deviceKey")));
        PlantDeviceProfile profile = ensureProfile(deviceKey);
        // 摄像头网关开启时强制关闭 Mock，避免仍生成玉米样例图
        if (gatewayProperties.getCamera().isEnabled() && Boolean.TRUE.equals(profile.getMockEnabled())) {
            profile.setMockEnabled(false);
            profile.setUpdatedAt(new Date());
            profileRepository.save(profile);
        }
        String commandType = asString(body.get("commandType"));
        if (commandType == null || commandType.trim().isEmpty()) {
            throw new IllegalArgumentException("commandType 不能为空");
        }

        // 旧前端「立即抓拍 / 云台」走 commands：网关开启时直接转真实摄像头接口
        if (gatewayProperties.getCamera().isEnabled() && cameraHardwareService != null) {
            if ("CAMERA_CAPTURE".equals(commandType.trim())) {
                Map<String, Object> captured = cameraHardwareService.capturePhoto(deviceKey);
                captured.put("via", "camera-gateway");
                return captured;
            }
            if ("GIMBAL_MOVE".equals(commandType.trim())) {
                Map<String, Object> moveBody = new LinkedHashMap<>();
                moveBody.put("deviceKey", deviceKey);
                if (body.get("payload") instanceof Map) {
                    moveBody.putAll((Map<String, Object>) body.get("payload"));
                }
                Object action = moveBody.get("action");
                if (action != null && "STOP".equalsIgnoreCase(String.valueOf(action))) {
                    return cameraHardwareService.ptzStop(deviceKey);
                }
                return cameraHardwareService.ptzMove(moveBody);
            }
        }

        String clientRequestId = asString(body.get("clientRequestId"));
        if (clientRequestId != null && !clientRequestId.isEmpty()) {
            Optional<PlantCommandLog> existing = commandRepository.findByClientRequestId(clientRequestId);
            if (existing.isPresent()) {
                return toCommandMap(existing.get());
            }
        }

        Object payloadObj = body.get("payload");
        String payloadJson = payloadObj == null ? "{}" : objectMapper.writeValueAsString(payloadObj);

        PlantCommandLog log = new PlantCommandLog();
        log.setDeviceKey(deviceKey);
        log.setClientRequestId(clientRequestId);
        log.setCommandType(commandType.trim());
        log.setPayloadJson(payloadJson);
        log.setStatus("PENDING");
        log.setOperatorName(currentOperator());
        log.setCreatedAt(new Date());
        log = commandRepository.save(log);

        // optimistic local state for UI when mock or immediate feedback needed
        applyOptimisticState(deviceKey, commandType, payloadObj);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schemaVersion", "1.0");
        envelope.put("deviceKey", deviceKey);
        envelope.put("commandId", "c-" + log.getId());
        envelope.put("clientRequestId", clientRequestId);
        envelope.put("commandType", commandType.trim());
        envelope.put("issuedAt", formatDate(log.getCreatedAt()));
        envelope.put("payload", payloadObj == null ? Collections.emptyMap() : payloadObj);

        String json = objectMapper.writeValueAsString(envelope);
        boolean mock = Boolean.TRUE.equals(ensureProfile(deviceKey).getMockEnabled());
        boolean sent = false;
        Date now = new Date();
        if (!mock) {
            sent = mqttService.publishPlantCommand(deviceKey, json);
            if (sent) {
                log.setStatus("SENT");
                log.setSentAt(now);
            } else {
                // MQTT 未开时保留 PENDING，供边缘 HTTP 轮询 /commands/pending
                log.setStatus("PENDING");
                log.setResultMessage("waiting edge poll (MQTT off or publish failed)");
            }
        } else {
            // mock: auto ACK
            log.setStatus("ACKED");
            log.setResultMessage("mock auto-ack");
            log.setSentAt(now);
            log.setFinishedAt(now);
            sent = true;
        }
        commandRepository.save(log);

        Map<String, Object> result = toCommandMap(log);
        result.put("sent", sent);
        broadcast("command_status", deviceKey, result);
        return result;
    }

    public Map<String, Object> getCommand(Long commandId) {
        PlantCommandLog log = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("指令不存在: " + commandId));
        return toCommandMap(log);
    }

    /**
     * 边缘轮询待执行指令；取走后标记 SENT。
     */
    @Transactional
    public List<Map<String, Object>> pollPendingCommands(String deviceKey, int limit) {
        String key = resolveDeviceKey(deviceKey);
        int size = Math.max(1, Math.min(limit, 50));
        List<PlantCommandLog> list = commandRepository.findByDeviceKeyAndStatusOrderByCreatedAtAsc(
                key, "PENDING", PageRequest.of(0, size));
        Date now = new Date();
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlantCommandLog log : list) {
            log.setStatus("SENT");
            log.setSentAt(now);
            log.setResultMessage("delivered via HTTP pending poll");
            commandRepository.save(log);
            Map<String, Object> item = toCommandMap(log);
            try {
                if (log.getPayloadJson() != null) {
                    item.put("payload", objectMapper.readValue(log.getPayloadJson(), Object.class));
                }
            } catch (Exception ignore) {
                item.put("payload", Collections.emptyMap());
            }
            out.add(item);
        }
        return out;
    }

    /**
     * 边缘拉取采集策略（SAMPLE_INTERVAL）。
     */
    @Transactional
    public Map<String, Object> getSampleStrategy(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        PlantDeviceProfile profile = ensureProfile(key);
        int intervalSec = 15;
        boolean enabled = true;
        List<String> metrics = Arrays.asList("air.temperature", "air.humidity", "nutrient.ph");
        try {
            if (profile.getConfigJson() != null && !profile.getConfigJson().trim().isEmpty()) {
                Map<String, Object> cfg = objectMapper.readValue(profile.getConfigJson(),
                        new TypeReference<Map<String, Object>>() {});
                if (cfg.get("sampleIntervalSec") != null) {
                    intervalSec = Integer.parseInt(String.valueOf(cfg.get("sampleIntervalSec")));
                }
                if (cfg.get("sampleEnabled") != null) {
                    enabled = Boolean.parseBoolean(String.valueOf(cfg.get("sampleEnabled")));
                }
                if (cfg.get("metrics") instanceof List) {
                    List<String> m = new ArrayList<>();
                    for (Object o : (List<?>) cfg.get("metrics")) {
                        if (o != null) {
                            m.add(String.valueOf(o));
                        }
                    }
                    if (!m.isEmpty()) {
                        metrics = m;
                    }
                }
            }
        } catch (Exception ignore) {
            // keep defaults
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("intervalSec", intervalSec);
        config.put("metrics", metrics);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deviceKey", key);
        out.put("strategyType", "SAMPLE_INTERVAL");
        out.put("target", "sensor");
        out.put("enabled", enabled);
        out.put("name", "默认环境采集");
        out.put("config", config);
        out.put("updatedAt", profile.getUpdatedAt());
        return out;
    }

    @Transactional
    public Map<String, Object> saveSampleStrategy(Map<String, Object> body) {
        String key = resolveDeviceKey(asString(body.get("deviceKey")));
        PlantDeviceProfile profile = ensureProfile(key);
        Map<String, Object> cfg = new LinkedHashMap<>();
        try {
            if (profile.getConfigJson() != null && !profile.getConfigJson().trim().isEmpty()) {
                cfg.putAll(objectMapper.readValue(profile.getConfigJson(),
                        new TypeReference<Map<String, Object>>() {}));
            }
        } catch (Exception ignore) {
            cfg = new LinkedHashMap<>();
        }
        if (body.get("intervalSec") != null) {
            cfg.put("sampleIntervalSec", Integer.parseInt(String.valueOf(body.get("intervalSec"))));
        }
        if (body.get("enabled") != null) {
            cfg.put("sampleEnabled", Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        }
        if (body.get("metrics") instanceof List) {
            cfg.put("metrics", body.get("metrics"));
        }
        try {
            profile.setConfigJson(objectMapper.writeValueAsString(cfg));
        } catch (Exception e) {
            throw new IllegalArgumentException("保存策略失败");
        }
        profile.setUpdatedAt(new Date());
        profileRepository.save(profile);
        return getSampleStrategy(key);
    }

    public List<Map<String, Object>> queryMetrics(String deviceKey, String metric, Date from, Date to, int limit) {
        String key = resolveDeviceKey(deviceKey);
        if (from == null) {
            from = new Date(System.currentTimeMillis() - 24L * 3600_000);
        }
        if (to == null) {
            to = new Date();
        }
        if (metric == null || metric.trim().isEmpty()) {
            metric = "air.temperature";
        }
        List<PlantSensorReading> list = readingRepository
                .findByDeviceKeyAndMetricAndSampledAtBetweenOrderBySampledAtAsc(key, metric.trim(), from, to);
        if (limit > 0 && list.size() > limit) {
            list = list.subList(list.size() - limit, list.size());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlantSensorReading r : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("metric", r.getMetric());
            m.put("value", r.getValueNum());
            m.put("unit", r.getUnit());
            m.put("quality", r.getQuality());
            m.put("sampledAt", r.getSampledAt());
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> listMedia(String deviceKey, int limit) {
        String key = resolveDeviceKey(deviceKey);
        // 仅 Mock 时补样例图；实机媒体库勿再塞玉米样例
        PlantDeviceProfile profile = ensureProfile(key);
        if (Boolean.TRUE.equals(profile.getMockEnabled())
                && !gatewayProperties.getCamera().isEnabled()) {
            ensureSampleMedia(key);
        }
        List<PlantMediaAsset> list = mediaRepository.findByDeviceKeyOrderByCapturedAtDesc(
                key, PageRequest.of(0, Math.max(1, limit)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlantMediaAsset a : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("deviceKey", a.getDeviceKey());
            m.put("mediaType", a.getMediaType());
            String path = a.getStoragePath();
            boolean hasLocalFile = path != null && !path.trim().isEmpty() && Files.exists(Paths.get(path));
            m.put("hasLocalFile", hasLocalFile);
            m.put("storagePath", path);
            m.put("url", hasLocalFile ? ("/plant/api/media/" + a.getId() + "/file") : null);
            m.put("capturedAt", a.getCapturedAt());
            m.put("metaJson", a.getMetaJson());
            String label = "Media #" + a.getId();
            String remotePath = null;
            boolean sample = false;
            try {
                if (a.getMetaJson() != null && !a.getMetaJson().trim().isEmpty()) {
                    Map<String, Object> meta = objectMapper.readValue(a.getMetaJson(),
                            new TypeReference<Map<String, Object>>() {});
                    if (meta.get("label") != null) {
                        label = String.valueOf(meta.get("label"));
                    }
                    if (meta.get("remotePath") != null) {
                        remotePath = String.valueOf(meta.get("remotePath"));
                    } else if (meta.get("file") != null) {
                        remotePath = String.valueOf(meta.get("file"));
                    }
                    sample = Boolean.TRUE.equals(meta.get("sample"));
                }
            } catch (Exception ignore) {
                // keep defaults
            }
            if (sample) {
                label = "玉米植株样例";
                if (!hasLocalFile) {
                    m.put("url", "/static/images/plant/corn_plant_sample.png");
                }
            } else if (!hasLocalFile && "VIDEO".equalsIgnoreCase(a.getMediaType())) {
                label = label == null || label.startsWith("Media #") ? "录像（待拉取）" : label;
            } else if (hasLocalFile && "VIDEO".equalsIgnoreCase(a.getMediaType())
                    && (label == null || label.startsWith("Media #"))) {
                label = "网关录像";
            }
            m.put("label", label);
            m.put("remotePath", remotePath);
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> listEvents(String deviceKey, int limit) {
        return recentEvents(resolveDeviceKey(deviceKey), limit);
    }

    public List<Map<String, Object>> listCommands(String deviceKey, int limit) {
        return recentCommands(resolveDeviceKey(deviceKey), limit);
    }

    @Transactional
    public Map<String, Object> uploadMedia(String deviceKey, MultipartFile file, Date capturedAt, String metaJson)
            throws IOException {
        String key = resolveDeviceKey(deviceKey);
        ensureProfile(key);
        Path dir = Paths.get(mediaDir, key);
        Files.createDirectories(dir);
        String original = file.getOriginalFilename() == null ? "capture.jpg" : file.getOriginalFilename();
        String safe = System.currentTimeMillis() + "_" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = dir.resolve(safe);
        file.transferTo(target.toFile());

        PlantMediaAsset asset = new PlantMediaAsset();
        asset.setDeviceKey(key);
        asset.setMediaType("IMAGE");
        asset.setStoragePath(target.toString());
        asset.setCapturedAt(capturedAt == null ? new Date() : capturedAt);
        asset.setMetaJson(metaJson);
        asset.setCreatedAt(new Date());
        asset = mediaRepository.save(asset);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", asset.getId());
        result.put("url", "/plant/api/media/" + asset.getId() + "/file");
        result.put("capturedAt", asset.getCapturedAt());
        return result;
    }

    public PlantMediaAsset getMedia(Long id) {
        return mediaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("媒体不存在: " + id));
    }

    public void exportTelemetryCsv(String deviceKey, String metric, Date from, Date to, OutputStream out)
            throws IOException {
        String key = resolveDeviceKey(deviceKey);
        if (from == null) {
            from = new Date(System.currentTimeMillis() - 24L * 3600_000);
        }
        if (to == null) {
            to = new Date();
        }
        List<PlantSensorReading> list = readingRepository.findForExport(
                key,
                (metric == null || metric.trim().isEmpty()) ? null : metric.trim(),
                from,
                to,
                PageRequest.of(0, exportMaxRows)
        );

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.write('\uFEFF');
            writer.write("device_key,metric,value,unit,quality,sampled_at,received_at");
            writer.newLine();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            for (PlantSensorReading r : list) {
                writer.write(csv(r.getDeviceKey()));
                writer.write(',');
                writer.write(csv(r.getMetric()));
                writer.write(',');
                writer.write(r.getValueNum() == null ? "" : String.valueOf(r.getValueNum()));
                writer.write(',');
                writer.write(csv(r.getUnit()));
                writer.write(',');
                writer.write(csv(r.getQuality()));
                writer.write(',');
                writer.write(csv(r.getSampledAt() == null ? "" : fmt.format(r.getSampledAt())));
                writer.write(',');
                writer.write(csv(r.getReceivedAt() == null ? "" : fmt.format(r.getReceivedAt())));
                writer.newLine();
            }
        }
    }

    /** Excel-friendly SpreadsheetML without POI dependency. */
    public void exportTelemetryExcel(String deviceKey, String metric, Date from, Date to, OutputStream out)
            throws IOException {
        String key = resolveDeviceKey(deviceKey);
        if (from == null) {
            from = new Date(System.currentTimeMillis() - 24L * 3600_000);
        }
        if (to == null) {
            to = new Date();
        }
        List<PlantSensorReading> list = readingRepository.findForExport(
                key,
                (metric == null || metric.trim().isEmpty()) ? null : metric.trim(),
                from,
                to,
                PageRequest.of(0, exportMaxRows)
        );
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?><?mso-application progid=\"Excel.Sheet\"?>");
        sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" ");
        sb.append("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">");
        sb.append("<Worksheet ss:Name=\"telemetry\"><Table>");
        sb.append("<Row>");
        for (String h : Arrays.asList("device_key", "metric", "value", "unit", "quality", "sampled_at", "received_at")) {
            sb.append("<Cell><Data ss:Type=\"String\">").append(xml(h)).append("</Data></Cell>");
        }
        sb.append("</Row>");
        for (PlantSensorReading r : list) {
            sb.append("<Row>");
            sb.append("<Cell><Data ss:Type=\"String\">").append(xml(r.getDeviceKey())).append("</Data></Cell>");
            sb.append("<Cell><Data ss:Type=\"String\">").append(xml(r.getMetric())).append("</Data></Cell>");
            if (r.getValueNum() == null) {
                sb.append("<Cell><Data ss:Type=\"String\"></Data></Cell>");
            } else {
                sb.append("<Cell><Data ss:Type=\"Number\">").append(r.getValueNum()).append("</Data></Cell>");
            }
            sb.append("<Cell><Data ss:Type=\"String\">").append(xml(r.getUnit())).append("</Data></Cell>");
            sb.append("<Cell><Data ss:Type=\"String\">").append(xml(r.getQuality())).append("</Data></Cell>");
            sb.append("<Cell><Data ss:Type=\"String\">")
                    .append(xml(r.getSampledAt() == null ? "" : fmt.format(r.getSampledAt())))
                    .append("</Data></Cell>");
            sb.append("<Cell><Data ss:Type=\"String\">")
                    .append(xml(r.getReceivedAt() == null ? "" : fmt.format(r.getReceivedAt())))
                    .append("</Data></Cell>");
            sb.append("</Row>");
        }
        sb.append("</Table></Worksheet></Workbook>");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public void recordEvent(String deviceKey, String level, String code, String message) {
        PlantRuntimeEvent event = new PlantRuntimeEvent();
        event.setDeviceKey(resolveDeviceKey(deviceKey));
        event.setLevel(level == null ? "INFO" : level);
        event.setCode(code);
        event.setMessage(message);
        event.setCreatedAt(new Date());
        eventRepository.save(event);
    }

    @Transactional
    public void ingestMockTelemetry(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        ensureProfile(key);
        List<PlantSensorReading> latest = readingRepository.findLatestByDeviceAndMetric(
                key, "air.temperature", PageRequest.of(0, 1));
        if (!latest.isEmpty() && latest.get(0).getSampledAt() != null) {
            long age = System.currentTimeMillis() - latest.get(0).getSampledAt().getTime();
            if (age < 4000L) {
                ensureActuatorDefaults(key);
                return;
            }
        }
        Date now = new Date();
        Random random = new Random();
        saveMetric(key, "air.temperature", 24.5 + random.nextDouble() * 3, "°C", now);
        saveMetric(key, "air.humidity", 55 + random.nextDouble() * 15, "%RH", now);
        saveMetric(key, "nutrient.ph", 5.8 + random.nextDouble() * 0.8, "", now);
        saveMetric(key, "nutrient.ec", 1.6 + random.nextDouble() * 0.4, "mS/cm", now);
        saveMetric(key, "air.co2", 420 + random.nextDouble() * 80, "ppm", now);
        saveMetric(key, "nutrient.do", 4.0 + random.nextDouble() * 3.5, "mg/L", now);

        ensureActuatorDefaults(key);
    }

    private void ensureActuatorDefaults(String deviceKey) {
        for (PlantActuatorCatalog.ActuatorDef def : PlantActuatorCatalog.v1()) {
            if (!actuatorRepository.findByDeviceKeyAndActuatorId(deviceKey, def.id).isPresent()) {
                Map<String, Object> state = new LinkedHashMap<>();
                state.put("actuatorId", def.id);
                state.put("on", false);
                try {
                    upsertActuator(deviceKey, def.id, state, "BOOTSTRAP");
                } catch (IOException ignored) {
                }
            }
        }
        String[] leds = {"led.ch1", "led.ch2", "led.ch3", "led.ch4", "led.ch5", "led.ch6", "led.ch7", "led.ch8"};
        for (String led : leds) {
            if (!actuatorRepository.findByDeviceKeyAndActuatorId(deviceKey, led).isPresent()) {
                Map<String, Object> state = new LinkedHashMap<>();
                state.put("actuatorId", led);
                state.put("on", true);
                state.put("brightness", 60);
                try {
                    upsertActuator(deviceKey, led, state, "MOCK");
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void saveMetric(String deviceKey, String metric, double value, String unit, Date sampledAt) {
        PlantSensorReading reading = new PlantSensorReading();
        reading.setDeviceKey(deviceKey);
        reading.setMetric(metric);
        reading.setValueNum(Math.round(value * 10.0) / 10.0);
        reading.setUnit(unit);
        reading.setQuality("GOOD");
        reading.setSampledAt(sampledAt);
        reading.setReceivedAt(new Date());
        readingRepository.save(reading);
    }

    @SuppressWarnings("unchecked")
    private void applyOptimisticState(String deviceKey, String commandType, Object payloadObj) throws IOException {
        if (!(payloadObj instanceof Map)) {
            return;
        }
        Map<String, Object> payload = (Map<String, Object>) payloadObj;
        if ("LED_SET".equals(commandType)) {
            Object channels = payload.get("channels");
            if (channels instanceof List) {
                for (Object ch : (List<Object>) channels) {
                    if (ch instanceof Map) {
                        Map<String, Object> c = (Map<String, Object>) ch;
                        String actuatorId = asString(c.get("actuatorId"));
                        if (actuatorId == null) {
                            continue;
                        }
                        Map<String, Object> state = new LinkedHashMap<>();
                        state.put("actuatorId", actuatorId);
                        Double brightness = asDouble(c.get("brightness"));
                        state.put("brightness", brightness == null ? 0 : brightness);
                        state.put("on", brightness != null && brightness > 0);
                        upsertActuator(deviceKey, actuatorId, state, "COMMAND_OPTIMISTIC");
                    }
                }
            }
        } else if ("PUMP_SET".equals(commandType) || "RELAY_SET".equals(commandType)
                || "FAN_SET".equals(commandType) || "SHADE_SET".equals(commandType)) {
            String actuatorId = asString(payload.get("actuatorId"));
            if (actuatorId != null) {
                Map<String, Object> state = new LinkedHashMap<>();
                state.put("actuatorId", actuatorId);
                state.put("on", Boolean.TRUE.equals(payload.get("on")) || "true".equalsIgnoreCase(asString(payload.get("on"))));
                upsertActuator(deviceKey, actuatorId, state, "COMMAND_OPTIMISTIC");
            }
        } else if ("CAMERA_CAPTURE".equals(commandType)) {
            // 仅 Mock 且未开摄像头网关时造样例图；实机模式不再写假照片
            PlantDeviceProfile profile = ensureProfile(deviceKey);
            if (Boolean.TRUE.equals(profile.getMockEnabled())
                    && !gatewayProperties.getCamera().isEnabled()) {
                createSampleCapture(deviceKey, payload);
            }
        }
    }

    private void createSampleCapture(String deviceKey, Map<String, Object> payload) throws IOException {
        Path dir = Paths.get(mediaDir, deviceKey);
        Files.createDirectories(dir);
        String fileName = "capture_" + System.currentTimeMillis() + "_corn.png";
        Path target = dir.resolve(fileName);
        copySampleCornImage(target);

        Map<String, Object> meta = new LinkedHashMap<>();
        if (payload != null) {
            meta.putAll(payload);
        }
        meta.put("sample", true);
        meta.put("label", "玉米植株样例图");
        meta.put("source", "static/images/plant/corn_plant_sample.png");

        PlantMediaAsset asset = new PlantMediaAsset();
        asset.setDeviceKey(deviceKey);
        asset.setMediaType("IMAGE");
        asset.setStoragePath(target.toAbsolutePath().toString());
        asset.setCapturedAt(new Date());
        asset.setMetaJson(objectMapper.writeValueAsString(meta));
        asset.setCreatedAt(new Date());
        mediaRepository.save(asset);
    }

    /** 确保至少有一张玉米样例采集图，方便历史页开箱可见。 */
    @Transactional
    public void ensureSampleMedia(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        List<PlantMediaAsset> existing = mediaRepository.findByDeviceKeyOrderByCapturedAtDesc(key, PageRequest.of(0, 1));
        if (!existing.isEmpty()) {
            return;
        }
        try {
            createSampleCapture(key, Collections.<String, Object>singletonMap("reason", "bootstrap-sample"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copySampleCornImage(Path target) throws IOException {
        InputStream in = PlantService.class.getResourceAsStream("/static/images/plant/corn_plant_sample.png");
        if (in == null) {
            // fallback: try project-relative path during IDE run
            Path classpathFallback = Paths.get("src/main/resources/static/images/plant/corn_plant_sample.png");
            if (Files.exists(classpathFallback)) {
                Files.copy(classpathFallback, target);
                return;
            }
            Files.write(target, new byte[0]);
            return;
        }
        try {
            Files.copy(in, target);
        } finally {
            in.close();
        }
    }

    private void upsertActuator(String deviceKey, String actuatorId, Map<String, Object> state, String source)
            throws IOException {
        PlantActuatorState entity = actuatorRepository.findByDeviceKeyAndActuatorId(deviceKey, actuatorId)
                .orElseGet(PlantActuatorState::new);
        Date now = new Date();
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
        }
        entity.setDeviceKey(deviceKey);
        entity.setActuatorId(actuatorId);
        entity.setStateJson(objectMapper.writeValueAsString(state));
        entity.setSource(source);
        entity.setUpdatedAt(now);
        actuatorRepository.save(entity);
    }

    private boolean isOnline(String deviceKey) {
        Devices device = deviceJpaReposity.findByDeviceKey(deviceKey);
        if (device == null) {
            return false;
        }
        if (!"online".equalsIgnoreCase(device.getStatus())) {
            return false;
        }
        if (device.getLastHeartbeatTime() == null) {
            return false;
        }
        long age = System.currentTimeMillis() - device.getLastHeartbeatTime().getTime();
        return age <= offlineTimeoutSeconds * 1000L;
    }

    private Map<String, Object> latestMetrics(String deviceKey) {
        String[] metrics = {
                "air.temperature", "air.humidity", "air.co2",
                "nutrient.ph", "nutrient.ec", "nutrient.do", "nutrient.level"
        };
        Map<String, Object> out = new LinkedHashMap<>();
        for (String metric : metrics) {
            List<PlantSensorReading> latest = readingRepository.findLatestByDeviceAndMetric(
                    deviceKey, metric, PageRequest.of(0, 1));
            if (latest.isEmpty()) {
                continue;
            }
            PlantSensorReading r = latest.get(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("metric", r.getMetric());
            m.put("value", r.getValueNum());
            m.put("unit", r.getUnit());
            m.put("quality", r.getQuality());
            m.put("sampledAt", r.getSampledAt());
            out.put(metric, m);
        }
        return out;
    }

    private List<Map<String, Object>> latestActuators(String deviceKey) {
        List<PlantActuatorState> list = actuatorRepository.findByDeviceKeyOrderByActuatorIdAsc(deviceKey);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlantActuatorState a : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("actuatorId", a.getActuatorId());
            m.put("source", a.getSource());
            m.put("updatedAt", a.getUpdatedAt());
            try {
                Map<String, Object> state = objectMapper.readValue(a.getStateJson(),
                        new TypeReference<Map<String, Object>>() {});
                m.put("state", state);
            } catch (Exception e) {
                m.put("state", Collections.emptyMap());
            }
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> recentEvents(String deviceKey, int limit) {
        List<PlantRuntimeEvent> list = eventRepository.findByDeviceKeyOrderByCreatedAtDesc(
                deviceKey, PageRequest.of(0, Math.max(1, limit)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlantRuntimeEvent e : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("level", e.getLevel());
            m.put("code", e.getCode());
            m.put("message", e.getMessage());
            m.put("createdAt", e.getCreatedAt());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> recentCommands(String deviceKey, int limit) {
        List<PlantCommandLog> list = commandRepository.findByDeviceKeyOrderByCreatedAtDesc(
                deviceKey, PageRequest.of(0, Math.max(1, limit)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlantCommandLog log : list) {
            out.add(toCommandMap(log));
        }
        return out;
    }

    private Map<String, Object> toCommandMap(PlantCommandLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("commandId", "c-" + log.getId());
        m.put("id", log.getId());
        m.put("deviceKey", log.getDeviceKey());
        m.put("commandType", log.getCommandType());
        m.put("payloadJson", log.getPayloadJson());
        m.put("status", log.getStatus());
        m.put("resultMessage", log.getResultMessage());
        m.put("operator", log.getOperatorName());
        m.put("createdAt", log.getCreatedAt());
        m.put("finishedAt", log.getFinishedAt());
        return m;
    }

    private void broadcast(String type, String deviceKey, Object payload) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("domain", "plant");
            msg.put("type", type);
            msg.put("deviceKey", deviceKey);
            msg.put("payload", payload);
            msg.put("ts", System.currentTimeMillis());
            PlantWebSocket.broadcast(objectMapper.writeValueAsString(msg));
        } catch (Exception ignored) {
        }
    }

    private String currentOperator() {
        try {
            UserAuth user = UserContext.getCurrentUser();
            if (user != null && user.getUsername() != null) {
                return user.getUsername();
            }
        } catch (Exception ignored) {
        }
        return "system";
    }

    private String defaultConfigJson() {
        return "{"
                + "\"pollIntervalMs\":5000,"
                + "\"sensors\":["
                + "{\"metric\":\"air.temperature\"},{\"metric\":\"air.humidity\"},"
                + "{\"metric\":\"nutrient.ph\"},{\"metric\":\"nutrient.ec\"}"
                + "],"
                + "\"actuators\":["
                + "{\"actuatorId\":\"led.ch1\"},{\"actuatorId\":\"pump.water\"},{\"actuatorId\":\"pump.oxygen\"}"
                + "]"
                + "}";
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Double asDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static Date parseDate(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return new Date(((Number) o).longValue());
        }
        String s = String.valueOf(o).trim();
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                return new SimpleDateFormat(pattern).parse(s);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(date);
    }

    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String xml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
