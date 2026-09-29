package cn.geek51.dao;

import cn.geek51.domain.Devices;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface DeviceJpaReposity extends JpaRepository<Devices, Long> {
    Devices findByDeviceKey(String deviceKey);

    Devices findByDeviceName(String deviceName);

    Devices findByIpAddress(String ipAddress);

    List<Devices> findByStatus(String status);

    List<Devices> findByStatusNotAndLastHeartbeatTimeBefore(String status, Date lastHeartbeatTime);

    List<Devices> findByStatusNotAndLastHeartbeatTimeIsNull(String status);

    List<Devices> findByStatusNot(String status);

    boolean existsByDeviceKey(String deviceKey);
}
