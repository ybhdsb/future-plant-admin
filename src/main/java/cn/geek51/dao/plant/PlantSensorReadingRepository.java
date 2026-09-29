package cn.geek51.dao.plant;

import cn.geek51.domain.plant.PlantSensorReading;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface PlantSensorReadingRepository extends JpaRepository<PlantSensorReading, Long> {

    List<PlantSensorReading> findByDeviceKeyAndMetricAndSampledAtBetweenOrderBySampledAtAsc(
            String deviceKey, String metric, Date from, Date to);

    @Query("select r from PlantSensorReading r where r.deviceKey = :deviceKey and r.metric = :metric " +
            "order by r.sampledAt desc")
    List<PlantSensorReading> findLatestByDeviceAndMetric(@Param("deviceKey") String deviceKey,
                                                         @Param("metric") String metric,
                                                         Pageable pageable);

    @Query("select r from PlantSensorReading r where r.deviceKey = :deviceKey " +
            "and (:metric is null or r.metric = :metric) " +
            "and r.sampledAt >= :from and r.sampledAt <= :to order by r.sampledAt asc")
    List<PlantSensorReading> findForExport(@Param("deviceKey") String deviceKey,
                                           @Param("metric") String metric,
                                           @Param("from") Date from,
                                           @Param("to") Date to,
                                           Pageable pageable);
}
