package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.MqConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TransitServiceApp {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Local state maintained asynchronously via ActiveMQ topic subscriptions
    private static final Map<String, Integer> localDelayStages = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        initMqConsumer();

        Javalin app = Javalin.create().start(7053);

        app.get("/health", ctx -> ctx.result("OK"));

        // GET /eta/{hubId}
        app.get("/eta/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").toUpperCase();

            // Fetch Hub Details synchronously from Hub Service (:7051)
            Map<String, Object> hubDetails = fetchHubDetails(hubId);
            if (hubDetails == null) {
                ctx.status(404).result("Hub details unavailable for: " + hubId);
                return;
            }

            // Read delay stage directly from our local cache (fed by ActiveMQ topic)
            int delayStage = localDelayStages.getOrDefault(hubId, 0);

            int baseHours = 24;
            int delayHours = delayStage * 6;
            int totalEstimatedHours = baseHours + delayHours;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("hub_id", hubId);
            response.put("province", hubDetails.get("province"));
            response.put("sorting_center", hubDetails.get("sorting_center"));
            response.put("delay_stage", delayStage);
            response.put("estimated_transit_hours", totalEstimatedHours);
            response.put("status", delayStage > 4 ? "SEVERE_DELAY" : "ON_SCHEDULE");

            ctx.json(response);
        });
    }

    private static void initMqConsumer() {
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
                        String body = textMessage.getText();
                        Map<String, Object> data = objectMapper.readValue(body, new TypeReference<>() {});
                        if (data.containsKey("hubId") && data.containsKey("stage")) {
                            String hubId = data.get("hubId").toString().toUpperCase();
                            int stage = Integer.parseInt(data.get("stage").toString());
                            localDelayStages.put(hubId, stage);
                            System.out.println("TransitService updated local stage for " + hubId + " -> " + stage);
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing ActiveMQ message: " + e.getMessage());
                    }
                }
            });
            System.out.println("TransitService subscribed to ActiveMQ topic: " + MqConfig.TOPIC);
        } catch (Exception e) {
            System.err.println("Failed to initialize ActiveMQ consumer: " + e.getMessage());
        }
    }

    private static Map<String, Object> fetchHubDetails(String hubId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:7051/hubs/" + hubId))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch hub details: " + e.getMessage());
        }
        return null;
    }
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
