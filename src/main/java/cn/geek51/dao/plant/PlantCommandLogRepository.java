package cn.geek51.dao.plant;

import cn.geek51.domain.plant.PlantCommandLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantCommandLogRepository extends JpaRepository<PlantCommandLog, Long> {
    List<PlantCommandLog> findByDeviceKeyOrderByCreatedAtDesc(String deviceKey, Pageable pageable);

    List<PlantCommandLog> findByDeviceKeyAndStatusOrderByCreatedAtAsc(String deviceKey, String status, Pageable pageable);

    Optional<PlantCommandLog> findByClientRequestId(String clientRequestId);
}
