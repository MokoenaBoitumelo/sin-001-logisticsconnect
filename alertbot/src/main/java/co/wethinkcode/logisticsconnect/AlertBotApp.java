package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.util.Map;

/**
 * AlertBot Notification Service (:7054)
 * Subscribes to package-status-topic and emits simulated public announcements
 * when delay stages cross a critical threshold.
 */
public class AlertBotApp {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int SEVERE_DELAY_THRESHOLD = 4;

    public static void main(String[] args) {
        // Start Javalin REST Server on Port 7054
        Javalin app = Javalin.create().start(7054);

        // Health Check Endpoint
        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Posts proactive delay notifications to public transit social media pages (simulated).)
        // Mechanism: Outbound webhook, simulated social post

        // Initialize ActiveMQ Listener
        initMqSubscriber();
    }

    private static void initMqSubscriber() {
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            Connection connection = factory.createConnection();
            connection.start();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(MqConfig.TOPIC);

            MessageConsumer consumer = session.createConsumer(topic);
            consumer.setMessageListener(message -> {
                if (message instanceof TextMessage textMessage) {
                    try {
                        String payload = textMessage.getText();
                        Map<String, Object> event = objectMapper.readValue(payload, new TypeReference<>() {
                        });

                        if (event.containsKey("hubId") && event.containsKey("stage")) {
                            String hubId = event.get("hubId").toString().toUpperCase();
                            int stage = Integer.parseInt(event.get("stage").toString());

                            // Check if delay stage crosses threshold
                            if (stage >= SEVERE_DELAY_THRESHOLD) {
                                publishSimulatedSocialAlert(hubId, stage);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("AlertBot failed to parse MQ message: " + e.getMessage());
                    }
                }
            });

            System.out.println("AlertBot connected to ActiveMQ broker and listening on topic: " + MqConfig.TOPIC);

        } catch (Exception e) {
            System.err.println("AlertBot failed to connect to ActiveMQ broker: " + e.getMessage());
        }
    }
}

// MQ TODO (stretch goal): subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
