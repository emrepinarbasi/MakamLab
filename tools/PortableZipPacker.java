import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a deterministic UTF-8/NFC ZIP that extracts with intact Turkish names on Windows. */
public final class PortableZipPacker {
    private static final long ZIP_EPOCH_MILLIS = 315_532_800_000L; // 1980-01-01 UTC

    private PortableZipPacker() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: PortableZipPacker <root-directory> <output.zip>");
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("Root directory not found: " + root);
        }
        Files.createDirectories(output.getParent());

        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !shouldExclude(root.relativize(path)))
                    .forEach(files::add);
        }
        files.sort((left, right) -> entryName(root, left).compareTo(entryName(root, right)));

        Set<String> emitted = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(output)), StandardCharsets.UTF_8)) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            byte[] buffer = new byte[128 * 1024];
            for (Path file : files) {
                String name = entryName(root, file);
                if (!emitted.add(name)) {
                    throw new IOException("Duplicate name after Unicode normalization: " + name);
                }
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(ZIP_EPOCH_MILLIS);
                zip.putNextEntry(entry);
                try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file))) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            zip.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static String entryName(Path root, Path file) {
        String raw = root.relativize(file).toString().replace('\\', '/');
        return Normalizer.normalize(raw, Normalizer.Form.NFC);
    }

    private static boolean shouldExclude(Path relative) {
        String name = relative.getFileName().toString();
        return name.equals(".DS_Store") || name.startsWith("._")
                || name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdb");
    }
}
