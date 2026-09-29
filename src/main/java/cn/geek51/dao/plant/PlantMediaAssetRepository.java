package cn.geek51.dao.plant;

import cn.geek51.domain.plant.PlantMediaAsset;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlantMediaAssetRepository extends JpaRepository<PlantMediaAsset, Long> {
    List<PlantMediaAsset> findByDeviceKeyOrderByCapturedAtDesc(String deviceKey, Pageable pageable);
}
