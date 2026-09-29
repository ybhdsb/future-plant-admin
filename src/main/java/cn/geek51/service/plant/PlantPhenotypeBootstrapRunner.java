package cn.geek51.service.plant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class PlantPhenotypeBootstrapRunner implements ApplicationRunner {

    private final PlantPhenotypeService phenotypeService;

    public PlantPhenotypeBootstrapRunner(PlantPhenotypeService phenotypeService) {
        this.phenotypeService = phenotypeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            phenotypeService.ensureSeed(PlantService.DEFAULT_DEVICE_KEY);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
