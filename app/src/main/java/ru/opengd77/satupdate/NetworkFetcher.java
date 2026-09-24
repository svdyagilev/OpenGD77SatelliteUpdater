package ru.opengd77.satupdate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class NetworkFetcher {
    private NetworkFetcher() {}

    static String get(String urlText) throws IOException {
        URL url = new URL(urlText);
        if (!"https".equalsIgnoreCase(url.getProtocol()))
            throw new IOException("Only HTTPS URLs are accepted");
        HttpURLConnection c = (HttpURLConnection)url.openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "OpenGD77-SatUpdate/0.1 Android");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        try (InputStream in = c.getInputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }
}
