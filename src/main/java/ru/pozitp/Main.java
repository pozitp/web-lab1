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
    private static final BigDecimal X_MIN = new BigDecimal("-3");
    private static final BigDecimal X_MAX = new BigDecimal("5");

    private static final double Y_MIN = -5.0;
    private static final double Y_MAX = 5.0;
    private static final double EPS = 1e-9;

    private Main() {

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
                writeErrorResponse(400, "Bad Request", validation.errors, elapsedMs);
                return;
            }


            BigDecimal x = validation.x;
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
            List<String> errors = new ArrayList<>();
            errors.add("Internal error: " + ex.getMessage());
            writeErrorResponse(500, "Internal Server Error", errors, elapsedMs);
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
        List<String> errors = new ArrayList<>();

        BigDecimal x = null;
        Double y = null;
        Double r = null;

        if (!params.containsKey("x")) {
            errors.add("Missing parameter: x");
        }
        if (!params.containsKey("y")) {
            errors.add("Missing parameter: y");
        }
        if (!params.containsKey("r")) {
            errors.add("Missing parameter: r");
        }

        if (errors.isEmpty()) {
            try {
                x = new BigDecimal(params.get("x"));
            } catch (Exception ex) {
                errors.add("Parameter x must be a number.");
            }
            try {
                y = Double.parseDouble(params.get("y"));
            } catch (Exception ex) {
                errors.add("Parameter y must be a number.");
            }
            try {
                r = Double.parseDouble(params.get("r"));
            } catch (Exception ex) {
                errors.add("Parameter r must be a number.");
            }
        }

        if (errors.isEmpty() && x != null) {
            if (x.compareTo(X_MIN) < 0 || x.compareTo(X_MAX) > 0) {
                errors.add(String.format(Locale.US, "Parameter x must be between %s and %s.", X_MIN.toPlainString(), X_MAX.toPlainString()));
            }
        }
        if (errors.isEmpty() && y != null) {
            if (y < Y_MIN || y > Y_MAX) {
                errors.add(String.format(Locale.US, "Parameter y must be between %.1f and %.1f.", Y_MIN, Y_MAX));
            }
        }
        if (errors.isEmpty() && r != null) {
            if (r <= 0) {
                errors.add("Parameter r must be positive.");
            } else {
                boolean allowedMatched = false;
                for (Double allowed : ALLOWED_R) {
                    if (Math.abs(allowed - r) < EPS) {
                        allowedMatched = true;
                        break;
                    }
                }
                if (!allowedMatched) {
                    errors.add("Parameter r is not within the allowed set (1, 1.5, 2, 2.5, 3).");
                }
            }
        }

        if (!errors.isEmpty()) {
            return Validation.error(errors);
        }
        return Validation.ok(x, y, r);
    }

    private static boolean checkHit(BigDecimal xDecimal, double y, double r) {
        double x = xDecimal.doubleValue();
        double halfR = r / 2.0;
        if (x >= -EPS && y >= -EPS) {
            if (x <= halfR + EPS && y <= halfR + EPS) {
                double radiusSquared = halfR * halfR;
                return x * x + y * y <= radiusSquared + EPS;
            }
            return false;
        }
        if (x >= -EPS && y <= EPS) {
            if (x <= halfR + EPS && y >= -halfR - EPS) {
                return y >= x - halfR - EPS;
            }
            return false;
        }

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
        json.append("\"x\":").append(quote(formatNumber(record.x))).append(',');
        json.append("\"y\":").append(formatNumber(record.y)).append(',');

        json.append("\"r\":").append(formatNumber(record.r)).append(',');
        json.append("\"hit\":").append(record.hit).append(',');
        json.append("\"currentTime\":").append(quote(record.formattedTime())).append(',');
        json.append("\"processingTimeMs\":").append(formatNumber(record.processingTimeMs));

        json.append('}');
    }


    private static void writeErrorResponse(int statusCode, String statusText, List<String> errors, double processingMs) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"status\":\"error\",");
        json.append("\"errors\":[");
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) json.append(',');
            json.append(quote(errors.get(i)));
        }
        json.append(']').append(',');
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

    private static String formatNumber(BigDecimal value) {
        if (value == null) return "null";
        return value.toPlainString();
    }

    private static String formatNumber(double value) {
        return formatNumber(BigDecimal.valueOf(value));
    }


    private static final class Validation {
        private final BigDecimal x;
        private final double y;
        private final double r;
        private final List<String> errors;

        private Validation(BigDecimal x, double y, double r, List<String> errors) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.errors = errors;
        }

        private static Validation ok(BigDecimal x, double y, double r) {
            return new Validation(x, y, r, null);
        }

        private static Validation error(List<String> errors) {
            return new Validation(null, Double.NaN, Double.NaN, errors);

        }


        private boolean isValid() {
            return errors == null || errors.isEmpty();
        }
    }

    private static final class HitRecord {
        private final BigDecimal x;
        private final double y;
        private final double r;
        private final boolean hit;
        private final OffsetDateTime timestamp;
        private final double processingTimeMs;

        private HitRecord(BigDecimal x, double y, double r, boolean hit, OffsetDateTime timestamp, double processingTimeMs) {
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
