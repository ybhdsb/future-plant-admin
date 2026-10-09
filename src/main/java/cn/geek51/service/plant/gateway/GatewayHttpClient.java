package cn.geek51.service.plant.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 轻量 HTTP 客户端（不引入额外依赖），用于调用同事机器上的设备网关。
 */
@Component
public class GatewayHttpClient {

    private final ObjectMapper objectMapper;

    public GatewayHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getJson(String baseUrl, String path, String apiKey,
                                       int connectTimeoutMs, int readTimeoutMs) throws Exception {
        return requestJson("GET", baseUrl, path, apiKey, null, connectTimeoutMs, readTimeoutMs);
    }

    public Map<String, Object> postJson(String baseUrl, String path, String apiKey, Object body,
                                        int connectTimeoutMs, int readTimeoutMs) throws Exception {
        return requestJson("POST", baseUrl, path, apiKey, body, connectTimeoutMs, readTimeoutMs);
    }

    public byte[] getBytes(String baseUrl, String path, String apiKey,
                           int connectTimeoutMs, int readTimeoutMs) throws Exception {
        String url = join(baseUrl, path);
        HttpURLConnection conn = open(url, "GET", apiKey, connectTimeoutMs, readTimeoutMs);
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        byte[] bytes = readAll(in);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException(formatHttpError(code, new String(bytes, StandardCharsets.UTF_8), path));
        }
        return bytes;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestJson(String method, String baseUrl, String path, String apiKey, Object body,
                                            int connectTimeoutMs, int readTimeoutMs) throws Exception {
        String url = join(baseUrl, path);
        HttpURLConnection conn;
        try {
            conn = open(url, method, apiKey, connectTimeoutMs, readTimeoutMs);
        } catch (Exception e) {
            throw new IllegalStateException("连不上摄像头网关 " + url + "（" + e.getMessage()
                    + "）。请确认同事电脑上的网关服务已启动、且本机可访问该地址。", e);
        }
        try {
            if (!"GET".equalsIgnoreCase(method) && !"DELETE".equalsIgnoreCase(method)) {
                conn.setDoOutput(true);
                byte[] payload = body == null ? new byte[0] : objectMapper.writeValueAsBytes(body);
                if (body != null) {
                    conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                }
                conn.setRequestProperty("Content-Length", String.valueOf(payload.length));
                try (OutputStream os = conn.getOutputStream()) {
                    if (payload.length > 0) {
                        os.write(payload);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("连不上摄像头网关 " + url + "（" + e.getMessage()
                    + "）。请确认同事电脑上的网关服务已启动、且本机可访问该地址。", e);
        }
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        byte[] bytes = readAll(in);
        String text = new String(bytes == null ? new byte[0] : bytes, StandardCharsets.UTF_8);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException(formatHttpError(code, text, path));
        }
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Object parsed = objectMapper.readValue(text, Object.class);
        if (parsed instanceof Map) {
            return (Map<String, Object>) parsed;
        }
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("data", parsed);
        return wrap;
    }

    private HttpURLConnection open(String url, String method, String apiKey,
                                   int connectTimeoutMs, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(Math.max(500, connectTimeoutMs));
        conn.setReadTimeout(Math.max(500, readTimeoutMs));
        conn.setRequestProperty("Accept", "application/json, */*");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            conn.setRequestProperty("X-API-Key", apiKey.trim());
        }
        return conn;
    }

    private static String join(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String p = path == null ? "" : path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return base + p;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        if (in == null) {
            return new byte[0];
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        return bos.toByteArray();
    }

    private String formatHttpError(int code, String body, String path) {
        String detail = extractDetail(body);
        String tip;
        switch (code) {
            case 401:
                tip = "API Key 无效，检查 application-local.yml 里 plant.gateway.camera.api-key";
                break;
            case 404:
                tip = "接口不存在，确认网关已用 start-api 启动且路径为 /api/v1";
                break;
            case 409:
                tip = "设备未就绪或冲突（常见：无预览画面、相机未连接），请先在同事侧确认 GET /status 与预览正常";
                break;
            case 502:
            case 503:
            case 504:
                tip = "网关进程在、但海康相机/预览上游失败。请让同学检查相机在线、SDK/预览是否正常，并重启网关后再拍";
                break;
            default:
                tip = "同事摄像头网关返回错误";
                break;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(tip).append("（HTTP ").append(code);
        if (path != null && !path.isEmpty()) {
            sb.append(" ").append(path);
        }
        sb.append("）");
        if (detail != null && !detail.isEmpty()) {
            sb.append("：").append(detail);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractDetail(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        try {
            Object parsed = objectMapper.readValue(body, Object.class);
            if (parsed instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) parsed;
                Object d = map.get("detail");
                if (d == null) {
                    d = map.get("message");
                }
                if (d != null) {
                    return String.valueOf(d);
                }
            }
        } catch (Exception ignore) {
            // keep raw
        }
        String trimmed = body.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed;
    }
}
