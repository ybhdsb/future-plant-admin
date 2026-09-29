package cn.geek51.service.plant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化默认未来植物设备画像，并写入一包 Mock 数据，保证页面开箱可用。
 */
@Component
public class PlantBootstrapRunner implements ApplicationRunner {

    private final PlantService plantService;

    public PlantBootstrapRunner(PlantService plantService) {
        this.plantService = plantService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            plantService.ensureProfile(PlantService.DEFAULT_DEVICE_KEY);
            plantService.ingestMockTelemetry(PlantService.DEFAULT_DEVICE_KEY);
            plantService.ensureSampleMedia(PlantService.DEFAULT_DEVICE_KEY);
            plantService.recordEvent(PlantService.DEFAULT_DEVICE_KEY, "INFO", "BOOTSTRAP",
                    "未来植物模块已初始化（Mock 数据就绪）");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
