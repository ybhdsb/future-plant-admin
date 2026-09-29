package cn.geek51.service.plant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PlantAutomationRunner {

    private final PlantAutomationService automationService;

    public PlantAutomationRunner(PlantAutomationService automationService) {
        this.automationService = automationService;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void tick() {
        try {
            automationService.evaluateTimeRules();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
