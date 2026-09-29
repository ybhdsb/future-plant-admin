package cn.geek51.service.plant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PlantLedScheduleRunner {

    private final PlantLedScheduleService scheduleService;

    public PlantLedScheduleRunner(PlantLedScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void tick() {
        try {
            scheduleService.evaluateAndApply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
