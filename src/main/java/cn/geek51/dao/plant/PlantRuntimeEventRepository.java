package cn.geek51.dao.plant;

import cn.geek51.domain.plant.PlantRuntimeEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlantRuntimeEventRepository extends JpaRepository<PlantRuntimeEvent, Long> {
    List<PlantRuntimeEvent> findByDeviceKeyOrderByCreatedAtDesc(String deviceKey, Pageable pageable);
}
