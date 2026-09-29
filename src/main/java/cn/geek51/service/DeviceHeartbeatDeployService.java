package cn.geek51.service;

import cn.geek51.domain.Devices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 网页新增板端设备后，自动 SSH 部署 MQTT 心跳服务。
 */
@Service
public class DeviceHeartbeatDeployService {

    @Value("${device.heartbeat-script-dir:scripts/device-heartbeat}")
    private String heartbeatScriptDir;

    @Value("${device.heartbeat-broker-hosts:192.168.124.8,10.13.13.10}")
    private String brokerHosts;

    @Value("${device.auto-deploy-heartbeat:true}")
    private boolean autoDeployEnabled;

    public boolean isAutoDeployEnabled() {
        return autoDeployEnabled;
    }

    public Map<String, Object> deploy(Devices device) {
        Map<String, Object> result = new HashMap<>();
        if (device == null) {
            result.put("success", false);
            result.put("message", "设备为空");
            return result;
        }
        if (isBlank(device.getIpAddress())) {
            result.put("success", false);
            result.put("message", "缺少 IP，无法自动部署心跳");
            return result;
        }
        if (isBlank(device.getSshUsername()) || isBlank(device.getSshPassword())) {
            result.put("success", false);
            result.put("message", "缺少 SSH 账号或密码，无法自动部署心跳");
            return result;
        }

        File script = resolveDeployScript();
        if (script == null || !script.isFile()) {
            result.put("success", false);
            result.put("message", "找不到部署脚本 deploy_one.sh，请检查 device.heartbeat-script-dir");
            return result;
        }

        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(script.getAbsolutePath());
        command.add(device.getIpAddress().trim());
        command.add(device.getSshUsername().trim());
        command.add(device.getSshPassword());
        command.add(device.getDeviceKey().trim());
        command.add(isBlank(device.getDeviceName()) ? device.getDeviceKey().trim() : device.getDeviceName().trim());
        command.add(isBlank(device.getDeviceType()) ? "device" : device.getDeviceType().trim());
        command.add("AUTO");
        command.add(brokerHosts == null ? "" : brokerHosts.trim());

        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.directory(script.getParentFile());
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.put("success", false);
                result.put("message", "部署超时（120秒）");
                result.put("log", output.toString());
                return result;
            }
            int code = process.exitValue();
            String log = output.toString();
            result.put("log", log);
            if (code == 0) {
                result.put("success", true);
                result.put("message", "心跳服务已部署并设为开机自启");
                result.put("brokerHost", extractBroker(log));
            } else {
                result.put("success", false);
                result.put("message", "部署失败，exit=" + code + "。请确认 IP/账号密码，以及设备能否访问 MQTT broker");
            }
            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "部署异常: " + e.getMessage());
            result.put("log", output.toString());
            return result;
        }
    }

    /** 删除设备时，远程停止并禁用心跳，避免删后又自动上线 */
    public Map<String, Object> stopHeartbeat(Devices device) {
        Map<String, Object> result = new HashMap<>();
        if (device == null) {
            result.put("success", false);
            result.put("message", "设备为空");
            return result;
        }
        if (isBlank(device.getIpAddress()) || isBlank(device.getSshUsername()) || isBlank(device.getSshPassword())) {
            result.put("success", false);
            result.put("message", "无 SSH 信息，跳过远程停心跳");
            return result;
        }
        File script = resolveScript("stop_one.sh");
        if (script == null || !script.isFile()) {
            result.put("success", false);
            result.put("message", "找不到 stop_one.sh");
            return result;
        }
        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(script.getAbsolutePath());
        command.add(device.getIpAddress().trim());
        command.add(device.getSshUsername().trim());
        command.add(device.getSshPassword());
        return runScript(command, script.getParentFile(), 60, "远程心跳已停止");
    }

    public boolean shouldAutoDeploy(Devices device) {
        if (!autoDeployEnabled || device == null) {
            return false;
        }
        String type = device.getDeviceType() == null ? "" : device.getDeviceType().toLowerCase();
        if ("phone".equals(type) || "mobile".equals(type)) {
            return false;
        }
        return !isBlank(device.getIpAddress())
                && !isBlank(device.getSshUsername())
                && !isBlank(device.getSshPassword())
                && !isBlank(device.getDeviceKey());
    }

    private Map<String, Object> runScript(List<String> command, File workDir, int timeoutSec, String okMessage) {
        Map<String, Object> result = new HashMap<>();
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            if (workDir != null) {
                pb.directory(workDir);
            }
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.put("success", false);
                result.put("message", "操作超时");
                result.put("log", output.toString());
                return result;
            }
            int code = process.exitValue();
            result.put("log", output.toString());
            if (code == 0) {
                result.put("success", true);
                result.put("message", okMessage);
            } else {
                result.put("success", false);
                result.put("message", "操作失败，exit=" + code);
            }
            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "操作异常: " + e.getMessage());
            result.put("log", output.toString());
            return result;
        }
    }

    private File resolveDeployScript() {
        return resolveScript("deploy_one.sh");
    }

    private File resolveScript(String scriptName) {
        File relative = new File(heartbeatScriptDir, scriptName);
        if (relative.isFile()) {
            return relative;
        }
        File fromUserDir = new File(System.getProperty("user.dir"), heartbeatScriptDir + "/" + scriptName);
        if (fromUserDir.isFile()) {
            return fromUserDir;
        }
        File parent = new File(System.getProperty("user.dir")).getParentFile();
        if (parent != null) {
            File sibling = new File(parent, heartbeatScriptDir + "/" + scriptName);
            if (sibling.isFile()) {
                return sibling;
            }
        }
        return relative;
    }

    private String extractBroker(String log) {
        if (log == null) {
            return null;
        }
        String marker = "selected broker=";
        int idx = log.lastIndexOf(marker);
        if (idx >= 0) {
            int start = idx + marker.length();
            int end = log.indexOf('\n', start);
            if (end < 0) {
                end = log.length();
            }
            return log.substring(start, end).trim();
        }
        marker = "BROKER_HOST=";
        idx = log.lastIndexOf(marker);
        if (idx >= 0) {
            int start = idx + marker.length();
            int end = log.indexOf('\n', start);
            if (end < 0) {
                end = log.length();
            }
            return log.substring(start, end).trim();
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || "".equals(value.trim());
    }
}
