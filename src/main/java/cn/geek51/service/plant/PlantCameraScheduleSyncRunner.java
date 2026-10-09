package cn.geek51.service.plant;

import cn.geek51.config.PlantGatewayProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时拍照跑在同事网关侧：平台周期性同步 last_file → 本地媒体库，供「最近影像」展示。
 */
@Component
public class PlantCameraScheduleSyncRunner {

    private final PlantGatewayProperties gatewayProperties;
    private final PlantCameraHardwareService cameraHardwareService;

    public PlantCameraScheduleSyncRunner(PlantGatewayProperties gatewayProperties,
                                         PlantCameraHardwareService cameraHardwareService) {
        this.gatewayProperties = gatewayProperties;
        this.cameraHardwareService = cameraHardwareService;
    }

    @Scheduled(fixedDelayString = "${plant.gateway.camera.schedule-sync-ms:5000}")
    public void sync() {
        if (!gatewayProperties.getCamera().isEnabled()) {
            return;
        }
        cameraHardwareService.syncScheduledPhotos(null);
    }
}
