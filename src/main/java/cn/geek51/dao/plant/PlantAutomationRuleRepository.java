package cn.geek51.dao.plant;

import cn.geek51.domain.plant.PlantAutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlantAutomationRuleRepository extends JpaRepository<PlantAutomationRule, Long> {
    List<PlantAutomationRule> findByDeviceKeyOrderByPriorityAscIdDesc(String deviceKey);

    List<PlantAutomationRule> findByEnabledTrueOrderByPriorityAscIdAsc();

    List<PlantAutomationRule> findByDeviceKeyAndActuatorIdOrderByPriorityAscIdDesc(String deviceKey, String actuatorId);
}
