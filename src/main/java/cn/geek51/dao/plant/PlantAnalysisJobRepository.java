package cn.geek51.dao.plant;

import cn.geek51.domain.plant.PlantAnalysisJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlantAnalysisJobRepository extends JpaRepository<PlantAnalysisJob, Long> {
    List<PlantAnalysisJob> findByDeviceKeyOrderByCreatedAtDesc(String deviceKey, Pageable pageable);

    long countByDeviceKey(String deviceKey);
}
