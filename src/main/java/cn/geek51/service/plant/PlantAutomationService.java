package cn.geek51.service.plant;

import cn.geek51.dao.plant.PlantAutomationRuleRepository;
import cn.geek51.dao.plant.PlantSensorReadingRepository;
import cn.geek51.domain.plant.PlantAutomationRule;
import cn.geek51.domain.plant.PlantSensorReading;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class PlantAutomationService {

    private final PlantAutomationRuleRepository ruleRepository;
    private final PlantSensorReadingRepository readingRepository;
    private final PlantService plantService;

    /** deviceKey|actuatorId -> last desired on/off to avoid spam */
    private final Map<String, String> lastDesired = new HashMap<>();

    public PlantAutomationService(PlantAutomationRuleRepository ruleRepository,
                                  PlantSensorReadingRepository readingRepository,
                                  PlantService plantService) {
        this.ruleRepository = ruleRepository;
        this.readingRepository = readingRepository;
        this.plantService = plantService;
    }

    public List<PlantAutomationRule> list(String deviceKey, String actuatorId) {
        String key = plantService.resolveDeviceKey(deviceKey);
        if (actuatorId != null && !actuatorId.trim().isEmpty()) {
            return ruleRepository.findByDeviceKeyAndActuatorIdOrderByPriorityAscIdDesc(key, actuatorId.trim());
        }
        return ruleRepository.findByDeviceKeyOrderByPriorityAscIdDesc(key);
    }

    @Transactional
    public PlantAutomationRule save(Map<String, Object> body) {
        String deviceKey = plantService.resolveDeviceKey(asString(body.get("deviceKey")));
        Long id = asLong(body.get("id"));
        PlantAutomationRule r = id == null ? new PlantAutomationRule()
                : ruleRepository.findById(id).orElse(new PlantAutomationRule());
        Date now = new Date();
        if (r.getId() == null) {
            r.setCreatedAt(now);
            if (asString(body.get("anchorDate")) == null) {
                r.setAnchorDate(new SimpleDateFormat("yyyy-MM-dd").format(now));
            }
        }
        r.setDeviceKey(deviceKey);
        r.setName(asString(body.get("name")));
        r.setActuatorId(asString(body.get("actuatorId")));
        r.setRuleType(asString(body.get("ruleType")));
        r.setAction(asString(body.get("action")) == null ? "ON" : asString(body.get("action")));
        r.setBrightness(asInt(body.get("brightness"), null));
        r.setOnTime(asString(body.get("onTime")));
        r.setOffTime(asString(body.get("offTime")));
        r.setEveryNDays(asInt(body.get("everyNDays"), null));
        r.setDurationMinutes(asInt(body.get("durationMinutes"), null));
        r.setOnMinutes(asInt(body.get("onMinutes"), null));
        r.setOffMinutes(asInt(body.get("offMinutes"), null));
        r.setMetric(asString(body.get("metric")));
        r.setOperatorName(asString(body.get("operator")));
        r.setThresholdValue(asDouble(body.get("thresholdValue")));
        r.setCooldownMinutes(asInt(body.get("cooldownMinutes"), 5));
        if (asString(body.get("anchorDate")) != null) {
            r.setAnchorDate(asString(body.get("anchorDate")));
        }
        r.setEnabled(body.get("enabled") == null || Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        r.setPriority(asInt(body.get("priority"), 100));
        r.setRemark(asString(body.get("remark")));
        r.setUpdatedAt(now);
        validate(r);
        return ruleRepository.save(r);
    }

    @Transactional
    public void delete(Long id) {
        ruleRepository.deleteById(id);
    }

    @Transactional
    public PlantAutomationRule setEnabled(Long id, boolean enabled) {
        PlantAutomationRule r = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("规则不存在"));
        r.setEnabled(enabled);
        r.setUpdatedAt(new Date());
        return ruleRepository.save(r);
    }

    /** 每分钟评估时间类规则 */
    public void evaluateTimeRules() {
        Calendar now = Calendar.getInstance();
        List<PlantAutomationRule> rules = ruleRepository.findByEnabledTrueOrderByPriorityAscIdAsc();
        // 按 device+actuator 聚合：同一目标若有多条命中，优先级数字小者优先；同优先级后写覆盖
        Map<String, Desired> desiredMap = new LinkedHashMap<>();
        for (PlantAutomationRule r : rules) {
            if ("SENSOR_THRESHOLD".equalsIgnoreCase(r.getRuleType())) {
                continue; // 传感触发在遥测入库时评估
            }
            Desired d = evalTimeRule(r, now);
            if (d == null) {
                continue;
            }
            String k = r.getDeviceKey() + "|" + r.getActuatorId();
            Desired old = desiredMap.get(k);
            if (old == null || r.getPriority() == null || old.priority == null
                    || r.getPriority() <= old.priority) {
                d.priority = r.getPriority();
                d.rule = r;
                desiredMap.put(k, d);
            }
        }
        for (Desired d : desiredMap.values()) {
            applyDesired(d);
        }
        // 时间窗规则：未命中开灯/开启窗时，对 DAILY_WINDOW 目标置 OFF
        Set<String> dailyTargets = new HashSet<>();
        Set<String> dailyOn = new HashSet<>();
        for (PlantAutomationRule r : rules) {
            if (!"DAILY_WINDOW".equalsIgnoreCase(r.getRuleType())) {
                continue;
            }
            String k = r.getDeviceKey() + "|" + r.getActuatorId();
            dailyTargets.add(k);
            Desired d = evalTimeRule(r, now);
            if (d != null && d.on) {
                dailyOn.add(k);
            }
        }
        for (String k : dailyTargets) {
            if (dailyOn.contains(k)) {
                continue;
            }
            if (desiredMap.containsKey(k)) {
                continue;
            }
            String[] parts = k.split("\\|", 2);
            Desired off = new Desired();
            off.deviceKey = parts[0];
            off.actuatorId = parts[1];
            off.on = false;
            off.priority = 9999;
            applyDesired(off);
        }
    }

    /** 遥测入库后立刻检查传感触发规则 */
    @Transactional
    public void evaluateSensorRules(String deviceKey) {
        String key = plantService.resolveDeviceKey(deviceKey);
        Calendar now = Calendar.getInstance();
        List<PlantAutomationRule> rules = ruleRepository.findByEnabledTrueOrderByPriorityAscIdAsc();
        for (PlantAutomationRule r : rules) {
            if (!key.equals(r.getDeviceKey())) {
                continue;
            }
            if (!"SENSOR_THRESHOLD".equalsIgnoreCase(r.getRuleType())) {
                continue;
            }
            if (!matchSensor(r)) {
                continue;
            }
            if (inCooldown(r, now.getTime())) {
                continue;
            }
            Desired d = new Desired();
            d.deviceKey = r.getDeviceKey();
            d.actuatorId = r.getActuatorId();
            d.on = !"OFF".equalsIgnoreCase(r.getAction());
            d.brightness = r.getBrightness();
            d.rule = r;
            d.priority = r.getPriority();
            applyDesired(d);
            r.setLastTriggeredAt(now.getTime());
            r.setUpdatedAt(now.getTime());
            ruleRepository.save(r);
        }
    }

    private Desired evalTimeRule(PlantAutomationRule r, Calendar now) {
        if ("DAILY_WINDOW".equalsIgnoreCase(r.getRuleType())) {
            if (!inDailyWindow(r, now)) {
                return null;
            }
            return desiredFromRule(r, true);
        }
        if ("INTERVAL".equalsIgnoreCase(r.getRuleType())) {
            int onM = nz(r.getOnMinutes());
            int offM = nz(r.getOffMinutes());
            int cycle = Math.max(1, onM + offM);
            int minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            boolean on = (minuteOfDay % cycle) < onM;
            return desiredFromRule(r, on);
        }
        if ("EVERY_N_DAYS".equalsIgnoreCase(r.getRuleType())) {
            if (!isAnchorDay(r, now)) {
                return null;
            }
            int on = parseHm(r.getOnTime());
            if (on < 0) {
                return null;
            }
            int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            int dur = r.getDurationMinutes() == null ? 10 : Math.max(1, r.getDurationMinutes());
            if (nowMin >= on && nowMin < on + dur) {
                return desiredFromRule(r, true);
            }
            // 刚过窗口则关（仅在窗口结束后 1 分钟内强制关，避免与其他规则冲突过多）
            if (nowMin == on + dur) {
                return desiredFromRule(r, false);
            }
            return null;
        }
        return null;
    }

    private Desired desiredFromRule(PlantAutomationRule r, boolean onPhase) {
        Desired d = new Desired();
        d.deviceKey = r.getDeviceKey();
        d.actuatorId = r.getActuatorId();
        d.rule = r;
        d.priority = r.getPriority();
        d.brightness = r.getBrightness();
        if (!onPhase) {
            d.on = false;
            return d;
        }
        if ("OFF".equalsIgnoreCase(r.getAction())) {
            d.on = false;
        } else {
            d.on = true;
        }
        return d;
    }

    private void applyDesired(Desired d) {
        if (d == null || d.actuatorId == null) {
            return;
        }
        String cacheKey = d.deviceKey + "|" + d.actuatorId;
        String sig = (d.on ? "ON" : "OFF") + ":" + (d.brightness == null ? "-" : d.brightness);
        if (sig.equals(lastDesired.get(cacheKey))) {
            return;
        }
        try {
            String commandType = resolveCommandType(d.actuatorId);
            if ("LED_SET".equals(commandType) || d.actuatorId.startsWith("led.")) {
                int b = d.on ? (d.brightness == null ? 80 : d.brightness) : 0;
                plantService.applyLedAllChannels(d.deviceKey, b, "automation");
            } else {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("actuatorId", d.actuatorId);
                payload.put("on", d.on);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("deviceKey", d.deviceKey);
                body.put("commandType", commandType);
                body.put("clientRequestId", "auto-" + System.currentTimeMillis());
                body.put("payload", payload);
                plantService.issueCommand(body);
            }
            lastDesired.put(cacheKey, sig);
            if (d.rule != null) {
                d.rule.setLastAppliedAt(new Date());
                d.rule.setLastAppliedAction(sig);
                d.rule.setUpdatedAt(new Date());
                ruleRepository.save(d.rule);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean matchSensor(PlantAutomationRule r) {
        if (r.getMetric() == null || r.getOperatorName() == null || r.getThresholdValue() == null) {
            return false;
        }
        List<PlantSensorReading> latest = readingRepository.findLatestByDeviceAndMetric(
                r.getDeviceKey(), r.getMetric(), PageRequest.of(0, 1));
        if (latest.isEmpty() || latest.get(0).getValueNum() == null) {
            return false;
        }
        double v = latest.get(0).getValueNum();
        double th = r.getThresholdValue();
        String op = r.getOperatorName().trim().toUpperCase(Locale.ROOT);
        if ("LT".equals(op) || "<".equals(op)) {
            return v < th;
        }
        if ("LTE".equals(op) || "<=".equals(op)) {
            return v <= th;
        }
        if ("GT".equals(op) || ">".equals(op)) {
            return v > th;
        }
        if ("GTE".equals(op) || ">=".equals(op)) {
            return v >= th;
        }
        if ("EQ".equals(op) || "=".equals(op) || "==".equals(op)) {
            return Math.abs(v - th) < 1e-6;
        }
        return false;
    }

    private boolean inCooldown(PlantAutomationRule r, Date now) {
        if (r.getLastTriggeredAt() == null) {
            return false;
        }
        int cd = r.getCooldownMinutes() == null ? 5 : Math.max(0, r.getCooldownMinutes());
        long elapsed = now.getTime() - r.getLastTriggeredAt().getTime();
        return elapsed < cd * 60_000L;
    }

    private boolean inDailyWindow(PlantAutomationRule r, Calendar now) {
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int on = parseHm(r.getOnTime());
        int off = parseHm(r.getOffTime());
        if (on < 0 || off < 0) {
            return false;
        }
        if (on == off) {
            return true;
        }
        if (on < off) {
            return nowMin >= on && nowMin < off;
        }
        return nowMin >= on || nowMin < off;
    }

    private boolean isAnchorDay(PlantAutomationRule r, Calendar now) {
        int n = r.getEveryNDays() == null ? 1 : Math.max(1, r.getEveryNDays());
        Date anchor;
        try {
            anchor = new SimpleDateFormat("yyyy-MM-dd").parse(
                    r.getAnchorDate() == null ? new SimpleDateFormat("yyyy-MM-dd").format(r.getCreatedAt())
                            : r.getAnchorDate());
        } catch (Exception e) {
            anchor = r.getCreatedAt() == null ? new Date() : r.getCreatedAt();
        }
        Calendar a = Calendar.getInstance();
        a.setTime(anchor);
        clearTime(a);
        Calendar b = (Calendar) now.clone();
        clearTime(b);
        long days = (b.getTimeInMillis() - a.getTimeInMillis()) / (24L * 3600_000L);
        if (days < 0) {
            return false;
        }
        return days % n == 0;
    }

    private static void clearTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private String resolveCommandType(String actuatorId) {
        PlantActuatorCatalog.ActuatorDef def = PlantActuatorCatalog.find(actuatorId);
        if (def != null) {
            return def.commandType;
        }
        if (actuatorId != null && actuatorId.startsWith("led.")) {
            return "LED_SET";
        }
        if (actuatorId != null && actuatorId.startsWith("fan.")) {
            return "FAN_SET";
        }
        if (actuatorId != null && actuatorId.startsWith("pump.")) {
            return "PUMP_SET";
        }
        return "RELAY_SET";
    }

    private void validate(PlantAutomationRule r) {
        if (r.getActuatorId() == null || r.getActuatorId().isEmpty()) {
            throw new IllegalArgumentException("请选择执行器");
        }
        if (r.getRuleType() == null) {
            throw new IllegalArgumentException("请选择规则类型");
        }
        String t = r.getRuleType();
        if ("DAILY_WINDOW".equalsIgnoreCase(t)) {
            if (parseHm(r.getOnTime()) < 0 || parseHm(r.getOffTime()) < 0) {
                throw new IllegalArgumentException("请填写每天开/关时间 HH:mm");
            }
        } else if ("EVERY_N_DAYS".equalsIgnoreCase(t)) {
            if (r.getEveryNDays() == null || r.getEveryNDays() < 1) {
                throw new IllegalArgumentException("每隔天数至少为 1");
            }
            if (parseHm(r.getOnTime()) < 0) {
                throw new IllegalArgumentException("请填写开启时刻 HH:mm");
            }
            if (r.getDurationMinutes() == null || r.getDurationMinutes() < 1) {
                throw new IllegalArgumentException("请填写单次开启持续分钟");
            }
        } else if ("INTERVAL".equalsIgnoreCase(t)) {
            if (nz(r.getOnMinutes()) + nz(r.getOffMinutes()) <= 0) {
                throw new IllegalArgumentException("请填写开/关分钟数");
            }
        } else if ("SENSOR_THRESHOLD".equalsIgnoreCase(t)) {
            if (r.getMetric() == null || r.getOperatorName() == null || r.getThresholdValue() == null) {
                throw new IllegalArgumentException("请完整填写传感器测点、比较符与阈值");
            }
        } else {
            throw new IllegalArgumentException("不支持的规则类型: " + t);
        }
    }

    private static int parseHm(String hm) {
        if (hm == null || !hm.matches("^\\d{1,2}:\\d{2}$")) {
            return -1;
        }
        String[] p = hm.split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            return -1;
        }
        return h * 60 + m;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static Long asLong(Object o) {
        if (o == null || String.valueOf(o).trim().isEmpty()) {
            return null;
        }
        return Long.parseLong(String.valueOf(o));
    }

    private static Integer asInt(Object o, Integer def) {
        if (o == null || String.valueOf(o).trim().isEmpty()) {
            return def;
        }
        return Integer.parseInt(String.valueOf(o));
    }

    private static Double asDouble(Object o) {
        if (o == null || String.valueOf(o).trim().isEmpty()) {
            return null;
        }
        return Double.parseDouble(String.valueOf(o));
    }

    private static class Desired {
        String deviceKey;
        String actuatorId;
        boolean on;
        Integer brightness;
        Integer priority;
        PlantAutomationRule rule;
    }
}
