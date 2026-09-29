package cn.geek51.service.plant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mock 模式下定时灌入遥测，方便历史曲线有数据。
 */
@Component
public class PlantMockScheduler {

    private final PlantService plantService;

    @Value("${plant.mock.enabled:true}")
    private boolean mockEnabled;

    public PlantMockScheduler(PlantService plantService) {
        this.plantService = plantService;
    }

    @Scheduled(fixedDelayString = "${plant.mock.interval-ms:5000}")
    public void tick() {
        if (!mockEnabled) {
            return;
        }
        try {
            if (Boolean.TRUE.equals(plantService.ensureProfile(null).getMockEnabled())) {
                plantService.ingestMockTelemetry(null);
            }
        } catch (Exception e) {
            // keep scheduler alive
        }
    }
}
