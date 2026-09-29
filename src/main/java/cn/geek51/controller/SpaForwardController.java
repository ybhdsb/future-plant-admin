package cn.geek51.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Vue SPA（/app）常用路由回退。更完整的回退见 SpaFallbackFilter。
 */
@Controller
public class SpaForwardController {

    @GetMapping({
            "/app",
            "/app/",
            "/app/login",
            "/app/dashboard",
            "/app/history",
            "/app/data",
            "/app/control",
            "/app/control/",
            "/app/control/led",
            "/app/control/actuators",
            "/app/control/camera",
            "/app/control/logs",
            "/app/phenotype",
            "/app/phenotype/",
            "/app/phenotype/digital",
            "/app/phenotype/metrics",
            "/app/phenotype/media",
            "/app/phenotype/jobs",
            "/app/devices",
            "/app/model-library",
            "/app/model-library/{id}",
            "/app/datasets",
            "/app/datasets/{id}",
            "/app/auth-manage"
    })
    public String forwardSpa() {
        return "forward:/app/index.html";
    }
}
