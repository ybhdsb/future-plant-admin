package cn.geek51.service;

import cn.geek51.dao.DeviceJpaReposity;
import cn.geek51.domain.Devices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 实验室固定坐标：设备未上报位置时统一使用该默认值。
 */
@Component
@Order(15)
public class DeviceLocationDefaults implements ApplicationRunner {

    @Value("${device.default-latitude:30.475800}")
    private String defaultLatitude;

    @Value("${device.default-longitude:114.353600}")
    private String defaultLongitude;

    @Autowired
    private DeviceJpaReposity deviceRepository;

    public String getDefaultLatitude() {
        return defaultLatitude;
    }

    public String getDefaultLongitude() {
        return defaultLongitude;
    }

    public void applyIfMissing(Devices device) {
        if (device == null) {
            return;
        }
        if (!isValidCoordinate(device.getLatituded())) {
            device.setLatituded(defaultLatitude);
        }
        if (!isValidCoordinate(device.getLongituded())) {
            device.setLongituded(defaultLongitude);
        }
    }

    public String resolveLatitude(Devices device) {
        if (device != null && isValidCoordinate(device.getLatituded())) {
            return device.getLatituded().trim();
        }
        return defaultLatitude;
    }

    public String resolveLongitude(Devices device) {
        if (device != null && isValidCoordinate(device.getLongituded())) {
            return device.getLongituded().trim();
        }
        return defaultLongitude;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Devices> devices = deviceRepository.findAll();
        boolean changed = false;
        for (Devices device : devices) {
            if (!isValidCoordinate(device.getLatituded()) || !isValidCoordinate(device.getLongituded())) {
                applyIfMissing(device);
                deviceRepository.save(device);
                changed = true;
            }
        }
        if (changed) {
            System.out.println("已为缺失坐标的设备写入默认位置: " + defaultLatitude + ", " + defaultLongitude);
        }
    }

    private boolean isValidCoordinate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            double number = Double.parseDouble(value.trim());
            return !Double.isNaN(number) && !Double.isInfinite(number);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
