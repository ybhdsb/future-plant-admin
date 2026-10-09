package cn.geek51;

import cn.geek51.util.interceptor.LoginInterceptor;
import org.apache.ibatis.session.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import javax.sql.DataSource;
import javax.xml.crypto.Data;
import java.sql.Connection;

@SpringBootApplication(scanBasePackages = "cn.geek51")
@EnableScheduling
public class Application extends WebMvcConfigurationSupport {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor()).addPathPatterns("/**").excludePathPatterns(
                "/static/**", "/app/**", "/register", "/login/**", "/**.html", "/**.ico", "/auths/login",
                // 手机扫码加入与 HTTP 心跳（无需后台登录）
                "/device/join", "/device/join/**",
                "/device/api/join", "/device/api/invite/**", "/device/api/heartbeat",
                // 边缘遥测 / 取指令 / 拉策略（无需后台登录）
                "/plant/api/telemetry",
                "/plant/api/commands/pending",
                "/plant/api/strategy",
                // 联邦服务器/客户端下载模型与数据集（实验室内网）
                "/library/files/**"
        );

    }
    @Bean
    public LoginInterceptor loginInterceptor() {
        return new LoginInterceptor();
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**", "/favicon.ico").addResourceLocations("classpath:/static/");
        // Vue SPA 产物（vite base=/app/）
        registry.addResourceHandler("/app/**").addResourceLocations("classpath:/static/app/");
        super.addResourceHandlers(registry);
    }

}
