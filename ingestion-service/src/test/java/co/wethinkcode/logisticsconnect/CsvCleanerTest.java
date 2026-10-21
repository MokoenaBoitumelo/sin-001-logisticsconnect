package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvCleanerTest {

    @Test
    @DisplayName("Should successfully parse and clean hubs-global.csv records")
    void testLoadAndCleanHubs() {
        List<Hub> hubs = CsvCleaner.loadAndCleanHubs("hubs-global.csv");

        assertNotNull(hubs);
        assertFalse(hubs.isEmpty(), "Hub list should not be empty");

        // Verify H-501 record cleaning (casing and boolean parsing)
        Hub h501 = hubs.stream()
                .filter(h -> "H-501".equals(h.getHubId()))
                .findFirst()
                .orElse(null);

        assertNotNull(h501);
        assertEquals("Western Cape", h501.getProvince());
        assertEquals("Cape Town Port", h501.getSortingCenter());
        assertTrue(h501.isActive());
    }

    @Test
    @DisplayName("Should handle missing resource gracefully")
    void testMissingResourceThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            CsvCleaner.loadAndCleanHubs("non-existent.csv");
        });
    }

}