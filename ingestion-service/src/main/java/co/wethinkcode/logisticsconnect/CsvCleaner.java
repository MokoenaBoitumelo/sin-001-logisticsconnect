package co.wethinkcode.logisticsconnect;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Utility class to parse, sanitize, and deduplicate hubs-global.csv records.
 */
public class CsvCleaner {

    public static List<Hub> loadAndCleanHubs(String resourcePath) {
        Map<String, Hub> hubMap = new LinkedHashMap<>();

        InputStream is = CsvCleaner.class.getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
        }

        try (CSVReader reader = new CSVReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String[] header = reader.readNext(); // Skip CSV header
            String[] line;

            while ((line = reader.readNext()) != null) {
                if (line.length < 4) continue;

                // 1. Trim padding and collapse internal double spaces
                String rawId = line[0].trim();
                String rawProvince = collapseSpaces(line[1].trim());
                String rawCenter = collapseSpaces(line[2].trim());
                String rawActive = line[3].trim();

                // Skip entries without an ID
                if (rawId.isEmpty()) continue;

                // 2. Normalize Casing
                String cleanId = rawId.toUpperCase();
                String cleanProvince = cleanId.isEmpty() ? "Unknown" : toTitleCase(rawProvince);
                String cleanCenter = toTitleCase(rawCenter);

                // 3. Normalize Booleans
                boolean cleanActive = parseBoolean(rawActive);

                Hub hub = new Hub(cleanId, cleanProvince, cleanCenter, cleanActive);

                // 4. Deduplication: Prefer active record if duplicate ID is encountered
                if (!hubMap.containsKey(cleanId) || hub.isActive()) {
                    hubMap.put(cleanId, hub);
                }
            }
        } catch (IOException | CsvValidationException e) {
            e.printStackTrace();
        }

        return new ArrayList<>(hubMap.values());
    }

    private static String collapseSpaces(String text) {
        return text.replaceAll("\\s+", " ");
    }

    private static String toTitleCase(String text) {
        if (text == null || text.isBlank()) return "Unknown";
        String[] words = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

    private static boolean parseBoolean(String value) {
        if (value == null) return false;
        String val = value.trim().toLowerCase();
        return Set.of("y", "yes", "1", "true").contains(val);
    }
}