package ru.opengd77.satupdate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class SatelliteConfigParser {
    private SatelliteConfigParser() {}

    static List<SatelliteConfig> parse(InputStream in) throws IOException {
        List<SatelliteConfig> list = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
        String line;
        boolean first = true;
        while ((line = br.readLine()) != null) {
            if (first) { first = false; continue; }
            if (line.trim().isEmpty()) continue;
            String[] p = line.split(",", -1);
            if (p.length < 11) continue;
            String cat = p[0].trim();
            if (cat.endsWith("U")) cat = cat.substring(0, cat.length() - 1);
            int catalog;
            try { catalog = Integer.parseInt(cat.trim()); }
            catch (NumberFormatException e) { continue; }

            list.add(new SatelliteConfig(
                    catalog,
                    p[1].trim(),
                    p[2].trim(), p[3].trim(), p[4].trim(), p[5].trim(),
                    p[6].trim(), p[7].trim(), p[8].trim(), p[9].trim(), p[10].trim()));
        }
        return list;
    }
}
