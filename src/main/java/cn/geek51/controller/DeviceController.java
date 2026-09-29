package cn.geek51.controller;

import cn.geek51.domain.Devices;
import cn.geek51.service.DeviceService;
import cn.geek51.service.DeviceLocationDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/device")
public class DeviceController {
    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceLocationDefaults deviceLocationDefaults;

    /**
     * 手机扫码落地页的可访问根地址。必须是手机能打开的局域网/公网地址，不能是 localhost。
     */
    @org.springframework.beans.factory.annotation.Value("${device.join-base-url:}")
    private String joinBaseUrl;

    @GetMapping
    public String showDevices(Model model) {
        return "device_view";
    }

    /** 手机扫码落地页（无需登录） */
    @GetMapping("/join")
    public String showJoinPage(@RequestParam(value = "token", required = false) String token, Model model) {
        model.addAttribute("token", token == null ? "" : token);
        return "device_join";
    }

    @GetMapping("/api/list")
    @ResponseBody
    public List<Map<String, Object>> getDeviceList() {
        List<Devices> devices = deviceService.getAllDevices();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Devices device : devices) {
            result.add(toDeviceView(device));
        }
        return result;
    }

    @GetMapping("/api/status")
    @ResponseBody
    public Map<String, Integer> getDeviceStatus() {
        int online = deviceService.getDevicesByStatus("online").size();
        int offline = deviceService.getDevicesByStatus("offline").size();
        int warning = deviceService.getDevicesByStatus("warning").size();
        int total = online + offline + warning;
        Map<String, Integer> statusMap = new HashMap<>();
        statusMap.put("online", online);
        statusMap.put("offline", offline);
        statusMap.put("warning", warning);
        statusMap.put("total", total);
        return statusMap;
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createDevice(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean autoDeploy = true;
            if (body.get("autoDeploy") != null) {
                autoDeploy = Boolean.parseBoolean(String.valueOf(body.get("autoDeploy")));
            }
            Map<String, Object> created = deviceService.createDeviceAndDeploy(
                    asString(body.get("deviceName")),
                    asString(body.get("deviceType")),
                    asString(body.get("ipAddress")),
                    asString(body.get("deviceKey")),
                    asString(body.get("sshUsername")),
                    asString(body.get("sshPassword")),
                    autoDeploy
            );
            Devices device = (Devices) created.get("device");
            result.put("success", true);
            result.put("message", "设备已创建");
            result.put("device", toDeviceView(device));
            result.put("deviceKey", created.get("deviceKey"));
            result.put("deployAttempted", created.get("deployAttempted"));
            result.put("deploySuccess", created.get("deploySuccess"));
            result.put("deployMessage", created.get("deployMessage"));
            result.put("brokerHost", created.get("brokerHost"));
            if (created.get("deployLog") != null) {
                result.put("deployLog", created.get("deployLog"));
            }
            // 板端已部署时提示稍后刷新；手机/跳过部署也返回成功创建
            if (Boolean.TRUE.equals(created.get("deployAttempted")) && !Boolean.TRUE.equals(created.get("deploySuccess"))) {
                result.put("message", "设备已创建，但本次心跳部署失败。"
                        + "若稍后显示在线，可能是该机器上旧心跳仍在发送，不代表本次部署成功。"
                        + "请点击「重新部署心跳」确认。"
                        + (created.get("deployMessage") != null ? (" 原因: " + created.get("deployMessage")) : ""));
                result.put("needRedeploy", true);
                return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(result);
            }
            if (Boolean.TRUE.equals(created.get("deploySuccess"))) {
                result.put("message", "设备已创建，心跳已自动部署（开机自启）。约 10 秒内应变为在线。");
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @PostMapping("/api/{id}/redeploy")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> redeployHeartbeat(@PathVariable("id") Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> deploy = deviceService.redeployHeartbeat(id);
            result.put("success", Boolean.TRUE.equals(deploy.get("success")));
            result.put("message", deploy.get("message"));
            result.put("brokerHost", deploy.get("brokerHost"));
            result.put("log", deploy.get("log"));
            if (Boolean.TRUE.equals(deploy.get("success"))) {
                return ResponseEntity.ok(result);
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "重新部署失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteDevice(@PathVariable("id") Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            deviceService.deleteDevice(id);
            result.put("success", true);
            result.put("message", "设备已删除");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /** 管理端：生成扫码邀请 */
    @PostMapping("/api/invite")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createInvite(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        int ttlMinutes = 30;
        if (body != null && body.get("ttlMinutes") != null) {
            try {
                ttlMinutes = Integer.parseInt(String.valueOf(body.get("ttlMinutes")));
            } catch (NumberFormatException ignored) {
            }
        }
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> invite = deviceService.createJoinInvite(ttlMinutes);
            String joinUrl = buildJoinUrl(request, String.valueOf(invite.get("token")));
            invite.put("joinUrl", joinUrl);
            invite.put("success", true);
            return ResponseEntity.ok(invite);
        } catch (IllegalStateException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /** 手机端：查询邀请是否有效 */
    @GetMapping("/api/invite/{token}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getInvite(@PathVariable("token") String token) {
        Map<String, Object> invite = deviceService.getJoinInvite(token);
        if (invite == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("valid", false);
            result.put("message", "邀请码无效或已过期");
            return ResponseEntity.status(HttpStatus.GONE).body(result);
        }
        invite.put("success", true);
        return ResponseEntity.ok(invite);
    }

    /** 手机端：扫码加入 */
    @PostMapping("/api/join")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> joinByInvite(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Devices device = deviceService.joinByInvite(
                    asString(body.get("token")),
                    asString(body.get("deviceName")),
                    asString(body.get("deviceType"))
            );
            result.put("success", true);
            result.put("message", "加入成功");
            result.put("deviceKey", device.getDeviceKey());
            result.put("device", toDeviceView(device));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "加入失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 手机端 HTTP 心跳（浏览器不便直连 MQTT 时使用）。
     * 也可供 Jetson 在 MQTT 不可用时兜底。
     */
    @PostMapping("/api/heartbeat")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> httpHeartbeat(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            Devices device = new Devices();
            device.setDeviceKey(asString(body.get("deviceKey")));
            device.setDeviceName(asString(body.get("deviceName")));
            device.setDeviceType(asString(body.get("deviceType")));
            String ip = asString(body.get("ipAddress"));
            if (ip == null || "".equals(ip.trim())) {
                ip = resolveClientIp(request);
            }
            device.setIpAddress(ip);
            device.setStatus("online");
            device.setLatituded(asString(body.get("latituded")));
            device.setLongituded(asString(body.get("longituded")));
            device.setCpuLoad(asDouble(body.get("cpuLoad")));
            device.setMemPct(asDouble(body.get("memPct")));
            device.setGpuLoad(asDouble(body.get("gpuLoad")));
            device.setBatteryPct(asDouble(body.get("batteryPct")));
            device.setRttMs(asDouble(body.get("rttMs")));
            device.setPacketLossPct(asDouble(body.get("packetLossPct")));
            device.setTemperatureC(asDouble(body.get("temperatureC")));

            Map<String, Object> extras = new HashMap<>();
            Object applied = body.get("applied_strategy");
            if (applied == null) {
                applied = body.get("appliedStrategy");
            }
            if (applied instanceof Map) {
                extras.put("applied_strategy", applied);
            }
            deviceService.saveOrUpdateDevice(device, extras);

            Devices saved = deviceService.getDeviceByKey(device.getDeviceKey());
            result.put("success", true);
            result.put("message", "heartbeat ok");
            result.put("deviceKey", saved == null ? device.getDeviceKey() : saved.getDeviceKey());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    private Map<String, Object> toDeviceView(Devices device) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", device.getDeviceId());
        item.put("deviceKey", device.getDeviceKey());
        item.put("code", device.getDeviceKey() != null ? device.getDeviceKey() : device.getDeviceName());
        item.put("name", device.getDeviceName());
        item.put("deviceType", device.getDeviceType());
        item.put("deviceTypeText", getTypeText(device.getDeviceType(), device.getDeviceName()));
        item.put("status", device.getStatus());
        item.put("statusText", getStatusText(device.getStatus()));
        item.put("ip", device.getIpAddress());
        item.put("latituded", deviceLocationDefaults.resolveLatitude(device));
        item.put("longituded", deviceLocationDefaults.resolveLongitude(device));
        item.put("address", buildLocationText(device));
        item.put("manager", "-");
        item.put("lastReport", formatTime(device));
        item.put("image", getImage(device));
        return item;
    }

    private String getStatusText(String status) {
        if ("online".equals(status)) {
            return "在线";
        }
        if ("warning".equals(status)) {
            return "告警";
        }
        return "离线";
    }

    private String getTypeText(String deviceType, String deviceName) {
        String type = deviceType;
        if (type == null || "".equals(type.trim())) {
            type = deviceName;
        }
        if (type == null) {
            return "设备";
        }
        String lower = type.toLowerCase();
        if (lower.contains("jetson")) {
            return "Jetson";
        }
        if (lower.contains("raspberry") || type.contains("树莓")) {
            return "树莓派";
        }
        if (lower.contains("robot") || type.contains("机器")) {
            return "机器人";
        }
        if (lower.contains("phone") || lower.contains("mobile") || type.contains("手机")) {
            return "手机";
        }
        return type;
    }

    private String buildLocationText(Devices device) {
        if (device.getLatituded() == null && device.getLongituded() == null) {
            return "-";
        }
        return nullToDash(device.getLatituded()) + ", " + nullToDash(device.getLongituded());
    }

    private String formatTime(Devices device) {
        if (device.getLastHeartbeatTime() == null) {
            return "-";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(device.getLastHeartbeatTime());
    }

    private String getImage(Devices device) {
        String type = device.getDeviceType() != null ? device.getDeviceType().toLowerCase() : "";
        String deviceName = device.getDeviceName() != null ? device.getDeviceName() : "";
        String lowerDeviceName = deviceName.toLowerCase();

        if (type.contains("jetson") || lowerDeviceName.contains("jetson")) {
            return "/static/images/icons/jetson.svg";
        }
        if (type.contains("raspberry") || lowerDeviceName.contains("raspberry")
                || lowerDeviceName.contains("raspberrypi") || deviceName.contains("树莓派")) {
            return "/static/images/icons/raspberry.svg";
        }
        if (type.contains("robot") || lowerDeviceName.contains("robot") || deviceName.contains("机器人")) {
            return "/static/images/icons/robot.svg";
        }
        if (type.contains("phone") || lowerDeviceName.contains("phone")
                || lowerDeviceName.contains("mobile") || deviceName.contains("手机")) {
            return "/static/images/icons/phone.svg";
        }

        if ("online".equals(device.getStatus())) {
            return "/static/images/logos/assets-line-big-ic-laptop-issue.png";
        }
        if ("warning".equals(device.getStatus())) {
            return "/static/images/icons/设备关机报警.png";
        }
        return "/static/images/icons/设备关机.png";
    }

    private String nullToDash(String value) {
        return value == null || "".equals(value) ? "-" : value;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return null;
            }
            return Double.parseDouble(text);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildJoinUrl(HttpServletRequest request, String token) {
        String base = trimTrailingSlash(joinBaseUrl);
        if (base == null || isLocalHostUrl(base)) {
            String proto = request.getHeader("X-Forwarded-Proto");
            if (proto == null || "".equals(proto.trim())) {
                proto = request.getScheme();
            }
            String host = request.getHeader("X-Forwarded-Host");
            if (host == null || "".equals(host.trim())) {
                host = request.getHeader("Host");
            }
            if (host == null || "".equals(host.trim())) {
                host = request.getServerName() + ":" + request.getServerPort();
            }
            base = proto + "://" + host;
            // 浏览器用 localhost 打开管理页时，二维码不能继续用 localhost
            if (isLocalHostUrl(base)) {
                throw new IllegalStateException(
                        "请在 application.yml 配置 device.join-base-url 为手机可访问的地址，例如 http://192.168.124.8:8707");
            }
        }
        return base + "/device/join?token=" + token;
    }

    private boolean isLocalHostUrl(String url) {
        if (url == null) {
            return true;
        }
        String lower = url.toLowerCase();
        return lower.contains("://localhost") || lower.contains("://127.0.0.1") || lower.contains("://0.0.0.0");
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ("".equals(trimmed)) {
            return null;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !"".equals(value.trim()) && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
