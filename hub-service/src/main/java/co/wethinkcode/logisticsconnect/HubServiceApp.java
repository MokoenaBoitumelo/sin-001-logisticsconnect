package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HubServiceApp {

    private static final Map<String, Map<String, Object>> hubCache = new ConcurrentHashMap<>();
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7051);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Serves provinces and sorting centers (place-name source of truth).)
        // Add domain endpoints for hub-service here.
        // GET /hubs/{hubId} -> Fetch specific hub details
        app.get("/hubs/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId").toUpperCase();

            // Refresh/ensure cache has data from ingestion-service
            if (hubCache.isEmpty()) {
                fetchHubsFromIngestion();
            }

            Map<String, Object> hub = hubCache.get(hubId);
            if (hub != null) {
                ctx.json(hub);
            } else {
                ctx.status(404).result("Hub not found: " + hubId);
            }
        });
    }

    private static void fetchHubsFromIngestion() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:7050/hubs"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var hubsList = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>[]>() {});
                for (Map<String, Object> hub : hubsList) {
                    if (hub.containsKey("hub_id")) {
                        hubCache.put(((String) hub.get("hub_id")).toUpperCase(), hub);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch hubs from ingestion service: " + e.getMessage());
        }
    }
}
