package cn.geek51.service;

import cn.geek51.domain.Devices;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public interface DeviceService {
    List<Devices> getAllDevices();

    List<Devices> getDevicesByStatus(String status);

    Devices getDeviceById(Long id);

    Devices getDeviceByKey(String deviceKey);

    /** MQTT / HTTP 心跳：按 deviceKey 匹配，自动更新 IP */
    void saveOrUpdateDevice(Devices device);

    void saveOrUpdateDevice(Devices device, Map<String, Object> heartbeatExtras);

    void refreshDeviceStatuses();

    Devices createDevice(String deviceName, String deviceType, String ipAddress, String deviceKey,
                         String sshUsername, String sshPassword);

    /** 创建设备后可选自动部署心跳，返回 create + deploy 结果 */
    Map<String, Object> createDeviceAndDeploy(String deviceName, String deviceType, String ipAddress,
                                              String deviceKey, String sshUsername, String sshPassword,
                                              boolean autoDeploy);

    Map<String, Object> redeployHeartbeat(Long id);

    void deleteDevice(Long id);

    Map<String, Object> createJoinInvite(int ttlMinutes);

    Map<String, Object> getJoinInvite(String token);

    Devices joinByInvite(String token, String deviceName, String deviceType);
}
