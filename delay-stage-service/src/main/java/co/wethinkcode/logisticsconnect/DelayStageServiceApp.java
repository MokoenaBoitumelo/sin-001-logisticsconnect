package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DelayStageServiceApp {

    private static final Map<String, Integer> delayStages = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7052);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Tracks the Transit Delay Stage (0-8, e.g. weather shutdowns).)
        // Add domain endpoints for delay-stage-service here.

        // GET /delay-stage/{hubId}
        app.get("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").toUpperCase();
            int stage = delayStages.getOrDefault(hubId, 0); // Default stage 0 (no delay)
            ctx.json(Map.of("hubId", hubId, "stage", stage));
        });

        // POST /delay-stage/{hubId} with body {"stage": 3}
        app.post("/delay-stage/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").toUpperCase();
            Map<?, ?> body = objectMapper.readValue(ctx.body(), Map.class);

            if (body.containsKey("stage")) {
                int stage = Integer.parseInt(body.get("stage").toString());
                delayStages.put(hubId, stage);
                ctx.json(Map.of("hubId", hubId, "stage", stage, "status", "updated"));
            } else {
                ctx.status(400).result("Missing 'stage' parameter");
            }
        });
    }
}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)
