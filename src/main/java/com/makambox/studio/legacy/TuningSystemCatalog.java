package com.makambox.studio.legacy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Safe text replacement for the v1.0 serialized tuning-system collection. */
public final class TuningSystemCatalog {
    public record SystemData(String name, Map<String, double[]> makamIntervals) {
        public double[] intervalsFor(String makamName) {
            double[] values = makamIntervals.get(makamName.toLowerCase(Locale.ROOT));
            return values == null ? null : values.clone();
        }
    }

    private static final Map<String, String> BUILT_INS = Map.ofEntries(
            Map.entry("Arel_Ezgi_Uzdilek", "arel.txt"),
            Map.entry("Karadeniz_41", "karadeniz.txt"),
            Map.entry("Yarman_21", "yarman21.txt"),
            Map.entry("Yarman_24", "yarman24.txt"),
            Map.entry("Yarman_79", "yarman79.txt"),
            Map.entry("Yavuzoglu_48", "yavuzoglu48.txt")
    );

    public Map<String, SystemData> loadBuiltIns() {
        Map<String, SystemData> result = new LinkedHashMap<>();
        for (String name : List.of("Arel_Ezgi_Uzdilek", "Karadeniz_41", "Yarman_21",
                "Yarman_24", "Yarman_79", "Yavuzoglu_48")) {
            String resource = "/legacy/tuning-systems/" + BUILT_INS.get(name);
            try (InputStream stream = TuningSystemCatalog.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Eksik akort sistemi kaynağı: " + resource);
                result.put(name, new SystemData(name, read(new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)))));
            } catch (IOException exception) {
                throw new IllegalStateException("Akort sistemi okunamadı: " + name, exception);
            }
        }
        return result;
    }

    public SystemData loadExternal(Path path) throws IOException {
        if (Files.size(path) > 2_000_000L) throw new IOException("Akort sistemi dosyası 2 MB sınırını aşıyor.");
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, double[]> data = read(reader);
            if (data.isEmpty()) throw new IOException("Dosyada makam ve cent aralığı bulunamadı.");
            String fileName = path.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String name = dot > 0 ? fileName.substring(0, dot) : fileName;
            return new SystemData(name, data);
        }
    }

    private static Map<String, double[]> read(BufferedReader reader) throws IOException {
        Map<String, double[]> result = new LinkedHashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isBlank() || line.startsWith("#")) continue;
            int bracket = line.indexOf('[');
            int closing = line.lastIndexOf(']');
            if (bracket < 1 || closing <= bracket) continue;
            String header = line.substring(0, bracket).trim();
            String makam = header.split("[\\s\\t,;]+", 2)[0].trim().toLowerCase(Locale.ROOT);
            String[] tokens = line.substring(bracket + 1, closing).split("[,;\\s]+");
            List<Double> values = new ArrayList<>();
            for (String token : tokens) {
                if (token.isBlank()) continue;
                try { values.add(Double.parseDouble(token)); }
                catch (NumberFormatException ignored) { }
            }
            if (!makam.isBlank() && values.size() >= 2) {
                double[] intervals = new double[values.size()];
                for (int i = 0; i < intervals.length; i++) intervals[i] = values.get(i);
                result.put(makam, intervals);
            }
        }
        return result;
    }
}
