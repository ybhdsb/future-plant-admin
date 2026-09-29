package cn.geek51.service.plant;

import java.util.*;

/**
 * 对照《原型系统建设方案》与 V1 任务表的执行器目录。
 */
public final class PlantActuatorCatalog {

    private PlantActuatorCatalog() {
    }

    public static final class ActuatorDef {
        public final String id;
        public final String name;
        public final String group;
        public final String tip;
        public final String commandType; // PUMP_SET / RELAY_SET / FAN_SET / SHADE_SET
        public final boolean v1;

        public ActuatorDef(String id, String name, String group, String tip, String commandType, boolean v1) {
            this.id = id;
            this.name = name;
            this.group = group;
            this.tip = tip;
            this.commandType = commandType;
            this.v1 = v1;
        }
    }

    private static final List<ActuatorDef> ALL = Collections.unmodifiableList(Arrays.asList(
            new ActuatorDef("pump.water", "循环水泵/供液泵", "供水曝气", "建设方案：基础供液水泵", "PUMP_SET", true),
            new ActuatorDef("pump.oxygen", "氧泵/曝气泵", "供水曝气", "任务表：营养液曝气", "PUMP_SET", true),
            new ActuatorDef("fan.1", "循环风机 1", "通风", "建设方案：循环风机×2", "FAN_SET", true),
            new ActuatorDef("fan.2", "循环风机 2", "通风", "建设方案：循环风机×2", "FAN_SET", true),
            new ActuatorDef("relay.1", "继电器开关 1", "通用继电器", "控制箱预留通用负载", "RELAY_SET", true),
            new ActuatorDef("relay.2", "继电器开关 2", "通用继电器", "控制箱预留通用负载", "RELAY_SET", true),
            new ActuatorDef("shade.1", "遮光电机 1", "遮光(V2预留)", "建设方案 V2：遮光轨道电机", "SHADE_SET", false),
            new ActuatorDef("shade.2", "遮光电机 2", "遮光(V2预留)", "建设方案 V2：遮光轨道电机", "SHADE_SET", false),
            new ActuatorDef("shade.3", "遮光电机 3", "遮光(V2预留)", "建设方案 V2：遮光轨道电机", "SHADE_SET", false),
            new ActuatorDef("shade.4", "遮光电机 4", "遮光(V2预留)", "建设方案 V2：遮光轨道电机", "SHADE_SET", false)
    ));

    public static List<ActuatorDef> all() {
        return ALL;
    }

    public static List<ActuatorDef> v1() {
        List<ActuatorDef> list = new ArrayList<>();
        for (ActuatorDef d : ALL) {
            if (d.v1) {
                list.add(d);
            }
        }
        return list;
    }

    public static ActuatorDef find(String actuatorId) {
        if (actuatorId == null) {
            return null;
        }
        for (ActuatorDef d : ALL) {
            if (d.id.equals(actuatorId)) {
                return d;
            }
        }
        return null;
    }

    public static List<Map<String, Object>> toMaps(boolean includeV2) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ActuatorDef d : ALL) {
            if (!includeV2 && !d.v1) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("actuatorId", d.id);
            m.put("name", d.name);
            m.put("group", d.group);
            m.put("tip", d.tip);
            m.put("commandType", d.commandType);
            m.put("v1", d.v1);
            out.add(m);
        }
        return out;
    }
}
