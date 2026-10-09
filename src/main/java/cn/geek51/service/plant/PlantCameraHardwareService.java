package cn.geek51.service.plant;

import cn.geek51.config.PlantGatewayProperties;
import cn.geek51.dao.plant.PlantCommandLogRepository;
import cn.geek51.dao.plant.PlantMediaAssetRepository;
import cn.geek51.domain.plant.PlantCommandLog;
import cn.geek51.domain.plant.PlantMediaAsset;
import cn.geek51.service.plant.gateway.GatewayHttpClient;
import cn.geek51.service.plant.gateway.GatewaySftpClient;
import cn.geek51.service.plant.gateway.VideoTranscodeHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 摄像头平台侧：写指令日志 +（可选）调用同事海康网关。
 */
@Service
public class PlantCameraHardwareService {

    private static final Map<String, String> ACTION_TO_DIRECTION;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("UP", "up");
        m.put("DOWN", "down");
        m.put("LEFT", "left");
        m.put("RIGHT", "right");
        m.put("UP_LEFT", "up_left");
        m.put("UP_RIGHT", "up_right");
        m.put("DOWN_LEFT", "down_left");
        m.put("DOWN_RIGHT", "down_right");
        m.put("ZOOM_IN", "zoom_in");
        m.put("ZOOM_OUT", "zoom_out");
        m.put("HOME", "up"); // 无角度回中时用短上移占位，前端尽量别用
        ACTION_TO_DIRECTION = Collections.unmodifiableMap(m);
    }

    /** 各设备已同步到媒体库的网关 last_file，避免重复入库 */
    private final ConcurrentHashMap<String, String> lastSyncedRemoteFile = new ConcurrentHashMap<>();

    private final PlantGatewayProperties gatewayProperties;
    private final GatewayHttpClient httpClient;
    private final PlantCommandLogRepository commandLogRepository;
    private final PlantMediaAssetRepository mediaAssetRepository;
    private final ObjectMapper objectMapper;

    @Value("${plant.media-dir:uploads/plant}")
    private String mediaDir;

    @Value("${plant.default-device-key:plant-ctrl-01}")
    private String defaultDeviceKey;

    public PlantCameraHardwareService(PlantGatewayProperties gatewayProperties,
                                      GatewayHttpClient httpClient,
                                      PlantCommandLogRepository commandLogRepository,
                                      PlantMediaAssetRepository mediaAssetRepository,
                                      ObjectMapper objectMapper) {
        this.gatewayProperties = gatewayProperties;
        this.httpClient = httpClient;
        this.commandLogRepository = commandLogRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> status(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deviceKey", key);
        out.put("gatewayEnabled", gatewayProperties.getCamera().isEnabled());
        out.put("baseUrl", gatewayProperties.getCamera().getBaseUrl());
        if (!gatewayProperties.getCamera().isEnabled()) {
            out.put("connected", false);
            out.put("message", "camera gateway disabled (mock/local)");
            return out;
        }
        try {
            Map<String, Object> remote = httpClient.getJson(
                    gatewayProperties.getCamera().getBaseUrl(), "/status",
                    gatewayProperties.getCamera().getApiKey(),
                    gatewayProperties.getCamera().getConnectTimeoutMs(),
                    gatewayProperties.getCamera().getReadTimeoutMs());
            out.put("gateway", remote);
            boolean connected = true;
            Object conn = remote.get("connection");
            if (conn instanceof Map) {
                Object flag = ((Map<?, ?>) conn).get("connected");
                if (flag != null) {
                    connected = Boolean.parseBoolean(String.valueOf(flag));
                }
            }
            out.put("connected", connected);
            Object auth = remote.get("authentication");
            if (auth instanceof Map) {
                out.put("authVerified", ((Map<?, ?>) auth).get("verified"));
            }
            Object ptz = remote.get("ptz");
            if (ptz instanceof Map) {
                out.put("ptzReady", ((Map<?, ?>) ptz).get("ready"));
            }
            Object recording = remote.get("recording");
            if (recording instanceof Map) {
                Map<?, ?> rec = (Map<?, ?>) recording;
                out.put("recordingPhase", rec.get("phase"));
                out.put("recordingFile", rec.get("file"));
                // 打开页面时若有已保存录像且本机没有，经 SSH 补拉一次
                if ("saved".equals(String.valueOf(rec.get("phase")))
                        && gatewayProperties.getCamera().isSshEnabled()
                        && rec.get("file") != null) {
                    Map<String, Object> pullMeta = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : rec.entrySet()) {
                        if (e.getKey() != null) {
                            pullMeta.put(String.valueOf(e.getKey()), e.getValue());
                        }
                    }
                    Map<String, Object> pulled = registerGatewayMedia(
                            key, "VIDEO", pullMeta, "gateway-recording-sync", "网关录像");
                    if (pulled != null) {
                        out.put("localPull", pulled);
                    }
                }
            }
            Object preview = remote.get("preview");
            boolean previewOk = false;
            if (preview instanceof Map) {
                Object pConn = ((Map<?, ?>) preview).get("connected");
                previewOk = pConn != null && Boolean.parseBoolean(String.valueOf(pConn));
                out.put("previewConnected", previewOk);
                out.put("previewMessage", ((Map<?, ?>) preview).get("message"));
            } else {
                out.put("previewConnected", false);
            }
            Object photos = remote.get("photos");
            if (photos instanceof Map) {
                out.put("photosActive", ((Map<?, ?>) photos).get("active"));
                out.put("photoCount", ((Map<?, ?>) photos).get("count"));
            }
            out.put("cameraIp", remote.get("camera"));
            if (!connected) {
                out.put("message", "gateway reports disconnected");
            } else if (!previewOk) {
                Object pm = out.get("previewMessage");
                out.put("message", pm == null ? "预览未连接，拍照/实时画面不可用" : String.valueOf(pm));
            } else {
                out.put("message", "gateway ok");
            }
            // 补转历史 HEVC 录像为 H.264，避免页面一直显示 0:00
            int fixed = ensureLocalVideosPlayable(key);
            if (fixed > 0) {
                out.put("videosTranscoded", fixed);
            }
            return out;
        } catch (Exception e) {
            out.put("connected", false);
            out.put("message", e.getMessage());
            return out;
        }
    }

    @Transactional
    public Map<String, Object> ptzMove(Map<String, Object> body) {
        String deviceKey = resolveDeviceKey(asString(body.get("deviceKey")));
        String direction = resolveDirection(body);
        int speed = body.get("speed") == null ? 30 : ((Number) toNumber(body.get("speed"))).intValue();
        if (speed < 1) {
            speed = 1;
        }
        if (speed > 100) {
            speed = 100;
        }
        int durationMs = body.get("durationMs") == null
                ? gatewayProperties.getCamera().getDefaultMoveDurationMs()
                : ((Number) toNumber(body.get("durationMs"))).intValue();
        if (durationMs < 50) {
            durationMs = 50;
        }
        if (durationMs > 10000) {
            durationMs = 10000;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("direction", direction);
        payload.put("speed", speed);
        payload.put("durationMs", durationMs);
        PlantCommandLog log = beginCommand(deviceKey, "GIMBAL_MOVE", payload);

        try {
            if (gatewayProperties.getCamera().isEnabled()) {
                Map<String, Object> moveBody = new LinkedHashMap<>();
                moveBody.put("direction", direction);
                moveBody.put("speed", speed);
                httpClient.postJson(
                        gatewayProperties.getCamera().getBaseUrl(), "/ptz/move",
                        gatewayProperties.getCamera().getApiKey(), moveBody,
                        gatewayProperties.getCamera().getConnectTimeoutMs(),
                        gatewayProperties.getCamera().getReadTimeoutMs());
                Thread.sleep(durationMs);
                httpClient.postJson(
                        gatewayProperties.getCamera().getBaseUrl(), "/ptz/stop",
                        gatewayProperties.getCamera().getApiKey(), null,
                        gatewayProperties.getCamera().getConnectTimeoutMs(),
                        gatewayProperties.getCamera().getReadTimeoutMs());
                finishCommand(log, "ACKED", "ptz move+stop ok");
            } else {
                finishCommand(log, "ACKED", "local mock ptz (gateway.disabled)");
            }
            return toCommandView(log);
        } catch (Exception e) {
            try {
                if (gatewayProperties.getCamera().isEnabled()) {
                    httpClient.postJson(
                            gatewayProperties.getCamera().getBaseUrl(), "/ptz/stop",
                            gatewayProperties.getCamera().getApiKey(), null,
                            gatewayProperties.getCamera().getConnectTimeoutMs(),
                            gatewayProperties.getCamera().getReadTimeoutMs());
                }
            } catch (Exception ignore) {
                // ignore stop failure
            }
            finishCommand(log, "FAILED", e.getMessage());
            throw new IllegalStateException("云台控制失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> ptzStop(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        PlantCommandLog log = beginCommand(key, "GIMBAL_STOP", Collections.emptyMap());
        try {
            if (gatewayProperties.getCamera().isEnabled()) {
                httpClient.postJson(
                        gatewayProperties.getCamera().getBaseUrl(), "/ptz/stop",
                        gatewayProperties.getCamera().getApiKey(), null,
                        gatewayProperties.getCamera().getConnectTimeoutMs(),
                        gatewayProperties.getCamera().getReadTimeoutMs());
                finishCommand(log, "ACKED", "stop ok");
            } else {
                finishCommand(log, "ACKED", "local mock stop");
            }
            return toCommandView(log);
        } catch (Exception e) {
            finishCommand(log, "FAILED", e.getMessage());
            throw new IllegalStateException("云台停止失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> recordingStart(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        PlantCommandLog log = beginCommand(key, "CAMERA_RECORD_START", Collections.emptyMap());
        try {
            Map<String, Object> resp = Collections.emptyMap();
            if (gatewayProperties.getCamera().isEnabled()) {
                resp = httpClient.postJson(
                        gatewayProperties.getCamera().getBaseUrl(), "/recording/start",
                        gatewayProperties.getCamera().getApiKey(), null,
                        gatewayProperties.getCamera().getConnectTimeoutMs(),
                        gatewayProperties.getCamera().getReadTimeoutMs());
                finishCommand(log, "ACKED", "recording start");
            } else {
                finishCommand(log, "ACKED", "local mock recording start");
            }
            Map<String, Object> out = toCommandView(log);
            out.put("gateway", resp);
            return out;
        } catch (Exception e) {
            finishCommand(log, "FAILED", e.getMessage());
            throw new IllegalStateException("开始录像失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> recordingStop(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        PlantCommandLog log = beginCommand(key, "CAMERA_RECORD_STOP", Collections.emptyMap());
        try {
            Map<String, Object> status = Collections.emptyMap();
            Map<String, Object> recover = Collections.emptyMap();
            if (gatewayProperties.getCamera().isEnabled()) {
                httpClient.postJson(
                        gatewayProperties.getCamera().getBaseUrl(), "/recording/stop",
                        gatewayProperties.getCamera().getApiKey(), null,
                        gatewayProperties.getCamera().getConnectTimeoutMs(),
                        gatewayProperties.getCamera().getReadTimeoutMs());
                status = waitRecordingSaved();
                // 录像在同事电脑：SSH/SFTP 拉到本机后写入媒体库，前端可直接播放
                Map<String, Object> pulled = registerGatewayMedia(key, "VIDEO", status, "gateway-recording", "网关录像");
                // 停录后预览流常会断：自动尝试重连并等待预览恢复
                recover = reconnectAndWaitPreview(8);
                finishCommand(log, "ACKED", pulled != null && Boolean.TRUE.equals(pulled.get("localReady"))
                        ? "recording stop + video pulled"
                        : "recording stop");
                if (pulled != null) {
                    status = new LinkedHashMap<>(status);
                    status.put("localPull", pulled);
                }
            } else {
                finishCommand(log, "ACKED", "local mock recording stop");
            }
            Map<String, Object> out = toCommandView(log);
            out.put("recording", status);
            out.put("previewRecover", recover);
            return out;
        } catch (Exception e) {
            finishCommand(log, "FAILED", e.getMessage());
            throw new IllegalStateException("停止录像失败: " + e.getMessage(), e);
        }
    }

    /** 调用网关 POST /camera/reconnect，并可选等待预览恢复 */
    public Map<String, Object> reconnect(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        if (!gatewayProperties.getCamera().isEnabled()) {
            throw new IllegalStateException("摄像头网关未启用");
        }
        Map<String, Object> recover = reconnectAndWaitPreview(10);
        recover.put("deviceKey", key);
        return recover;
    }

    private Map<String, Object> reconnectAndWaitPreview(int waitSeconds) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            Map<String, Object> resp = httpClient.postJson(
                    gatewayProperties.getCamera().getBaseUrl(), "/camera/reconnect",
                    gatewayProperties.getCamera().getApiKey(), null,
                    gatewayProperties.getCamera().getConnectTimeoutMs(),
                    Math.max(gatewayProperties.getCamera().getReadTimeoutMs(), 20000));
            out.put("reconnect", resp);
        } catch (Exception e) {
            out.put("reconnectOk", false);
            out.put("message", "重连失败: " + e.getMessage());
            return out;
        }
        boolean previewOk = false;
        String previewMessage = null;
        int waited = Math.max(1, waitSeconds);
        for (int i = 0; i < waited; i++) {
            try {
                Thread.sleep(1000L);
                Map<String, Object> st = httpClient.getJson(
                        gatewayProperties.getCamera().getBaseUrl(), "/status",
                        gatewayProperties.getCamera().getApiKey(),
                        gatewayProperties.getCamera().getConnectTimeoutMs(),
                        gatewayProperties.getCamera().getReadTimeoutMs());
                Object preview = st.get("preview");
                if (preview instanceof Map) {
                    Object flag = ((Map<?, ?>) preview).get("connected");
                    previewMessage = asString(((Map<?, ?>) preview).get("message"));
                    previewOk = flag != null && Boolean.parseBoolean(String.valueOf(flag));
                    if (previewOk) {
                        break;
                    }
                }
            } catch (Exception ignore) {
                // keep trying
            }
        }
        out.put("reconnectOk", true);
        out.put("previewConnected", previewOk);
        out.put("previewMessage", previewMessage);
        out.put("message", previewOk
                ? "预览已恢复"
                : (previewMessage == null
                ? "已请求重连，但预览仍未恢复（请检查相机网络或让同事重启网关）"
                : previewMessage));
        return out;
    }

    public Map<String, Object> recordingStatus(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deviceKey", key);
        if (!gatewayProperties.getCamera().isEnabled()) {
            out.put("phase", "idle");
            out.put("message", "gateway disabled");
            return out;
        }
        try {
            Map<String, Object> remote = httpClient.getJson(
                    gatewayProperties.getCamera().getBaseUrl(), "/recording/status",
                    gatewayProperties.getCamera().getApiKey(),
                    gatewayProperties.getCamera().getConnectTimeoutMs(),
                    gatewayProperties.getCamera().getReadTimeoutMs());
            // 已有 saved 录像但本机还没有时，顺带 SSH 拉一次（补历史条目）
            Object phase = remote.get("phase");
            if ("saved".equals(phase) && gatewayProperties.getCamera().isSshEnabled()) {
                Map<String, Object> pulled = registerGatewayMedia(key, "VIDEO", remote, "gateway-recording-sync", "网关录像");
                if (pulled != null) {
                    remote = new LinkedHashMap<>(remote);
                    remote.put("localPull", pulled);
                }
            }
            return remote;
        } catch (Exception e) {
            throw new IllegalStateException("查询录像状态失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> capturePhoto(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        PlantCommandLog log = beginCommand(key, "CAMERA_CAPTURE", Collections.singletonMap("reason", "manual"));
        try {
            Map<String, Object> resp;
            if (gatewayProperties.getCamera().isEnabled()) {
                resp = httpClient.postJson(
                        gatewayProperties.getCamera().getBaseUrl(), "/photos/capture",
                        gatewayProperties.getCamera().getApiKey(), null,
                        gatewayProperties.getCamera().getConnectTimeoutMs(),
                        gatewayProperties.getCamera().getReadTimeoutMs());
                // 网关文件在同事电脑上，本机通常读不到路径：再拉一帧预览落盘，供前端显示真画面
                saveMediaFromGateway(key, "IMAGE", resp);
                Object fileObj = resp == null ? null : resp.get("file");
                if (fileObj != null) {
                    lastSyncedRemoteFile.put(key, String.valueOf(fileObj));
                }
                savePreviewFrameAsMedia(key, resp, "gateway-manual-capture", "手动抓拍");
                finishCommand(log, "ACKED", "capture ok");
            } else {
                resp = new LinkedHashMap<>();
                resp.put("message", "local mock capture (no file from gateway)");
                finishCommand(log, "ACKED", "local mock capture");
            }
            Map<String, Object> out = toCommandView(log);
            out.put("photo", resp);
            return out;
        } catch (Exception e) {
            finishCommand(log, "FAILED", e.getMessage());
            throw new IllegalStateException("拍照失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> scheduleStart(Map<String, Object> body) {
        String key = resolveDeviceKey(asString(body.get("deviceKey")));
        double interval = body.get("intervalSec") == null ? 10d : toNumber(body.get("intervalSec")).doubleValue();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("interval_seconds", interval);
        PlantCommandLog log = beginCommand(key, "CAMERA_CAPTURE_SCHEDULE", payload);
        try {
            Map<String, Object> resp = Collections.emptyMap();
            if (gatewayProperties.getCamera().isEnabled()) {
                Map<String, Object> req = new LinkedHashMap<>();
                req.put("interval_seconds", interval);
                resp = httpClient.postJson(
                        gatewayProperties.getCamera().getBaseUrl(), "/photos/schedule/start",
                        gatewayProperties.getCamera().getApiKey(), req,
                        gatewayProperties.getCamera().getConnectTimeoutMs(),
                        gatewayProperties.getCamera().getReadTimeoutMs());
                finishCommand(log, "ACKED", "schedule start");
            } else {
                finishCommand(log, "ACKED", "local mock schedule start");
            }
            Map<String, Object> out = toCommandView(log);
            out.put("gateway", resp);
            return out;
        } catch (Exception e) {
            finishCommand(log, "FAILED", e.getMessage());
            throw new IllegalStateException("启动定时拍照失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> scheduleStop(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        PlantCommandLog log = beginCommand(key, "CAMERA_CAPTURE_SCHEDULE",
                Collections.singletonMap("enabled", false));
        try {
            if (gatewayProperties.getCamera().isEnabled()) {
                httpClient.postJson(
                        gatewayProperties.getCamera().getBaseUrl(), "/photos/schedule/stop",
                        gatewayProperties.getCamera().getApiKey(), null,
                        gatewayProperties.getCamera().getConnectTimeoutMs(),
                        gatewayProperties.getCamera().getReadTimeoutMs());
                finishCommand(log, "ACKED", "schedule stop");
            } else {
                finishCommand(log, "ACKED", "local mock schedule stop");
            }
            return toCommandView(log);
        } catch (Exception e) {
            finishCommand(log, "FAILED", e.getMessage());
            throw new IllegalStateException("停止定时拍照失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> photosStatus(String deviceKey) {
        String key = resolveDeviceKey(deviceKey);
        if (!gatewayProperties.getCamera().isEnabled()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("deviceKey", key);
            out.put("active", false);
            out.put("message", "gateway disabled");
            return out;
        }
        try {
            Map<String, Object> status = fetchPhotosStatus();
            int synced = syncNewPhotoFromStatus(key, status);
            status.put("deviceKey", key);
            status.put("syncedToMedia", synced);
            return status;
        } catch (Exception e) {
            throw new IllegalStateException("查询拍照状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 轮询网关 photos/status：若出现新的 last_file，拉预览帧写入本地媒体库（供「最近影像」展示）。
     * @return 本次新入库条数（0 或 1）
     */
    @Transactional
    public int syncScheduledPhotos(String deviceKey) {
        if (!gatewayProperties.getCamera().isEnabled()) {
            return 0;
        }
        String key = resolveDeviceKey(deviceKey);
        try {
            return syncNewPhotoFromStatus(key, fetchPhotosStatus());
        } catch (Exception e) {
            System.err.println("同步定时抓拍失败: " + e.getMessage());
            return 0;
        }
    }

    private Map<String, Object> fetchPhotosStatus() throws Exception {
        return httpClient.getJson(
                gatewayProperties.getCamera().getBaseUrl(), "/photos/status",
                gatewayProperties.getCamera().getApiKey(),
                gatewayProperties.getCamera().getConnectTimeoutMs(),
                gatewayProperties.getCamera().getReadTimeoutMs());
    }

    private int syncNewPhotoFromStatus(String deviceKey, Map<String, Object> status) {
        if (status == null || status.isEmpty()) {
            return 0;
        }
        Object last = status.get("last_file");
        if (last == null) {
            return 0;
        }
        String remote = String.valueOf(last).trim();
        if (remote.isEmpty() || "null".equalsIgnoreCase(remote)) {
            return 0;
        }
        String remembered = lastSyncedRemoteFile.get(deviceKey);
        if (remote.equals(remembered)) {
            return 0;
        }
        if (alreadyHaveRemotePath(deviceKey, remote)) {
            lastSyncedRemoteFile.put(deviceKey, remote);
            return 0;
        }
        Map<String, Object> meta = new LinkedHashMap<>(status);
        meta.put("remotePath", remote);
        meta.put("file", remote);
        savePreviewFrameAsMedia(deviceKey, meta, "gateway-schedule-sync", "定时抓拍");
        lastSyncedRemoteFile.put(deviceKey, remote);
        return 1;
    }

    private boolean alreadyHaveRemotePath(String deviceKey, String remotePath) {
        List<PlantMediaAsset> recent = mediaAssetRepository.findByDeviceKeyOrderByCapturedAtDesc(
                deviceKey, PageRequest.of(0, 40));
        for (PlantMediaAsset asset : recent) {
            String meta = asset.getMetaJson();
            if (meta != null && meta.contains(remotePath)) {
                return true;
            }
        }
        return false;
    }

    public byte[] previewFrame() {
        if (!gatewayProperties.getCamera().isEnabled()) {
            throw new IllegalStateException("摄像头网关未启用");
        }
        try {
            return httpClient.getBytes(
                    gatewayProperties.getCamera().getBaseUrl(), "/preview/frame",
                    gatewayProperties.getCamera().getApiKey(),
                    gatewayProperties.getCamera().getConnectTimeoutMs(),
                    gatewayProperties.getCamera().getReadTimeoutMs());
        } catch (Exception e) {
            throw new IllegalStateException("获取预览帧失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> waitRecordingSaved() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        Map<String, Object> last = Collections.emptyMap();
        while (System.currentTimeMillis() < deadline) {
            last = httpClient.getJson(
                    gatewayProperties.getCamera().getBaseUrl(), "/recording/status",
                    gatewayProperties.getCamera().getApiKey(),
                    gatewayProperties.getCamera().getConnectTimeoutMs(),
                    gatewayProperties.getCamera().getReadTimeoutMs());
            Object phase = last.get("phase");
            if ("saved".equals(phase) || "error".equals(phase) || "idle".equals(phase)) {
                return last;
            }
            Thread.sleep(1000L);
        }
        return last;
    }

    private void saveMediaFromGateway(String deviceKey, String mediaType, Map<String, Object> gateway) {
        registerGatewayMedia(deviceKey, mediaType, gateway, "gateway-file", null);
    }

    /**
     * 登记网关侧媒体：本机可读则复制；录像优先 SSH/SFTP 拉取；否则仍写库（页面提示路径）。
     * @return 登记结果摘要；跳过/失败时可能为 null
     */
    private Map<String, Object> registerGatewayMedia(String deviceKey, String mediaType, Map<String, Object> gateway,
                                                     String source, String label) {
        if (gateway == null || gateway.isEmpty()) {
            return null;
        }
        Object fileObj = gateway.get("file");
        if (fileObj == null) {
            fileObj = gateway.get("last_file");
        }
        String remotePath = fileObj == null ? null : String.valueOf(fileObj);
        if (remotePath == null || remotePath.trim().isEmpty() || "null".equalsIgnoreCase(remotePath)) {
            return null;
        }
        remotePath = remotePath.trim();
        PlantMediaAsset existing = findAssetByRemotePath(deviceKey, remotePath);
        if (existing != null && localFileReady(existing.getStoragePath())) {
            // 已拉取但仍是 HEVC 时，补转 H.264（否则浏览器时长一直 0:00）
            Path playable = toBrowserPlayable(Paths.get(existing.getStoragePath()));
            if (playable != null && !playable.toAbsolutePath().toString().equals(existing.getStoragePath())) {
                existing.setStoragePath(playable.toAbsolutePath().toString());
                try {
                    Map<String, Object> meta = metaMap(existing.getMetaJson());
                    meta.put("browserPlayable", true);
                    meta.put("transcodedH264", true);
                    existing.setMetaJson(objectMapper.writeValueAsString(meta));
                } catch (Exception ignore) {
                }
                mediaAssetRepository.save(existing);
            }
            Map<String, Object> skip = new LinkedHashMap<>();
            skip.put("localReady", true);
            skip.put("storagePath", existing.getStoragePath());
            skip.put("skipped", true);
            return skip;
        }
        try {
            PlantMediaAsset asset = existing == null ? new PlantMediaAsset() : existing;
            if (existing == null) {
                asset.setDeviceKey(deviceKey);
                asset.setMediaType(mediaType);
                asset.setCapturedAt(new Date());
                asset.setCreatedAt(new Date());
            }
            Map<String, Object> meta = new LinkedHashMap<>(gateway);
            meta.put("remotePath", remotePath);
            meta.put("source", source == null ? "gateway-file" : source);
            if (label != null) {
                meta.put("label", label);
            } else if ("VIDEO".equalsIgnoreCase(mediaType)) {
                meta.put("label", "网关录像");
            }

            Path dir = Paths.get(mediaDir, deviceKey);
            Files.createDirectories(dir);
            String name = new File(remotePath).getName();
            if (name.isEmpty()) {
                name = "media.bin";
            }
            // 浏览器播 MP4：本地文件名尽量用 .mp4
            String localName = name.toLowerCase(Locale.ROOT).endsWith(".mkv")
                    ? name.substring(0, name.length() - 4) + ".mp4"
                    : name;
            Path dest = dir.resolve(new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date()) + "_" + localName);

            boolean localReady = false;
            String pullNote = null;
            if (Files.exists(Paths.get(remotePath))) {
                Files.copy(Paths.get(remotePath), dest);
                Path playable = toBrowserPlayable(dest);
                asset.setStoragePath(playable.toAbsolutePath().toString());
                meta.put("copiedLocal", true);
                meta.put("browserPlayable", playable.toString().endsWith("_web.mp4"));
                localReady = true;
            } else if ("VIDEO".equalsIgnoreCase(mediaType) || name.toLowerCase(Locale.ROOT).endsWith(".mp4")
                    || name.toLowerCase(Locale.ROOT).endsWith(".mkv")) {
                Path pulled = pullViaSftp(remotePath, dest);
                if (pulled != null && Files.exists(pulled)) {
                    Path playable = toBrowserPlayable(pulled);
                    asset.setStoragePath(playable.toAbsolutePath().toString());
                    meta.put("copiedLocal", true);
                    meta.put("pulledViaSftp", true);
                    meta.put("browserPlayable", playable.toString().endsWith("_web.mp4"));
                    meta.put("transcodedH264", playable.toString().endsWith("_web.mp4"));
                    localReady = true;
                    pullNote = playable.toString().endsWith("_web.mp4") ? "sftp+h264 ok" : "sftp ok";
                } else {
                    asset.setStoragePath(remotePath);
                    meta.put("copiedLocal", false);
                    meta.put("note", "文件在网关主机，SSH 拉取失败或未启用，无法直接播放");
                    pullNote = "sftp failed or disabled";
                }
            } else {
                asset.setStoragePath(remotePath);
                meta.put("copiedLocal", false);
                meta.put("note", "文件在网关主机，本平台无法直接打开播放");
            }
            asset.setMetaJson(objectMapper.writeValueAsString(meta));
            mediaAssetRepository.save(asset);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("localReady", localReady);
            result.put("storagePath", asset.getStoragePath());
            result.put("remotePath", remotePath);
            result.put("mediaId", asset.getId());
            if (pullNote != null) {
                result.put("pull", pullNote);
            }
            return result;
        } catch (Exception e) {
            System.err.println("登记网关媒体失败: " + e.getMessage());
            return null;
        }
    }

    private Path toBrowserPlayable(Path localVideo) {
        return VideoTranscodeHelper.ensureBrowserPlayable(
                localVideo, gatewayProperties.getCamera().getFfmpegPath());
    }

    /** 把本机已有但非浏览器可播的录像转成 H.264 _web.mp4 并回写 storage_path */
    private int ensureLocalVideosPlayable(String deviceKey) {
        int fixed = 0;
        try {
            List<PlantMediaAsset> recent = mediaAssetRepository.findByDeviceKeyOrderByCapturedAtDesc(
                    deviceKey, PageRequest.of(0, 20));
            for (PlantMediaAsset asset : recent) {
                if (asset.getMediaType() == null || !"VIDEO".equalsIgnoreCase(asset.getMediaType())) {
                    continue;
                }
                String path = asset.getStoragePath();
                if (!localFileReady(path) || path.endsWith("_web.mp4")) {
                    continue;
                }
                Path playable = toBrowserPlayable(Paths.get(path));
                if (playable == null || !Files.exists(playable)) {
                    continue;
                }
                String playableAbs = playable.toAbsolutePath().toString();
                if (playableAbs.equals(path)) {
                    continue;
                }
                asset.setStoragePath(playableAbs);
                try {
                    Map<String, Object> meta = metaMap(asset.getMetaJson());
                    meta.put("browserPlayable", true);
                    meta.put("transcodedH264", true);
                    asset.setMetaJson(objectMapper.writeValueAsString(meta));
                } catch (Exception ignore) {
                }
                mediaAssetRepository.save(asset);
                fixed++;
            }
        } catch (Exception e) {
            System.err.println("补转本地录像失败: " + e.getMessage());
        }
        return fixed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metaMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Path pullViaSftp(String remotePath, Path localDest) {
        PlantGatewayProperties.Camera cam = gatewayProperties.getCamera();
        if (!cam.isSshEnabled()
                || cam.getSshHost() == null || cam.getSshHost().trim().isEmpty()
                || cam.getSshUsername() == null || cam.getSshUsername().trim().isEmpty()) {
            return null;
        }
        String remote = resolveRemoteAbsolutePath(remotePath, cam.getSshRecordingsDir());
        try {
            return GatewaySftpClient.download(
                    cam.getSshHost(),
                    cam.getSshPort(),
                    cam.getSshUsername(),
                    cam.getSshPassword(),
                    remote,
                    localDest,
                    Math.max(cam.getConnectTimeoutMs(), 8000));
        } catch (Exception e) {
            System.err.println("SFTP 拉取录像失败: " + e.getMessage());
            return null;
        }
    }

    private static String resolveRemoteAbsolutePath(String remotePath, String recordingsDir) {
        String p = remotePath == null ? "" : remotePath.trim();
        if (p.startsWith("/")) {
            return p;
        }
        String dir = recordingsDir == null ? "" : recordingsDir.trim();
        if (dir.isEmpty()) {
            return p;
        }
        if (dir.endsWith("/")) {
            return dir + p;
        }
        return dir + "/" + p;
    }

    private PlantMediaAsset findAssetByRemotePath(String deviceKey, String remotePath) {
        List<PlantMediaAsset> recent = mediaAssetRepository.findByDeviceKeyOrderByCapturedAtDesc(
                deviceKey, PageRequest.of(0, 40));
        for (PlantMediaAsset asset : recent) {
            String meta = asset.getMetaJson();
            if (meta != null && meta.contains(remotePath)) {
                return asset;
            }
        }
        return null;
    }

    private static boolean localFileReady(String storagePath) {
        return storagePath != null && !storagePath.trim().isEmpty()
                && Files.exists(Paths.get(storagePath));
    }

    /** 从网关拉取当前预览帧，写入本地媒体库供页面显示 */
    private void savePreviewFrameAsMedia(String deviceKey, Map<String, Object> captureResp,
                                         String source, String label) {
        try {
            byte[] jpeg = httpClient.getBytes(
                    gatewayProperties.getCamera().getBaseUrl(), "/preview/frame",
                    gatewayProperties.getCamera().getApiKey(),
                    gatewayProperties.getCamera().getConnectTimeoutMs(),
                    gatewayProperties.getCamera().getReadTimeoutMs());
            if (jpeg == null || jpeg.length == 0) {
                return;
            }
            Path dir = Paths.get(mediaDir, deviceKey);
            Files.createDirectories(dir);
            String prefix = source != null && source.contains("schedule") ? "sched_" : "live_";
            String name = prefix + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date()) + ".jpg";
            Path dest = dir.resolve(name);
            Files.write(dest, jpeg);
            PlantMediaAsset asset = new PlantMediaAsset();
            asset.setDeviceKey(deviceKey);
            asset.setMediaType("IMAGE");
            asset.setStoragePath(dest.toAbsolutePath().toString());
            asset.setCapturedAt(new Date());
            asset.setCreatedAt(new Date());
            Map<String, Object> meta = new LinkedHashMap<>();
            if (captureResp != null) {
                meta.putAll(captureResp);
            }
            meta.put("source", source == null ? "gateway-preview-frame" : source);
            meta.put("label", label == null ? "网关实拍预览帧" : label);
            asset.setMetaJson(objectMapper.writeValueAsString(meta));
            mediaAssetRepository.save(asset);
        } catch (Exception e) {
            System.err.println("保存预览帧失败: " + e.getMessage());
        }
    }

    private PlantCommandLog beginCommand(String deviceKey, String type, Object payload) {
        PlantCommandLog log = new PlantCommandLog();
        log.setDeviceKey(deviceKey);
        log.setCommandType(type);
        try {
            log.setPayloadJson(objectMapper.writeValueAsString(payload == null ? Collections.emptyMap() : payload));
        } catch (Exception e) {
            log.setPayloadJson("{}");
        }
        log.setStatus("PENDING");
        log.setCreatedAt(new Date());
        log.setSentAt(new Date());
        return commandLogRepository.save(log);
    }

    private void finishCommand(PlantCommandLog log, String status, String message) {
        log.setStatus(status);
        log.setResultMessage(message == null ? null : (message.length() > 500 ? message.substring(0, 500) : message));
        log.setFinishedAt(new Date());
        commandLogRepository.save(log);
    }

    private Map<String, Object> toCommandView(PlantCommandLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("commandId", "c-" + log.getId());
        m.put("id", log.getId());
        m.put("deviceKey", log.getDeviceKey());
        m.put("commandType", log.getCommandType());
        m.put("status", log.getStatus());
        m.put("resultMessage", log.getResultMessage());
        m.put("createdAt", log.getCreatedAt());
        m.put("finishedAt", log.getFinishedAt());
        return m;
    }

    private String resolveDirection(Map<String, Object> body) {
        String direction = asString(body.get("direction"));
        if (direction != null && !direction.isEmpty()) {
            return direction.toLowerCase(Locale.ROOT);
        }
        String action = asString(body.get("action"));
        if (action != null) {
            String mapped = ACTION_TO_DIRECTION.get(action.toUpperCase(Locale.ROOT));
            if (mapped != null) {
                return mapped;
            }
            return action.toLowerCase(Locale.ROOT);
        }
        throw new IllegalArgumentException("direction 或 action 不能为空");
    }

    private String resolveDeviceKey(String deviceKey) {
        if (deviceKey == null || deviceKey.trim().isEmpty()) {
            return defaultDeviceKey;
        }
        return deviceKey.trim();
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static Number toNumber(Object o) {
        if (o instanceof Number) {
            return (Number) o;
        }
        return Double.parseDouble(String.valueOf(o).trim());
    }
}
