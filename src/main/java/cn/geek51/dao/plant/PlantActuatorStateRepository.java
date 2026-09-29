package cn.geek51.dao.plant;

import cn.geek51.domain.plant.PlantActuatorState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantActuatorStateRepository extends JpaRepository<PlantActuatorState, Long> {
    List<PlantActuatorState> findByDeviceKeyOrderByActuatorIdAsc(String deviceKey);

    Optional<PlantActuatorState> findByDeviceKeyAndActuatorId(String deviceKey, String actuatorId);
}
