import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class PayloadPacker {
    private static final byte[] MAGIC = "MBX2ZIP!".getBytes(StandardCharsets.US_ASCII);

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("stub payload output");
        Path stub = Path.of(args[0]);
        Path payload = Path.of(args[1]);
        Path output = Path.of(args[2]);
        try (BufferedOutputStream target = new BufferedOutputStream(Files.newOutputStream(output,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
             BufferedInputStream stubInput = new BufferedInputStream(Files.newInputStream(stub));
             BufferedInputStream payloadInput = new BufferedInputStream(Files.newInputStream(payload))) {
            stubInput.transferTo(target);
            payloadInput.transferTo(target);
            target.write(ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN)
                    .putLong(Files.size(payload)).array());
            target.write(MAGIC);
        }
    }
}
