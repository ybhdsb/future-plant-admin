package cn.geek51.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 真机网关地址（同事机器，如 192.168.123.103）。
 * enabled=false 时平台走本地 Mock/库更新，不发起外呼。
 */
@Component
@ConfigurationProperties(prefix = "plant.gateway")
public class PlantGatewayProperties {

    private final Camera camera = new Camera();
    private final Led led = new Led();

    public Camera getCamera() {
        return camera;
    }

    public Led getLed() {
        return led;
    }

    public static class Camera {
        /** 是否调用摄像头网关 */
        private boolean enabled = false;
        /** 例：http://192.168.123.103:8080/api/v1 */
        private String baseUrl = "http://192.168.123.103:8080/api/v1";
        private String apiKey = "";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 15000;
        /** 云台短动默认时长 ms */
        private int defaultMoveDurationMs = 500;
        /** 定时拍照结果同步到媒体库的轮询间隔 ms */
        private int scheduleSyncMs = 5000;
        /**
         * 停录后是否经 SSH/SFTP 把录像拉到本机（浏览器才能播）。
         * 账号密码只写 application-local.yml，勿提交仓库。
         */
        private boolean sshEnabled = false;
        private String sshHost = "192.168.123.103";
        private int sshPort = 22;
        private String sshUsername = "";
        private String sshPassword = "";
        /** 网关录像目录（当 status.file 非绝对路径时拼接） */
        private String sshRecordingsDir = "/home/hzauaiot/project/HIKVISION/recordings";
        /** ffmpeg 可执行文件；空则用 PATH 中的 ffmpeg（HEVC→H.264） */
        private String ffmpegPath = "ffmpeg";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getDefaultMoveDurationMs() {
            return defaultMoveDurationMs;
        }

        public void setDefaultMoveDurationMs(int defaultMoveDurationMs) {
            this.defaultMoveDurationMs = defaultMoveDurationMs;
        }

        public int getScheduleSyncMs() {
            return scheduleSyncMs;
        }

        public void setScheduleSyncMs(int scheduleSyncMs) {
            this.scheduleSyncMs = scheduleSyncMs;
        }

        public boolean isSshEnabled() {
            return sshEnabled;
        }

        public void setSshEnabled(boolean sshEnabled) {
            this.sshEnabled = sshEnabled;
        }

        public String getSshHost() {
            return sshHost;
        }

        public void setSshHost(String sshHost) {
            this.sshHost = sshHost;
        }

        public int getSshPort() {
            return sshPort;
        }

        public void setSshPort(int sshPort) {
            this.sshPort = sshPort;
        }

        public String getSshUsername() {
            return sshUsername;
        }

        public void setSshUsername(String sshUsername) {
            this.sshUsername = sshUsername;
        }

        public String getSshPassword() {
            return sshPassword;
        }

        public void setSshPassword(String sshPassword) {
            this.sshPassword = sshPassword;
        }

        public String getSshRecordingsDir() {
            return sshRecordingsDir;
        }

        public void setSshRecordingsDir(String sshRecordingsDir) {
            this.sshRecordingsDir = sshRecordingsDir;
        }

        public String getFfmpegPath() {
            return ffmpegPath;
        }

        public void setFfmpegPath(String ffmpegPath) {
            this.ffmpegPath = ffmpegPath;
        }
    }

    public static class Led {
        private boolean enabled = false;
        /** 例：http://192.168.123.103:8090/api/v1 */
        private String baseUrl = "http://192.168.123.103:8090/api/v1";
        private String apiKey = "";
        private String rackKey = "led-rack-01";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 10000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getRackKey() {
            return rackKey;
        }

        public void setRackKey(String rackKey) {
            this.rackKey = rackKey;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }
}
