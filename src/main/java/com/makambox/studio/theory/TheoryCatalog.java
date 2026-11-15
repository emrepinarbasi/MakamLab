package com.makambox.studio.theory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class TheoryCatalog {
    public record Note(String key, String displayName, double ratio) {
        @Override public String toString() { return displayName; }
    }

    public record Makam(String name, String tonicKey) {
        @Override public String toString() { return displayName(name); }
    }

    private final Map<String, Note> notes = new LinkedHashMap<>();
    private final List<Makam> makams = new ArrayList<>();

    public static TheoryCatalog loadDefault() {
        TheoryCatalog catalog = new TheoryCatalog();
        try (InputStream stream = TheoryCatalog.class.getResourceAsStream("/theory/turkish-theory.tsv")) {
            if (stream == null) throw new IOException("Theory resource is missing");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] parts = line.split("\\t");
                    if (parts.length >= 3 && "NOTE".equals(parts[0])) {
                        String key = parts[1].trim();
                        catalog.notes.put(key, new Note(key, displayName(key), Double.parseDouble(parts[2])));
                    } else if (parts.length >= 3 && "MAKAM".equals(parts[0])) {
                        catalog.makams.add(new Makam(parts[1].trim(), parts[2].trim()));
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Türk makam kuramı verisi yüklenemedi", exception);
        }
        return catalog;
    }

    public List<Makam> makams() { return Collections.unmodifiableList(makams); }

    public List<Note> notes() { return List.copyOf(notes.values()); }

    public Note note(String key) {
        return Objects.requireNonNull(notes.get(key), "Unknown note: " + key);
    }

    public Note tonicFor(Makam makam) { return note(makam.tonicKey()); }

    public double relativeCent(Makam makam, Note target) {
        return 1200.0 * (Math.log(target.ratio() / tonicFor(makam).ratio()) / Math.log(2.0));
    }

    public List<Note> notesAround(Makam makam) {
        double tonicRatio = tonicFor(makam).ratio();
        return notes.values().stream()
                .filter(note -> note.ratio() >= tonicRatio * 0.48 && note.ratio() <= tonicRatio * 2.05)
                .sorted((a, b) -> Double.compare(a.ratio(), b.ratio()))
                .toList();
    }

    /**
     * Returns every explicitly named pitch center supported by the bundled
     * theory catalog, including its Kaba, Tiz and Tiz Tiz registers.
     *
     * <p>{@link #notesAround} deliberately remains the compact, familiar
     * register used by selectors and initial views. Full-record pitch-event
     * classification must not use that UI range, however: a real Muhayyer-
     * üstü sound would otherwise remain visible only in the raw F0 trace and
     * never become a named event. This method keeps each register's explicit
     * catalog identity (for example {@code tiz segah}) rather than folding its
     * frequency into the base octave.</p>
     */
    public List<Note> notesForPitchAnalysis(Makam makam) {
        Objects.requireNonNull(makam, "makam");
        // Validate that the supplied makam belongs to a usable theory context.
        tonicFor(makam);
        return notes.values().stream()
                .sorted((a, b) -> Double.compare(a.ratio(), b.ratio()))
                .toList();
    }

    public static String displayName(String raw) {
        Map<String, String> replacements = Map.ofEntries(
                Map.entry("acem", "Acem"), Map.entry("acemasiran", "Acemaşiran"),
                Map.entry("buselik", "Bûselik"), Map.entry("cargah", "Çargâh"),
                Map.entry("dugah", "Dügâh"), Map.entry("evic", "Eviç"),
                Map.entry("gerdaniye", "Gerdaniye"), Map.entry("gevest", "Geveşt"),
                Map.entry("hicaz", "Hicaz"), Map.entry("hisar", "Hisar"),
                Map.entry("huseyni", "Hüseynî"), Map.entry("irak", "Irak"),
                Map.entry("kurdi", "Kürdî"), Map.entry("mahur", "Mâhur"),
                Map.entry("muhayyer", "Muhayyer"), Map.entry("neva", "Nevâ"),
                Map.entry("rast", "Rast"), Map.entry("segah", "Segâh"),
                Map.entry("sehnaz", "Şehnâz"), Map.entry("sunbule", "Sünbüle"),
                Map.entry("ussak", "Uşşak"), Map.entry("huzzam", "Hüzzam"),
                Map.entry("yegah", "Yegâh"), Map.entry("zirgule", "Zirgüle"),
                Map.entry("dik", "Dik"), Map.entry("nim", "Nim"),
                Map.entry("kaba", "Kaba"), Map.entry("tiz", "Tiz"),
                Map.entry("asiran", "Aşiran")
        );
        String[] words = raw.toLowerCase(Locale.ROOT).split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) result.append(' ');
            result.append(replacements.getOrDefault(word,
                    Character.toUpperCase(word.charAt(0)) + word.substring(1)));
        }
        return result.toString();
    }
}
