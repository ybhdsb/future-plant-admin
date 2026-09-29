package cn.geek51.dao.plant;

import cn.geek51.domain.plant.PlantCropSpecimen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantCropSpecimenRepository extends JpaRepository<PlantCropSpecimen, Long> {
    List<PlantCropSpecimen> findByDeviceKeyOrderByPosRowAscPosColAsc(String deviceKey);

    Optional<PlantCropSpecimen> findByDeviceKeyAndPlantCode(String deviceKey, String plantCode);

    long countByDeviceKey(String deviceKey);
}
