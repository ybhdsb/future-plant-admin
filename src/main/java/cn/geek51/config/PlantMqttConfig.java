package cn.geek51.config;

import cn.geek51.service.plant.PlantService;
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

@Configuration
@ConditionalOnProperty(prefix = "plant.mqtt", name = "enabled", havingValue = "true")
public class PlantMqttConfig {

    @Value("${mqtt.broker-url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.client-id-prefix:springboot-mqtt-client}")
    private String clientIdPrefix;

    @Value("${mqtt.plant-telemetry-topic:plant/telemetry/#}")
    private String telemetryTopic;

    @Value("${mqtt.plant-ack-topic:plant/ack/#}")
    private String ackTopic;

    @Value("${mqtt.completion-timeout:5000}")
    private long completionTimeout;

    @Value("${mqtt.recovery-interval:5000}")
    private int recoveryInterval;

    @Value("${mqtt.connection-timeout:10}")
    private int connectionTimeout;

    @Value("${mqtt.keep-alive-interval:20}")
    private int keepAliveInterval;

    @Bean
    public MessageChannel plantMqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter plantMqttAdapter() {
        DefaultMqttPahoClientFactory clientFactory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);
        clientFactory.setConnectionOptions(options);

        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        brokerUrl,
                        clientIdPrefix + "-plant-" + System.currentTimeMillis(),
                        clientFactory,
                        telemetryTopic,
                        ackTopic
                );
        adapter.setCompletionTimeout(completionTimeout);
        adapter.setRecoveryInterval(recoveryInterval);
        adapter.setOutputChannel(plantMqttInputChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "plantMqttInputChannel")
    public MessageHandler plantMqttMessageHandler(PlantService plantService) {
        return (Message<?> message) -> {
            String payload = String.valueOf(message.getPayload());
            Object topicHeader = message.getHeaders().get("mqtt_receivedTopic");
            String topic = topicHeader == null ? "" : String.valueOf(topicHeader);
            try {
                if (topic.contains("/ack/")) {
                    plantService.ingestAckJson(payload);
                } else {
                    plantService.ingestTelemetryJson(payload);
                }
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    plantService.recordEvent("plant-ctrl-01", "ERROR", "MQTT_PARSE_FAIL",
                            e.getMessage() == null ? "parse fail" : e.getMessage());
                } catch (Exception ignored) {
                }
            }
        };
    }
}
