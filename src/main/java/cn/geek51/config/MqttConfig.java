package cn.geek51.config;

import cn.geek51.domain.Devices;
import cn.geek51.service.DeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

import java.util.HashMap;
import java.util.Map;


@Configuration
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true")
public class MqttConfig {

    @Value("${mqtt.broker-url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.client-id-prefix:springboot-mqtt-client}")
    private String clientIdPrefix;

    @Value("${mqtt.status-topic:jetson/device/status}")
    private String statusTopic;

    @Value("${mqtt.completion-timeout:5000}")
    private long completionTimeout;

    @Value("${mqtt.recovery-interval:5000}")
    private int recoveryInterval;

    @Value("${mqtt.connection-timeout:10}")
    private int connectionTimeout;

    @Value("${mqtt.keep-alive-interval:20}")
    private int keepAliveInterval;

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttAdapter() {
        DefaultMqttPahoClientFactory clientFactory = new DefaultMqttPahoClientFactory();
        clientFactory.setConnectionOptions(mqttConnectOptions());

        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        brokerUrl,
                        clientIdPrefix + "-" + System.currentTimeMillis(),
                        clientFactory,
                        statusTopic
                );
        adapter.setCompletionTimeout(completionTimeout);
        adapter.setRecoveryInterval(recoveryInterval);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;

    }

    private MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);
        return options;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler mqttMessageHandler(DeviceService deviceService) {
        return new MessageHandler() {
            @Override
            public void handleMessage(Message<?> message) throws MessagingException {
                String payload = (String) message.getPayload();
                System.out.println("========================================");
                System.out.println("收到设备状态: " + payload);

                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> payloadMap = objectMapper.readValue(payload, Map.class);
                    Devices device = objectMapper.convertValue(payloadMap, Devices.class);
                    Map<String, Object> extras = extractHeartbeatExtras(payloadMap);
                    deviceService.saveOrUpdateDevice(device, extras);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private Map<String, Object> extractHeartbeatExtras(Map<String, Object> payloadMap) {
        Map<String, Object> extras = new HashMap<>();
        if (payloadMap.containsKey("applied_strategy")) {
            extras.put("applied_strategy", payloadMap.get("applied_strategy"));
        }
        if (payloadMap.containsKey("appliedStrategy")) {
            extras.put("appliedStrategy", payloadMap.get("appliedStrategy"));
        }
        return extras;
    }
}
