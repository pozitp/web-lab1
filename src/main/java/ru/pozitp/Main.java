package ru.pozitp;

import com.fastcgi.FCGIInterface;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Main {
    private static final FCGIInterface FCGI = new FCGIInterface();
    private static final List<HitRecord> HISTORY = new ArrayList<>();
    private static final int HISTORY_LIMIT = 100;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Set<Double> ALLOWED_R = Set.of(1.0, 1.5, 2.0, 2.5, 3.0);
    private static final double X_MIN = -3.0;
    private static final double X_MAX = 5.0;
    private static final double Y_MIN = -5.0;
    private static final double Y_MAX = 5.0;
    private static final double EPS = 1e-9;

    private Main() {
        // Prevent instantiation
    }

    public static void main(String[] args) {
        while (FCGI.FCGIaccept() >= 0) {
            handleRequest();
        }
    }

    private static void handleRequest() {
        long startNanos = System.nanoTime();
        try {
            Map<String, String> params = readParams();
            Validation validation = validate(params);
            if (!validation.isValid()) {
                double elapsedMs = toMillis(System.nanoTime() - startNanos);
                writeErrorResponse(400, "Bad Request", validation.error, elapsedMs);
                return;
            }

            double x = validation.x;
            double y = validation.y;
            double r = validation.r;

            boolean hit = checkHit(x, y, r);
            double elapsedMs = toMillis(System.nanoTime() - startNanos);
            HitRecord record = new HitRecord(x, y, r, hit, OffsetDateTime.now(), elapsedMs);
            addToHistory(record);
            List<HitRecord> snapshot = snapshotHistory();
            writeSuccessResponse(record, snapshot);
        } catch (Exception ex) {
            double elapsedMs = toMillis(System.nanoTime() - startNanos);
            writeErrorResponse(500, "Internal Server Error", "Internal error: " + ex.getMessage(), elapsedMs);
        }
    }

    private static Map<String, String> readParams() throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        String queryString = System.getProperty("QUERY_STRING");
        if (queryString != null && !queryString.isBlank()) {
            parseFormEncoded(queryString, params);
        }

        String method = System.getProperty("REQUEST_METHOD", "");
        if ("POST".equalsIgnoreCase(method)) {
            int contentLength = parseContentLength(System.getProperty("CONTENT_LENGTH"));
            if (contentLength > 0) {
                String body = readBody(System.in, contentLength);
                if (!body.isEmpty()) {
                    parseFormEncoded(body, params);
                }
            }
        }
        return params;
    }

    private static void parseFormEncoded(String data, Map<String, String> target) {
        String[] pairs = data.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key;
            String value;
            if (idx >= 0) {
                key = decodeComponent(pair.substring(0, idx));
                value = decodeComponent(pair.substring(idx + 1));
            } else {
                key = decodeComponent(pair);
                value = "";
            }
            if (!key.isEmpty()) {
                target.put(key, value);
            }
        }
    }

    private static String decodeComponent(String raw) {
        try {
            return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private static int parseContentLength(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String readBody(InputStream in, int contentLength) throws IOException {
        byte[] buffer = new byte[contentLength];
        int read = 0;
        while (read < contentLength) {
            int r = in.read(buffer, read, contentLength - read);
            if (r < 0) {
                break;
            }
            read += r;
        }
        return new String(buffer, 0, read, StandardCharsets.UTF_8);
    }

    private static Validation validate(Map<String, String> params) {
        if (!params.containsKey("x") || !params.containsKey("y") || !params.containsKey("r")) {
            return Validation.error("Missing required parameters (x, y, r).");
        }
        double x;
        double y;
        double r;
        try {
            x = Double.parseDouble(params.get("x"));
        } catch (NumberFormatException ex) {
            return Validation.error("Parameter x must be a number.");
        }
        try {
            y = Double.parseDouble(params.get("y"));
        } catch (NumberFormatException ex) {
            return Validation.error("Parameter y must be a number.");
        }
        try {
            r = Double.parseDouble(params.get("r"));
        } catch (NumberFormatException ex) {
            return Validation.error("Parameter r must be a number.");
        }

        if (x < X_MIN || x > X_MAX) {
            return Validation.error(String.format(Locale.US, "Parameter x must be between %.1f and %.1f.", X_MIN, X_MAX));
        }
        if (y < Y_MIN || y > Y_MAX) {
            return Validation.error(String.format(Locale.US, "Parameter y must be between %.1f and %.1f.", Y_MIN, Y_MAX));
        }
        if (r <= 0) {
            return Validation.error("Parameter r must be positive.");
        }
        if (ALLOWED_R.stream().noneMatch(allowed -> Math.abs(allowed - r) < EPS)) {
            return Validation.error("Parameter r is not within the allowed set (1, 1.5, 2, 2.5, 3).");
        }
        return Validation.ok(x, y, r);
    }

    private static boolean checkHit(double x, double y, double r) {
        double halfR = r / 2.0;
        // Quarter circle in the first quadrant
        if (x >= -EPS && y >= -EPS) {
            if (x <= halfR + EPS && y <= halfR + EPS) {
                double radiusSquared = halfR * halfR;
                return x * x + y * y <= radiusSquared + EPS;
            }
            return false;
        }
        // Right triangle in the fourth quadrant
        if (x >= -EPS && y <= EPS) {
            if (x <= halfR + EPS && y >= -halfR - EPS) {
                return y >= x - halfR - EPS;
            }
            return false;
        }
        // Rectangle in the third quadrant (including negative x and y)
        if (x <= EPS && y <= EPS) {
            return x >= -r - EPS && y >= -halfR - EPS;
        }
        return false;
    }

    private static void addToHistory(HitRecord record) {
        synchronized (HISTORY) {
            HISTORY.add(record);
            if (HISTORY.size() > HISTORY_LIMIT) {
                HISTORY.remove(0);
            }
        }
    }

    private static List<HitRecord> snapshotHistory() {
        synchronized (HISTORY) {
            return new ArrayList<>(HISTORY);
        }
    }

    private static void writeSuccessResponse(HitRecord current, List<HitRecord> history) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"status\":\"ok\",");
        json.append("\"data\":");
        appendRecord(json, current);
        json.append(',');
        json.append("\"history\":[");
        for (int i = 0; i < history.size(); i++) {
            appendRecord(json, history.get(i));
            if (i + 1 < history.size()) {
                json.append(',');
            }
        }
        json.append(']');
        json.append('}');
        writeResponse(200, "OK", json.toString());
    }

    private static void appendRecord(StringBuilder json, HitRecord record) {
        json.append('{');
        json.append("\"x\":").append(formatNumber(record.x)).append(',');
        json.append("\"y\":").append(formatNumber(record.y)).append(',');
        json.append("\"r\":").append(formatNumber(record.r)).append(',');
        json.append("\"hit\":").append(record.hit).append(',');
        json.append("\"currentTime\":").append(quote(record.formattedTime())).append(',');
        json.append("\"processingTimeMs\":").append(formatNumber(record.processingTimeMs));
        json.append('}');
    }

    private static void writeErrorResponse(int statusCode, String statusText, String message, double processingMs) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"status\":\"error\",");
        json.append("\"message\":").append(quote(message)).append(',');
        json.append("\"currentTime\":").append(quote(TIME_FORMATTER.format(OffsetDateTime.now()))).append(',');
        json.append("\"processingTimeMs\":").append(formatNumber(processingMs)).append(',');
        json.append("\"history\":[");
        List<HitRecord> history = snapshotHistory();
        for (int i = 0; i < history.size(); i++) {
            appendRecord(json, history.get(i));
            if (i + 1 < history.size()) {
                json.append(',');
            }
        }
        json.append(']');
        json.append('}');
        writeResponse(statusCode, statusText, json.toString());
    }

    private static void writeResponse(int statusCode, String statusText, String body) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        System.out.print("Status: " + statusCode + ' ' + statusText + "\r\n");
        System.out.print("Content-Type: application/json; charset=UTF-8\r\n");
        System.out.print("Content-Length: " + bodyBytes.length + "\r\n\r\n");
        System.out.write(bodyBytes, 0, bodyBytes.length);
        System.out.flush();
    }

    private static String quote(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return '"' + escaped + '"';
    }

    private static double toMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static String formatNumber(double value) {
        BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        return decimal.scale() < 0 ? decimal.setScale(0).toPlainString() : decimal.toPlainString();
    }

    private static final class Validation {
        private final double x;
        private final double y;
        private final double r;
        private final String error;

        private Validation(double x, double y, double r, String error) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.error = error;
        }

        private static Validation ok(double x, double y, double r) {
            return new Validation(x, y, r, null);
        }

        private static Validation error(String message) {
            return new Validation(Double.NaN, Double.NaN, Double.NaN, message);
        }

        private boolean isValid() {
            return error == null;
        }
    }

    private static final class HitRecord {
        private final double x;
        private final double y;
        private final double r;
        private final boolean hit;
        private final OffsetDateTime timestamp;
        private final double processingTimeMs;

        private HitRecord(double x, double y, double r, boolean hit, OffsetDateTime timestamp, double processingTimeMs) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.hit = hit;
            this.timestamp = timestamp;
            this.processingTimeMs = processingTimeMs;
        }

        private String formattedTime() {
            return TIME_FORMATTER.format(timestamp);
        }
    }
}
