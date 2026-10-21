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