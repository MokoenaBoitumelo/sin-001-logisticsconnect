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
        Javalin app = Javalin.create().start(7054);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Posts proactive delay notifications to public transit social media pages (simulated).)
        // Mechanism: Outbound webhook, simulated social post
    }
}

// MQ TODO (stretch goal): subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
