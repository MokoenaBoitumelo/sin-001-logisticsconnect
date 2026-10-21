package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;
import java.util.List;

public class IngestionServiceApp {

    public static void main(String[] args) {
        // Clean CSV records at startup
        List<Hub> cleanedHubs = CsvCleaner.loadAndCleanHubs("hubs-global.csv");

        Javalin app = Javalin.create().start(7050);

        // Health check endpoint
        app.get("/health", ctx -> ctx.result("OK"));

        // TODO: read and clean src/main/resources/hubs-global.csv (hubs, sorting centers, regional districts data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.

        // Stage 1 Endpoint: Expose cleaned hub records
        app.get("/hubs", ctx -> ctx.json(cleanedHubs));
    }
}
