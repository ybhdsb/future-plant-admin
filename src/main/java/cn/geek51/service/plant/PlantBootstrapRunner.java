package cn.geek51.service.plant;

import cn.geek51.domain.plant.PlantDeviceProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化默认未来植物设备画像。
 * 默认实机（plant.mock.enabled=false）：启动时强制关闭库内 Mock，避免仍显示样例图。
 * 仅当 plant.mock.enabled=true 时写入 Mock 遥测/样例媒体。
 */
@Component
@Order(1)
public class PlantBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlantBootstrapRunner.class);

    private final PlantService plantService;

    @Value("${plant.mock.enabled:false}")
    private boolean mockEnabledByDefault;

    public PlantBootstrapRunner(PlantService plantService) {
        this.plantService = plantService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String key = PlantService.DEFAULT_DEVICE_KEY;
            plantService.ensureProfile(key);
            if (mockEnabledByDefault) {
                plantService.setMockEnabled(key, true);
                plantService.ingestMockTelemetry(key);
                plantService.ensureSampleMedia(key);
                log.info("未来植物已开启 Mock：deviceKey={}", key);
            } else {
                plantService.setMockEnabled(key, false);
                log.info("未来植物默认实机模式：deviceKey={}", key);
            }
            PlantDeviceProfile profile = plantService.ensureProfile(key);
            plantService.recordEvent(key, "INFO", "BOOTSTRAP",
                    Boolean.TRUE.equals(profile.getMockEnabled())
                            ? "未来植物模块已初始化（Mock 模式）"
                            : "未来植物模块已初始化（实机模式）");
        } catch (Exception e) {
            log.error("未来植物启动初始化失败", e);
        }
    }
}
