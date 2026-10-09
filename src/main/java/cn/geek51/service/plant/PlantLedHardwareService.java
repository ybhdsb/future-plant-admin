package cn.geek51.service.plant;

import cn.geek51.config.PlantGatewayProperties;
import cn.geek51.dao.plant.PlantLedCommandLogRepository;
import cn.geek51.dao.plant.PlantLedStateRepository;
import cn.geek51.domain.plant.PlantLedCommandLog;
import cn.geek51.domain.plant.PlantLedState;
import cn.geek51.service.plant.gateway.GatewayHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * LED 平台侧：写库 +（可选）调用同事网关。
 */
@Service
public class PlantLedHardwareService {

    /** V1.5：8 台双通道驱动器（0x96～0x9D），光谱同组地址成对 */
    private static final String[] ADDRESSES = {
            "0x96", "0x97", "0x98", "0x99", "0x9A", "0x9B", "0x9C", "0x9D"
    };
    private static final String[][] SPECTRA = {
            {"660nm", "395nm"},
            {"450nm", "530nm"},
            {"630nm", "430nm"},
            {"730nm", "Full Spectrum"},
            {"660nm", "395nm"},
            {"450nm", "530nm"},
            {"630nm", "430nm"},
            {"730nm", "Full Spectrum"}
    };

    private final PlantGatewayProperties gatewayProperties;
    private final GatewayHttpClient httpClient;
    private final PlantLedStateRepository stateRepository;
    private final PlantLedCommandLogRepository commandLogRepository;
    private final ObjectMapper objectMapper;

    public PlantLedHardwareService(PlantGatewayProperties gatewayProperties,
                                   GatewayHttpClient httpClient,
                                   PlantLedStateRepository stateRepository,
                                   PlantLedCommandLogRepository commandLogRepository,
                                   ObjectMapper objectMapper) {
        this.gatewayProperties = gatewayProperties;
        this.httpClient = httpClient;
        this.stateRepository = stateRepository;
        this.commandLogRepository = commandLogRepository;
        this.objectMapper = objectMapper;
    }

    public String resolveRackKey(String rackKey) {
        if (rackKey != null && !rackKey.trim().isEmpty()) {
            return rackKey.trim();
        }
        return gatewayProperties.getLed().getRackKey();
    }

    @Transactional
    public Map<String, Object> getState(String rackKey) {
        String key = resolveRackKey(rackKey);
        ensureSeed(key);
        // 真机：静默从网关读回（不写指令流水，避免刷新刷屏）
        if (gatewayProperties.getLed().isEnabled()) {
            try {
                syncFromGateway(key);
            } catch (Exception e) {
                Map<String, Object> out = buildStateResponse(key);
                out.put("syncError", e.getMessage());
                return out;
            }
        }
        return buildStateResponse(key);
    }

    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gatewayEnabled", gatewayProperties.getLed().isEnabled());
        out.put("baseUrl", gatewayProperties.getLed().getBaseUrl());
        out.put("rackKey", gatewayProperties.getLed().getRackKey());
        if (!gatewayProperties.getLed().isEnabled()) {
            out.put("ok", false);
            out.put("serialConnected", false);
            out.put("message", "LED 网关未启用（plant.gateway.led.enabled=false）");
            return out;
        }
        try {
            Map<String, Object> remote = httpClient.getJson(
                    gatewayProperties.getLed().getBaseUrl(), "/led/health",
                    gatewayProperties.getLed().getApiKey(),
                    gatewayProperties.getLed().getConnectTimeoutMs(),
                    gatewayProperties.getLed().getReadTimeoutMs());
            out.putAll(remote);
            if (!out.containsKey("ok")) {
                out.put("ok", true);
            }
            return out;
        } catch (Exception e) {
            out.put("ok", false);
            out.put("serialConnected", false);
            out.put("message", e.getMessage());
            return out;
        }
    }

    private Map<String, Object> buildStateResponse(String key) {
        List<PlantLedState> list = stateRepository.findByRackKeyOrderByBusAddressAscChannelAsc(key);
        List<Map<String, Object>> channels = new ArrayList<>();
        for (PlantLedState s : list) {
            channels.add(toStateMap(s));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rackKey", key);
        out.put("gatewayEnabled", gatewayProperties.getLed().isEnabled());
        out.put("channels", channels);
        out.put("health", health());
        return out;
    }

    @Transactional
    public Map<String, Object> setSingle(Map<String, Object> body) {
        String rackKey = resolveRackKey(asString(body.get("rackKey")));
        String bus = normalizeAddress(asString(body.get("busAddress")));
        int ch1 = clampLevel(body.get("ch1"));
        int ch2 = clampLevel(body.get("ch2"));
        ensureSeed(rackKey);

        PlantLedCommandLog log = beginLog(rackKey, "LED_SET_CHANNEL", "SINGLE", bus, body);
        try {
            if (gatewayProperties.getLed().isEnabled()) {
                Map<String, Object> req = new LinkedHashMap<>();
                req.put("busAddress", bus);
                req.put("ch1", ch1);
                req.put("ch2", ch2);
                Map<String, Object> resp = httpClient.postJson(
                        gatewayProperties.getLed().getBaseUrl(), "/led/set",
                        gatewayProperties.getLed().getApiKey(), req,
                        gatewayProperties.getLed().getConnectTimeoutMs(),
                        gatewayProperties.getLed().getReadTimeoutMs());
                finishLog(log, "ACKED", "gateway ok");
                upsertLevels(rackKey, bus, ch1, ch2, true, "SET");
                Map<String, Object> out = resultBase(log, bus, ch1, ch2);
                out.put("gateway", resp);
                return out;
            }
            upsertLevels(rackKey, bus, ch1, ch2, true, "SET");
            finishLog(log, "ACKED", "local mock (gateway.disabled)");
            return resultBase(log, bus, ch1, ch2);
        } catch (Exception e) {
            finishLog(log, "FAILED", e.getMessage());
            throw new IllegalStateException("LED 单设备调光失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> readSingle(String rackKey, String busAddress) {
        String key = resolveRackKey(rackKey);
        String bus = normalizeAddress(busAddress);
        ensureSeed(key);
        PlantLedCommandLog log = beginLog(key, "LED_READ_CHANNEL", "SINGLE", bus,
                Collections.singletonMap("busAddress", bus));
        try {
            int ch1;
            int ch2;
            if (gatewayProperties.getLed().isEnabled()) {
                Map<String, Object> resp = httpClient.getJson(
                        gatewayProperties.getLed().getBaseUrl(),
                        "/led/read?busAddress=" + bus,
                        gatewayProperties.getLed().getApiKey(),
                        gatewayProperties.getLed().getConnectTimeoutMs(),
                        gatewayProperties.getLed().getReadTimeoutMs());
                ch1 = clampLevel(resp.get("ch1"));
                ch2 = clampLevel(resp.get("ch2"));
                upsertLevels(key, bus, ch1, ch2, true, "READ");
                finishLog(log, "ACKED", "gateway ok");
            } else {
                ch1 = levelOf(key, bus, 1);
                ch2 = levelOf(key, bus, 2);
                finishLog(log, "ACKED", "local mock read");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("commandId", "led-" + log.getId());
            out.put("busAddress", bus);
            out.put("ch1", ch1);
            out.put("ch2", ch2);
            out.put("readAt", new Date());
            return out;
        } catch (Exception e) {
            finishLog(log, "FAILED", e.getMessage());
            throw new IllegalStateException("LED 单设备读取失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> broadcastSet(Map<String, Object> body) {
        String rackKey = resolveRackKey(asString(body.get("rackKey")));
        int ch1 = clampLevel(body.get("ch1"));
        int ch2 = clampLevel(body.get("ch2"));
        ensureSeed(rackKey);
        PlantLedCommandLog log = beginLog(rackKey, "LED_BROADCAST_SET", "BROADCAST", null, body);
        try {
            if (gatewayProperties.getLed().isEnabled()) {
                Map<String, Object> req = new LinkedHashMap<>();
                req.put("ch1", ch1);
                req.put("ch2", ch2);
                httpClient.postJson(
                        gatewayProperties.getLed().getBaseUrl(), "/led/broadcast/set",
                        gatewayProperties.getLed().getApiKey(), req,
                        gatewayProperties.getLed().getConnectTimeoutMs(),
                        gatewayProperties.getLed().getReadTimeoutMs());
                finishLog(log, "ACKED", "broadcast sent");
                // V1.5：广播无逐台 ack，等待约 3 秒再 read-all 核实
                try {
                    Thread.sleep(3000L);
                    syncFromGateway(rackKey);
                } catch (Exception ignore) {
                    for (String addr : ADDRESSES) {
                        upsertLevels(rackKey, addr, ch1, ch2, true, "BROADCAST");
                    }
                }
            } else {
                finishLog(log, "ACKED", "local mock broadcast");
                for (String addr : ADDRESSES) {
                    upsertLevels(rackKey, addr, ch1, ch2, true, "BROADCAST");
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("commandId", "led-" + log.getId());
            out.put("status", log.getStatus());
            out.put("scope", "BROADCAST");
            out.put("ch1", ch1);
            out.put("ch2", ch2);
            out.put("state", buildStateResponse(rackKey));
            return out;
        } catch (Exception e) {
            finishLog(log, "FAILED", e.getMessage());
            throw new IllegalStateException("LED 广播失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> readAll(String rackKey) {
        String key = resolveRackKey(rackKey);
        ensureSeed(key);
        PlantLedCommandLog log = beginLog(key, "LED_READ_ALL", "BROADCAST", null, Collections.emptyMap());
        try {
            List<Map<String, Object>> devices;
            if (gatewayProperties.getLed().isEnabled()) {
                devices = syncFromGateway(key);
                finishLog(log, "ACKED", "gateway read-all");
            } else {
                devices = new ArrayList<>();
                for (String addr : ADDRESSES) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("busAddress", addr);
                    row.put("ch1", levelOf(key, addr, 1));
                    row.put("ch2", levelOf(key, addr, 2));
                    row.put("ok", true);
                    devices.add(row);
                }
                finishLog(log, "ACKED", "local mock read-all");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("rackKey", key);
            out.put("commandId", "led-" + log.getId());
            out.put("devices", devices);
            out.put("readAt", new Date());
            return out;
        } catch (Exception e) {
            finishLog(log, "FAILED", e.getMessage());
            throw new IllegalStateException("LED 读全部失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> syncFromGateway(String key) throws Exception {
        Map<String, Object> resp = httpClient.getJson(
                gatewayProperties.getLed().getBaseUrl(), "/led/read-all",
                gatewayProperties.getLed().getApiKey(),
                gatewayProperties.getLed().getConnectTimeoutMs(),
                gatewayProperties.getLed().getReadTimeoutMs());
        List<Map<String, Object>> devices = new ArrayList<>();
        Object listObj = resp.get("devices");
        if (!(listObj instanceof List)) {
            return devices;
        }
        for (Object item : (List<?>) listObj) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> d = (Map<String, Object>) item;
            String bus = normalizeAddress(asString(d.get("busAddress")));
            boolean ok = d.get("ok") == null || Boolean.parseBoolean(String.valueOf(d.get("ok")));
            Integer ch1 = null;
            Integer ch2 = null;
            if (ok && d.get("ch1") != null && d.get("ch2") != null) {
                ch1 = clampLevel(d.get("ch1"));
                ch2 = clampLevel(d.get("ch2"));
                upsertLevels(key, bus, ch1, ch2, true, "READ");
            } else {
                // 失败时不把 null 当成 0，只标离线
                markOffline(key, bus);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("busAddress", bus);
            row.put("ch1", ch1);
            row.put("ch2", ch2);
            row.put("ok", ok);
            if (d.get("error") != null) {
                row.put("error", d.get("error"));
            }
            devices.add(row);
        }
        return devices;
    }

    /**
     * 前端 16 滑条一次性下发：led.ch1..ch16（0-100）→ 8 台设备。
     */
    @Transactional
    public Map<String, Object> applyUiChannels(Map<String, Object> body) {
        String rackKey = resolveRackKey(asString(body.get("rackKey")));
        @SuppressWarnings("unchecked")
        Map<String, Object> levels = body.get("levels") instanceof Map
                ? (Map<String, Object>) body.get("levels") : body;
        ensureSeed(rackKey);
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < ADDRESSES.length; i++) {
            String bus = ADDRESSES[i];
            int uiCh1 = i * 2 + 1;
            int uiCh2 = i * 2 + 2;
            int ch1 = percentToLevel(levels.get("led.ch" + uiCh1));
            if (levels.get("led.ch" + uiCh1) == null && levels.get(String.valueOf(uiCh1)) != null) {
                ch1 = percentToLevel(levels.get(String.valueOf(uiCh1)));
            }
            int ch2 = percentToLevel(levels.get("led.ch" + uiCh2));
            if (levels.get("led.ch" + uiCh2) == null && levels.get(String.valueOf(uiCh2)) != null) {
                ch2 = percentToLevel(levels.get(String.valueOf(uiCh2)));
            }
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("rackKey", rackKey);
            req.put("busAddress", bus);
            req.put("ch1", ch1);
            req.put("ch2", ch2);
            results.add(setSingle(req));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rackKey", rackKey);
        out.put("results", results);
        out.put("state", getState(rackKey));
        return out;
    }

    private void ensureSeed(String rackKey) {
        List<PlantLedState> existing = stateRepository.findByRackKeyOrderByBusAddressAscChannelAsc(rackKey);
        if (existing != null && existing.size() >= ADDRESSES.length * 2) {
            return;
        }
        Date now = new Date();
        for (int i = 0; i < ADDRESSES.length; i++) {
            for (int ch = 1; ch <= 2; ch++) {
                Optional<PlantLedState> opt = stateRepository.findByRackKeyAndBusAddressAndChannel(
                        rackKey, ADDRESSES[i], ch);
                if (opt.isPresent()) {
                    PlantLedState existingRow = opt.get();
                    // V1.5 光谱表变更时刷新展示名
                    if (!SPECTRA[i][ch - 1].equals(existingRow.getSpectrum())) {
                        existingRow.setSpectrum(SPECTRA[i][ch - 1]);
                        stateRepository.save(existingRow);
                    }
                    continue;
                }
                PlantLedState s = new PlantLedState();
                s.setRackKey(rackKey);
                s.setBusAddress(ADDRESSES[i]);
                s.setChannel(ch);
                s.setSpectrum(SPECTRA[i][ch - 1]);
                s.setLevel(0);
                s.setOnline(true);
                s.setSource("MOCK");
                s.setUpdatedAt(now);
                stateRepository.save(s);
            }
        }
    }

    private void upsertLevels(String rackKey, String bus, int ch1, int ch2, boolean online, String source) {
        upsertOne(rackKey, bus, 1, ch1, online, source);
        upsertOne(rackKey, bus, 2, ch2, online, source);
    }

    private void markOffline(String rackKey, String bus) {
        for (int ch = 1; ch <= 2; ch++) {
            Optional<PlantLedState> opt = stateRepository.findByRackKeyAndBusAddressAndChannel(rackKey, bus, ch);
            if (!opt.isPresent()) {
                continue;
            }
            PlantLedState s = opt.get();
            s.setOnline(false);
            s.setSource("READ");
            s.setUpdatedAt(new Date());
            stateRepository.save(s);
        }
    }

    private void upsertOne(String rackKey, String bus, int channel, int level, boolean online, String source) {
        PlantLedState s = stateRepository.findByRackKeyAndBusAddressAndChannel(rackKey, bus, channel)
                .orElseGet(PlantLedState::new);
        s.setRackKey(rackKey);
        s.setBusAddress(bus);
        s.setChannel(channel);
        if (s.getSpectrum() == null) {
            int idx = indexOfAddress(bus);
            if (idx >= 0) {
                s.setSpectrum(SPECTRA[idx][channel - 1]);
            }
        }
        s.setLevel(level);
        s.setOnline(online);
        s.setSource(source);
        s.setUpdatedAt(new Date());
        stateRepository.save(s);
    }

    private int levelOf(String rackKey, String bus, int channel) {
        return stateRepository.findByRackKeyAndBusAddressAndChannel(rackKey, bus, channel)
                .map(PlantLedState::getLevel)
                .orElse(0);
    }

    private PlantLedCommandLog beginLog(String rackKey, String type, String scope, String bus, Object payload) {
        PlantLedCommandLog log = new PlantLedCommandLog();
        log.setRackKey(rackKey);
        log.setCommandType(type);
        log.setScope(scope);
        log.setBusAddress(bus);
        try {
            log.setPayloadJson(objectMapper.writeValueAsString(payload == null ? Collections.emptyMap() : payload));
        } catch (Exception e) {
            log.setPayloadJson("{}");
        }
        log.setStatus("PENDING");
        log.setCreatedAt(new Date());
        return commandLogRepository.save(log);
    }

    private void finishLog(PlantLedCommandLog log, String status, String message) {
        log.setStatus(status);
        log.setResultMessage(message == null ? null : (message.length() > 500 ? message.substring(0, 500) : message));
        log.setFinishedAt(new Date());
        commandLogRepository.save(log);
    }

    private Map<String, Object> resultBase(PlantLedCommandLog log, String bus, int ch1, int ch2) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commandId", "led-" + log.getId());
        out.put("status", log.getStatus());
        out.put("busAddress", bus);
        out.put("ch1", ch1);
        out.put("ch2", ch2);
        return out;
    }

    private Map<String, Object> toStateMap(PlantLedState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("busAddress", s.getBusAddress());
        m.put("channel", s.getChannel());
        m.put("spectrum", s.getSpectrum());
        m.put("level", s.getLevel());
        m.put("percent", levelToPercent(s.getLevel()));
        m.put("online", Boolean.TRUE.equals(s.getOnline()));
        m.put("source", s.getSource());
        m.put("updatedAt", s.getUpdatedAt());
        int idx = indexOfAddress(s.getBusAddress());
        if (idx >= 0 && s.getChannel() != null) {
            m.put("uiChannel", "led.ch" + (idx * 2 + s.getChannel()));
        }
        return m;
    }

    private static int indexOfAddress(String bus) {
        for (int i = 0; i < ADDRESSES.length; i++) {
            if (ADDRESSES[i].equalsIgnoreCase(bus)) {
                return i;
            }
        }
        return -1;
    }

    private static String normalizeAddress(String bus) {
        if (bus == null || bus.trim().isEmpty()) {
            throw new IllegalArgumentException("busAddress 不能为空");
        }
        String v = bus.trim().toUpperCase(Locale.ROOT);
        if (!v.startsWith("0X")) {
            try {
                v = String.format("0x%02X", Integer.parseInt(v));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("非法 busAddress: " + bus);
            }
        } else {
            v = "0x" + v.substring(2);
        }
        boolean ok = false;
        for (String a : ADDRESSES) {
            if (a.equalsIgnoreCase(v)) {
                ok = true;
                v = a;
                break;
            }
        }
        if (!ok) {
            throw new IllegalArgumentException("busAddress 须为 0x96~0x9D: " + bus);
        }
        return v;
    }

    private static int clampLevel(Object v) {
        if (v == null) {
            return 0;
        }
        int n;
        if (v instanceof Number) {
            n = ((Number) v).intValue();
        } else {
            n = (int) Math.round(Double.parseDouble(String.valueOf(v).trim()));
        }
        if (n < 0) {
            return 0;
        }
        if (n > 255) {
            return 255;
        }
        return n;
    }

    /** UI 0-100 → 协议 0-255；若已 >100 则按 0-255 理解 */
    private static int percentToLevel(Object v) {
        if (v == null) {
            return 0;
        }
        double d = v instanceof Number ? ((Number) v).doubleValue() : Double.parseDouble(String.valueOf(v).trim());
        if (d <= 100) {
            return (int) Math.round(d * 255.0 / 100.0);
        }
        return clampLevel(d);
    }

    private static int levelToPercent(Integer level) {
        if (level == null) {
            return 0;
        }
        return (int) Math.round(level * 100.0 / 255.0);
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
