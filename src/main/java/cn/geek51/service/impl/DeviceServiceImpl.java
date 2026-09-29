package cn.geek51.service.impl;

import cn.geek51.dao.DeviceJpaReposity;
import cn.geek51.domain.Devices;
import cn.geek51.service.DeviceHeartbeatDeployService;
import cn.geek51.service.DeviceJoinInviteService;
import cn.geek51.service.DeviceLocationDefaults;
import cn.geek51.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 未来植物独立项目：保留设备登记/心跳/在线状态，去掉能力本体与机器人依赖。
 */
@Service
public class DeviceServiceImpl implements DeviceService {

    @Autowired
    private DeviceJpaReposity deviceRepository;

    @Autowired
    private DeviceJoinInviteService joinInviteService;

    @Autowired
    private DeviceHeartbeatDeployService heartbeatDeployService;

    @Autowired
    private DeviceLocationDefaults deviceLocationDefaults;

    @Value("${device.offline-timeout-seconds:30}")
    private long offlineTimeoutSeconds;

    @Value("${device.auto-register-on-heartbeat:false}")
    private boolean autoRegisterOnHeartbeat;

    @Override
    public List<Devices> getAllDevices() {
        refreshDeviceStatuses();
        return deviceRepository.findAll();
    }

    @Override
    public List<Devices> getDevicesByStatus(String status) {
        refreshDeviceStatuses();
        return deviceRepository.findByStatus(status);
    }

    @Override
    public Devices getDeviceById(Long id) {
        return deviceRepository.findById(id).orElse(null);
    }

    @Override
    public Devices getDeviceByKey(String deviceKey) {
        if (isBlank(deviceKey)) {
            return null;
        }
        return deviceRepository.findByDeviceKey(deviceKey.trim());
    }

    @Override
    @Transactional
    public void saveOrUpdateDevice(Devices device) {
        saveOrUpdateDevice(device, null);
    }

    @Override
    @Transactional
    public void saveOrUpdateDevice(Devices device, Map<String, Object> heartbeatExtras) {
        normalizeDeviceIdentifier(device);
        Devices existingDevice = findExistingDevice(device);
        Date heartbeatTime = new Date();

        if (existingDevice != null) {
            mergeHeartbeat(existingDevice, device, heartbeatTime);
            deviceRepository.save(existingDevice);
        } else if (autoRegisterOnHeartbeat) {
            if (isBlank(device.getDeviceKey())) {
                device.setDeviceKey(generateDeviceKey(device.getDeviceType()));
            }
            device.setStatus(resolveStatus(device.getStatus()));
            device.setLastHeartbeatTime(heartbeatTime);
            if (isBlank(device.getDeviceType())) {
                device.setDeviceType(inferTypeFromName(device.getDeviceName()));
            }
            deviceRepository.save(device);
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${device.offline-check-interval-ms:10000}")
    @Transactional
    public void refreshDeviceStatuses() {
        long now = System.currentTimeMillis();
        List<Devices> candidates = deviceRepository.findByStatusNot("offline");
        for (Devices device : candidates) {
            Date lastHeartbeat = device.getLastHeartbeatTime();
            boolean expired = lastHeartbeat == null
                    || now - lastHeartbeat.getTime() > offlineTimeoutSeconds * 1000L;
            if (expired) {
                device.setStatus("offline");
                deviceRepository.save(device);
            }
        }
    }

    @Override
    @Transactional
    public Devices createDevice(String deviceName, String deviceType, String ipAddress, String deviceKey,
                                String sshUsername, String sshPassword) {
        String name = trimToNull(deviceName);
        if (name == null) {
            throw new IllegalArgumentException("设备名称不能为空");
        }
        String type = normalizeType(deviceType);
        String key = trimToNull(deviceKey);
        if (key == null) {
            key = generateDeviceKey(type);
        }
        if (deviceRepository.existsByDeviceKey(key)) {
            throw new IllegalArgumentException("设备标识已存在: " + key);
        }
        if (deviceRepository.findByDeviceName(name) != null) {
            throw new IllegalArgumentException("设备名称已存在: " + name);
        }

        Devices device = new Devices();
        device.setDeviceKey(key);
        device.setDeviceName(name);
        device.setDeviceType(type);
        if (isUsableDeviceIp(ipAddress)) {
            device.setIpAddress(ipAddress.trim());
        }
        if (!isBlank(sshUsername)) {
            device.setSshUsername(sshUsername.trim());
        }
        if (!isBlank(sshPassword)) {
            device.setSshPassword(sshPassword);
        }
        device.setStatus("offline");
        deviceLocationDefaults.applyIfMissing(device);
        return deviceRepository.save(device);
    }

    @Override
    public Map<String, Object> createDeviceAndDeploy(String deviceName, String deviceType, String ipAddress,
                                                     String deviceKey, String sshUsername, String sshPassword,
                                                     boolean autoDeploy) {
        Map<String, Object> result = new HashMap<>();
        Devices saved = createDevice(deviceName, deviceType, ipAddress, deviceKey, sshUsername, sshPassword);
        result.put("device", saved);
        result.put("deviceKey", saved.getDeviceKey());

        boolean wantDeploy = autoDeploy && heartbeatDeployService.shouldAutoDeploy(saved);
        result.put("deployAttempted", wantDeploy);
        if (!wantDeploy) {
            String type = saved.getDeviceType() == null ? "" : saved.getDeviceType();
            if ("phone".equalsIgnoreCase(type)) {
                result.put("deploySuccess", false);
                result.put("deployMessage", "手机设备请使用扫码加入，无需 SSH 部署");
            } else if (isBlank(saved.getIpAddress()) || isBlank(saved.getSshUsername()) || isBlank(saved.getSshPassword())) {
                result.put("deploySuccess", false);
                result.put("deployMessage", "未提供 IP/SSH 账号密码，已仅登记设备（离线）。");
            } else {
                result.put("deploySuccess", false);
                result.put("deployMessage", "已跳过自动部署");
            }
            return result;
        }

        Map<String, Object> deployResult = heartbeatDeployService.deploy(saved);
        result.put("deploySuccess", Boolean.TRUE.equals(deployResult.get("success")));
        result.put("deployMessage", deployResult.get("message"));
        result.put("deployLog", deployResult.get("log"));
        result.put("brokerHost", deployResult.get("brokerHost"));
        return result;
    }

    @Override
    public Map<String, Object> redeployHeartbeat(Long id) {
        Devices device = getDeviceById(id);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在");
        }
        Map<String, Object> deployResult = heartbeatDeployService.deploy(device);
        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", id);
        result.put("deviceKey", device.getDeviceKey());
        result.putAll(deployResult);
        return result;
    }

    @Override
    @Transactional
    public void deleteDevice(Long id) {
        if (id == null || !deviceRepository.existsById(id)) {
            throw new IllegalArgumentException("设备不存在");
        }
        Devices device = deviceRepository.findById(id).orElse(null);
        if (device != null) {
            heartbeatDeployService.stopHeartbeat(device);
        }
        deviceRepository.deleteById(id);
    }

    @Override
    public Map<String, Object> createJoinInvite(int ttlMinutes) {
        DeviceJoinInviteService.Invite invite = joinInviteService.create(ttlMinutes);
        Map<String, Object> result = new HashMap<>();
        result.put("token", invite.getToken());
        result.put("expireAt", invite.getExpireAt());
        result.put("ttlMinutes", ttlMinutes <= 0 ? 30 : ttlMinutes);
        return result;
    }

    @Override
    public Map<String, Object> getJoinInvite(String token) {
        DeviceJoinInviteService.Invite invite = joinInviteService.get(token);
        if (invite == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("token", invite.getToken());
        result.put("expireAt", invite.getExpireAt());
        result.put("valid", true);
        return result;
    }

    @Override
    @Transactional
    public Devices joinByInvite(String token, String deviceName, String deviceType) {
        if (!joinInviteService.consume(token)) {
            throw new IllegalArgumentException("邀请码无效或已过期，请重新扫码");
        }
        String name = trimToNull(deviceName);
        if (name == null) {
            name = "手机-" + System.currentTimeMillis() % 100000;
        }
        String type = normalizeType(isBlank(deviceType) ? "phone" : deviceType);
        Devices device = createDevice(name, type, null, null, null, null);
        device.setStatus("online");
        device.setLastHeartbeatTime(new Date());
        return deviceRepository.save(device);
    }

    private Devices findExistingDevice(Devices device) {
        if (!isBlank(device.getDeviceKey())) {
            return deviceRepository.findByDeviceKey(device.getDeviceKey().trim());
        }
        if (!isBlank(device.getDeviceName())) {
            return deviceRepository.findByDeviceName(device.getDeviceName().trim());
        }
        return null;
    }

    private void mergeHeartbeat(Devices existing, Devices incoming, Date heartbeatTime) {
        if (!isBlank(incoming.getDeviceKey()) && isBlank(existing.getDeviceKey())) {
            existing.setDeviceKey(incoming.getDeviceKey().trim());
        }
        if (!isBlank(incoming.getDeviceName())) {
            existing.setDeviceName(incoming.getDeviceName().trim());
        }
        if (!isBlank(incoming.getDeviceType()) && isBlank(existing.getDeviceType())) {
            existing.setDeviceType(normalizeType(incoming.getDeviceType()));
        }
        if (isUsableDeviceIp(incoming.getIpAddress())) {
            existing.setIpAddress(incoming.getIpAddress().trim());
        }
        existing.setStatus(resolveStatus(incoming.getStatus()));
        if (isValidCoordinate(incoming.getLatituded())) {
            existing.setLatituded(incoming.getLatituded().trim());
        }
        if (isValidCoordinate(incoming.getLongituded())) {
            existing.setLongituded(incoming.getLongituded().trim());
        }
        deviceLocationDefaults.applyIfMissing(existing);
        existing.setLastHeartbeatTime(heartbeatTime);
    }

    private String resolveStatus(String status) {
        if (isBlank(status)) {
            return "online";
        }
        return status.trim();
    }

    private void normalizeDeviceIdentifier(Devices device) {
        if (!isBlank(device.getDeviceKey())) {
            device.setDeviceKey(device.getDeviceKey().trim());
        }
        if (!isBlank(device.getDeviceName())) {
            device.setDeviceName(device.getDeviceName().trim());
        }
        if (isBlank(device.getDeviceName()) && !isBlank(device.getDeviceKey())) {
            device.setDeviceName(device.getDeviceKey());
        }
        if (isBlank(device.getDeviceKey()) && !isBlank(device.getDeviceName())) {
            device.setDeviceKey(device.getDeviceName().trim());
        }
        if (!isBlank(device.getDeviceType())) {
            device.setDeviceType(normalizeType(device.getDeviceType()));
        }
    }

    private String generateDeviceKey(String deviceType) {
        String prefix = normalizeType(deviceType);
        String key;
        do {
            key = prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        } while (deviceRepository.existsByDeviceKey(key));
        return key;
    }

    private String normalizeType(String deviceType) {
        if (isBlank(deviceType)) {
            return "plant_controller";
        }
        String type = deviceType.trim().toLowerCase();
        if (type.contains("jetson")) {
            return "jetson";
        }
        if (type.contains("raspberry") || type.contains("树莓") || type.contains("plant")) {
            return "raspberry";
        }
        if (type.contains("phone") || type.contains("mobile") || type.contains("手机")) {
            return "phone";
        }
        return type;
    }

    private String inferTypeFromName(String deviceName) {
        if (isBlank(deviceName)) {
            return "device";
        }
        return normalizeType(deviceName);
    }

    private boolean isUsableDeviceIp(String ipAddress) {
        if (isBlank(ipAddress)) {
            return false;
        }
        String ip = ipAddress.trim();
        return !"127.0.0.1".equals(ip) && !"localhost".equalsIgnoreCase(ip) && !ip.startsWith("127.");
    }

    private boolean isValidCoordinate(String value) {
        if (isBlank(value)) {
            return false;
        }
        try {
            double number = Double.parseDouble(value.trim());
            return !Double.isNaN(number) && !Double.isInfinite(number);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || "".equals(value.trim());
    }

    private String trimToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
