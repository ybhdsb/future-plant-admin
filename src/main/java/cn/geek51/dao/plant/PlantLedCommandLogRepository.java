package cn.geek51.dao.plant;

import cn.geek51.domain.plant.PlantLedCommandLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlantLedCommandLogRepository extends JpaRepository<PlantLedCommandLog, Long> {
    List<PlantLedCommandLog> findByRackKeyOrderByCreatedAtDesc(String rackKey, Pageable pageable);
}
