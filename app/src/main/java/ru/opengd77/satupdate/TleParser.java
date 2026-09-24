package ru.opengd77.satupdate;

import java.util.LinkedHashMap;
import java.util.Map;

final class TleParser {
    private TleParser() {}

    static Map<Integer, TleEntry> parse(String text) {
        Map<Integer, TleEntry> out = new LinkedHashMap<>();
        String[] lines = text.replace("\r", "").split("\n");
        String pendingName = "";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("1 ") && i + 1 < lines.length && lines[i + 1].startsWith("2 ")) {
                String line2 = lines[++i];
                int cat1 = parseCatalog(line);
                int cat2 = parseCatalog(line2);
                if (cat1 > 0 && cat1 == cat2) {
                    out.put(cat1, new TleEntry(pendingName, cat1, padRight(line, 69), padRight(line2, 69)));
                }
                pendingName = "";
            } else if (!line.trim().isEmpty()) {
                pendingName = line.trim();
            }
        }
        return out;
    }

    private static int parseCatalog(String line) {
        try {
            return Integer.parseInt(field(line, 2, 5).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    static String field(String line, int start, int length) {
        String padded = padRight(line, start + length);
        return padded.substring(start, start + length);
    }

    private static String padRight(String s, int size) {
        if (s.length() >= size) return s;
        StringBuilder b = new StringBuilder(s);
        while (b.length() < size) b.append(' ');
        return b.toString();
    }
}
