package dev.aris.arisac.util;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class DiscordWebhook {

    private final String url;

    public DiscordWebhook(String url) {
        this.url = url;
    }

    public CompletableFuture<Void> sendEmbed(Embed embed) {
        return CompletableFuture.runAsync(() -> {
            try {
                String payload = buildPayload(embed);
                HttpURLConnection conn = (HttpURLConnection)
                        URI.create(url).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "ArisAC/1.0");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code < 200 || code >= 300) {
                    System.err.println("[ArisAC] Discord webhook failed: HTTP " + code);
                }
            } catch (Exception e) {
                System.err.println("[ArisAC] Discord webhook error: " + e.getMessage());
            }
        });
    }

    private String buildPayload(Embed embed) {
        StringBuilder fields = new StringBuilder();
        for (Field f : embed.fields) {
            if (fields.length() > 0) fields.append(",");
            fields.append("{\"name\":").append(json(f.name))
                  .append(",\"value\":").append(json(f.value))
                  .append(",\"inline\":").append(f.inline).append("}");
        }

        return "{\"embeds\":[{"
                + "\"title\":" + json(embed.title)
                + ",\"description\":" + json(embed.description)
                + ",\"color\":" + embed.color
                + ",\"fields\":[" + fields + "]"
                + ",\"footer\":{\"text\":" + json(embed.footer) + "}"
                + ",\"thumbnail\":{\"url\":" + json(embed.thumbnailUrl) + "}"
                + "}]}";
    }

    private String json(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "") + "\"";
    }

    public static class Embed {
        public String title = "";
        public String description = "";
        public int color = 0xFF0000;
        public String footer = "ArisAC";
        public String thumbnailUrl = "";
        public java.util.List<Field> fields = new java.util.ArrayList<>();

        public Embed addField(String name, String value, boolean inline) {
            fields.add(new Field(name, value, inline));
            return this;
        }
    }

    public record Field(String name, String value, boolean inline) {}
}
