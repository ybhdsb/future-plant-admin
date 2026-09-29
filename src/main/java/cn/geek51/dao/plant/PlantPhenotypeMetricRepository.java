package cn.geek51.dao.plant;

import cn.geek51.domain.plant.PlantPhenotypeMetric;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface PlantPhenotypeMetricRepository extends JpaRepository<PlantPhenotypeMetric, Long> {

    List<PlantPhenotypeMetric> findByDeviceKeyAndPlantCodeOrderBySampledAtDesc(
            String deviceKey, String plantCode, Pageable pageable);

    List<PlantPhenotypeMetric> findByDeviceKeyOrderBySampledAtDesc(String deviceKey, Pageable pageable);

    @Query("select m from PlantPhenotypeMetric m where m.deviceKey = :deviceKey "
            + "and (:plantCode is null or m.plantCode = :plantCode) "
            + "and (:metric is null or m.metric = :metric) "
            + "and (:from is null or m.sampledAt >= :from) "
            + "and (:to is null or m.sampledAt <= :to) "
            + "order by m.sampledAt desc")
    List<PlantPhenotypeMetric> query(@Param("deviceKey") String deviceKey,
                                     @Param("plantCode") String plantCode,
                                     @Param("metric") String metric,
                                     @Param("from") Date from,
                                     @Param("to") Date to,
                                     Pageable pageable);

    long countByDeviceKey(String deviceKey);
}
