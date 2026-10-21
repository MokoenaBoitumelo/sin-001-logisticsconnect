package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DelayStageServiceApp {

    private static final Map<String, Integer> delayStages = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static Connection mqConnection;
    private static Session mqSession;
    private static MessageProducer mqProducer;

    public static void main(String[] args) {
        initMq();

        Javalin app = Javalin.create().start(7052);

        app.get("/health", ctx -> ctx.result("OK"));

        // GET /delay-stage/{hubId}
        app.get("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").toUpperCase();
            int stage = delayStages.getOrDefault(hubId, 0);
            ctx.json(Map.of("hubId", hubId, "stage", stage));
        });

        // POST /delay-stage/{hubId} -> Updates stage and publishes event to ActiveMQ
        app.post("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").toUpperCase();
            Map<?, ?> body = objectMapper.readValue(ctx.body(), Map.class);

            if (body.containsKey("stage")) {
                int stage = Integer.parseInt(body.get("stage").toString());
                delayStages.put(hubId, stage);

                // Publish update event to ActiveMQ package-status-topic
                publishStageUpdate(hubId, stage);

                ctx.json(Map.of("hubId", hubId, "stage", stage, "status", "updated"));
            } else {
                ctx.status(400).result("Missing 'stage' parameter");
            }
        });
    }

    private static void initMq() {
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            mqConnection = factory.createConnection();
            mqConnection.start();
            mqSession = mqConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = mqSession.createTopic(MqConfig.TOPIC);
            mqProducer = mqSession.createProducer(topic);
            System.out.println("DelayStageService connected to ActiveMQ broker at " + MqConfig.BROKER_URL);
        } catch (Exception e) {
            System.err.println("Failed to initialize ActiveMQ connection: " + e.getMessage());
        }
    }

    private static void publishStageUpdate(String hubId, int stage) {
        if (mqProducer == null || mqSession == null) return;

        try {
            Map<String, Object> payload = Map.of(
                    "hubId", hubId,
                    "stage", stage,
                    "timestamp", Instant.now().toString()
            );

            String jsonPayload = objectMapper.writeValueAsString(payload);
            TextMessage message = mqSession.createTextMessage(jsonPayload);
            mqProducer.send(message);
            System.out.println("Published MQ update: " + jsonPayload);
        } catch (Exception e) {
            System.err.println("Failed to publish stage update to MQ: " + e.getMessage());
        }
    }
}
// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
