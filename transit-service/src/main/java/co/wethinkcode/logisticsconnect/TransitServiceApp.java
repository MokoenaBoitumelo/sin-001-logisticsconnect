package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

public class TransitServiceApp {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7053);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Calculates estimated arrival windows based on hub and delay stage.)
        // Add domain endpoints for transit-service here.

        // GET /eta/{hubId}
        app.get("/eta/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").toUpperCase();

            // 1. Fetch Hub Details synchronously from Hub Service (:7051)
            Map<String, Object> hubDetails = fetchHubDetails(hubId);
            if (hubDetails == null) {
                ctx.status(404).result("Hub details unavailable for: " + hubId);
                return;
            }

            // 2. Fetch Delay Stage synchronously from Delay Stage Service (:7052)
            int delayStage = fetchDelayStage(hubId);

            // 3. Calculate ETA based on base transit hours + delay stage penalty
            int baseHours = 24;
            int delayHours = delayStage * 6; // Each stage adds 6 hours
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
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
