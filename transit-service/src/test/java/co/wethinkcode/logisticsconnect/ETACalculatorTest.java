package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ETACalculatorTest {

    private int calculateEtaHours(int delayStage) {
        int baseHours = 24;
        int delayHours = delayStage * 6;
        return baseHours + delayHours;
    }

    private String determineStatus(int delayStage) {
        return delayStage > 4 ? "SEVERE_DELAY" : "ON_SCHEDULE";
    }

    @ParameterizedTest
    @CsvSource({
            "0, 24, ON_SCHEDULE",
            "1, 30, ON_SCHEDULE",
            "3, 42, ON_SCHEDULE",
            "4, 48, ON_SCHEDULE",
            "5, 54, SEVERE_DELAY",
            "8, 72, SEVERE_DELAY"
    })
    @DisplayName("Should correctly calculate total transit hours and status based on delay stage")
    void testEtaCalculation(int delayStage, int expectedHours, String expectedStatus) {
        assertEquals(expectedHours, calculateEtaHours(delayStage));
        assertEquals(expectedStatus, determineStatus(delayStage));
    }

}