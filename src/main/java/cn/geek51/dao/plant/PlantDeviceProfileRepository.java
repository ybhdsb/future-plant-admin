package cn.geek51.dao.plant;

import cn.geek51.domain.plant.PlantDeviceProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlantDeviceProfileRepository extends JpaRepository<PlantDeviceProfile, Long> {
    Optional<PlantDeviceProfile> findByDeviceKey(String deviceKey);
}
