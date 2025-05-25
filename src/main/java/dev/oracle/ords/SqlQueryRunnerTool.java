package dev.oracle.ords;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class SqlQueryRunnerTool {
    public String executeSqlQuery(String sql) throws Exception {
        String ordsSqlEndpoint = "http://localhost:8080/ords/hr1/_/sql";
        String basicAuth = "Basic aHIxOm9yYWNsZQ=="; // base64 of "hr1:oracle"

        URL url = new URL(ordsSqlEndpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", basicAuth);
        conn.setDoOutput(true);

        // Create JSON payload: {"statementText": "...", "limit": 2}
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("statementText", sql);
        payload.put("limit", 2);

        String jsonInput = objectMapper.writeValueAsString(payload);

        // Write JSON to output stream
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            try (InputStream errorStream = conn.getErrorStream();
                 Scanner scanner = new Scanner(errorStream, StandardCharsets.UTF_8)) {
                StringBuilder error = new StringBuilder();
                while (scanner.hasNext()) {
                    error.append(scanner.nextLine());
                }
                throw new RuntimeException("ORDS error: " + status + " - " + error.toString());
            }
        }

        // Read response
        StringBuilder response = new StringBuilder();
        try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
            while (scanner.hasNext()) {
                response.append(scanner.nextLine());
            }
        }

        JsonNode jsonResponse = objectMapper.readTree(response.toString());
        return jsonResponse.toPrettyString();
    }

}
