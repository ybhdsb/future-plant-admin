package cn.geek51.service.plant;

import cn.geek51.dao.plant.PlantLedScheduleRepository;
import cn.geek51.domain.plant.PlantLedSchedule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PlantLedScheduleService {

    private final PlantLedScheduleRepository scheduleRepository;
    private final PlantService plantService;

    /** 避免每分钟重复发同样亮度 */
    private final Map<String, Integer> lastAppliedBrightness = new HashMap<>();

    public PlantLedScheduleService(PlantLedScheduleRepository scheduleRepository, PlantService plantService) {
        this.scheduleRepository = scheduleRepository;
        this.plantService = plantService;
    }

    public List<PlantLedSchedule> list(String deviceKey) {
        return scheduleRepository.findByDeviceKeyOrderByIdDesc(plantService.resolveDeviceKey(deviceKey));
    }

    @Transactional
    public PlantLedSchedule save(Map<String, Object> body) {
        String deviceKey = plantService.resolveDeviceKey(asString(body.get("deviceKey")));
        Long id = asLong(body.get("id"));
        PlantLedSchedule s = id == null ? new PlantLedSchedule()
                : scheduleRepository.findById(id).orElse(new PlantLedSchedule());
        Date now = new Date();
        if (s.getId() == null) {
            s.setCreatedAt(now);
        }
        s.setDeviceKey(deviceKey);
        s.setName(asString(body.get("name")));
        s.setScheduleType(asString(body.get("scheduleType")));
        s.setBrightness(clampBrightness(asInt(body.get("brightness"), 80)));
        s.setOnTime(asString(body.get("onTime")));
        s.setOffTime(asString(body.get("offTime")));
        s.setOnMinutes(asInt(body.get("onMinutes"), null));
        s.setOffMinutes(asInt(body.get("offMinutes"), null));
        s.setEnabled(body.get("enabled") == null || Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        s.setRemark(asString(body.get("remark")));
        s.setUpdatedAt(now);
        validate(s);
        return scheduleRepository.save(s);
    }

    @Transactional
    public void delete(Long id) {
        scheduleRepository.deleteById(id);
    }

    @Transactional
    public PlantLedSchedule setEnabled(Long id, boolean enabled) {
        PlantLedSchedule s = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("策略不存在"));
        s.setEnabled(enabled);
        s.setUpdatedAt(new Date());
        return scheduleRepository.save(s);
    }

    /**
     * 每分钟评估所有启用策略，若当前应对某设备施加亮度则下发。
     * 多策略同时命中时取最高亮度（更安全的补光优先）。
     */
    public void evaluateAndApply() {
        List<PlantLedSchedule> enabled = scheduleRepository.findByEnabledTrue();
        Map<String, Integer> desired = new HashMap<>();
        Calendar now = Calendar.getInstance();
        for (PlantLedSchedule s : enabled) {
            Integer brightness = desiredBrightness(s, now);
            if (brightness == null) {
                continue;
            }
            Integer prev = desired.get(s.getDeviceKey());
            if (prev == null || brightness > prev) {
                desired.put(s.getDeviceKey(), brightness);
            }
        }
        // 对启用了策略但当前应关灯的设备，若没有任何策略要求开灯，则关灯
        Set<String> devicesWithSchedule = new HashSet<>();
        for (PlantLedSchedule s : enabled) {
            devicesWithSchedule.add(s.getDeviceKey());
            if (!desired.containsKey(s.getDeviceKey())) {
                // 该设备至少有一个启用策略，但此刻都未命中开灯 → 关灯
                desired.putIfAbsent(s.getDeviceKey(), 0);
            }
        }
        for (Map.Entry<String, Integer> e : desired.entrySet()) {
            applyIfChanged(e.getKey(), e.getValue());
        }
    }

    private Integer desiredBrightness(PlantLedSchedule s, Calendar now) {
        if ("DAILY_WINDOW".equalsIgnoreCase(s.getScheduleType())) {
            return evalDailyWindow(s, now) ? clampBrightness(s.getBrightness()) : null;
        }
        if ("INTERVAL".equalsIgnoreCase(s.getScheduleType())) {
            return evalInterval(s, now) ? clampBrightness(s.getBrightness()) : null;
        }
        return null;
    }

    private boolean evalDailyWindow(PlantLedSchedule s, Calendar now) {
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int on = parseHm(s.getOnTime());
        int off = parseHm(s.getOffTime());
        if (on < 0 || off < 0) {
            return false;
        }
        if (on == off) {
            return true; // 全天
        }
        if (on < off) {
            return nowMin >= on && nowMin < off;
        }
        // 跨午夜，如 20:00-06:00
        return nowMin >= on || nowMin < off;
    }

    private boolean evalInterval(PlantLedSchedule s, Calendar now) {
        int onM = s.getOnMinutes() == null ? 0 : s.getOnMinutes();
        int offM = s.getOffMinutes() == null ? 0 : s.getOffMinutes();
        if (onM <= 0 && offM <= 0) {
            return false;
        }
        int cycle = Math.max(1, onM + offM);
        // 以当天 0 点为周期起点，便于理解
        int minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int pos = minuteOfDay % cycle;
        return pos < onM;
    }

    private void applyIfChanged(String deviceKey, int brightness) {
        Integer last = lastAppliedBrightness.get(deviceKey);
        if (last != null && last == brightness) {
            return;
        }
        try {
            plantService.applyLedAllChannels(deviceKey, brightness, "led-schedule");
            lastAppliedBrightness.put(deviceKey, brightness);
            // 回写策略最近执行时间（同设备多策略共享一次执行记录）
            Date now = new Date();
            for (PlantLedSchedule s : scheduleRepository.findByDeviceKeyOrderByIdDesc(deviceKey)) {
                if (!Boolean.TRUE.equals(s.getEnabled())) {
                    continue;
                }
                s.setLastAppliedAt(now);
                s.setLastAppliedBrightness(brightness);
                s.setUpdatedAt(now);
                scheduleRepository.save(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void validate(PlantLedSchedule s) {
        if (s.getScheduleType() == null || s.getScheduleType().trim().isEmpty()) {
            throw new IllegalArgumentException("scheduleType 不能为空");
        }
        if ("DAILY_WINDOW".equalsIgnoreCase(s.getScheduleType())) {
            if (parseHm(s.getOnTime()) < 0 || parseHm(s.getOffTime()) < 0) {
                throw new IllegalArgumentException("请填写正确的开/关时间，格式 HH:mm");
            }
        } else if ("INTERVAL".equalsIgnoreCase(s.getScheduleType())) {
            if ((s.getOnMinutes() == null || s.getOnMinutes() < 0)
                    || (s.getOffMinutes() == null || s.getOffMinutes() < 0)
                    || ((s.getOnMinutes() == 0) && (s.getOffMinutes() == 0))) {
                throw new IllegalArgumentException("请填写开灯/关灯分钟数");
            }
        } else {
            throw new IllegalArgumentException("不支持的策略类型: " + s.getScheduleType());
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

    private static int clampBrightness(Integer v) {
        if (v == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, v));
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
}
