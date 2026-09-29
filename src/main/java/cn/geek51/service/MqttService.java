package cn.geek51.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 未来植物独立项目：仅保留植物控制指令下发。
 */
@Service
public class MqttService {

    @Value("${mqtt.enabled:false}")
    private boolean enabled;

    @Value("${mqtt.broker-url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.plant-cmd-topic-prefix:plant/cmd/}")
    private String plantCmdTopicPrefix;

    @Value("${mqtt.connection-timeout:10}")
    private int connectionTimeout;

    @Value("${mqtt.keep-alive-interval:20}")
    private int keepAliveInterval;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MqttClient client;

    private synchronized MqttClient getConnectedClient() throws MqttException {
        if (client != null && client.isConnected()) {
            return client;
        }
        client = new MqttClient(brokerUrl, MqttClient.generateClientId());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);
        client.connect(options);
        return client;
    }

    /** 未来植物控制指令。主题：{plantCmdTopicPrefix}{deviceKey} */
    public boolean publishPlantCommand(String deviceKey, String payloadJson) {
        if (!enabled) {
            System.err.println("MQTT 未启用，跳过植物控制指令: " + deviceKey);
            return false;
        }
        if (deviceKey == null || deviceKey.trim().isEmpty() || payloadJson == null) {
            return false;
        }
        try {
            String topic = plantCmdTopicPrefix + deviceKey.trim();
            MqttMessage message = new MqttMessage(payloadJson.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            message.setRetained(false);
            getConnectedClient().publish(topic, message);
            System.out.println("已下发植物控制指令: " + deviceKey + " -> " + topic);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
