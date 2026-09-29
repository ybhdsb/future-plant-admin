package cn.geek51.controller;

import cn.geek51.domain.UserAuth;
import cn.geek51.util.UserContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 未来植物独立项目页面转发。
 */
@Controller
public class RedirectController {

    @GetMapping("/login")
    public String toLogin() {
        // 默认进入商业级 Vue 登录页；旧 Layui 登录保留为 /login/legacy
        return "redirect:/app/login";
    }

    @GetMapping("/login/legacy")
    public String toLoginLegacy() {
        return "user/login";
    }

    @GetMapping("/register")
    public String toRegister() {
        return "user/register";
    }

    @GetMapping("/devices")
    public String toDevices() {
        return "device_view";
    }

    @GetMapping("/model_library")
    public String toModelLibrary() {
        return "model_library_view";
    }

    @GetMapping("/model_library/{id}")
    public String toModelLibraryDetail(@PathVariable("id") Long id, Model model) {
        model.addAttribute("assetId", id);
        return "model_library_detail_view";
    }

    @GetMapping("/datasets")
    public String toDatasets() {
        return "dataset_view";
    }

    @GetMapping("/datasets/{id}")
    public String toDatasetDetail(@PathVariable("id") Long id, Model model) {
        model.addAttribute("assetId", id);
        return "dataset_detail_view";
    }

    @GetMapping("/auth")
    public String toAuth() {
        return "auth_view";
    }

    @GetMapping({"/", "/index"})
    public String toIndex(Model model) {
        // 默认进入 Vue SPA；旧 Layui 壳保留为 /index/legacy
        return "redirect:/app/";
    }

    @GetMapping("/index/legacy")
    public String toIndexLegacy(Model model) {
        UserAuth user = UserContext.getCurrentUser();
        model.addAttribute("user", user);
        return "index_view";
    }
}
