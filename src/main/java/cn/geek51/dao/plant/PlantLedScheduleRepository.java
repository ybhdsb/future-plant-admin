package cn.geek51.dao.plant;

import cn.geek51.domain.plant.PlantLedSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlantLedScheduleRepository extends JpaRepository<PlantLedSchedule, Long> {
    List<PlantLedSchedule> findByDeviceKeyOrderByIdDesc(String deviceKey);

    List<PlantLedSchedule> findByEnabledTrue();
}
