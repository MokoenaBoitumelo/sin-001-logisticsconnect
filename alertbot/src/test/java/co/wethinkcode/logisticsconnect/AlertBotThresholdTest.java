package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AlertBotThresholdTest {

    private static final int SEVERE_DELAY_THRESHOLD = 4;

    private boolean shouldAlert(int stage) {
        return stage >= SEVERE_DELAY_THRESHOLD;
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 6, 7, 8})
    @DisplayName("Should trigger alert for delay stages at or above threshold 4")
    void testShouldTriggerAlert(int stage) {
        assertTrue(shouldAlert(stage));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    @DisplayName("Should NOT trigger alert for delay stages below threshold 4")
    void testShouldNotTriggerAlert(int stage) {
        assertFalse(shouldAlert(stage));
    }

}